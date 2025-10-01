package maildir;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple message model used by SMTP → MailBox and POP3 → client.
 * Stores minimal headers and body. Formats to RFC-822-ish text.
 */
public class MailMessage {

    private String from;
    private final List<String> to = new ArrayList<>();
    private String subject = "";
    private String body = "";

    public MailMessage() {}

    public MailMessage(String from, List<String> to, String subject, String body) {
        this.from = from;
        if (to != null) this.to.addAll(to);
        if (subject != null) this.subject = subject;
        if (body != null) this.body = body;
    }

    // setters (fluent for convenience)
    public MailMessage setFrom(String from) { this.from = from; return this; }
    public MailMessage addRecipient(String r) { if (r != null && !r.isBlank()) this.to.add(r); return this; }
    public MailMessage setRecipients(List<String> rs) { this.to.clear(); if (rs != null) this.to.addAll(rs); return this; }
    public MailMessage setSubject(String subject) { this.subject = subject == null ? "" : subject; return this; }
    public MailMessage setBody(String body) { this.body = body == null ? "" : body; return this; }

    // getters
    public String getFrom() { return from; }
    public List<String> getTo() { return List.copyOf(to); }
    public String getSubject() { return subject; }
    public String getBody() { return body; }

    /**
     * Returns message as CRLF-delimited text with minimal headers.
     * POP3 RETR should send exactly what is stored here.
     */
    public String toWireFormat() {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());
        StringBuilder sb = new StringBuilder();

        // Headers
        sb.append("From: ").append(from == null ? "" : from).append("\r\n");
        sb.append("To: ").append(String.join(", ", to)).append("\r\n");
        if (!subject.isBlank()) sb.append("Subject: ").append(subject).append("\r\n");
        sb.append("Date: ").append(date).append("\r\n");
        sb.append("\r\n"); // blank line between headers and body

        // Body (ensure CRLF endings)
        String b = body == null ? "" : body;
        b = b.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
        sb.append(b).append("\r\n"); // end with CRLF

        return sb.toString();
    }
}
