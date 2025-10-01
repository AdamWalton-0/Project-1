package pop3;

/** Simple checked exception for POP3-specific failures. */
public class POP3Exception extends Exception {
    public POP3Exception(String msg) { super(msg); }
    public POP3Exception(String msg, Throwable cause) { super(msg, cause); }
}