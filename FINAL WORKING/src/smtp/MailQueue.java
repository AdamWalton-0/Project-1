package smtp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import maildir.MailMessage;


public class MailQueue {
    private final BlockingQueue<MailMessage> q = new LinkedBlockingQueue<>();
    public void put(MailMessage m) { if (m != null) q.offer(m); }
    public MailMessage take() throws InterruptedException { return q.take(); }
}

