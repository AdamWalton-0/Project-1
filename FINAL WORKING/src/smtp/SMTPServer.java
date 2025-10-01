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

/**
 * Lightweight SMTP daemon.
 * Uses merrimackutil for JSON + logging. Names & comments simplified.
 */
public class SMTPServer {

    private final Config.SmtpConfig cfg;
    private final Log lg;
    private final ExecutorService exec;

    public SMTPServer(Config.SmtpConfig cfg) throws IOException {
        this.cfg = cfg;
        this.lg  = LoggerSetup.make(cfg.log, "smtpd");
        this.lg.log("boot " + cfg);
        int n = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        this.exec = Executors.newFixedThreadPool(n);
    }

    public void start() {
        try (ServerSocket srv = new ServerSocket(cfg.port)) {
            lg.log("listen " + cfg.port + " | host=" + cfg.serverName + " | spool=" + cfg.spool);
            while (true) {
                Socket sock = srv.accept();
                lg.log("conn " + sock.getRemoteSocketAddress());
                exec.execute(new SMTPHandler(sock, cfg, lg));
            }
        } catch (IOException e) {
            lg.log("fatal " + e.getMessage());
        }
    }

public static void main(String[] args) {
    try {
        String p = (args.length > 0) ? args[0] : "smtpd.json";
        if (!new File(p).exists()) {
            String alt = "config/smtpd.json";
            if (new File(alt).exists()) p = alt;
        }

        // FIX: use File overload (or just ConfigLoader.loadSmtp())
        util.Config.SmtpConfig cfg = util.ConfigLoader.loadSmtp(new File(p));
        new SMTPServer(cfg).start();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
}