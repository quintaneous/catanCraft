package com.quin.catancraft.world;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Exact sealed-file mutation checks; never bypasses names, bytes, phases or city reservations. */
@GameTestHolder("catancraft_reservation_verifier") @PrefixGameTestTemplate(false)
public final class DefenseReservationVerifierTests {
    @GameTest(template="townhall_state_empty",batch="reservation_bytes",timeoutTicks=200)
    public static void cached_full_seal_rejects_same_size_same_timestamp_corruption(GameTestHelper h){try{
        Path root=root(h),file=root.resolve("data/catancraft_sector_a_north_v1.dat");CompoundTag j=journal(root,"COMMITTED");ProducerJournal.write(file,j,false);var verifier=new DefenseReservationVerifier();verifier.requireAvailable(root,"a",null);verifier.requireAvailable(root,"a",null);h.assertTrue(verifier.decodedCount()==1,"Unchanged exact bytes unnecessarily decoded");
        byte[] valid=Files.readAllBytes(file),bad=valid.clone();FileTime time=Files.getLastModifiedTime(file);bad[bad.length-1]^=1;Files.write(file,bad);Files.setLastModifiedTime(file,time);h.assertTrue(Files.size(file)==valid.length&&Files.getLastModifiedTime(file).equals(time),"Mutation fixture did not preserve length/time");refuse(()->verifier.requireAvailable(root,"a",null));h.assertTrue(Arrays.equals(bad,Files.readAllBytes(file)),"Verifier modified damaged retained evidence");Files.write(file,valid);verifier.requireAvailable(root,"a",null);h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="reservation_new_pending",timeoutTicks=200)
    public static void new_legacy_pending_and_resealed_phase_transitions_are_observed(GameTestHelper h){try{
        Path root=root(h),own=root.resolve("data/catancraft_sector_a_north_v1.dat"),other=root.resolve("data/catancraft_defense_a_front_r4a_r6_1.dat");ProducerJournal.write(own,journal(root,"APPLYING"),false);var verifier=new DefenseReservationVerifier();verifier.requireAvailable(root,"a",own);ProducerJournal.write(other,journal(root,"RESERVED"),false);refuse(()->verifier.requireAvailable(root,"a",own));
        ProducerJournal.write(other,journal(root,"ROLLED_BACK"),true);verifier.requireAvailable(root,"a",own);ProducerJournal.write(other,journal(root,"COMMITTED"),true);verifier.requireAvailable(root,"a",own);ProducerJournal.write(other,journal(root,"PHYSICAL_COMPLETE"),true);refuse(()->verifier.requireAvailable(root,"a",own));
        CompoundTag foreign=journal(root,"COMMITTED");foreign.getCompound("identity").putString("world",root.resolve("foreign").toString());ProducerJournal.write(other,foreign,true);refuse(()->verifier.requireAvailable(root,"a",own));h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="reservation_eviction",timeoutTicks=200)
    public static void eviction_and_symlink_replacement_never_reuse_unread_metadata(GameTestHelper h){try{
        Path root=root(h);var verifier=new DefenseReservationVerifier();List<Path> files=new ArrayList<>();for(int i=0;i<10;i++){Path p=root.resolve("data/catancraft_sector_a_side"+i+"_v1.dat");ProducerJournal.write(p,journal(root,"COMMITTED"),false);files.add(p);}verifier.requireAvailable(root,"a",null);ProducerJournal.write(files.get(0),journal(root,"APPLYING"),true);refuse(()->verifier.requireAvailable(root,"a",null));ProducerJournal.write(files.get(0),journal(root,"COMMITTED"),true);verifier.requireAvailable(root,"a",null);
        Path p=files.get(0),target=root.resolve("retained-sector-evidence.dat");Files.move(p,target);try{Files.createSymbolicLink(p,target);refuse(()->verifier.requireAvailable(root,"a",null));h.assertTrue(Files.isRegularFile(target)&&Files.isSymbolicLink(p),"Verifier changed retained symlink evidence");}catch(UnsupportedOperationException unsupported){Files.move(target,p);}h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="reservation_connection",timeoutTicks=200)
    public static void pending_connection_blocks_sector_and_legacy_operations(GameTestHelper h){try{
        Path root=root(h),connection=root.resolve("data/catancraft_connection_a_north_east_v1.dat");
        var verifier=new DefenseReservationVerifier();
        for(String phase:List.of("PREPARED","APPLYING","ROLLING_BACK")){
            ProducerJournal.write(connection,journal(root,phase),Files.exists(connection));
            refuse(()->DefenseCityReservations.requireAvailable(root,"a",null));
            refuse(()->verifier.requireAvailable(root,"a",null));
            DefenseCityReservations.requireAvailable(root,"a",connection);verifier.requireAvailable(root,"a",connection);
        }
        for(String phase:List.of("COMMITTED","ROLLED_BACK")){
            ProducerJournal.write(connection,journal(root,phase),true);
            DefenseCityReservations.requireAvailable(root,"a",null);verifier.requireAvailable(root,"a",null);
        }
        h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    private static Path root(GameTestHelper h)throws IOException{if(!(h.getLevel().getServer() instanceof GameTestServer))throw new IOException("Isolated GameTestServer required");Path p=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/reservation-verifier/"+UUID.randomUUID());Files.createDirectories(p.resolve("data"));return p;}
    private static CompoundTag journal(Path root,String phase){CompoundTag j=new CompoundTag(),id=new CompoundTag();j.putUUID("id",UUID.randomUUID());j.putString("phase",phase);id.putString("world",root.toString());id.putString("city","a");j.put("identity",id);return j;}
    private interface Checked{void run()throws IOException;}private static void refuse(Checked c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Expected retained journal refusal");}
}
