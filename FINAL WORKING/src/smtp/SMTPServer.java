package smtp;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import merrimackutil.net.Log;
import util.Config;
import util.ConfigLoader;
import util.LoggerSetup;


public class SMTPServer {
//config, log, and thread pool
    private final Config.SmtpConfig cfg;
    private final Log lg;
    private final ExecutorService exec;
//Constructor to initialize config, log, and thread pool
    public SMTPServer(Config.SmtpConfig cfg) throws IOException {
        this.cfg = cfg;
//Create file logger
        this.lg  = LoggerSetup.make(cfg.log, "smtpd");
//Log boot
        this.lg.log("boot " + cfg);
//Determine number of threads and create fixed thread pool
        int n = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        this.exec = Executors.newFixedThreadPool(n);
    }
//Start the SMTP server
    public void start() {
//Listen on configured port
        try (ServerSocket srv = new ServerSocket(cfg.port)) {
            lg.log("listen " + cfg.port + " | host=" + cfg.serverName + " | spool=" + cfg.spool);
            while (true) {
//Accept incoming connection
                Socket sock = srv.accept();
                lg.log("conn " + sock.getRemoteSocketAddress());
//Handle connection in a new thread
                exec.execute(new SMTPHandler(sock, cfg, lg));
            }
        } catch (IOException e) {
//Log fatal error
            lg.log("fatal " + e.getMessage());
        }
    }
//Main method to load config and start server
public static void main(String[] args) {
    try {
//Determine config file path
        String p = (args.length > 0) ? args[0] : "smtpd.json";
//Check if file exists otherwise try alternative path
        if (!new File(p).exists()) {
            String alt = "config/smtpd.json";
            if (new File(alt).exists()) p = alt;
        }

//Load SMTP config from JSON
        util.Config.SmtpConfig cfg = util.ConfigLoader.loadSmtp(new File(p));
//Start SMTP server with loaded config
        new SMTPServer(cfg).start();
    } catch (Exception e) {
//Error message
        e.printStackTrace();
    }
}
}
