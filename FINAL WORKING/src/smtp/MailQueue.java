package smtp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import maildir.MailMessage;

//Thread safe queue for mailmessage
public class MailQueue {
//Queue to hold mail messages objects
    private final BlockingQueue<MailMessage> q = new LinkedBlockingQueue<>();
//Add message to the queue if not null
    public void put(MailMessage m) { if (m != null) q.offer(m); }
//Take and remove message from the queue block if empty
    public MailMessage take() throws InterruptedException { return q.take(); }
}
