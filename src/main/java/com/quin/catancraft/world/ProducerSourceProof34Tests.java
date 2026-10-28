package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Generated native fixtures; immutable source exposure, not arbitrary ground repair. */
@GameTestHolder("catancraft_producer_source34") @PrefixGameTestTemplate(false)
public final class ProducerSourceProof34Tests {
    private static final String INTERRUPTED="intentional producer source-proof interruption";
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY,
        RESUME=ProducerTransaction.Action.RESUME,ROLLBACK=ProducerTransaction.Action.ROLLBACK;
    private static final int FLAGS=2|16|32;

    @GameTest(template="townhall_state_empty",batch="producer_source34_resume",timeoutTicks=1200)
    public static void prepared_native_source_decay_resumes_once_preserving_inventory(GameTestHelper h)throws Exception {
        var e=fixture(h,0);var nation=e.nation().save();var inventories=e.inventories();
        prepared(h,e);BlockPos grass=coveredLumberyardGrass(h,e);
        CompoundTag proof=ProducerJournal.read(e.file()).getCompound("sourceCoveredGrassV1").copy();
        assertProved(h,proof,grass,e.level().getBlockState(grass.above()));
        decay(h,e,grass);e.reload();
        h.assertTrue(ProducerTransaction.run(e,RESUME).writes()==4291,"Recovery changed the delivered grouped write count");
        assertCompleted(h,e,nation,inventories,grass,proof);
        CatanCraft.LOGGER.info("PRODUCER_SOURCE_PROOF preparedNativeDecay=PASS originalPriceOnce=PASS readOnlyDirt=PASS inventory=PASS persistedReceipt=PASS");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="producer_source34_fresh",timeoutTicks=1200)
    public static void fresh_aged_source_seals_exact_cover_and_preserves_dirt(GameTestHelper h)throws Exception {
        var e=fixture(h,180);BlockPos grass=coveredLumberyardGrass(h,e);
        BlockState cover=e.level().getBlockState(grass.above());decay(h,e,grass);
        var nation=e.nation().save();var inventories=e.inventories();
        h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==4291,"Fresh aged source changed the delivered write count");
        CompoundTag proof=ProducerJournal.read(e.file()).getCompound("sourceCoveredGrassV1").copy();
        assertProved(h,proof,grass,cover);assertCompleted(h,e,nation,inventories,grass,proof);
        CatanCraft.LOGGER.info("PRODUCER_SOURCE_PROOF freshAgedSource=PASS exactSourceCover=PASS originalPriceOnce=PASS dirtNotRepainted=PASS");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="producer_source34_legacy",timeoutTicks=1200)
    public static void proofless_history_keeps_strict_source_without_charge_or_rewrite(GameTestHelper h)throws Exception {
        var e=fixture(h,0);prepared(h,e);
        CompoundTag legacy=ProducerJournal.read(e.file());legacy.remove("sourceCoveredGrassV1");
        // Test-only generation of the old schema: a valid checksum with no new proof.
        ProducerJournal.write(e.file(),legacy,true);decay(h,e,coveredLumberyardGrass(h,e));e.reload();
        assertRefusalUnchanged(h,e,RESUME,"Modified producer cell");
        assertRefusalUnchanged(h,e,ROLLBACK,"Modified producer cell");
        h.assertTrue(!ProducerJournal.read(e.file()).contains("sourceCoveredGrassV1")
            &&ProducerTransaction.phase(ProducerJournal.read(e.file()))==ProducerTransaction.Phase.PREPARED,
            "Old history was upgraded or advanced without source proof");
        CatanCraft.LOGGER.info("PRODUCER_SOURCE_PROOF prooflessLegacyStrict=PASS resumeAndRollbackRefuse=PASS noCharge=PASS originalHistoryRetained=PASS");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="producer_source34_refusals",timeoutTicks=1200)
    public static void corrupt_proof_exposed_dirt_and_foreign_states_refuse(GameTestHelper h)throws Exception {
        var e=fixture(h,180);prepared(h,e);CompoundTag original=ProducerJournal.read(e.file());
        CompoundTag changed=original.copy();changed.getCompound("sourceCoveredGrassV1").putInt("version",2);
        ProducerJournal.write(e.file(),changed,true);assertRefusalUnchanged(h,e,RESUME,"Changed producer source-cover proof");

        BlockPos exposed=exposedGrass(h,e);changed=original.copy();
        CompoundTag invented=new CompoundTag();invented.putLong("pos",exposed.asLong());
        invented.put("sourceCover",NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        changed.getCompound("sourceCoveredGrassV1").getList("covers",Tag.TAG_COMPOUND).add(invented);
        ProducerJournal.write(e.file(),changed,true);assertRefusalUnchanged(h,e,RESUME,"Changed producer source-cover proof");

        // Restore only the synthetic damaged journal, then inject distinct world conflicts.
        ProducerJournal.write(e.file(),original,true);
        e.level().setBlock(exposed,Blocks.DIRT.defaultBlockState(),FLAGS);
        assertRefusalUnchanged(h,e,RESUME,"Modified producer cell");
        e.level().setBlock(exposed,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
        BlockPos grass=coveredLumberyardGrass(h,e);
        e.level().setBlock(grass,Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);
        assertRefusalUnchanged(h,e,RESUME,"Modified producer cell");
        e.level().setBlock(grass,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
        e.level().setBlock(grass.above(),Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);
        assertRefusalUnchanged(h,e,RESUME,"Modified producer cell");
        CatanCraft.LOGGER.info("PRODUCER_SOURCE_PROOF corruptVersion=PASS inventedExposure=PASS exposedDirt=PASS foreignGround=PASS foreignCover=PASS noChargeOrWrites=PASS");
        h.succeed();
    }

    private static ProducerTests.Env fixture(GameTestHelper h,int rotation)throws IOException {
        h.assertTrue(h.getLevel().getServer() instanceof GameTestServer,"Source proof fixture outside GameTestServer");
        var e=new ProducerTests.Env(h,rotation,1);e.prepare(1);e.select(1,2);return e;
    }
    private static void prepared(GameTestHelper h,ProducerTests.Env e)throws IOException {
        boolean[] reached={false};
        try{ProducerTransaction.run(e,APPLY,(stage,n)->{if(stage.equals("prepared")){reached[0]=true;throw new IOException(INTERRUPTED);}});h.fail("Prepared interruption was not reached");}
        catch(IOException expected){h.assertTrue(reached[0]&&INTERRUPTED.equals(expected.getMessage()),"Unexpected preparation refusal: "+expected);}
        h.assertTrue(ProducerTransaction.phase(ProducerJournal.read(e.file()))==ProducerTransaction.Phase.PREPARED,"Missing durable PREPARED intent");
    }
    private static BlockPos coveredLumberyardGrass(GameTestHelper h,ProducerTests.Env e)throws IOException {
        var old=ProducerRevisions.plan("lumberyard",1,e.anchors.get("lumberyard"));
        var next=ProducerRevisions.plan("lumberyard",2,e.anchors.get("lumberyard"));
        for(int i=0;i<old.size();i++)if(old.get(i).localX()==12&&old.get(i).localY()==1&&old.get(i).localZ()==0){
            BlockPos p=old.get(i).position();
            h.assertTrue(old.get(i).state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                &&old.get(i).state().equals(next.get(i).state())
                &&e.level().getBlockState(p.above()).is(Blocks.SPRUCE_LOG),"Pinned unchanged covered lumberyard source changed");
            return p;
        }
        throw new IOException("Pinned covered lumberyard source position missing");
    }
    private static BlockPos exposedGrass(GameTestHelper h,ProducerTests.Env e)throws IOException {
        var old=ProducerRevisions.plan("lumberyard",1,e.anchors.get("lumberyard"));
        var next=ProducerRevisions.plan("lumberyard",2,e.anchors.get("lumberyard"));
        for(int i=0;i<old.size();i++)if(old.get(i).state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
            &&old.get(i).state().equals(next.get(i).state())&&e.level().getBlockState(old.get(i).position().above()).is(Blocks.AIR)){
            BlockPos p=old.get(i).position();
            for(Tag t:ProducerJournal.read(e.file()).getCompound("sourceCoveredGrassV1").getList("covers",Tag.TAG_COMPOUND))
                h.assertTrue(((CompoundTag)t).getLong("pos")!=p.asLong(),"Exposed grass incorrectly received source-cover proof");
            return p;
        }
        throw new IOException("No unchanged exposed source grass fixture");
    }
    private static void decay(GameTestHelper h,ProducerTests.Env e,BlockPos grass){
        h.assertTrue(e.level().getBlockState(grass).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Native decay did not start with exact grass");
        Blocks.GRASS_BLOCK.randomTick(e.level().getBlockState(grass),e.level(),grass,RandomSource.create(7));
        h.assertTrue(e.level().getBlockState(grass).equals(Blocks.DIRT.defaultBlockState()),"Native covered grass did not decay to exact dirt");
    }
    private static void assertProved(GameTestHelper h,CompoundTag proof,BlockPos grass,BlockState cover){
        h.assertTrue(proof.getInt("version")==1,"Source-cover proof version missing");int matches=0;
        for(Tag t:proof.getList("covers",Tag.TAG_COMPOUND))if(((CompoundTag)t).getLong("pos")==grass.asLong()){
            matches++;h.assertTrue(((CompoundTag)t).getCompound("sourceCover").equals(NbtUtils.writeBlockState(cover)),"Proof did not capture the exact pinned source cover");
        }
        h.assertTrue(matches==1,"Covered source did not have exactly one proof entry");
    }
    private static void assertCompleted(GameTestHelper h,ProducerTests.Env e,CompoundTag before,
            Map<BlockPos,CompoundTag> inventories,BlockPos dirt,CompoundTag proof)throws IOException {
        NationData original=NationData.load(before);
        h.assertTrue(e.nation().treasury()==original.treasury()-e.spec().cost().money(),"Money was not charged exactly once");
        for(ResourceType type:ResourceType.values())h.assertTrue(e.nation().resource(type)==original.resource(type)-e.spec().cost().resources().getOrDefault(type,0L),"Resource charge differs");
        CompoundTag journal=ProducerJournal.read(e.file());
        h.assertTrue(ProducerTransaction.phase(journal)==ProducerTransaction.Phase.COMMITTED
            &&e.territory().producerLevel()==2&&e.receipt().equals(journal.getUUID("id")+":paid")
            &&proof.equals(journal.getCompound("sourceCoveredGrassV1")),"Completion receipt, level or immutable proof differs");
        for(String family:ProducerRevisions.FAMILIES)for(var cell:ProducerRevisions.plan(family,2,e.anchors.get(family))){
            BlockState actual=e.level().getBlockState(cell.position());
            h.assertTrue(cell.position().equals(dirt)?actual.equals(Blocks.DIRT.defaultBlockState()):ProducerRevisions.matches(actual,cell.state()),"Completed geometry differs at "+cell.position());
        }
        h.assertTrue(inventories.equals(e.inventories()),"Recovery changed protected inventory NBT");
        byte[] sealed=Files.readAllBytes(e.file());CompoundTag ledger=e.ledger().copy();e.reload();
        h.assertTrue(ledger.equals(e.ledger()),"Saved ledger differs after reload");
        h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0&&ProducerTransaction.run(e,RESUME).writes()==0
            &&ledger.equals(e.ledger())&&Arrays.equals(sealed,Files.readAllBytes(e.file()))
            &&inventories.equals(e.inventories())&&e.level().getBlockState(dirt).equals(Blocks.DIRT.defaultBlockState()),
            "Completed recovery replayed payment, writes, history or inventory");
    }
    private static void assertRefusalUnchanged(GameTestHelper h,ProducerTests.Env e,ProducerTransaction.Action action,String reason)throws IOException {
        byte[] history=Files.readAllBytes(e.file());CompoundTag ledger=e.ledger().copy();
        var world=e.snapshot();var inventory=e.inventories();boolean refused=false;
        try{ProducerTransaction.run(e,action);}catch(IOException expected){refused=true;h.assertTrue(expected.getMessage().contains(reason),"Wrong refusal: "+expected);}
        h.assertTrue(refused&&ledger.equals(e.ledger())&&world.equals(e.snapshot())&&inventory.equals(e.inventories())
            &&Arrays.equals(history,Files.readAllBytes(e.file())),"Refusal charged, wrote, changed inventory or rewrote history");
    }
}
