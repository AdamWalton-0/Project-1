package smtp;

import maildir.MailMessage;
import util.Config.SmtpConfig;
import merrimackutil.net.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

import java.util.concurrent.*;

public class MailQueueThread implements Runnable {
        private final MailQueue q;
        private final SmtpConfig cfg;
        private final Log lg;

        MailQueueThread(MailQueue q, util.Config.SmtpConfig cfg, Log lg) { 
            this.q = q; 
            this.cfg = cfg; 
            this.lg = lg; 
        }

        @Override 
        public void run() {
            while (true) {
                try {
                    MailMessage m = q.take();
                    for (String rcpt : m.getTo()) {
                        deliver(rcpt, m.toWireFormat());
                    }
                } catch (InterruptedException ie) {
                    return;
                } catch (Exception e) {
                    try { 
                        lg.log("delivery error: " + e.getMessage()); 
                    } 
                    catch (Exception ignored) {
                    }
                }
            }
        }

        private void deliver(String rcpt, String body) throws IOException {
            int at = rcpt.indexOf('@');
            if (at < 0) { 
                lg.log("invalid recipient: " + rcpt);
                return;
            }

            String user = rcpt.substring(0, at);
            String domain = rcpt.substring(at + 1);
            if (!domain.equals(cfg.serverName)) { 
                lg.log("dropping message for non-local domain: " + rcpt); 
                return; 
            }

            Path userDir = Paths.get(cfg.spool, user);
            Path tmp = userDir.resolve("tmp");
            Path nw = userDir.resolve("new");
            Files.createDirectories(tmp);
            Files.createDirectories(nw);

            String fn = Instant.now().toString() + "-" + ThreadLocalRandom.current().nextInt(100000);
            Path tmpFile = tmp.resolve(fn);
            Files.writeString(tmpFile, body, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
            Files.move(tmpFile, nw.resolve(fn), StandardCopyOption.ATOMIC_MOVE);

            lg.log("delivered to " + user + ": " + fn);
        }
    }
