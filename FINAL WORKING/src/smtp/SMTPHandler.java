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

//Handles a single SMTP connection
public class SMTPHandler implements Runnable {
//tracks current SMTP phase
    private enum Phase { NEW, HELO, MAIL, RCPT, DATA }

    private final Socket sk;
    private final Config.SmtpConfig cfg;
    private final Log lg;
//current phase, sender, recipients, and message buffer
    private Phase ph = Phase.NEW;
    private String from = null;
    private final List<String> rcpt = new ArrayList<>();
    private final StringBuilder buf = new StringBuilder();
//Constructor to initialize fields
    public SMTPHandler(Socket sk, Config.SmtpConfig cfg, Log lg) {
        this.sk = sk;
        this.cfg = cfg;
        this.lg = lg;
    }

    @Override
//Run method to handle SMTP session
    public void run() {
        try (Socket s = sk;
//set up input and output streams
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
//set socket timeout to 5 minutes
            s.setSoTimeout(5 * 60 * 1000);
//send initial greeting
            send(out, 220, cfg.serverName + " ready");
//read and process commands
            String line;
            while ((line = in.readLine()) != null) {
                String raw = line.trim();
                if (raw.isEmpty()) { send(out, 500, "empty"); continue; }
                String up = raw.toUpperCase(Locale.ROOT);
//HELO / EHLO 
                if (up.startsWith("HELO ") || up.startsWith("EHLO ")) {
//HELO can be sent anytime, reset transaction state
                    ph = Phase.HELO; resetTx();
                    send(out, 250, cfg.serverName + " hello " + raw.substring(5).trim());
                }
//MAIL FROM
                else if (up.startsWith("MAIL FROM:")) {
                    if (ph != Phase.HELO && ph != Phase.MAIL) { send(out, 503, "seq"); continue; }
//extract and validate email path
                    String a = path(raw.substring(10).trim());
                    if (a == null) { send(out, 501, "MAIL FROM:<user@host>"); continue; }
                    from = a; ph = Phase.MAIL; send(out, 250, "ok");
                }
//RCPT TO
                else if (up.startsWith("RCPT TO:")) {
                    if (ph != Phase.MAIL && ph != Phase.RCPT) { send(out, 503, "seq"); continue; }
//extract and validate email path
                    String a = path(raw.substring(8).trim());
                    if (a == null) { send(out, 501, "RCPT TO:<user@host>"); continue; }
                    rcpt.add(a); ph = Phase.RCPT; send(out, 250, "ok");
                }
//DATA
                else if (up.equals("DATA")) {
                    if (ph != Phase.RCPT || rcpt.isEmpty() || from == null) { send(out, 503, "seq"); continue; }
//prompt for data
                    send(out, 354, "end with .");
//process and store message
                    readData(in, out);
//reset transaction state
                    resetTx();
//back to HELO phase
                    ph = Phase.HELO;
                }
//reset transaction state
                else if (up.equals("RSET")) {
                    resetTx(); ph = Phase.HELO; send(out, 250, "ok");
                }
//Response only
                else if (up.equals("NOOP")) {
                    send(out, 250, "ok");
                }
//End session
                else if (up.equals("QUIT")) {
                    send(out, 221, "bye"); break;
                }
//Unrecognized command
                else {
                    send(out, 502, "nope");
                }
            }
        } catch (IOException e) {
//log I/O errors
            lg.log("io " + e.getMessage());
        } catch (Exception e) {
//log other errors
            lg.log("err " + e.getMessage());
        }
    }
//Reads DATA from client
    private void readData(BufferedReader in, BufferedWriter out) throws IOException {
//clear buffer
        buf.setLength(0);
        String ln;
        while ((ln = in.readLine()) != null) {
//end of message
            if (ln.equals(".")) {
//store message in local mailboxes
                store();
//acknowledge storage
                send(out, 250, "stored");
                return;
            }
//handle dot-stuffing
            if (ln.startsWith("..")) ln = ln.substring(1);
//append line to buffer
            buf.append(ln).append("\r\n");
        }
//connection was lost before end of message
        send(out, 451, "link lost");
    }
//Store message in local mailboxes
    private void store() {
        String body = buf.toString();
        String subj = "";
//extract subject from body
        for (String l : body.split("\r\n")) {
            if (l.regionMatches(true, 0, "Subject:", 0, 8)) { subj = l.substring(8).trim(); break; }
        }
//create MailMessage object
        MailMessage m = new MailMessage()
                .setFrom(from)
                .setRecipients(new ArrayList<>(rcpt))
                .setSubject(subj)
                .setBody(body);
//determine local host for recipient filtering
        String localHost = cfg.serverName.toLowerCase(Locale.ROOT);
//process each recipient
        for (String r : rcpt) {
//Split address into user and domain
            String[] parts = split(r);
            if (parts == null) { lg.log("bad rcpt " + r); continue; }
            String user = parts[0];
            String dom  = parts[1].toLowerCase(Locale.ROOT);
//Skip if domain does not match local host
            if (!dom.equals(localHost)) { lg.log("skip remote " + r); continue; }
            try {
//Open mailbox
                MailBox mb = new MailBox(cfg.spool, user);
//Add message to mailbox
                mb.add(m);
                lg.log("-> " + user + "@" + cfg.serverName);
            } catch (MailBoxException e) {
                lg.log("store fail " + r + ": " + e.getMessage());
            }
        }
    }
//Clear transaction buffers
    private void resetTx() { from = null; rcpt.clear(); buf.setLength(0); }
//Helper to parse user@host 
    private static String path(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
        if (!t.contains("@")) return null;
        return t;
    }
//Helper to split email address into user and host
    private static String[] split(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
        int at = t.lastIndexOf('@');
        if (at <= 0 || at == t.length() - 1) return null;
        return new String[]{ t.substring(0, at), t.substring(at + 1) };
    }
//Helper to send response to client
    private static void send(BufferedWriter out, int code, String msg) throws IOException {
        out.write(code + " " + msg + "\r\n");
        out.flush();
    }
}
