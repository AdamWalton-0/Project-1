

import merrimackutil.json.types.JSONObject;
import merrimackutil.json.types.JSONArray;
import merrimackutil.json.JsonIO;
import merrimackutil.json.JSONSerializable;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Mail Delivery Agent (POP3) – working skeleton with:
 * - merrimackutil JSON config + file logging
 * - accounts.json loading
 * - thread pool + connection handler
 * - basic RFC1939 commands: USER, PASS, STAT, LIST, RETR, DELE, NOOP, RSET, QUIT
 *
 * NOTE: No console printing – logs only.
 */
public class Pop3Server {
    private static class Config { String spool, serverName, log, accounts; int port; }
    private static class Account { String user, pass, spool; }

    private static Log log;
    private static Config cfg;
    private static Map<String, Account> accounts = new HashMap<>();

    public static void main(String[] args) {
        try {
            cfg = loadConfig("pop3d.json");
            log = new Log(cfg.log);
            loadAccounts(cfg.accounts);
            log.info("pop3d starting on port " + cfg.port + ", serverName=" + cfg.serverName + ", spool=" + cfg.spool);

            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try (ServerSocket ss = new ServerSocket(cfg.port)) {
                while (true) {
                    Socket c = ss.accept();
                    pool.submit(new Conn(c));
                }
            }
        } catch (Exception e) {
            try { if (log != null) log.error("fatal: " + e.getMessage()); } catch (Exception ignored) {}
        }
    }

    private static Config loadConfig(String path) throws Exception {
        try (FileReader fr = new FileReader(path)) {
            JSONObject o = (JSONObject) JsonIO.read(fr);   // FIXED
            Config c = new Config();
            c.spool = o.getString("spool");
            c.serverName = o.getString("server-name");
            c.port = o.getInt("port");
            c.log = o.getString("log");
            c.accounts = o.getString("accounts");
            return c;
        }
    }

    private static void loadAccounts(String path) throws Exception {
        try (FileReader fr = new FileReader(path)) {
            JSONObject root = (JSONObject) JsonIO.read(fr);   // FIXED
            JSONArray arr = root.getArray("accounts");
            for (int i = 0; i < arr.size(); i++) {
                JSONObject a = arr.getObject(i);
                Account ac = new Account();
                ac.user = a.getString("username");
                ac.pass = a.getString("pass");
                ac.spool = a.getString("spool");
                accounts.put(ac.user, ac);
            }
            if (log != null) log.info("loaded accounts: " + accounts.keySet());
        }
    }

    // Simple file logger
    private static class Log {
        private final BufferedWriter writer;

