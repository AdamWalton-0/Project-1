package util;

import java.util.ArrayList;
import java.util.List;

public final class Config {
// Prevent instantiation
    private Config() {}
//SMTP server configuration
    public static final class SmtpConfig {
//initialize spool, serverName, port, and log
        public String spool;
        public String serverName;
        public int port;
        public String log;

        @Override public String toString() {
//Readable string representation
            return "SmtpConfig{spool=" + spool + ", serverName=" + serverName + ", port=" + port + ", log=" + log + "}";
        }
    }
//POP3 server configuration
    public static final class Pop3Config {
//initialize spool, serverName, port, log, and accounts
        public String spool;
        public String serverName;
        public int port;
        public String log;
        public String accounts;
        @Override public String toString() {
//Readable string representation
            return "Pop3Config{spool=" + spool + ", serverName=" + serverName + ", port=" + port +
                   ", log=" + log + ", accounts=" + accounts + "}";
        }
    }
//User account information
    public static final class Account {
//initialize username, pass, and spool
        public String username;
        public String pass;
        public String spool;
        @Override public String toString() {
//Readable string representation
            return "Account{username=" + username + ", spool=" + spool + "}";
        }
    }
//Database of user accounts
    public static final class AccountsDB {
//List of all accounts
        public final List<Account> accounts = new ArrayList<>();
//Find account by username
        public Account find(String u) {
            for (Account a : accounts) if (a.username != null && a.username.equals(u)) return a;
            return null;
        }
//Validate username and password
        public boolean validate(String u, String p) {
            Account a = find(u);
            return a != null && a.pass != null && a.pass.equals(p);
        }
    }
}
