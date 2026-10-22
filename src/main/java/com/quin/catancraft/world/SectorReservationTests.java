package com.quin.catancraft.world;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@GameTestHolder("catancraft_sectors") @PrefixGameTestTemplate(false)
public final class SectorReservationTests {
    @GameTest(template="townhall_state_empty",batch="sector_reservation",timeoutTicks=200)
    public static void pending_city_lock_spans_both_backends_and_rejects_foreign_history(GameTestHelper h) {
        try {
            Path root=Files.createTempDirectory("catancraft-sector-lock-test-");
            Path sector=root.resolve("data/catancraft_sector_a_north_v1.dat"),old=root.resolve("data/catancraft_defense_a_fixture_r4a_r6_1.dat");
            CompoundTag j=new CompoundTag(),id=new CompoundTag();id.putString("world",root.toString());id.putString("city","a");j.put("identity",id);j.putUUID("id",UUID.randomUUID());j.putString("phase","PHYSICAL_COMPLETE");ProducerJournal.write(sector,j,false);
            refused(()->DefenseCityReservations.requireAvailable(root,"a",old));
            DefenseCityReservations.requireAvailable(root,"a",sector);
            j.putString("phase","COMMITTED");ProducerJournal.write(sector,j,true);
            DefenseCityReservations.requireAvailable(root,"a",old);
            j.putString("phase","RESERVED");ProducerJournal.write(old,j,false);
            refused(()->DefenseCityReservations.requireAvailable(root,"a",sector));
            j.putString("phase","ROLLED_BACK");ProducerJournal.write(old,j,true);
            DefenseCityReservations.requireAvailable(root,"a",sector);
            id.putString("world","foreign");ProducerJournal.write(old,j,true);
            refused(()->DefenseCityReservations.requireAvailable(root,"a",sector));h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Checked {void run()throws IOException;}
    private static void refused(Checked c)throws IOException {boolean failed=false;try{c.run();}catch(IOException expected){failed=true;}if(!failed)throw new IOException("Required city reservation refusal missing");}
}
