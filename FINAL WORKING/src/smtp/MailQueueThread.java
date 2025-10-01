package smtp;

import java.util.List;
import java.util.Locale;

import merrimackutil.net.Log;
import maildir.MailBox;
import maildir.MailBoxException;
import maildir.MailMessage;


public class MailQueueThread implements Runnable {
    private final MailQueue q;
    private final String spool;
    private final String host;
    private final Log lg;
    private volatile boolean run = true;

    public MailQueueThread(MailQueue q, String spool, String host, Log lg) {
        this.q = q;
        this.spool = spool;
        this.host = host.toLowerCase(Locale.ROOT);
        this.lg = lg;
    }

    public void stop() { run = false; }

    @Override public void run() {
        lg.log("queue start");
        while (run) {
            try {
                MailMessage m = q.take();
                for (String addr : m.getTo()) {
                    String[] parts = splitAddr(addr);
                    if (parts == null) { lg.log("bad rcpt " + addr); continue; }
                    String user = parts[0];
                    String dom  = parts[1].toLowerCase(Locale.ROOT);
                    if (!dom.equals(host)) { lg.log("skip remote " + addr); continue; }
                    try {
                        MailBox mb = new MailBox(spool, user);
                        mb.add(m);
                        lg.log("queued -> " + user + "@" + host);
                    } catch (MailBoxException e) {
                        lg.log("queue fail " + addr + ": " + e.getMessage());
                    }
                }
            } catch (InterruptedException ignore) {}
        }
        lg.log("queue stop");
    }


    private static String[] splitAddr(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("<") && t.endsWith(">")) t = t.substring(1, t.length() - 1).trim();
        int at = t.lastIndexOf('@');
        if (at <= 0 || at == t.length() - 1) return null;
        return new String[]{ t.substring(0, at), t.substring(at + 1) };
    }

}
