package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * One-shot, purchase-driven TH3->TH4 and TH4->TH5 on an explicitly marked,
 * BACKED UP disposable R7 development world only. No blind schematic paste.
 * Unknown edited buildings, occupied interiors, or stale saved placement tokens
 * stop BEFORE any charge or writes. Historical transactions remain immutable.
 *
 * An interrupted journal deliberately locks the upgrade until a dedicated
 * journal reconciliation/rollback tool is built. Do not delete that file.
 */
public final class TownHallPrestigeUpgradeService {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int TOTAL_CELLS = 75 * 47 * 65;
    private static final int LOCAL_MANAGER_X = 37, LOCAL_MANAGER_Y = 5, LOCAL_MANAGER_Z = 17;
    private static final String LEVEL_NAME = "CatanCraft Environment V4 - Visual Polish R2";
    private static final String R7_WORLD_ROOT = "CatanCraft_Season1_R7_Development";

    public record Result(boolean success, String message) {}
    private record Delta(BlockPos pos, BlockState before, BlockState after, CompoundTag beforeNbt) {}
    private TownHallPrestigeUpgradeService() {}

    public static synchronized Result upgrade(MinecraftServer server, CatanSavedData data,
            NationData nation, TerritoryData territory, EconomyCost cost, int nextLevel) {
        boolean journalWritten = false;
        boolean physicalWritten = false;
        boolean economyChanged = false;
        Path journal = null;
        try {
            if(nextLevel != 4 && nextLevel != 5 || territory.cityLevel() != nextLevel - 1)
                throw new IllegalStateException("Invalid Town Hall prestige tier transition");
            Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            if (!R7_WORLD_ROOT.equals(world.getFileName().toString()) ||
                    !LEVEL_NAME.equals(server.getWorldData().getLevelName()))
                throw new IllegalStateException("Town Hall prestige upgrades are test-only on the exact R7 development world");
            Path marker = world.resolve(R5CivicAutomaticInstaller.MARKER_NAME);
            if (!Files.isRegularFile(marker) || Files.size(marker)>250 ||
                    !R5CivicAutomaticInstaller.MARKER_VALUE.equals(Files.readString(marker).trim()))
                throw new IllegalStateException("Missing R7 disposable test-world authorization marker");
            if (!nation.id().equals(territory.ownerNationId()) || data.nation(nation.id()) == null ||
                    !cost.canAfford(nation))
                throw new IllegalStateException("Nation owner or resources changed");

            String cityId = territory.id().toLowerCase(Locale.ROOT);
            TownHallRepairService.requireNoPending(server,cityId);
            String sourceId = "th" + (nextLevel-1);
            String targetId = "th" + nextLevel;
            SchematicAssetRegistry.Asset sourceAsset = SchematicAssetRegistry.asset(sourceId);
            SchematicAssetRegistry.Asset targetAsset = SchematicAssetRegistry.asset(targetId);
            if (sourceAsset == null || targetAsset == null)
                throw new IllegalStateException("Missing Town Hall tier asset contracts");
            String stateKey = "territory:"+cityId+":town_hall";
            String installed = data.placedMapAsset(stateKey);
            int sourceRevision = nextLevel==5 ? TownHallRevisions.revision(4,installed) : 4;
            String sourceToken = nextLevel==4 ? sourceAsset.placementToken() : TownHallRevisions.token(4,sourceRevision);
            String targetToken = TownHallRevisions.token(nextLevel,2);
            if (!sourceToken.equals(installed))
                throw new IllegalStateException("Unexpected existing Town Hall visual token: "+installed+
                        "; cannot replace this building without migration");

            journal = world.resolve("data").resolve("catancraft_th_prestige_"+cityId+"_"+nextLevel+"_v1.dat");
            if (Files.exists(journal))
                throw new IllegalStateException("Historical or incomplete Town Hall journal already exists: "+journal+
                        ". Do not delete or replay it");
            TerritoryDefinition definition = MapDefinitionManager.territory(cityId);
            if (definition == null || definition.cityCenter()==null || definition.siegeRegion()==null ||
                    definition.restorationRegion()==null)
                throw new IllegalStateException("Missing protected city geometry");
            MapAnchor srcAnchor = SchematicAssetRegistry.territoryAnchor(sourceId,definition);
            MapAnchor dstAnchor = SchematicAssetRegistry.territoryAnchor(targetId,definition);
            if (srcAnchor==null||dstAnchor==null ||
                    srcAnchor.x()!=dstAnchor.x() || srcAnchor.y()!=dstAnchor.y() ||
                    srcAnchor.z()!=dstAnchor.z() ||
                    srcAnchor.rotation()!=dstAnchor.rotation() ||
                    (srcAnchor.rotation()!=0 && srcAnchor.rotation()!=180))
                throw new IllegalStateException("Prestige source and destination placement contracts differ");
            if(nextLevel==4) {
                SchematicPlacementService.Result validSrc = SchematicPlacementService.validateAsset(sourceId,srcAnchor);
                if(!validSrc.success())throw new IllegalStateException("TH3 source invalid: "+validSrc.message());
            }
            // TH4/TH5 R1 and R2 come from separately hash-pinned bundled bytes,
            // never an overwritten loose expected-before template.

            ServerLevel level = null;
            for(ServerLevel candidate:server.getAllLevels())
                if(candidate.dimension().location().toString().equals(definition.dimension())){
                    level=candidate;break;
                }
            if(level==null)throw new IllegalStateException("Town Hall dimension is unloaded");
            List<SchematicPlacementService.PlanCell> before = nextLevel==4
                    ? SchematicPlacementService.plannedCells(sourceId,srcAnchor)
                    : TownHallRevisions.plan(4,sourceRevision,srcAnchor);
            List<SchematicPlacementService.PlanCell> after = TownHallRevisions.plan(nextLevel,2,dstAnchor);
            if(before.size()!=TOTAL_CELLS||after.size()!=TOTAL_CELLS)
                throw new IllegalStateException("Town Hall full envelope size changed");
            // A door is ordinary building interaction, not a requirement that
            // the entire Town Hall remain an untouched, closed schematic.
            Map<BlockPos, BlockState> sourceDoors = new HashMap<>();
            Map<BlockPos, BlockState> targetDoors = new HashMap<>();
            for (var cell : before) if (cell.state().getBlock() instanceof DoorBlock)
                sourceDoors.put(cell.position(), cell.state());
            for (var cell : after) if (cell.state().getBlock() instanceof DoorBlock)
                targetDoors.put(cell.position(), cell.state());
            TownHallInteractionPolicy.validateDoorPairs(targetDoors);
            final ServerLevel liveLevel = level;
            for (BlockPos p : sourceDoors.keySet())
                if (!level.hasChunkAt(p)) throw new IllegalStateException("Unloaded Town Hall door at " + p);
            Set<BlockPos> missingDoors = TownHallInteractionPolicy.missingDoorCells(
                    sourceDoors, liveLevel::getBlockState);
            BlockState[] finalStates = new BlockState[TOTAL_CELLS];
            int normalInteractions = 0;
            ArrayList<Delta> changes = new ArrayList<>();
            BlockPos manager = null;
            CompoundTag managerNbt = null;
            int managedEntities=0;
            BlockPos lower=null, upper=null;
            for(int i=0;i<TOTAL_CELLS;i++) {
                var old = before.get(i);var replacement=after.get(i);
                BlockPos p=old.position();
                if(!p.equals(replacement.position()) ||
                        old.localX()!=replacement.localX() || old.localY()!=replacement.localY() ||
                        old.localZ()!=replacement.localZ())
                    throw new IllegalStateException("Mismatched Town Hall coordinates at "+p);
                if(!level.hasChunkAt(p) || !definition.siegeRegion().contains(p) ||
                        !definition.restorationRegion().contains(p))
                    throw new IllegalStateException("Unloaded/outside protected city envelope at "+p);
                if(lower==null){lower=p;upper=p;}
                else {lower=new BlockPos(Math.min(lower.getX(),p.getX()),Math.min(lower.getY(),p.getY()),Math.min(lower.getZ(),p.getZ()));
                      upper=new BlockPos(Math.max(upper.getX(),p.getX()),Math.max(upper.getY(),p.getY()),Math.max(upper.getZ(),p.getZ()));}
                boolean managementCell=old.localX()==LOCAL_MANAGER_X &&
                        old.localY()==LOCAL_MANAGER_Y && old.localZ()==LOCAL_MANAGER_Z;
                BlockState current = level.getBlockState(p);
                boolean compatible = matchesSource(current,old.state(),managementCell);
                if (!current.equals(old.state()) && compatible) normalInteractions++;
                if(!compatible && !missingDoors.contains(p))
                    throw new IllegalStateException("Town Hall modified at "+p+": required "+old.state()+
                            ", found "+current+". Refusing to overwrite player structures");
                if(!level.getFluidState(p).isEmpty())
                    throw new IllegalStateException("Unexpected Town Hall liquid at "+p);
                BlockEntity be=level.getBlockEntity(p);
                if(managementCell) {
                    manager=p;
                    if(old.state().getBlock()!=Blocks.LECTERN || replacement.state().getBlock()!=Blocks.LECTERN ||
                            !matchesSource(old.state(),replacement.state(),true) ||
                            current.getBlock()!=Blocks.LECTERN ||
                            !(be instanceof LecternBlockEntity))
                        throw new IllegalStateException("Management lectern is missing at "+p);
                    // The lectern block itself is NEVER touched; this preserves
                    // books and the existing block-entity/UI data exactly.
                    managerNbt=be.saveWithFullMetadata().copy();
                    finalStates[i]=current;
                    managedEntities++;
                    continue;
                }
                if(be != null) {
                    if(current.getBlock()!=Blocks.LECTERN || !(be instanceof LecternBlockEntity lectern) ||
                            !lectern.getBook().isEmpty())
                        throw new IllegalStateException("Nonempty/unexpected block entity within Town Hall at "+p);
                    // TH3 has one decorative EMPTY lectern removed by TH4.
                }
                if(replacement.state().hasBlockEntity() && replacement.state().getBlock()!=Blocks.LECTERN)
                    throw new IllegalStateException("Unapproved new Town Hall block entity at "+p);
                BlockState target = TownHallInteractionPolicy.targetFor(current,replacement.state());
                finalStates[i]=target;
                if(!current.equals(target))changes.add(new Delta(p,current,target,
                        be==null?null:be.saveWithFullMetadata().copy()));
            }
            if(manager==null || managedEntities!=1 || managerNbt==null)
                throw new IllegalStateException("Exactly one preserved management lectern required");
            // If any players, vehicles, mobs or item entities occupy this
            // large building volume, refuse rather than burying or deleting them.
            AABB occupied=new AABB(lower.getX(),lower.getY(),lower.getZ(),
                    upper.getX()+1,upper.getY()+1,upper.getZ()+1);
            if(!level.getEntities((Entity)null,occupied,entity->!entity.isRemoved()).isEmpty())
                throw new IllegalStateException("Town Hall occupied by an entity; everyone must leave the building before upgrade");

            Set<UUID> previousItems = new HashSet<>();
            AABB observed = occupied.inflate(2.0);
            for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,observed))
                previousItems.add(item.getUUID());

