package util;

import java.io.File; // import statments for project and this file
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
    private ConfigLoader() {} // this constructor is private that way no one can make an object of this class.


//  default loaders, looks in current folder to find files
    public static SmtpConfig loadSmtp() throws IOException, InvalidJSONException { 
        return loadSmtp(new File("smtpd.json"));// uses "smtpd.json" as the default file also calls file version
    }
    public static Pop3Config loadPop3() throws IOException, InvalidJSONException {
        return loadPop3(new File("pop3d.json"));// uses "pop3d.json" as the default file also calls file version
    }
    public static AccountsDB loadAccounts() throws IOException, InvalidJSONException {
        return loadAccounts(new File("accounts.json")); // uses "accounts.json" as the default file also calls file version
    }

    //loads SMTP config
    public static SmtpConfig loadSmtp(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f);            // opens than turns json file into object
        SmtpConfig c = new SmtpConfig();
        c.spool = o.getString("spool"); // folder for emails
        c.serverName = o.getString("server-name");// severs name
        c.port = o.getInt("port");// port # for smtp
        c.log = o.getString("log");// where logs get stored
        return c;
    }

    public static Pop3Config loadPop3(File f) throws IOException, InvalidJSONException {// load pop3 config
        JSONObject o = JsonIO.readObject(f); // opens than turns json file into object
        Pop3Config c = new Pop3Config();
        c.spool = o.getString("spool");// folder for emails
        c.serverName = o.getString("server-name");// sever name
        c.port = o.getInt("port");// port # for pop 3
        c.log = o.getString("log");// where logs are stored
        c.accounts = o.getString("accounts");// accounts file path
        return c; // returns final config
    }

    public static AccountsDB loadAccounts(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f); // opens than turns json file into object
        JSONArray arr = o.getArray("accounts");// the json file grabs account array
        AccountsDB db = new AccountsDB();
        for (int i = 0; i < arr.size(); i++) {// grab account json object
            JSONObject ao = arr.getObject(i);
            Account a = new Account();// make new account
            a.username = ao.getString("username"); // account name
            a.pass = ao.getString("pass");// account password
            a.spool = ao.getString("spool");// spool folder
            if (a.username != null && !a.username.isBlank()) db.accounts.add(a); // only add the account if it has a username
        }
        return db;// returns database of finished accounts
    }
}
