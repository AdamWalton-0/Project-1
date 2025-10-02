package pop3;

//exeption for POP3 errors
public class POP3Exception extends Exception {
//Makes a POP3 error with a message
    public POP3Exception(String msg) { super(msg); }
//nested error
    public POP3Exception(String msg, Throwable cause) { super(msg, cause); }

}
