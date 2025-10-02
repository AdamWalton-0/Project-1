package pop3;

import merrimackutil.net.Log; // import statments for this file and project
import util.Config;
import util.ConfigLoader;
import util.LoggerSetup;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class POP3Server { // this is the main pop3 server/ reads setting aswell user accounts/ listen for ports to connect clients/ and makes handler for every client

    private final Config.Pop3Config cfg; // config info from the server
    private final Config.AccountsDB users; // database of user each users accounts
    private final Log lg;// writes events to log files
    private final ExecutorService exec;// this is a thread pool so muti users can connect

    public POP3Server(Config.Pop3Config cfg, Config.AccountsDB users) throws IOException {   // constructor sets server with config + accounts

        this.cfg = cfg;
        this.users = users;
        this.lg = LoggerSetup.make(cfg.log, "pop3d");// make logger that writes file to config
        this.lg.log("boot " + cfg); // log that we booted w config
        int n = Math.max(8, Runtime.getRuntime().availableProcessors() * 2); // set # of thread 
        this.exec = Executors.newFixedThreadPool(n);
    }

    public void start() { // start sever forever until its killed
        try (ServerSocket srv = new ServerSocket(cfg.port)) {
            lg.log("listen " + cfg.port + " | host=" + cfg.serverName + " | spool=" + cfg.spool);
            while (true) {// loop forever, clients are accepted one at a time 
                Socket sock = srv.accept(); // waits for client connection
                lg.log("conn " + sock.getRemoteSocketAddress());// connection of the log
                exec.execute(new POP3Handler(sock, cfg, users, lg));
            }
        } catch (IOException e) { // if sum fails log in as fatal
            lg.log("fatal " + e.getMessage());
        }
    }

    public static void main(String[] args) {// main entry
        try {
            String cfgPath = (args.length > 0) ? args[0] : "pop3d.json";// check user gave a congig path and uses pop3json
            if (!new File(cfgPath).exists()) { // if not found, use config pop3d.json
                String alt = "config/pop3d.json";
                if (new File(alt).exists()) cfgPath = alt;
            }
            Config.Pop3Config cfg = ConfigLoader.loadPop3(new File(cfgPath)); // loads config

            File acctFile = new File(cfg.accounts);// loads accounts given in config
            if (!acctFile.exists()) {// if if missing try config/accounts.json
                File alt = new File("config/accounts.json");
                if (alt.exists()) acctFile = alt;
            }
            Config.AccountsDB db = ConfigLoader.loadAccounts(acctFile);

            new POP3Server(cfg, db).start();// create server start it 
        } catch (Exception e) { // if error print full error
            e.printStackTrace();
        }
    }
}
