package maildir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


public class MailBox {
//initialize root, user, userDir, newDir, tmpDir
    private final Path root;
    private final String user;
    private final Path userDir;
    private final Path newDir;
    private final Path tmpDir;

//index of messages 
    private List<Path> index = new ArrayList<>();

//files marked for deletion
    private final Set<Path> toDelete = new ConcurrentSkipListSet<>();

//Create a new MailBox for the given user 
    public MailBox(String spoolRoot, String user) throws MailBoxException {
        if (spoolRoot == null || spoolRoot.isBlank()) throw new MailBoxException("spool root missing");
        if (user == null || user.isBlank()) throw new MailBoxException("user missing");

        this.root = Paths.get(spoolRoot);
        this.user = user;
        this.userDir = root.resolve(user);
        this.newDir  = userDir.resolve("new");
        this.tmpDir  = userDir.resolve("tmp");

        try {
//make sure the new and tmp directories exist
            Files.createDirectories(newDir);
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new MailBoxException("failed to create maildir folders", e);
        }
    }

//Add a new message, write to tmp, then move to new
    public synchronized void add(MailMessage m) throws MailBoxException {
        String base = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
//Temporary file name
        Path tmp = tmpDir.resolve(base + ".eml");
//Final file name
        Path fin = newDir.resolve(base + ".eml");
        try {
//Write messages into tmp
            Files.write(tmp, m.toWireFormat().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

//Automatically move tmp to new
            try {
                Files.move(tmp, fin, ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
//fallback if atomic move isnt supported
                Files.move(tmp, fin, REPLACE_EXISTING);
            }
        } catch (IOException e) {
//If error occurs clean the tmp file
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
            throw new MailBoxException("failed to add message", e);
        }
    }

//Load and index all messages in new 
    public synchronized void load() throws MailBoxException {
        try {
            List<Path> files = Files.list(newDir)
//only files not folders
                    .filter(p -> Files.isRegularFile(p))
                    .collect(Collectors.toList());
//Sort by file creation time then by filename
            files.sort((a, b) -> {
                try {
                    BasicFileAttributes aa = Files.readAttributes(a, BasicFileAttributes.class);
                    BasicFileAttributes bb = Files.readAttributes(b, BasicFileAttributes.class);
                    int t = aa.creationTime().compareTo(bb.creationTime());
                    return (t != 0) ? t : a.getFileName().toString().compareTo(b.getFileName().toString());
                } catch (IOException e) {
                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                }
            });
//refresh index
            index = files;
            toDelete.clear();
//clear old delete marks
        } catch (IOException e) {
            throw new MailBoxException("failed to load mailbox", e);
        }
    }

//total number of indexed messages
    public synchronized int count() {
        return index.size();
    }

//total size in bytes of all messages
    public synchronized long totalSize() {
        long sum = 0L;
        for (Path p : index) {
            try { sum += Files.size(p); } catch (IOException ignored) {}
        }
        return sum;
    }

//return size in bytes of one message
    public synchronized long size(int i) throws MailBoxException {
        Path p = pathFor(i);
        try { return Files.size(p); } catch (IOException e) { throw new MailBoxException("size failed", e); }
    }

//Read the text of one message 
    public synchronized String get(int i) throws MailBoxException {
        Path p = pathFor(i);
        try {
            byte[] data = Files.readAllBytes(p);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MailBoxException("read failed", e);
        }
    }

//mark a message for deletion
    public synchronized void markDelete(int i) throws MailBoxException {
        Path p = pathFor(i);
        toDelete.add(p.toAbsolutePath().normalize());
    }

//undo all delete marks (used by POP3 RSET)
    public synchronized void unmarkAll() {
        toDelete.clear();
    }

//Permanently delete any marked messages (used by POP3 QUIT)
    public synchronized void commitDeletes() throws MailBoxException {
        for (Path p : new HashSet<>(toDelete)) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new MailBoxException("delete failed: " + p.getFileName(), e);
            }
        }
        toDelete.clear();
//reload index to reflect removals
        load();
    }

//return map of message index (used by POP3 LIST)
    public synchronized Map<Integer, Long> listSizes() {
        Map<Integer, Long> m = new LinkedHashMap<>();
        for (int i = 1; i <= index.size(); i++) {
            try {
                m.put(i, Files.size(index.get(i - 1)));
            } catch (IOException e) {
                m.put(i, 0L);
            }
        }
        return m;
    }

//Help method to get file path for a message index
    private Path pathFor(int i) throws MailBoxException {
        if (i < 1 || i > index.size()) throw new MailBoxException("message index out of range: " + i);
        Path p = index.get(i - 1);
        return p;
    }

//Getters (for testing)
    public String getUser() { return user; }
    public Path getUserDir() { return userDir; }
    public Path getNewDir()  { return newDir; }
    public Path getTmpDir()  { return tmpDir; }
}
