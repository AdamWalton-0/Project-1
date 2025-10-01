package util;

import java.io.IOException;
import merrimackutil.net.Log;

//declares final class LoggerSetup with private constructor
public final class LoggerSetup {
    private LoggerSetup() {}

//static method to create and initialize a Log object
    public static Log make(String file, String service) throws IOException {
//create a new log object with file and service name
        Log lg = new Log(file, service);
//enable logging on the log object
        lg.loggingOn();
//Message indicating service and file 
        lg.log("log init [" + service + "] -> " + file);
//return object
        return lg;
    }
}
