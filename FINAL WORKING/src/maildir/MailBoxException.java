package maildir;

/** Thin wrapper for all mailbox-related errors. */
public class MailBoxException extends Exception {
    public MailBoxException(String msg) { super(msg); }
    public MailBoxException(String msg, Throwable cause) { super(msg, cause); }
}