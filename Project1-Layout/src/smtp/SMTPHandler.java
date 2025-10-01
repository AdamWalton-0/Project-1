package smtp;

import merrimackutil.net.Log;
import maildir.MailBox;
import maildir.MailBoxException;
import maildir.MailMessage;
import util.Config;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One client session. Commands: HELO/EHLO, MAIL, RCPT, DATA, RSET, NOOP, QUIT. */
public class SMTPHandler implements Runnable {

    private enum Phase { NEW, HELO, MAIL, RCPT, DATA }

    private final Socket sk;
    private final Config.SmtpConfig cfg;
    private final Log lg;

    private Phase ph = Phase.NEW;
    private String from = null;
    private final List<String> rcpt = new ArrayList<>();
    private final StringBuilder buf = new StringBuilder();

    public SMTPHandler(Socket sk, Config.SmtpConfig cfg, Log lg) {
        this.sk = sk;
        this.cfg = cfg;
        this.lg = lg;
    }

    @Override
    public void run() {
        try (Socket s = sk;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            s.setSoTimeout(5 * 60 * 1000);
            send(out, 220, cfg.serverName + " ready");

            String line;
            while ((line = in.readLine()) != null) {
                String raw = line.trim();
                if (raw.isEmpty()) { send(out, 500, "empty"); continue; }
                String up = raw.toUpperCase(Locale.ROOT);

                if (up.startsWith("HELO ") || up.startsWith("EHLO ")) {
                    ph = Phase.HELO; resetTx();
                    send(out, 250, cfg.serverName + " hello " + raw.substring(5).trim());
                }
                else if (up.startsWith("MAIL FROM:")) {
                    if (ph != Phase.HELO && ph != Phase.MAIL) { send(out, 503, "seq"); continue; }
                    String a = path(raw.substring(10).trim());
                    if (a == null) { send(out, 501, "MAIL FROM:<user@host>"); continue; }
                    from = a; ph = Phase.MAIL; send(out, 250, "ok");
                }
                else if (up.startsWith("RCPT TO:")) {
                    if (ph != Phase.MAIL && ph != Phase.RCPT) { send(out, 503, "seq"); continue; }
                    String a = path(raw.substring(8).trim());
                    if (a == null) { send(out, 501, "RCPT TO:<user@host>"); continue; }
                    rcpt.add(a); ph = Phase.RCPT; send(out, 250, "ok");
                }
                else if (up.equals("DATA")) {
                    if (ph != Phase.RCPT || rcpt.isEmpty() || from == null) { send(out, 503, "seq"); continue; }
                    send(out, 354, "end with .");
                    readData(in, out);
                    resetTx();
                    ph = Phase.HELO;
                }
                else if (up.equals("RSET")) {
                    resetTx(); ph = Phase.HELO; send(out, 250, "ok");
                }
                else if (up.equals("NOOP")) {
                    send(out, 250, "ok");
                }
                else if (up.equals("QUIT")) {
                    send(out, 221, "bye"); break;
                }
                else {
                    send(out, 502, "nope");
                }
            }
        } catch (IOException e) {
            lg.log("io " + e.getMessage());
        } catch (Exception e) {
            lg.log("err " + e.getMessage());
        }
    }

    private void readData(BufferedReader in, BufferedWriter out) throws IOException {
        buf.setLength(0);
        String ln;
        while ((ln = in.readLine()) != null) {
            if (ln.equals(".")) {
                store();
                send(out, 250, "stored");
                return;
            }
            if (ln.startsWith("..")) ln = ln.substring(1); // dot unstuff
            buf.append(ln).append("\r\n");
        }
        send(out, 451, "link lost");
    }

    private void store() {
        String body = buf.toString();
        String subj = "";
        for (String l : body.split("\r\n")) {
            if (l.regionMatches(true, 0, "Subject:", 0, 8)) { subj = l.substring(8).trim(); break; }
        }
        MailMessage m = new MailMessage()
                .setFrom(from)
                .setRecipients(new ArrayList<>(rcpt))
                .setSubject(subj)
                .setBody(body);

        String localHost = cfg.serverName.toLowerCase(Locale.ROOT);
        for (String r : rcpt) {
            String[] parts = split(r);
            if (parts == null) { lg.log("bad rcpt " + r); continue; }
            String user = parts[0];
            String dom  = parts[1].toLowerCase(Locale.ROOT);
            if (!dom.equals(localHost)) { lg.log("skip remote " + r); continue; }
            try {
                MailBox mb = new MailBox(cfg.spool, user);
                mb.add(m);
                lg.log("-> " + user + "@" + cfg.serverName);
            } catch (MailBoxException e) {
                lg.log("store fail " + r + ": " + e.getMessage());
            }
        }
    }

    private void resetTx() { from = null; rcpt.clear(); buf.setLength(0); }

    private static String path(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
        if (!t.contains("@")) return null;
        return t;
    }

    private static String[] split(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
        int at = t.lastIndexOf('@');
        if (at <= 0 || at == t.length() - 1) return null;
        return new String[]{ t.substring(0, at), t.substring(at + 1) };
    }

    private static void send(BufferedWriter out, int code, String msg) throws IOException {
        out.write(code + " " + msg + "\r\n");
        out.flush();
    }
}