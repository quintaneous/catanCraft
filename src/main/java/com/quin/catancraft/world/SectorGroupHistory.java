package com.quin.catancraft.world;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Shared by both wall schemas. Cached decoding never bypasses sealed-byte comparison. */
final class SectorGroupHistory {
    private SectorGroupHistory() {}
    private record Cached(byte[] bytes, CompoundTag journal) {}
    // Eight purchases form a complete city. The JVM may reclaim older decoded NBT earlier.
    private static final Cache<Path, Cached> CACHE =
            CacheBuilder.newBuilder().maximumSize(8).softValues().build();
    static synchronized CompoundTag history(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>8*1024*1024+40)throw new IOException("Invalid bounded city group history");
        byte[] before=sealedBytes(file);Cached old=CACHE.getIfPresent(file);if(old!=null&&Arrays.equals(old.bytes(),before))return old.journal();
        CompoundTag j=ProducerJournal.read(before);byte[] after=sealedBytes(file);if(!Arrays.equals(before,after))throw new IOException("City history changed while verifying");CACHE.put(file,new Cached(after,j));return j;
    }
    private static byte[] sealedBytes(Path file)throws IOException {
        try(var in=Files.newByteChannel(file,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){
            if(in.size()>8*1024*1024+40)throw new IOException("Oversized qualified city history");var out=new ByteArrayOutputStream();var buffer=java.nio.ByteBuffer.allocate(16384);int total=0;
            for(int n;(n=in.read(buffer))!=-1;){if(n==0)continue;if((total+=n)>8*1024*1024+40)throw new IOException("Growing qualified city history");out.write(buffer.array(),0,n);buffer.clear();}return out.toByteArray();
        }
    }
}
