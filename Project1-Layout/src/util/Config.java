package util;

import java.util.ArrayList;
import java.util.List;

public final class Config {
    private Config() {}

    public static final class SmtpConfig {
        public String spool;
        public String serverName;
        public int port;
        public String log;
        @Override public String toString() {
            return "SmtpConfig{spool=" + spool + ", serverName=" + serverName + ", port=" + port + ", log=" + log + "}";
        }
    }

    public static final class Pop3Config {
        public String spool;
        public String serverName;
        public int port;
        public String log;
        public String accounts; // path to accounts.json
        @Override public String toString() {
            return "Pop3Config{spool=" + spool + ", serverName=" + serverName + ", port=" + port +
                   ", log=" + log + ", accounts=" + accounts + "}";
        }
    }

    public static final class Account {
        public String username;
        public String pass;
        public String spool;
        @Override public String toString() {
            return "Account{username=" + username + ", spool=" + spool + "}";
        }
    }

    public static final class AccountsDB {
        public final List<Account> accounts = new ArrayList<>();
        public Account find(String u) {
            for (Account a : accounts) if (a.username != null && a.username.equals(u)) return a;
            return null;
        }
        public boolean validate(String u, String p) {
            Account a = find(u);
            return a != null && a.pass != null && a.pass.equals(p);
        }
    }
}
