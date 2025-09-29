
package mailuseragent;

import edu.merrimack.json.JSON;
import edu.merrimack.json.JSONObject;
import edu.merrimack.net.Log;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal UA skeleton to test your SMTP + POP3 servers.
 * - Reads smtpd.json and pop3d.json for ports/host.
 * - Sends one test email over SMTP.
 * - Lists inbox over POP3 (STAT + LIST) and quits.
 */
public class MailUserAgent {
    private static String smtpHost = "127.0.0.1";
    private static int smtpPort = 5000;
    private static String popHost = "127.0.0.1";
    private static int popPort = 5001;
    private static Log log;

    public static void main(String[] args) {
        try {
            log = new Log("ua.log");
            loadSmtpConfig("smtpd.json");
            loadPopConfig("pop3d.json");

            // Send a simple message via SMTP
            sendMail("zach@wonderland", "alice@wonderland", "Hello", "This is a test from UA.\r\nCheers!\r\n");

            // List POP3 mailbox (auth with demo creds from accounts.json)
            popList("alice", "password");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadSmtpConfig(String path) {
        try (FileReader fr = new FileReader(path)) {
            JSONObject cfg = (JSONObject) JSON.parse(fr);
            smtpPort = cfg.getInt("port");
            // host stays 127.0.0.1 for local testing
            log.info("UA SMTP config loaded, port=" + smtpPort);
        } catch (Exception e) {
            log.warn("UA failed to read smtpd.json; using defaults (" + smtpPort + ")");
        }
    }

    private static void loadPopConfig(String path) {
        try (FileReader fr = new FileReader(path)) {
            JSONObject cfg = (JSONObject) JSON.parse(fr);
            popPort = cfg.getInt("port");
            log.info("UA POP3 config loaded, port=" + popPort);
        } catch (Exception e) {
            log.warn("UA failed to read pop3d.json; using defaults (" + popPort + ")");
        }
    }

    private static void sendMail(String from, String to, String subject, String body) {
        try (Socket s = new Socket(smtpHost, smtpPort)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.US_ASCII));

            log.info("SMTP< " + in.readLine());
            send(out, "HELO <127.0.0.1>");
            log.info("SMTP> HELO <127.0.0.1>");
            log.info("SMTP< " + in.readLine());

            send(out, "MAIL FROM:" + from);
            log.info("SMTP> MAIL FROM:" + from);
            log.info("SMTP< " + in.readLine());

            send(out, "RCPT TO:" + to);
            log.info("SMTP> RCPT TO:" + to);
            log.info("SMTP< " + in.readLine());

            send(out, "DATA");
            log.info("SMTP> DATA");
            log.info("SMTP< " + in.readLine());

            String msg = "Subject: " + subject + "\r\n\r\n" + body + "\r\n.";
            sendRaw(out, msg + "\r\n");
            log.info("SMTP> <message body> .");
            log.info("SMTP< " + in.readLine());

            send(out, "QUIT");
            log.info("SMTP> QUIT");
            log.info("SMTP< " + in.readLine());
        } catch (IOException e) {
            log.error("UA SMTP error: " + e.getMessage());
        }
    }

    private static void popList(String user, String pass) {
        try (Socket s = new Socket(popHost, popPort)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.US_ASCII));

            log.info("POP< " + in.readLine());
            send(out, "USER " + user);
            log.info("POP> USER " + user);
            log.info("POP< " + in.readLine());

            send(out, "PASS " + pass);
            log.info("POP> PASS ******");
            log.info("POP< " + in.readLine());

            send(out, "STAT");
            log.info("POP> STAT");
            log.info("POP< " + in.readLine());

            send(out, "LIST");
            log.info("POP> LIST");
            String line;
            while ((line = in.readLine()) != null) {
                log.info("POP< " + line);
                if (line.equals(".")) break;
            }

            send(out, "QUIT");
            log.info("POP> QUIT");
            log.info("POP< " + in.readLine());
        } catch (IOException e) {
            log.error("UA POP3 error: " + e.getMessage());
        }
    }

    private static void send(BufferedWriter out, String line) throws IOException {
        out.write(line + "\r\n");
        out.flush();
    }

    private static void sendRaw(BufferedWriter out, String raw) throws IOException {
        out.write(raw);
        out.flush();
    }
}