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


//Handles a POP3 client session 
public class POP3Handler implements Runnable {
//connection phases
    private enum Phase { AUTH, TRANS, UPDATE }
//initialize feilds 
    private final Socket sk;
    private final Config.Pop3Config cfg;
    private final Config.AccountsDB accounts;
    private final Log lg;
    private Phase ph = Phase.AUTH;
    private String pendingUser = null;
    private MailBox box = null;
    private final Set<Integer> del = new HashSet<>();
//Contructor 
    public POP3Handler(Socket sk, Config.Pop3Config cfg, Config.AccountsDB accounts, Log lg) {
        this.sk = sk;
        this.cfg = cfg;
        this.accounts = accounts;
        this.lg = lg;
    }

    @Override
    public void run() {
//Try-with-resources automatically closes resources
        try (Socket s = sk;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
//Send greeting when connection is established
            greet(out);

            String line;
//Read lines until end of stream
            while ((line = in.readLine()) != null) {
                String raw = line.trim();
                if (raw.isEmpty()) { err(out, "empty command"); continue; }
                String up = raw.toUpperCase(Locale.ROOT);

//AUTH phase, with expect USER and PASS commands
                if (ph == Phase.AUTH) {
                    if (up.startsWith("USER ")) {
//save username for next PASS command
                        pendingUser = raw.substring(5).trim();
                        ok(out, "user accepted");
                    } else if (up.startsWith("PASS ")) {
//Password requires a prior USER command
                        if (pendingUser == null) { err(out, "use USER first"); continue; }
                        String pw = raw.substring(5).trim();
//validate username and password
                        if (!accounts.validate(pendingUser, pw)) {
                            err(out, "auth failed");
//Reset state
                            pendingUser = null;
                            continue;
                        }
//Login successful then open mailbox
                        try {
                            box = new MailBox(cfg.spool, pendingUser);
                            box.load(); // snapshot of current "new/" at login
                        } catch (MailBoxException e) {
                            err(out, "mailbox error");
                            lg.log("mailbox open failed for " + pendingUser + ": " + e.getMessage());
                            continue;
                        }
//Transition to TRANSACTION phase
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

//TRANSACTION phase with STAT, LIST, RETR, DELE, RSET, NOOP, QUIT
                if (ph == Phase.TRANS) {
                    if (up.equals("STAT")) {
//Show number of messages and total size
                        int c = visibleCount();
                        long sz = visibleBytes();
                        ok(out, c + " " + sz);
                    }
                    else if (up.equals("LIST")) {
//List all messages with sizes
                        ok(out, "scan listing follows");
                        for (int i = 1; i <= box.count(); i++) {
                            if (del.contains(i)) continue;
                            long size = safeSize(i);
                            writeln(out, i + " " + size);
                        }
                        writeln(out, ".");
                    }
                    else if (up.startsWith("LIST ")) {
//List a specific message
                        Integer id = parseIndex(raw.substring(5).trim());
                        if (id == null || !inRange(id)) { err(out, "no such message"); }
                        else if (del.contains(id)) { err(out, "message deleted"); }
                        else { ok(out, id + " " + safeSize(id)); }
                    }
                    else if (up.startsWith("RETR ")) {
//Retrieve a specific message
                        Integer id = parseIndex(raw.substring(5).trim());
                        if (id == null || !inRange(id)) { err(out, "no such message"); }
                        else if (del.contains(id)) { err(out, "message deleted"); }
                        else {
                            try {
                                String msg = box.get(id);
                                ok(out, "message follows");
//Output message with dot-stuffing
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
//Mark a message for deletion
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
//Reset all deletion marks
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
//Apply deletions and close mailbox
                        try {
                            box.commitDeletes();
                            ph = Phase.UPDATE;
                            ok(out, "bye");
                        } catch (MailBoxException e) {
                            err(out, "update failed");
                        }
//exit loop
                        break;
                    }
                    else {
                        err(out, "unknown command");
                    }
                    continue;
                }

//UPDATE
                err(out, "session closed");
            }
        } catch (IOException e) {
            lg.log("pop3 io: " + e.getMessage());
        } catch (Exception e) {
            lg.log("pop3 err: " + e.getMessage());
        }
    }

//Helper methods

//Send greeting message
    private void greet(BufferedWriter out) throws IOException {
        ok(out, cfg.serverName + " POP3 ready");
    }
//Check if message index is valid
    private boolean inRange(int i) { return i >= 1 && i <= box.count(); }
//Count of messages not marked for deletion
    private int visibleCount() {
        int total = 0;
        for (int i = 1; i <= box.count(); i++) if (!del.contains(i)) total++;
        return total;
    }
//Calculate total size of messages not marked for deletion
    private long visibleBytes() {
        long sum = 0;
        for (int i = 1; i <= box.count(); i++) {
            if (del.contains(i)) continue;
            sum += safeSize(i);
        }
        return sum;
    }
//Safely get size of a message
    private long safeSize(int i) {
        try { return box.size(i); } catch (Exception e) { return 0L; }
    }
//Parse a string to an integer index
    private static Integer parseIndex(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
//Normalize line endings to CRLF
    private static String toCRLF(String s) {
        String t = (s == null) ? "" : s;
        t = t.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
        return t;
    }
//Send OK response
    private static void ok(BufferedWriter out, String msg) throws IOException {
        writeln(out, "+OK " + msg);
    }
//Send ERR response
    private static void err(BufferedWriter out, String msg) throws IOException {
        writeln(out, "-ERR " + msg);
    }
//Write a line with CRLF
    private static void writeln(BufferedWriter out, String s) throws IOException {
        out.write(s);
        out.write("\r\n");
        out.flush();
    }
}
