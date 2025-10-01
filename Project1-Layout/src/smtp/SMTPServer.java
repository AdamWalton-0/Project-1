package smtp;

import maildir.MailBox;
import maildir.MailBoxException;
import util.Config.SmtpConfig;
import util.ConfigLoader;
import util.LoggerSetup;
import merrimackutil.cli.LongOption;
import merrimackutil.cli.OptionParser;
import merrimackutil.util.Tuple;

import java.io.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class SMTPServer {
    private static final Map<String, MailBox> mailboxes = new ConcurrentHashMap<>();
    private static merrimackutil.net.Log lg;
    private static SmtpConfig cfg;
    private static ExecutorService pool;

    public static void main(String[] args) {
        try {
            OptionParser parser = new OptionParser(args);
            LongOption configOpt = new LongOption("config", true, 'c');
            parser.setLongOpts(new LongOption[]{configOpt});
            Tuple<Character,String> opt;
            String configFile = "smtpd.json"; 
            while ((opt = parser.getLongOpt(true)) != null) {
                if (opt.getFirst() == 'c' && opt.getSecond() != null) {
                    configFile = opt.getSecond();
                }
            }

            cfg = ConfigLoader.loadSmtp(new File(configFile));
            lg = LoggerSetup.make(cfg.log, "SMTP");
            lg.log("smtpd starting on port " + cfg.port + ", serverName=" + cfg.serverName + ", spool=" + cfg.spool);

            Path spoolPath = Paths.get(cfg.spool);
            if (!Files.exists(spoolPath)) Files.createDirectories(spoolPath);

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(spoolPath)) {
                for (Path userPath : ds) {
                    if (Files.isDirectory(userPath)) {
                        String user = userPath.getFileName().toString();
                        try {
                            MailBox mb = new MailBox(cfg.spool, user);
                            mb.load();
                            mailboxes.put(user, mb);
                        } catch (MailBoxException e) {
                            lg.log("Failed to build mailbox for " + user + ": " + e.getMessage());
                        }
                    }
                }
            }

            MailQueue queue = new MailQueue();
            Thread qThread = new Thread(new MailQueueThread(queue, cfg, lg), "mail-queue");
            qThread.setDaemon(true);
            qThread.start();

            pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            try (ServerSocket ss = new ServerSocket(cfg.port)) {
                while (true) {
                    Socket c = ss.accept();
                    pool.submit(new SMTPHandler(c, cfg, lg, queue));
                }
            }
        } catch (Exception e) {
            try { if (lg != null) lg.log("fatal: " + e.getMessage()); 
        }
             catch (Exception ignored) {
             }
        } finally {
            if (pool != null) pool.shutdown();
        }
    }
}
