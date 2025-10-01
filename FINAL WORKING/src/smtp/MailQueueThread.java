package smtp;

import java.util.List;
import java.util.Locale;

import merrimackutil.net.Log;
import maildir.MailBox;
import maildir.MailBoxException;
import maildir.MailMessage;

//Thread that takes messages from a queue and stores them in local mailboxes
public class MailQueueThread implements Runnable {
//shared queue, directory for mailboxes, local domain, logger, and flag
    private final MailQueue q;
    private final String spool;
    private final String host;
    private final Log lg;
    private volatile boolean run = true;
//Constructor to initialize fields
    public MailQueueThread(MailQueue q, String spool, String host, Log lg) {
        this.q = q;
        this.spool = spool;
//normalize host to lowercase
        this.host = host.toLowerCase(Locale.ROOT);
        this.lg = lg;
    }
//Stop the thread by using run as false
    public void stop() { run = false; }

    @Override public void run() {
//log thread start
        lg.log("queue start");
//loop while run is true
        while (run) {
            try {
//Take message from queue
                MailMessage m = q.take();
//Get list of recipients
                for (String addr : m.getTo()) {
//Split address into user and domain
                    String[] parts = splitAddr(addr);
//invalid address log and continue
                    if (parts == null) { lg.log("bad rcpt " + addr); continue; }
                    String user = parts[0];
                    String dom  = parts[1].toLowerCase(Locale.ROOT);
//Skip if domain does not match local host
                    if (!dom.equals(host)) { lg.log("skip remote " + addr); continue; }
                    try {
//Create mailbox
                        MailBox mb = new MailBox(spool, user);
//Add message to mailbox
                        mb.add(m);
//Log successful
                        lg.log("queued -> " + user + "@" + host);
                    } catch (MailBoxException e) {
//Log failure
                        lg.log("queue fail " + addr + ": " + e.getMessage());
                    }
                }
//catch interruption exception and ignore
            } catch (InterruptedException ignore) {}
        }
//log thread stop
        lg.log("queue stop");
    }

//Helper method to split email address into user and domain
    private static String[] splitAddr(String s) {
//null check
        if (s == null) return null;
        String t = s.trim();
//remove angle brackets
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
//find '@' 
        int at = t.lastIndexOf('@');
//invalid if no '@' or at start or end
        if (at <= 0 || at == t.length() - 1) return null;
//return user and domain 
        return new String[]{ t.substring(0, at), t.substring(at + 1) };
    }
}
