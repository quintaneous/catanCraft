package com.quin.catancraft.world;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Independently sealed streetscape journals; never reads or rewrites purchase or producer history. */
final class StreetscapeJournal {
    private static final int MAGIC=0x43535031,LIMIT=8*1024*1024;
    private StreetscapeJournal() {}
    static CompoundTag read(Path p) throws IOException {
        if(Files.isSymbolicLink(p) || !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>LIMIT+40)
            throw new IOException("Invalid streetscape journal file");
        try(DataInputStream in=new DataInputStream(Files.newInputStream(p))) {
            if(in.readInt()!=MAGIC)throw new IOException("Wrong streetscape journal format");
            int size=in.readInt();if(size<1 || size>LIMIT)throw new IOException("Invalid streetscape length");
            byte[] digest=in.readNBytes(32),bytes=in.readNBytes(size);
            if(digest.length!=32 || bytes.length!=size || in.read()!=-1
                || !HexFormat.of().formatHex(digest).equals(TownHallRevisions.sha(bytes)))
                throw new IOException("Streetscape journal checksum mismatch");
            try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                byte[] decoded=gzip.readNBytes(64*1024*1024+1);
                if(decoded.length>64*1024*1024)throw new IOException("Oversized streetscape NBT");
                return NbtIo.read(new DataInputStream(new ByteArrayInputStream(decoded)));
            }
        }
    }
    static void write(Path p,CompoundTag n,boolean replace) throws IOException {
        Files.createDirectories(p.getParent());
        if(!replace && Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Streetscape history already exists");
        if(Files.isSymbolicLink(p))throw new IOException("Refusing journal symlink");
        ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(n,out);
        byte[] bytes=out.toByteArray();if(bytes.length>LIMIT)throw new IOException("Oversized streetscape journal");
        Path temp=Files.createTempFile(p.getParent(),p.getFileName().toString(),".writing");
        try {
            try(DataOutputStream d=new DataOutputStream(Files.newOutputStream(temp))) {
                d.writeInt(MAGIC);d.writeInt(bytes.length);d.write(HexFormat.of().parseHex(TownHallRevisions.sha(bytes)));d.write(bytes);
            }
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}
            Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel c=FileChannel.open(p,StandardOpenOption.WRITE)){c.force(true);}
            // Directory fsync is supported on Unix, not all Windows file systems.
            try(FileChannel c=FileChannel.open(p.getParent(),StandardOpenOption.READ)){c.force(true);}
            catch(UnsupportedOperationException|AccessDeniedException ignored){}
            if(!read(p).equals(n))throw new IOException("Streetscape journal read-back failed");
        }finally{Files.deleteIfExists(temp);}
    }
}