            // Real source preimage, original lectern NBT and all economic
            // balances are journaled DURABLY before modifying any block.
            CompoundTag txn = new CompoundTag();
            txn.putInt("schema",1);txn.putString("phase","PENDING");
            txn.putUUID("transaction",UUID.randomUUID());
            txn.putString("worldRoot",world.toString());txn.putString("city",cityId);
            txn.putString("ownerNation",nation.id().toString());
            txn.putInt("fromLevel",territory.cityLevel());txn.putInt("toLevel",nextLevel);
            txn.putString("sourceToken",sourceToken);
            txn.putString("targetToken",targetToken);
            txn.putString("targetSHA256",TownHallRevisions.hash(nextLevel,2));
            txn.putLong("treasuryBefore",nation.treasury());txn.putLong("treasuryCost",cost.money());
            CompoundTag resources=new CompoundTag();
            for(ResourceType r:ResourceType.values()) {
                CompoundTag row=new CompoundTag();row.putLong("before",nation.resource(r));
                row.putLong("cost",cost.resources().getOrDefault(r,0L));
                resources.put(r.name(),row);
            }
            txn.put("resources",resources);
            txn.putInt("managerX",manager.getX());txn.putInt("managerY",manager.getY());txn.putInt("managerZ",manager.getZ());
            txn.put("managerNbt",managerNbt);
            txn.putString("interactionPolicy","KNOWN_DOOR_PAIRS_AND_NORMAL_USE_V1");
            txn.putInt("acceptedMissingDoorCells",missingDoors.size());
            txn.putInt("acceptedNormalInteractionCells",normalInteractions);
            ListTag deltas=new ListTag();
            for(Delta delta:changes) {
                CompoundTag cell=new CompoundTag();cell.putLong("pos",delta.pos().asLong());
                cell.putString("before",delta.before().toString());
                cell.putString("after",delta.after().toString());
                // Structured actual preimages include AIR for a missing old door.
                // Never fabricate the old closed door as the recovery source.
                cell.put("beforeState",NbtUtils.writeBlockState(delta.before()));
                cell.put("afterState",NbtUtils.writeBlockState(delta.after()));
                if(delta.beforeNbt()!=null)cell.put("beforeBlockEntity",delta.beforeNbt());
                deltas.add(cell);
            }
            txn.put("blocks",deltas);
            writeJournal(journal,txn);journalWritten=true;
            try (java.io.InputStream in=Files.newInputStream(journal)) {
                if(!txn.equals(NbtIo.readCompressed(in)))
                    throw new IllegalStateException("Town Hall before-image journal read-back failed");
            }
            // Client updates + suppress drops and intermediate survival checks.
            // Preserved manager stays at its exact original world position.
            for(Delta delta:changes) {
                if(!level.getBlockState(delta.pos()).equals(delta.before()))
                    throw new IllegalStateException("Changed Town Hall block during upgrade at "+delta.pos());
                if(!level.setBlock(delta.pos(),delta.after(),FLAGS))
                    throw new IllegalStateException("Town Hall placement refused at "+delta.pos());
                physicalWritten=true;
            }
            for(int i=0;i<TOTAL_CELLS;i++) {
                BlockPos p=after.get(i).position();
                if(!level.getBlockState(p).equals(finalStates[i]))
                    throw new IllegalStateException("Final Town Hall full-envelope mismatch at "+p);
            }
            for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,observed))
                if(!previousItems.contains(item.getUUID()))
                    throw new IllegalStateException("Unexpected item drop during Town Hall upgrade at "+item.blockPosition());
            BlockEntity afterManager=level.getBlockEntity(manager);
            if(afterManager==null||!afterManager.saveWithFullMetadata().equals(managerNbt))
                throw new IllegalStateException("Management lectern NBT changed; journal retained");
            txn.putString("phase","PHYSICAL_COMPLETE_ECONOMY_PENDING");writeJournal(journal,txn);
            if(!cost.canAfford(nation)||territory.cityLevel()!=nextLevel-1 ||
                    !sourceToken.equals(data.placedMapAsset(stateKey)))
                throw new IllegalStateException("Economic eligibility changed after physical Town Hall placement");
            cost.charge(nation);
            territory.setCityLevel(nextLevel);
            data.setPlacedMapAsset(stateKey,targetToken);
            data.setDirty();economyChanged=true;
            if(!server.saveEverything(true,true,true))throw new IOException("Town Hall save incomplete; retain journal");
            txn.putString("phase","COMMITTED");writeJournal(journal,txn);
            CatanCraft.LOGGER.info("TH prestige City {} level {} COMMITTED {} writes; {} normal-use cells, {} missing door cells accepted; preserved lectern, journal {}",
                    cityId,nextLevel,changes.size(),normalInteractions,missingDoors.size(),journal);
            return new Result(true,"Town Hall upgraded automatically to Level "+nextLevel+
                     " R2 ("+changes.size()+" guarded block changes; "+missingDoors.size()+
                    " missing source door cells accepted). Management lectern preserved; charged once.");
        }catch(Exception error) {
            CatanCraft.LOGGER.error("Guarded TH prestige upgrade REFUSED city {} to level {}; journal={} written={} physical={} economy={}",
                    territory.id(),nextLevel,journal,journalWritten,physicalWritten,economyChanged,error);
            return new Result(false,"Town Hall prestige upgrade refused: "+error.getMessage()+
                    (journalWritten ? ". Transaction journal preserved; do NOT delete it or retry. Send diagnostics for recovery." :
                            ". No Town Hall blocks were changed and no resources charged."));
        }
    }

    private static boolean matchesSource(BlockState actual,BlockState expected,boolean manager) {
        return TownHallInteractionPolicy.matches(actual,expected,manager);
    }
    private static void writeJournal(Path file,CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp=file.resolveSibling(file.getFileName()+".pending-write");
        try {
            try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){
                NbtIo.writeCompressed(tag,out);
            }
            try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel c=FileChannel.open(file,StandardOpenOption.WRITE)){c.force(true);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
