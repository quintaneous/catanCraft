package com.quin.catancraft.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** A reward transaction holds one native lock and verifies that its path still names the same file. */
final class RewardStorageLock implements AutoCloseable {
    private static final AtomicBoolean busy = new AtomicBoolean();

    static void ensureSafePath(Path path) throws IOException {
        Path walk = path.getRoot();
        for (Path part : path) {
            walk = walk.resolve(part);
            if (Files.isSymbolicLink(walk) || (!walk.equals(path) && Files.exists(walk, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(walk, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Unsafe reward storage path");
            }
        }
    }

    static RewardStorageLock acquire(Path path) throws IOException {
        return acquire(path, BasicFileAttributes::fileKey);
    }

    static RewardStorageLock acquire(Path path, Function<BasicFileAttributes, Object> fileKey) throws IOException {
        if (!busy.compareAndSet(false, true)) throw new IOException("Another monument reward operation is active");
        FileChannel channel = null;
        Path anchor = null;
        try {
            ensureSafePath(path);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createFile(path);
                } catch (FileAlreadyExistsException raced) {
                    // A second process can initialize the same lock file before either takes the lock.
                }
            }
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw new IOException("Invalid reward lock file");
            Object key = fileKey.apply(attributes);
            if (key == null) {
                // Windows Java 17 returns null for every basic fileKey. A private hard link pins the
                // actual file across renames and byte-identical replacements without mutating it.
                Path alias = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".pin");
                try {
                    Files.createLink(alias, path);
                } catch (UnsupportedOperationException unsupported) {
                    throw new IOException("Reward storage needs file identity or hard-link support", unsupported);
                }
                anchor = alias;
            }
            channel = FileChannel.open(anchor == null ? path : anchor, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock held;
            try {
                held = channel.tryLock();
            } catch (OverlappingFileLockException contention) {
                throw new IOException("Reward storage is locked", contention);
            }
            if (held == null) throw new IOException("Reward storage is locked");
            RewardStorageLock result = new RewardStorageLock(path, anchor, key, channel, held);
            result.verify();
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                releaseLock(channel, anchor);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static void releaseLock(FileChannel channel, Path anchor) throws IOException {
        try {
            // Closing the only lock channel releases its FileLock before the alias is removed.
            if (channel != null) channel.close();
        } finally {
            try {
                if (anchor != null) Files.deleteIfExists(anchor);
            } finally {
                busy.set(false);
            }
        }
    }

    private final Path path, anchor;
    private final FileChannel channel;
    private final FileLock held;
    private final Object key;
    private boolean closed;

    private RewardStorageLock(Path path, Path anchor, Object key, FileChannel channel, FileLock held) {
        this.path = path;
        this.anchor = anchor;
        this.key = key;
        this.channel = channel;
        this.held = held;
    }

    void verify() throws IOException {
        ensureSafePath(path);
        if (closed || !held.isValid() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Reward lock changed");
        boolean same = anchor == null
                ? key.equals(Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey())
                : Files.isRegularFile(anchor, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(anchor, path);
        if (!same) throw new IOException("Reward lock changed");
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        releaseLock(channel, anchor);
    }
}
