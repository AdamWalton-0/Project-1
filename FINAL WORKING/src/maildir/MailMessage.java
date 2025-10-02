package maildir;// package for mail directory 

import java.time.ZonedDateTime; // import statments for this file 
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class MailMessage { // this class is the blueprint for users email messages/ holds who it's from, who it’s to, subject, body, etc

    private String from;// who sent the email
    private final List<String> to = new ArrayList<>();// people getting email
    private String subject = "";// subject line
    private String body = "";// body of email

    public MailMessage() {}// this empty constuctors method lets blank messages be made

    public MailMessage(String from, List<String> to, String subject, String body) {// constuctor that fills feilds when object is made
        this.from = from;

        if (to != null) this.to.addAll(to);// if list isnt null copy recipents 
        if (subject != null) this.subject = subject; // if not null set subject
        if (body != null) this.body = body; // set body if not null 
    }
    // setters 
    public MailMessage setFrom(String from) { this.from = from; return this; }
    public MailMessage addRecipient(String r) { if (r != null && !r.isBlank()) this.to.add(r); return this; }
    public MailMessage setRecipients(List<String> rs) { this.to.clear(); if (rs != null) this.to.addAll(rs); return this; } //wipes the old list,adds new ones if not null
    public MailMessage setSubject(String subject) { this.subject = subject == null ? "" : subject; return this; } // if its null use strings that are emtpy 
    public MailMessage setBody(String body) { this.body = body == null ? "" : body; return this; }// if null again use empty strings

    // getters 
    public String getFrom() { return from; }
    public List<String> getTo() { return List.copyOf(to); } // copy list cant be alterd
    public String getSubject() { return subject; }
    public String getBody() { return body; }



    public String toWireFormat() {// turns all messages to file format 
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()); // create date header
        StringBuilder sb = new StringBuilder();

        
        sb.append("From: ").append(from == null ? "" : from).append("\r\n");// headers
        sb.append("To: ").append(String.join(", ", to)).append("\r\n");
        if (!subject.isBlank()) sb.append("Subject: ").append(subject).append("\r\n");
        sb.append("Date: ").append(date).append("\r\n");
        sb.append("\r\n"); 


        // body
        String b = body == null ? "" : body;
        b = b.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
        sb.append(b).append("\r\n"); 

        return sb.toString(); // return full message as a string 
    }
}
