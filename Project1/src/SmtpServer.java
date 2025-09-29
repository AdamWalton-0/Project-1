import merrimackutil.json.types.JSONObject;
import merrimackutil.json.types.JSONArray;
import merrimackutil.json.types.JSONType;
import merrimackutil.json.JsonIO;
import merrimackutil.json.JSONSerializable;
  
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;


public class SmtpServer {

    private static class Config {
        String spool;
        String serverName;
        int port;
        String log;
    }

    private static Log log;
    private static Config cfg;

    private static class Log {
        private final BufferedWriter w;
        Log(String filename) throws IOException {
            Path path = Paths.get(filename);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        private synchronized void write(String level, String msg) {
            try {
                w.write(String.format("%s %s %s%n", Instant.now().toString(), level, msg));
                w.flush();
            } catch (IOException ignored) {}
        }
        void info(String msg) { 
            write("INFO", msg); 
        }
        void warn(String msg) {
             write("WARN", msg); 
            }
        void error(String msg) { 
            write("ERROR", msg); 
        }
    }

    public static void main(String[] args) {
        try {
            cfg = loadConfig("smtpd.json");
            log = new Log(cfg.log);
            log.info("smtpd starting on port " + cfg.port + ", serverName=" + cfg.serverName + ", spool=" + cfg.spool);
        } catch (Exception e) {
            try { 
                if (log != null) log.error("fatal: " + e.getMessage()); 
            } 
            catch (Exception ignored) {
            }
        }

       buildMailboxes(cfg.spool);
       queue = new MailQueue();
        Thread qThread = new Thread(new MailQueueThread(queue, cfg, log), "mail-queue");
        qThread.setDaemon(true);
        qThread.start();

        pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (ServerSocket ss = new ServerSocket(cfg.port)) {
            while (true) {
                Socket c = ss.accept();
                pool.submit(new Conn(c));
            }
       }
    }

    private static Config loadConfig(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) throw new FileNotFoundException("Config file not found: " + path);

        JSONObject object = JsonIO.readObject(file);
        Config config = new Config();
        config.spool = object.getString("spool");
        config.serverName = object.getString("server-name");
        config.port = object.getInt("port");
        config.log = object.getString("log");
        return config;
    }
}

private static final Map<String, Mailbox> mailboxes = new ConcurrentHashMap<>();

    private static class Mailbox {
        final String user;
        final Path root;
        final Path tmp;
        final Path newDir;

        Mailbox(String user, Path root) {
            this.user = user;
            this.root = root;
            this.tmp = root.resolve("tmp");
            this.newDir = root.resolve("new");
        }
    }

private static void buildMailboxes(String spool) {
        try {
            Path spoolPath = Paths.get(spool);
            if (!Files.exists(spoolPath)) Files.createDirectories(spoolPath);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(spoolPath)) {
                for (Path pth : ds) {
                    if (Files.isDirectory(pth)) {
                        String user = pth.getFileName().toString();
                        Path tmp = pth.resolve("tmp");
                        Path nw = pth.resolve("new");
                        mailboxes.put(user, new Mailbox(user, pth));
                        if (!Files.exists(tmp)) Files.createDirectories(tmp);
                        if (!Files.exists(nw)) Files.createDirectories(nw);
                    }
                }
            }
        } catch (IOException e) {
            try { 
                if (log != null) log.error("error building mailboxes: " + e.getMessage()); } 
            catch (Exception ignored) {
            }
        }
    }

private static class Conn implements Runnable {

        private final Socket sock;

        Conn(Socket s) { 
            this.sock = s;
        }

        enum State { 
            NEED_HELO, READY_FOR_MAIL, READY_FOR_RCPT_DATA, CLOSING
        }

        @Override public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.US_ASCII))) {

                send(out, "220 " + cfg.serverName + " SMTP tinysmtp");
                State st = State.NEED_HELO;
                MailMsg msg = null;

                String line;
                while ((line = in.readLine()) != null) {
                    log.info("s: c: " + line);
                    String u = line.trim();
                    String U = u.toUpperCase(Locale.ROOT);

                    if (U.startsWith("HELO")) {
                        send(out, "250 " + cfg.serverName + " Hello");
                        st = State.READY_FOR_MAIL;
                        continue;
                    }

                    if (U.startsWith("NOOP")) { 
                        send(out, "250 Ok"); 
                        continue;
                    }

                    if (U.startsWith("RSET")) {
                        if (st == State.READY_FOR_MAIL || st == State.READY_FOR_RCPT_DATA) {
                            msg = null;
                            send(out, "250 Ok");
                        } else send(out, "503 Bad sequence of commands");
                        continue;
                    }

                    if (U.startsWith("QUIT")) { 
                        send(out, "221 Bye"); 
                        break; 
                    }

                    if (U.startsWith("MAIL FROM:")) {
                        if (st != State.READY_FOR_MAIL) { 
                            send(out, "503 Bad sequence of commands"); 
                            continue; 
                        }
                        String from = u.substring("MAIL FROM:".length()).trim();
                        if (!checkDomain(from)) { 
                            send(out, "504 5.5.2 " + from + ": Sender address rejected"); 
                            continue; 
                        }
                        msg = new MailMsg();
                        msg.from = from;
                        st = State.READY_FOR_RCPT_DATA;
                        send(out, "250 Ok");
                        continue;
                    }

                    if (U.startsWith("RCPT TO:")) {
                        if (st != State.READY_FOR_RCPT_DATA || msg == null) { 
                            send(out, "503 Bad sequence of commands"); 
                            continue; 
                        }
                        String to = u.substring("RCPT TO:".length()).trim();
                        if (!checkDomain(to)) { 
                            send(out, "504 5.5.2 " + to + ": Recipient address rejected"); 
                            continue;
                        }
                        msg.rcpt.add(to);
                        send(out, "250 Ok");
                        continue;
                    }

                    if (U.equals("DATA")) {
                        if (st != State.READY_FOR_RCPT_DATA || msg == null || msg.rcpt.isEmpty()) {
                             send(out, "503 Bad sequence of commands"); 
                             continue; 
                            }
                        send(out, "354 End data with <CR><LF>.<CR><LF>");
                        while ((line = in.readLine()) != null) {
                            if (line.equals("."))
                             break;
                            msg.body.append(line).append("\r\n");
                        }
                        queue.add(msg);
                        send(out, "250 Ok delivered message.");
                        st = State.READY_FOR_MAIL;
                        msg = null;
                        continue;
                    }

                    send(out, "502 5.5.2 Error: command not recognized");
                }
            } catch (IOException e) {
                try { log.warn("conn closed: " + e.getMessage()); } catch (Exception ignored) {}
            } finally {
                try { sock.close(); } catch (IOException ignored) {}
            }
        }

        private boolean checkDomain(String addr) {
            int at = addr.lastIndexOf('@');
            if (at < 0) return false;
            return addr.substring(at + 1).equals(cfg.serverName);
        }

        private void send(BufferedWriter out, String line) throws IOException {
            log.info("s: s: " + line);
            out.write(line + "\r\n");
            out.flush();
        }
    }




