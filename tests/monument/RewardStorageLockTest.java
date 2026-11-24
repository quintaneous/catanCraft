package com.quin.catancraft.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** JDK-only file test. Supply a fresh directory on the Windows or Linux filesystem to test. */
public final class RewardStorageLockTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final byte[] MARKER = {4, 8, 15, 16, 23, 42};

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--contend")) {
            Path path = Path.of(args[1]);
            refused(() -> {
                try (var unexpected = RewardStorageLock.acquire(path)) { unexpected.verify(); }
            }, "locked");
            System.out.println("external JVM excluded");
            return;
        }
        Path root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(root);
        try (var entries = Files.list(root)) { check(entries.findAny().isEmpty(), "use a fresh directory"); }
        Path data = Files.createDirectory(root.resolve("data"));
        Path path = data.resolve("catancraft_reward41.lock");
        Path ledger = data.resolve("catancraft.dat"), intent = data.resolve("catancraft_reward41_example.dat");
        Path receipt = data.resolve("catancraft_reward41_example.receipt");
        var history = List.of(ledger, intent, receipt);
        for (Path f : history) Files.writeString(f, "private-fixture-" + f.getFileName(), StandardCharsets.UTF_8);
        // Existing locks can contain opaque bytes created by older versions.
        Files.write(path, MARKER);
        Object nativeKey = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        System.out.println("runtime=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
                + " filesystem=" + Files.getFileStore(path).type() + " basicFileKeyPresent=" + (nativeKey != null));
        if (WINDOWS && Runtime.version().feature() == 17) check(nativeKey == null, "expected Windows Java 17 null key");

        for (boolean forceNull : new boolean[]{false, true}) {
            var held = acquire(path, forceNull);
            try (held) {
                held.verify();
                refused(() -> RewardStorageLock.acquire(path), "active");
                Process child = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString(),
                        "-cp", System.getProperty("java.class.path"), RewardStorageLockTest.class.getName(),
                        "--contend", path.toString()).inheritIO().start();
                check(child.waitFor() == 0, "separate JVM accepted the held reward lock");
                held.verify();
                Path clone = root.resolve("same-contents");
                Files.write(clone, MARKER);
                FileTime time = Files.getLastModifiedTime(path);
                Files.setLastModifiedTime(clone, time);
                boolean replaced = false;
                try {
                    Files.move(clone, path, StandardCopyOption.REPLACE_EXISTING);
                    replaced = true;
                } catch (AccessDeniedException protectedByOS) {
                    if (!WINDOWS) throw protectedByOS;
                    Files.delete(clone);
                    held.verify();
                }
                // Some Windows storage policies can forbid replacing an open file outright.
                if (replaced) refused(held::verify, "changed");
                System.out.println("same-content replacement " + (replaced ? "detected" : "prevented by filesystem")
                        + " (forceNull=" + forceNull + ")");
            }
            refused(held::verify, "changed");
            try (var reopened = acquire(path, forceNull)) {
                held.close(); // a stale close cannot clear the newly held one
                refused(() -> RewardStorageLock.acquire(path), "active");
                reopened.verify();
            }
            check(Arrays.equals(Files.readAllBytes(path), MARKER), "opaque lock bytes changed");
            for (Path f : history) check(Files.readString(f).equals("private-fixture-" + f.getFileName()), "history changed");
            try (var entries = Files.list(data)) {
                check(entries.map(f -> f.getFileName().toString()).collect(Collectors.toSet()).equals(
                        Set.of(path.getFileName().toString(), ledger.getFileName().toString(),
                                intent.getFileName().toString(), receipt.getFileName().toString())),
                        "new journal history or a leftover pin");
            }
        }
        Path bad = Files.createDirectory(root.resolve("invalid-lock"));
        refused(() -> RewardStorageLock.acquire(bad), "Invalid");
        Path first = root.resolve("first-reward/data/catancraft_reward41.lock");
        try (var fresh = RewardStorageLock.acquire(first)) { fresh.verify(); }
        try (var fresh = RewardStorageLock.acquire(first)) { fresh.verify(); }
        System.out.println("PASS first creation, reopen, missing file keys, real process contention, replacement, "
                + "refusal cleanup, stale close, unchanged lock and history bytes");
    }

    private static RewardStorageLock acquire(Path path, boolean forceNull) throws IOException {
        return forceNull ? RewardStorageLock.acquire(path, ignored -> null) : RewardStorageLock.acquire(path);
    }

    private interface Action { void run() throws IOException; }

    private static void refused(Action action, String reason) throws IOException {
        try {
            action.run();
        } catch (IOException expected) {
            check(expected.getMessage().contains(reason) && !expected.getMessage().contains("private-fixture"),
                    "unexpected refusal reason " + expected.getMessage());
            return;
        }
        throw new AssertionError("expected refusal: " + reason);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
