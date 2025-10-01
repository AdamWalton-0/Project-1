package util;

// imports
import java.io.File;
import java.io.IOException;

import merrimackutil.json.InvalidJSONException;
import merrimackutil.json.JsonIO;
import merrimackutil.json.types.JSONArray;
import merrimackutil.json.types.JSONObject;

import util.Config.Account;
import util.Config.AccountsDB;
import util.Config.Pop3Config;
import util.Config.SmtpConfig;

public final class ConfigLoader {
    private ConfigLoader() {}

    /*
     * Loads SMTP configuration from the smtpd.json file
     */
    public static SmtpConfig loadSmtp() throws IOException, InvalidJSONException {
        return loadSmtp(new File("smtpd.json"));
    }
    /*
     * Loads Pop3 configuration from the pop3d.json file
     */
    public static Pop3Config loadPop3() throws IOException, InvalidJSONException {
        return loadPop3(new File("pop3d.json"));
    }
    /*
     * Loads the Accounts Database from the accounts.json file
     */
    public static AccountsDB loadAccounts() throws IOException, InvalidJSONException {
        return loadAccounts(new File("accounts.json"));
    }

    /*
     * Loads the SMTP configuration from the file f
     */
    public static SmtpConfig loadSmtp(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f); // read JSON from file          
        SmtpConfig c = new SmtpConfig(); // create config object
        c.spool = o.getString("spool"); // put spool in c
        c.serverName = o.getString("server-name"); // put servername in c
        c.port = o.getInt("port"); // put port in c
        c.log = o.getString("log"); // put log in c
        return c; // return c
    }

    /*
     * Loads the Pop3 configuration from the file f
     */
    public static Pop3Config loadPop3(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f); // read JSON from file
        Pop3Config c = new Pop3Config(); // create config object
        c.spool = o.getString("spool"); // put spool in c
        c.serverName = o.getString("server-name"); // put servername in c
        c.port = o.getInt("port"); // put port in c
        c.log = o.getString("log"); // put log in c 
        c.accounts = o.getString("accounts"); // put accounts in c
        return c; // return c
    }

    /*
     * Loads the accounts configuration from the file f
     */
    public static AccountsDB loadAccounts(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f); // read JSON from file
        JSONArray arr = o.getArray("accounts");
        AccountsDB db = new AccountsDB();
        for (int i = 0; i < arr.size(); i++) { // iterate over each account
            JSONObject ao = arr.getObject(i);
            Account a = new Account(); // create account object
            a.username = ao.getString("username"); // read username
            a.pass = ao.getString("pass"); // read pass
            a.spool = ao.getString("spool"); // read spool
            if (a.username != null && !a.username.isBlank()) db.accounts.add(a);
        }
        return db; // return db
    }
}

