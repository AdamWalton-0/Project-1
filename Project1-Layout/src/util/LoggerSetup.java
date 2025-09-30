package util;

import java.io.IOException;
import merrimackutil.net.Log;

/** Build a merrimackutil Log bound to a file. */
public final class LoggerSetup {
    private LoggerSetup() {}

    public static Log make(String file, String service) throws IOException {
        // Log has (String,String) and (String,String,String) ctors.
        Log lg = new Log(file, service);   // <— FIX: two-arg ctor
        // Optional: ensure logging is enabled (method exists in the class).
        lg.loggingOn();
        lg.log("log init [" + service + "] -> " + file);
        return lg;
    }
}