        Log(String file) throws IOException {
            writer = Files.newBufferedWriter(Paths.get(file), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        private synchronized void write(String level, String msg) {
            try {
                writer.write("[" + new Date() + "][" + level + "] " + msg);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {}
        }

        void info(String msg) { write("INFO", msg); }
        void warn(String msg) { write("WARN", msg); }
        void error(String msg) { write("ERROR", msg); }
    }

    // --- Conn class (unchanged) ---
    private static class Conn implements Runnable {
        private final Socket sock;
        Conn(Socket s) { this.sock = s; }

        enum State { AUTH, TRANS, UPDATE }

        private Account acct;
        private State st = State.AUTH;
        private List<Path> msgs = new ArrayList<>();
        private Set<Integer> del = new HashSet<>();
        private long totalBytes = 0L;

        @Override public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.US_ASCII))) {

                send(out, "+OK welcome to " + cfg.serverName + " tinypop3 server.");

                String line;
                while ((line = in.readLine()) != null) {
                    String u = line.trim();
                    String U = u.toUpperCase(Locale.ROOT);
                    log.info("p: c: " + u);

                    if (U.startsWith("NOOP")) { send(out, "+OK"); continue; }
                    if (U.startsWith("RSET")) { del.clear(); send(out, "+OK"); continue; }
                    if (U.startsWith("QUIT")) { quit(out); break; }

                    if (st == State.AUTH) {
                        if (U.startsWith("USER ")) {
                            String user = u.substring(5).trim();
                            acct = accounts.get(user);
                            if (acct != null) send(out, "+OK Hello " + user); else send(out, "-ERR no such user");
                        } else if (U.startsWith("PASS ")) {
                            if (acct == null) { send(out, "-ERR USER first"); continue; }
                            String pass = u.substring(5).trim();
                            if (acct.pass.equals(pass)) { loadMaildrop(); st = State.TRANS; send(out, "+OK authenticated user."); }
                            else send(out, "-ERR invalid password");
                        } else {
                            send(out, "-ERR authenticate first");
                        }
                        continue;
                    }

                    if (st == State.TRANS) {
                        if (U.equals("STAT")) {
                            send(out, "+OK " + msgs.size() + " " + totalBytes);
                        } else if (U.equals("LIST")) {
                            send(out, "+OK " + msgs.size() + " messages (" + totalBytes + " octets)");
                            for (int i = 0; i < msgs.size(); i++) {
                                if (!del.contains(i + 1)) {
                                    long sz = sizeOf(msgs.get(i));
                                    send(out, (i + 1) + " " + sz);
                                }
                            }
                            send(out, ".");
                        } else if (U.startsWith("LIST ")) {
                            int id = parseId(u.substring(5));
                            if (okId(id)) send(out, "+OK " + id + " " + sizeOf(msgs.get(id - 1)));
                            else send(out, "-ERR no such message");
                        } else if (U.startsWith("RETR ")) {
                            int id = parseId(u.substring(5));
                            if (!okId(id)) { send(out, "-ERR no such message"); continue; }
                            Path p = msgs.get(id - 1);
                            byte[] data = Files.readAllBytes(p);
                            send(out, "+OK " + data.length + " octets");
                            writeRaw(out, new String(data, StandardCharsets.US_ASCII));
                            send(out, ".");
                        } else if (U.startsWith("DELE ")) {
                            int id = parseId(u.substring(5));
                            if (okId(id)) { del.add(id); send(out, "+OK message " + id + " deleted."); }
                            else send(out, "-ERR no such message");
                        } else {
                            send(out, "-ERR unknown or unsupported command");
                        }
                        continue;
                    }
                }
            } catch (IOException e) {
                try { log.warn("conn closed: " + e.getMessage()); } catch (Exception ignored) {}
            } finally {
                try { sock.close(); } catch (IOException ignored) {}
            }
        }

        private void quit(BufferedWriter out) throws IOException {
            for (int id : del) {
                if (okId(id)) {
                    try { Files.deleteIfExists(msgs.get(id - 1)); } catch (IOException ioe) { log.warn("delete fail: " + ioe.getMessage()); }
                }
            }
            send(out, "+OK Bye");
        }

        private void loadMaildrop() throws IOException {
            msgs.clear();
            del.clear();
            totalBytes = 0L;
            Path newDir = Paths.get(cfg.spool, acct.user, "new");
            if (Files.isDirectory(newDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(newDir)) {
                    for (Path p : ds) {
                        if (Files.isRegularFile(p)) {
                            msgs.add(p);
                            totalBytes += sizeOf(p);
                        }
                    }
                }
            }
            log.info("maildrop for " + acct.user + ": msgs=" + msgs.size() + ", bytes=" + totalBytes);
        }

        private boolean okId(int id) { return id >= 1 && id <= msgs.size() && !del.contains(id); }
        private int parseId(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; } }
        private long sizeOf(Path p) { try { return Files.size(p); } catch (IOException e) { return 0L; } }

        private void send(BufferedWriter out, String line) throws IOException {
            log.info("p: s: " + line);
            out.write(line + "\r\n");
            out.flush();
        }
        private void writeRaw(BufferedWriter out, String raw) throws IOException { out.write(raw); out.write("\r\n"); out.flush(); }
    }
}


