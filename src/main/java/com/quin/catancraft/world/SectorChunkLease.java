package com.quin.catancraft.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.util.*;

/** Temporary, bounded residency for one construction operation. Never edits persistent /forceload state. */
final class SectorChunkLease implements AutoCloseable {
    private static final TicketType<UUID> TICKET=TicketType.create("catancraft_construction",Comparator.comparing(UUID::toString),100);
    private final ServerLevel level;
    private final UUID id=UUID.randomUUID();
    private final List<ChunkPos> chunks;
    private boolean closed;
    static SectorChunkLease acquire(ServerLevel level,AABB bounds)throws IOException {
        if(!level.getServer().isSameThread())throw new IOException("Construction chunk lease requires the server thread");
        return new SectorChunkLease(level,bounds);
    }
    private SectorChunkLease(ServerLevel level,AABB bounds)throws IOException {
        this.level=level;
        // Include every entity section checked by requireLoadedEntities, including negative-coordinate edges.
        int x0=((int)Math.floor(bounds.minX)-1)>>4,x1=((int)Math.ceil(bounds.maxX)+1)>>4;
        int z0=((int)Math.floor(bounds.minZ)-1)>>4,z1=((int)Math.ceil(bounds.maxZ)+1)>>4;
        long count=((long)x1-x0+1)*((long)z1-z0+1);
        if(count<1||count>512)throw new IOException("Construction chunk envelope exceeds one bounded city");
        List<ChunkPos> positions=new ArrayList<>();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)positions.add(new ChunkPos(x,z));
        chunks=List.copyOf(positions);
        try {
            keepAlive();
            // Source checks must never depend on the player's view/simulation distance.
            for(ChunkPos p:chunks)level.getChunk(p.x,p.z);
        } catch(RuntimeException failure) {close();throw failure;}
    }
    void keepAlive()throws IOException {
        if(closed)throw new IOException("Construction chunk lease is closed");
        for(ChunkPos p:chunks)level.getChunkSource().addRegionTicket(TICKET,p,2,id,true);
    }
    boolean ready() {
        if(closed)return false;
        for(ChunkPos p:chunks)if(level.getChunkSource().getChunkNow(p.x,p.z)==null||!level.areEntitiesLoaded(p.toLong()))return false;
        return true;
    }
    int chunkCount(){return chunks.size();}
    boolean closed(){return closed;}
    List<ChunkPos> positions(){return chunks;}
    @Override public void close(){
        if(closed)return;
        closed=true;
        for(ChunkPos p:chunks)level.getChunkSource().removeRegionTicket(TICKET,p,2,id,true);
    }
}
