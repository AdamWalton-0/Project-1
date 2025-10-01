package smtp;

import maildir.MailMessage;
import java.util.concurrent.*;

public class MailQueue {

    private final LinkedBlockingQueue<MailMessage> q = new LinkedBlockingQueue<>();

    void add(MailMessage m) {
        q.offer(m);
    }

    MailMessage take() throws InterruptedException {
        return q.take();
    }
}
