package util;

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

    // ---- Public API (default files in CWD) ----
    public static SmtpConfig loadSmtp() throws IOException, InvalidJSONException {
        return loadSmtp(new File("smtpd.json"));
    }
    public static Pop3Config loadPop3() throws IOException, InvalidJSONException {
        return loadPop3(new File("pop3d.json"));
    }
    public static AccountsDB loadAccounts() throws IOException, InvalidJSONException {
        return loadAccounts(new File("accounts.json"));
    }

    // ---- File-based loaders ----
    public static SmtpConfig loadSmtp(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f);            // parse JSON file -> object
        SmtpConfig c = new SmtpConfig();
        c.spool = o.getString("spool");
        c.serverName = o.getString("server-name");
        c.port = o.getInt("port");
        c.log = o.getString("log");
        return c;
    }

    public static Pop3Config loadPop3(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f);
        Pop3Config c = new Pop3Config();
        c.spool = o.getString("spool");
        c.serverName = o.getString("server-name");
        c.port = o.getInt("port");
        c.log = o.getString("log");
        c.accounts = o.getString("accounts");
        return c;
    }

    public static AccountsDB loadAccounts(File f) throws IOException, InvalidJSONException {
        JSONObject o = JsonIO.readObject(f);
        JSONArray arr = o.getArray("accounts");
        AccountsDB db = new AccountsDB();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject ao = arr.getObject(i);
            Account a = new Account();
            a.username = ao.getString("username");
            a.pass = ao.getString("pass");
            a.spool = ao.getString("spool");
            if (a.username != null && !a.username.isBlank()) db.accounts.add(a);
        }
        return db;
    }
}
