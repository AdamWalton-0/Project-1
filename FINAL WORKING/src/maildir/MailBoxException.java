package maildir;

public class MailBoxException extends Exception {// custom exeption if something goes wrong within the mailbox
    public MailBoxException(String msg) { super(msg); }// constuctor that makes messages for ex "spool not found"/ also sends message to exeption class
    public MailBoxException(String msg, Throwable cause) { super(msg, cause); }// constructor, takes a messsage aswell as og error.
}
