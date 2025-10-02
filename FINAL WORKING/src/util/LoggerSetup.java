package util;

import java.io.IOException;
import merrimackutil.net.Log;

//Utitility class for setting up a log object
public final class LoggerSetup {
//Private constructor so class cannot be instantiated
    private LoggerSetup() {}
//creates and configures a log instance
    public static Log make(String file, String service) throws IOException {
//Create a new log object with file and service name
        Log lg = new Log(file, service);
//turn logging on
        lg.loggingOn();
//write an ititial log entry to confirm setup
        lg.log("log init [" + service + "] -> " + file);
//return ready-to-use log
        return lg;
    }

}
