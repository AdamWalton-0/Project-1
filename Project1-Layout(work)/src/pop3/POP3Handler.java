package pop3;

import maildir.MailBox;
import maildir.MailBoxException;
import util.Config;
import merrimackutil.net.Log;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One POP3 session. Supported commands:
 * USER, PASS, STAT, LIST [msg], RETR msg, DELE msg, RSET, NOOP, QUIT
 */
public class POP3Handler implements Runnable {

    private enum Phase { AUTH, TRANS, UPDATE }

    private final Socket sk;
    private final Config.Pop3Config cfg;
    private final Config.AccountsDB accounts;
    private final Log lg;

    // session state
    private Phase ph = Phase.AUTH;
    private String pendingUser = null;
    private MailBox box = null;              // populated after successful login
    private final Set<Integer> del = new HashSet<>(); // local marks (for STAT/LIST view)

    public POP3Handler(Socket sk, Config.Pop3Config cfg, Config.AccountsDB accounts, Log lg) {
        this.sk = sk;
        this.cfg = cfg;
        this.accounts = accounts;
        this.lg = lg;
    }

    @Override
    public void run() {
        try (Socket s = sk;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            greet(out);

            String line;
            while ((line = in.readLine()) != null) {
                String raw = line.trim();
                if (raw.isEmpty()) { err(out, "empty command"); continue; }
                String up = raw.toUpperCase(Locale.ROOT);

                // ---- AUTH phase ----
                if (ph == Phase.AUTH) {
                    if (up.startsWith("USER ")) {
                        pendingUser = raw.substring(5).trim();
                        ok(out, "user accepted");
                    } else if (up.startsWith("PASS ")) {
                        if (pendingUser == null) { err(out, "use USER first"); continue; }
                        String pw = raw.substring(5).trim();
                        if (!accounts.validate(pendingUser, pw)) {
                            err(out, "auth failed");
                            // Clear any partial state for safety
                            pendingUser = null;
                            continue;
                        }
                        // login success: open mailbox snapshot
                        try {
                            box = new MailBox(cfg.spool, pendingUser);
                            box.load(); // snapshot of current "new/" at login
                        } catch (MailBoxException e) {
                            err(out, "mailbox error");
                            lg.log("mailbox open failed for " + pendingUser + ": " + e.getMessage());
                            continue;
                        }
                        ph = Phase.TRANS;
                        ok(out, pendingUser + " has " + visibleCount() + " messages");
                    } else if (up.equals("QUIT")) {
                        ok(out, "bye");
                        break;
                    } else if (up.equals("NOOP")) {
                        ok(out, "ok");
                    } else {
                        err(out, "auth required");
                    }
                    continue;
                }

                // ---- TRANSACTION phase ----
                if (ph == Phase.TRANS) {
                    if (up.equals("STAT")) {
                        int c = visibleCount();
                        long sz = visibleBytes();
                        ok(out, c + " " + sz);
                    }
                    else if (up.equals("LIST")) {
                        ok(out, "scan listing follows");
                        for (int i = 1; i <= box.count(); i++) {
                            if (del.contains(i)) continue;
                            long size = safeSize(i);
                            writeln(out, i + " " + size);
                        }
                        writeln(out, ".");
                    }
                    else if (up.startsWith("LIST ")) {
                        Integer id = parseIndex(raw.substring(5).trim());
                        if (id == null || !inRange(id)) { err(out, "no such message"); }
                        else if (del.contains(id)) { err(out, "message deleted"); }
                        else { ok(out, id + " " + safeSize(id)); }
                    }
                    else if (up.startsWith("RETR ")) {
                        Integer id = parseIndex(raw.substring(5).trim());
                        if (id == null || !inRange(id)) { err(out, "no such message"); }
                        else if (del.contains(id)) { err(out, "message deleted"); }
                        else {
                            try {
                                String msg = box.get(id);
                                ok(out, "message follows");
                                // dot-stuff and ensure CRLF
                                for (String ln : toCRLF(msg).split("\r\n", -1)) {
                                    if (ln.startsWith(".")) ln = "." + ln;
                                    writeln(out, ln);
                                }
                                writeln(out, ".");
                            } catch (MailBoxException e) {
                                err(out, "read failed");
                            }
                        }
                    }
                    else if (up.startsWith("DELE ")) {
                        Integer id = parseIndex(raw.substring(5).trim());
                        if (id == null || !inRange(id)) { err(out, "no such message"); }
                        else if (del.contains(id)) { err(out, "already deleted"); }
                        else {
                            try {
                                box.markDelete(id);
                                del.add(id);
                                ok(out, "deleted");
                            } catch (MailBoxException e) {
                                err(out, "delete failed");
                            }
                        }
                    }
                    else if (up.equals("RSET")) {
                        try {
                            box.unmarkAll();
                            del.clear();
                            ok(out, "reset");
                        } catch (Exception e) {
                            err(out, "reset failed");
                        }
                    }
                    else if (up.equals("NOOP")) {
                        ok(out, "ok");
                    }
                    else if (up.equals("QUIT")) {
                        // apply deletions
                        try {
                            box.commitDeletes();
                            ph = Phase.UPDATE;
                            ok(out, "bye");
                        } catch (MailBoxException e) {
                            err(out, "update failed");
                        }
                        break; // close after QUIT
                    }
                    else {
                        err(out, "unknown command");
                    }
                    continue;
                }

                // ---- UPDATE (should not get other commands) ----
                err(out, "session closed");
            }
        } catch (IOException e) {
            lg.log("pop3 io: " + e.getMessage());
        } catch (Exception e) {
            lg.log("pop3 err: " + e.getMessage());
        }
    }

    // ---------- helpers ----------

    private void greet(BufferedWriter out) throws IOException {
        ok(out, cfg.serverName + " POP3 ready");
    }

    private boolean inRange(int i) { return i >= 1 && i <= box.count(); }

    private int visibleCount() {
        int total = 0;
        for (int i = 1; i <= box.count(); i++) if (!del.contains(i)) total++;
        return total;
    }

    private long visibleBytes() {
        long sum = 0;
        for (int i = 1; i <= box.count(); i++) {
            if (del.contains(i)) continue;
            sum += safeSize(i);
        }
        return sum;
    }

    private long safeSize(int i) {
        try { return box.size(i); } catch (Exception e) { return 0L; }
    }

    private static Integer parseIndex(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private static String toCRLF(String s) {
        String t = (s == null) ? "" : s;
        t = t.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
        return t;
    }

    private static void ok(BufferedWriter out, String msg) throws IOException {
        writeln(out, "+OK " + msg);
    }

    private static void err(BufferedWriter out, String msg) throws IOException {
        writeln(out, "-ERR " + msg);
    }

    private static void writeln(BufferedWriter out, String s) throws IOException {
        out.write(s);
        out.write("\r\n");
        out.flush();
    }
}