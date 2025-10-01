package smtp;

import maildir.MailMessage;
import util.Config.SmtpConfig;
import merrimackutil.net.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;


public class SMTPHandler implements Runnable {

    private final Socket sock;
    private final SmtpConfig cfg;
    private final Log lg;
    private final MailQueue queue;


    SMTPHandler(Socket s, util.Config.SmtpConfig cfg, Log lg, MailQueue queue) { 
        this.sock = s;
        this.cfg = cfg;
        this.lg = lg;
        this.queue = queue;
    }

    enum State { 
        NEED_HELO, READY_FOR_MAIL, READY_FOR_RCPT_DATA, CLOSING
    }

    @Override 
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.US_ASCII))) {

            send(out, "220 " + cfg.serverName + " SMTP tinysmtp");
            State st = State.NEED_HELO;
            MailMessage msg = null;

            String line;
            while ((line = in.readLine()) != null) {
                lg.log("c: " + line);
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
                    if (from.isEmpty() || !from.contains("@")) { 
                        send(out, "501 Syntax: improper syntax"); 
                        continue;
                    }
                    if (!checkDomain(from)) { 
                        send(out, "504 5.5.2 " + from + ": Sender address rejected"); 
                        continue; 
                    }
                    msg = new MailMessage();
                    msg.setFrom(from);
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
                    if (to.isEmpty() || !to.contains("@")) {
                        send(out, "501 Syntax: improper syntax"); 
                        continue;
                    }
                    if (!checkDomain(to)) { 
                        send(out, "504 5.5.2 " + to + ": Recipient address rejected"); 
                        continue;
                    }
                    msg.addRecipient(to);
                    send(out, "250 Ok");
                    continue;
                }

                if (U.equals("DATA")) {
                    if (st != State.READY_FOR_RCPT_DATA || msg == null || msg.getTo().isEmpty()) {
                            send(out, "503 Bad sequence of commands"); 
                            continue; 
                        }
                    send(out, "354 End data with <CR><LF>.<CR><LF>");
                    StringBuilder body = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        if (line.equals("."))
                            break;
                            body.append(line).append("\r\n");
                    }
                    msg.setBody(body.toString());

                    queue.add(msg);
                    send(out, "250 Ok delivered message.");
                    st = State.READY_FOR_MAIL;
                    msg = null;
                    continue;
                }

                send(out, "502 5.5.2 Error: command not recognized");
            }
        } catch (IOException e) {
            try { 
                lg.log("conn closed: " + e.getMessage()); 
            } catch (Exception ignored) {

                }
        } finally {
            try { 
                sock.close(); 
            } catch (IOException ignored) {

            }
        }
    }

    private boolean checkDomain(String addr) {
        int at = addr.lastIndexOf('@');
        if (at < 0) return false;
        return addr.substring(at + 1).equals(cfg.serverName);
    }

    private void send(BufferedWriter out, String line) throws IOException {
        lg.log("s: " + line);
        out.write(line + "\r\n");
        out.flush();
    }
}
