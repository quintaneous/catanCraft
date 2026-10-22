package com.quin.catancraft.world;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Per-operation exact-byte metadata cache. Every call still scans all names and reads all sealed bytes. */
final class DefenseReservationVerifier {
    private static final int LIMIT=8*1024*1024,SEALED_LIMIT=LIMIT+40;
    private record Header(String world,String city,UUID id,String phase){}
    private record Entry(byte[] bytes,Header header){}
    private final LinkedHashMap<Path,Entry> cache=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Path,Entry> e){return size()>8;}};
    private int decodes;
    int decodedCount(){return decodes;}
    void requireAvailable(Path root,String city,Path ownJournal)throws IOException {
        if(city==null||!city.matches("[a-p]"))throw new IOException("Unknown reservation city");Path dir=root.resolve("data");
        if(Files.isSymbolicLink(dir))throw new IOException("Defense history directory is a symlink");if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return;
        int checked=0;
        for(String kind:new String[]{"defense","sector","connection"}){
            String suffix=kind.equals("defense")?"_r4a_r6_1.dat":"_v1.dat";
            Set<String> phases=kind.equals("defense")?Set.of("PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK"):Set.of("PREPARED","APPLYING","PHYSICAL_COMPLETE","COMMITTED","ROLLING_BACK","ROLLED_BACK");
            try(var paths=Files.newDirectoryStream(dir,"catancraft_"+kind+"_"+city+"_*"+suffix)){
                for(Path file:paths){if(++checked>4096)throw new IOException("Excess defense histories require review");Header h=header(file);
                    if(!root.toString().equals(h.world())||!city.equals(h.city())||h.id()==null||!phases.contains(h.phase()))throw new IOException("Unknown or foreign defense city history: "+file.getFileName());
                    if(!file.equals(ownJournal)&&!Set.of("COMMITTED","ROLLED_BACK").contains(h.phase()))throw new IOException("Pending "+kind+" city operation; recover retained history "+file.getFileName()+" first");
                }
            }
        }
    }
    private Header header(Path file)throws IOException {
        byte[] raw=sealedBytes(file);Entry previous=cache.get(file);if(previous!=null&&Arrays.equals(previous.bytes(),raw))return previous.header();
        // Decode the EXACT byte array compared above; no path reopen can substitute another journal.
        Header header=decode(raw);cache.put(file,new Entry(raw,header));decodes++;return header;
    }
    private static byte[] sealedBytes(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid producer journal file");
        Set<OpenOption> options=Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
        try(SeekableByteChannel in=Files.newByteChannel(file,options)){
            if(in.size()>SEALED_LIMIT)throw new IOException("Oversized producer journal file");ByteArrayOutputStream out=new ByteArrayOutputStream();ByteBuffer chunk=ByteBuffer.allocate(16384);int total=0;
            for(int n;(n=in.read(chunk))!=-1;){if(n==0)continue;if((total+=n)>SEALED_LIMIT)throw new IOException("Oversized producer journal file");out.write(chunk.array(),0,n);chunk.clear();}return out.toByteArray();
        }
    }
    private static Header decode(byte[] raw)throws IOException {
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw))){
            if(in.readInt()!=0x43505231)throw new IOException("Wrong producer journal format");int size=in.readInt();if(size<1||size>LIMIT)throw new IOException("Invalid producer length");
            byte[] digest=in.readNBytes(32),bytes=in.readNBytes(size);if(digest.length!=32||bytes.length!=size||in.read()!=-1||!HexFormat.of().formatHex(digest).equals(TownHallRevisions.sha(bytes)))throw new IOException("Producer journal checksum mismatch");
            CompoundTag journal;try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] decoded=gzip.readNBytes(64*1024*1024+1);if(decoded.length>64*1024*1024)throw new IOException("Oversized producer NBT");journal=NbtIo.read(new DataInputStream(new ByteArrayInputStream(decoded)));}
            CompoundTag identity=journal.getCompound("identity");return new Header(identity.getString("world"),identity.getString("city"),journal.hasUUID("id")?journal.getUUID("id"):null,journal.getString("phase"));
        }
    }
}
