package pop3;

import merrimackutil.net.Log;
import util.Config;
import util.ConfigLoader;
import util.LoggerSetup;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** POP3 daemon entry. Loads cfg + accounts via ConfigLoader, logs with merrimackutil. */
public class POP3Server {

    private final Config.Pop3Config cfg;
    private final Config.AccountsDB users;
    private final Log lg;
    private final ExecutorService exec;

    public POP3Server(Config.Pop3Config cfg, Config.AccountsDB users) throws IOException {
        this.cfg = cfg;
        this.users = users;
        this.lg = LoggerSetup.make(cfg.log, "pop3d");
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
                exec.execute(new POP3Handler(sock, cfg, users, lg));
            }
        } catch (IOException e) {
            lg.log("fatal " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            // Prefer CWD pop3d.json; fallback to config/pop3d.json
            String cfgPath = (args.length > 0) ? args[0] : "pop3d.json";
            if (!new File(cfgPath).exists()) {
                String alt = "config/pop3d.json";
                if (new File(alt).exists()) cfgPath = alt;
            }
            Config.Pop3Config cfg = ConfigLoader.loadPop3(new File(cfgPath));

            // Load accounts from cfg.accounts (relative to CWD)
            File acctFile = new File(cfg.accounts);
            if (!acctFile.exists()) {
                // fallback: config/accounts.json
                File alt = new File("config/accounts.json");
                if (alt.exists()) acctFile = alt;
            }
            Config.AccountsDB db = ConfigLoader.loadAccounts(acctFile);

            new POP3Server(cfg, db).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}