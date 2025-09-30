package util;

import maildir.MailBox;
import maildir.MailBoxException;
import maildir.MailMessage;

import util.ConfigLoader;
import util.Config;

public class Main {
    public static void main(String[] args) {
        try {
            // --- Load configs with merrimackutil ---
            Config.SmtpConfig smtp = ConfigLoader.loadSmtp();
            Config.Pop3Config pop3 = ConfigLoader.loadPop3();
            Config.AccountsDB db  = ConfigLoader.loadAccounts();

            System.out.println("SMTP config: " + smtp);
            System.out.println("POP3 config: " + pop3);
            System.out.println("Accounts loaded: " + db.accounts.size());
            System.out.println("Validate(alice, password) -> " + db.validate("alice", "password"));
            System.out.println();

            // --- Quick maildir smoke test using the first account (if any) ---
            if (!db.accounts.isEmpty()) {
                Config.Account a = db.accounts.get(0); // e.g., alice
                System.out.println("Testing mailbox for user: " + a.username + " (spool=" + a.spool + ")");

                MailBox mb = new MailBox(a.spool, a.username);

                // add a test message
                MailMessage msg = new MailMessage()
                        .setFrom("tester@" + smtp.serverName)
                        .addRecipient(a.username + "@" + smtp.serverName)
                        .setSubject("Config + Maildir test")
                        .setBody("Hello " + a.username + ", this is a test message.\nHave a nice day!");
                mb.add(msg);

                // load + show stats
                mb.load();
                System.out.println("count=" + mb.count() + ", totalBytes=" + mb.totalSize());

                // show first message (if present)
                if (mb.count() >= 1) {
                    System.out.println("--- message[1] ---");
                    System.out.println(mb.get(1));
                }

                // clean up: delete the first message we just added (if you want to persist, comment these out)
                if (mb.count() >= 1) {
                    mb.markDelete(1);
                    mb.commitDeletes();
                    mb.load();
                    System.out.println("after delete, count=" + mb.count());
                }
            } else {
                System.out.println("No accounts found in accounts.json — add at least one to test maildir.");
            }
        } catch (MailBoxException e) {
            System.err.println("MailBox error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // catches IOException and InvalidJSONException from merrimackutil
            System.err.println("Config / JSON error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
