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

/**
 * Maildir-style mailbox:
 * - Per user directory: <spool>/<user>/{new,tmp}
 * - Add: write to tmp/<name>, then atomic move to new/<name>
 * - Load: index messages in new/ (1-based)
 * - Two-phase delete: markDelete(i) then commitDeletes() on QUIT
 */
public class MailBox {

    private final Path root;     // e.g., "mail"
    private final String user;   // e.g., "alice"

    private final Path userDir;  // mail/alice
    private final Path newDir;   // mail/alice/new
    private final Path tmpDir;   // mail/alice/tmp

    // 1-based index → file path in new/
    private List<Path> index = new ArrayList<>();

    // marked-for-delete filenames (absolute paths) until commit
    private final Set<Path> toDelete = new ConcurrentSkipListSet<>();

    public MailBox(String spoolRoot, String user) throws MailBoxException {
        if (spoolRoot == null || spoolRoot.isBlank()) throw new MailBoxException("spool root missing");
        if (user == null || user.isBlank()) throw new MailBoxException("user missing");
        this.root = Paths.get(spoolRoot);
        this.user = user;

        this.userDir = root.resolve(user);
        this.newDir  = userDir.resolve("new");
        this.tmpDir  = userDir.resolve("tmp");

        try {
            Files.createDirectories(newDir);
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new MailBoxException("failed to create maildir folders", e);
        }
    }

    /** Write message safely: tmp → atomic move → new */
    public synchronized void add(MailMessage m) throws MailBoxException {
        String base = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path tmp = tmpDir.resolve(base + ".eml");
        Path fin = newDir.resolve(base + ".eml");
        try {
            // write to tmp
            Files.write(tmp, m.toWireFormat().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            // atomic move to new
            try {
                Files.move(tmp, fin, ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // fallback if FS doesn't support atomic move
                Files.move(tmp, fin, REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // try to clean tmp on error
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
            throw new MailBoxException("failed to add message", e);
        }
    }

    /** Scan new/ and build 1-based index (sorted by file creation time, then name). */
    public synchronized void load() throws MailBoxException {
        try {
            List<Path> files = Files.list(newDir)
                    .filter(p -> Files.isRegularFile(p))
                    .collect(Collectors.toList());

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

            index = files;
            toDelete.clear(); // fresh session
        } catch (IOException e) {
            throw new MailBoxException("failed to load mailbox", e);
        }
    }

    /** Number of messages currently indexed. */
    public synchronized int count() {
        return index.size();
    }

    /** Total size in bytes of all indexed messages. */
    public synchronized long totalSize() {
        long sum = 0L;
        for (Path p : index) {
            try { sum += Files.size(p); } catch (IOException ignored) {}
        }
        return sum;
    }

    /** Size in bytes of message i (1-based). */
    public synchronized long size(int i) throws MailBoxException {
        Path p = pathFor(i);
        try { return Files.size(p); } catch (IOException e) { throw new MailBoxException("size failed", e); }
    }

    /** Return raw RFC822 text of message i (1-based). */
    public synchronized String get(int i) throws MailBoxException {
        Path p = pathFor(i);
        try {
            byte[] data = Files.readAllBytes(p);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MailBoxException("read failed", e);
        }
    }

    /** Mark message i for delete (files are removed on commit). */
    public synchronized void markDelete(int i) throws MailBoxException {
        Path p = pathFor(i);
        toDelete.add(p.toAbsolutePath().normalize());
    }

    /** Clear all delete marks (used by POP3 RSET). */
    public synchronized void unmarkAll() {
        toDelete.clear();
    }

    /** Permanently delete any marked messages (used by POP3 QUIT on success). */
    public synchronized void commitDeletes() throws MailBoxException {
        for (Path p : new HashSet<>(toDelete)) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new MailBoxException("delete failed: " + p.getFileName(), e);
            }
        }
        toDelete.clear();
        // reload index to reflect removals
        load();
    }

    /** Utility for POP3 LIST (id → size). */
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

    /** Resolve 1-based id to Path with bounds checking and "not already marked" sanity. */
    private Path pathFor(int i) throws MailBoxException {
        if (i < 1 || i > index.size()) throw new MailBoxException("message index out of range: " + i);
        Path p = index.get(i - 1);
        // If it was marked for delete, still allow RETR during the same session per POP3 (until commit).
        return p;
    }

    // handy for tests
    public String getUser() { return user; }
    public Path getUserDir() { return userDir; }
    public Path getNewDir()  { return newDir; }
    public Path getTmpDir()  { return tmpDir; }
}
