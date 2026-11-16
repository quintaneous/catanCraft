package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.quin.catancraft.world.StreetscapeNatural53Tests.*;

/** Two-launch proof. Verify refuses a same-process replay or a changed candidate. */
@GameTestHolder("catancraft_streetscape_restart53") @PrefixGameTestTemplate(false)
public final class StreetscapeRestart53Tests {
    private static final UUID BOOT=UUID.randomUUID();
    @GameTest(template="townhall_state_empty",batch="street_restart53",timeoutTicks=2000)
    public static void source_prepare_and_target_rollback_survive_process_restart(GameTestHelper h) {
        try {
            String phase=System.getProperty("catancraft.natural53Phase","");
            String source=System.getProperty("catancraft.natural53Source","");
            if(!Set.of("seed","verify").contains(phase)||!source.matches("[0-9a-f]{40}"))throw new IOException("Explicit natural53Phase seed|verify and exact natural53Source are required");
            Path root=h.getLevel().getServer().getWorldPath(LevelResource.ROOT),proof=root.resolve("data/streetscape53-restart.dat");
            if(phase.equals("seed"))seed(h,root,proof,source);else verify(h,root,proof,source);
            h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static Env env(GameTestHelper h,Path root,int rotation,boolean target)throws IOException {
        int index=rotation==0?0:1;
        return new Env(h,rotation,16384+index*1024+(target?512:0),root.resolve("data/streetscape53-restart/"+rotation+"-"+(target?"target":"source")+"/journal.dat"));
    }
    private static void seed(GameTestHelper h,Path root,Path proof,String source)throws Exception {
        if(Files.exists(proof,LinkOption.NOFOLLOW_LINKS))throw new IOException("Restart seed refuses existing evidence");
        CompoundTag saved=new CompoundTag();saved.putInt("schema",1);saved.putString("source",source);saved.putUUID("boot",BOOT);saved.putString("world",root.toAbsolutePath().normalize().toString());
        ListTag cases=new ListTag();
        for(int rotation:new int[]{0,180}) {
            Env a=env(h,root,rotation,false);a.prepare();boolean[] prepared={false};
            refuse(h,()->StreetscapeTransaction.run(a,APPLY,(stage,writes)->{if(stage.equals("prepared")){prepared[0]=true;throw new IOException("Deliberate restart after prepared source capture");}}));
            h.assertTrue(prepared[0]&&StreetscapeTransaction.phase(StreetscapeJournal.read(a.file()))==StreetscapeTransaction.Phase.PREPARED,"Source restart fixture did not reach PREPARED");
            a.decay(a.source());record(cases,a,rotation,false);
            Env b=env(h,root,rotation,true);b.prepare();b.decay(b.source());StreetscapeTransaction.run(b,APPLY);b.decay(b.target());
            boolean[] interrupted={false};
            refuse(h,()->StreetscapeTransaction.run(b,ROLLBACK,(stage,writes)->{
                if(stage.equals("rollback_write")&&removedTargetCover(b)){interrupted[0]=true;throw new IOException("Deliberate restart after target cover removal");}
            }));
            h.assertTrue(interrupted[0]&&StreetscapeTransaction.phase(StreetscapeJournal.read(b.file()))==StreetscapeTransaction.Phase.ROLLING_BACK,"Target restart fixture did not stop after actual cover removal");
            h.assertTrue(StreetscapeJournal.read(b.file()).getCompound("naturalSoilV1").getLongArray("observedTargetDirt").length==18,"Rollback did not persist all actual target dirt observations before writes");
            record(cases,b,rotation,true);
        }
        saved.put("cases",cases);h.getLevel().getServer().saveEverything(false,true,true);NbtIo.writeCompressed(saved,proof.toFile());
        h.assertTrue(saved.equals(NbtIo.readCompressed(proof.toFile())),"Restart evidence did not persist");
        CatanCraft.LOGGER.info("STREETSCAPE_RESTART53 phase=seed cases=4 rotations=0,180 source={} sourcePrepared=2 targetRollingBack=2",source);
    }
    private static boolean removedTargetCover(Env e) {
        for(var row:e.target().entrySet())if(!e.level().getBlockState(row.getKey().above()).equals(row.getValue()))return true;return false;
    }
    private static void record(ListTag rows,Env e,int rotation,boolean target)throws IOException {
        CompoundTag row=new CompoundTag();row.putInt("rotation",rotation);row.putBoolean("target",target);row.put("journal",StreetscapeJournal.read(e.file()));row.put("ledger",e.ledger());rows.add(row);
    }
    private static void verify(GameTestHelper h,Path root,Path proof,String source)throws Exception {
        if(!Files.isRegularFile(proof,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(proof))throw new IOException("Missing original restart evidence; never restage verify");
        CompoundTag saved=NbtIo.readCompressed(proof.toFile());
        if(saved.getInt("schema")!=1||!saved.getString("source").equals(source)||!saved.hasUUID("boot")||saved.getUUID("boot").equals(BOOT)
            ||!saved.getString("world").equals(root.toAbsolutePath().normalize().toString())||saved.getList("cases",Tag.TAG_COMPOUND).size()!=4)throw new IOException("Restart requires a different process with the exact original world/candidate");
        Set<String> identities=new HashSet<>();
        for(Tag value:saved.getList("cases",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value;int rotation=row.getInt("rotation");boolean target=row.getBoolean("target");
            if((rotation!=0&&rotation!=180)||!identities.add(rotation+":"+target))throw new IOException("Duplicate/foreign restart case");
            Env e=env(h,root,rotation,target);e.loadChunks();e.reload();CompoundTag initial=StreetscapeJournal.read(e.file());
            h.assertTrue(initial.equals(row.getCompound("journal"))&&e.ledger().equals(row.getCompound("ledger")),"Original process journal/ledger changed before recovery");
            Map<BlockPos,BlockState> retainedSoil=new HashMap<>();
            for(var cell:e.plan().cells())if(!cell.changes()&&(e.source().containsKey(cell.pos())||e.target().containsKey(cell.pos())))retainedSoil.put(cell.pos(),e.level().getBlockState(cell.pos()));
            if(target) {
                h.assertTrue(StreetscapeTransaction.phase(initial)==StreetscapeTransaction.Phase.ROLLING_BACK&&removedTargetCover(e),"Partial rollback blocks did not survive process restart");
                for(var cover:e.target().entrySet()) {
                    BlockState actual=e.level().getBlockState(cover.getKey());boolean covered=e.level().getBlockState(cover.getKey().above()).equals(cover.getValue());
                    h.assertTrue(actual.equals(Blocks.DIRT.defaultBlockState())||!covered&&actual.equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Retained target soil differs after restart");
                }
                StreetscapeTransaction.run(e,ROLLBACK);
            }else {
                h.assertTrue(StreetscapeTransaction.phase(initial)==StreetscapeTransaction.Phase.PREPARED,"Prepared source journal did not survive restart");
                for(BlockPos p:e.source().keySet())h.assertTrue(e.level().getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Actual covered source dirt did not survive restart");
                StreetscapeTransaction.run(e,RESUME);StreetscapeTransaction.run(e,ROLLBACK);
            }
            Map<BlockPos,BlockState> expected=new LinkedHashMap<>();for(var c:e.plan().cells())expected.put(c.pos(),c.before());
            if(target)for(var c:e.plan().cells())if(c.changes()&&e.source().containsKey(c.pos()))expected.put(c.pos(),Blocks.DIRT.defaultBlockState());
            expected.putAll(retainedSoil);
            e.verify(expected);StreetscapeTransaction.run(e,STATUS);
            CompoundTag completed=StreetscapeJournal.read(e.file());
            h.assertTrue(StreetscapeTransaction.phase(completed)==StreetscapeTransaction.Phase.ROLLED_BACK&&e.ledger().equals(row.getCompound("ledger")),"Recovery changed economy or did not complete rollback");
            h.assertTrue(initial.getUUID("id").equals(completed.getUUID("id"))&&initial.get("blocks").equals(completed.get("blocks"))
                &&initial.getCompound("naturalSoilV1").get("sourceBefore").equals(completed.getCompound("naturalSoilV1").get("sourceBefore")),"Recovery rewrote captured before-images");
        }
        h.getLevel().getServer().saveEverything(false,true,true);
        CompoundTag result=new CompoundTag();result.putString("source",source);result.putUUID("seedBoot",saved.getUUID("boot"));result.putUUID("verifyBoot",BOOT);result.putInt("passedCases",4);
        NbtIo.writeCompressed(result,proof.resolveSibling("streetscape53-restart-verified.dat").toFile());
        CatanCraft.LOGGER.info("STREETSCAPE_RESTART53 phase=verify differentProcess=true cases=4 rotations=0,180 source={} exactBeforeImages=true",source);
    }
}
