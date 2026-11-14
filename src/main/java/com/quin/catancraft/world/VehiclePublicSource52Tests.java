package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Native NBT/BlockState contract checks in an isolated server. These do not paste
 * a city, complete its bootstrap, authenticate a client or prove vehicle physics. */
@GameTestHolder("catancraft_vehicle52_public") @PrefixGameTestTemplate(false)
public final class VehiclePublicSource52Tests {
    private static final UUID OWNER = UUID.fromString("52000000-0000-0000-0000-000000000052");
    private static final Path IDENTITY_ROOT = Path.of("/synthetic-vehicle52-public-identity-only");
    private VehiclePublicSource52Tests() {}

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_committed", timeoutTicks=600)
    public static void all_city_completed_and_rolled_back_histories_preserve_inputs(GameTestHelper h) throws Exception {
        controlled(h); int cities = 0, rows = 0;
        for (var city : MapDefinitionManager.territories()) {
            var plan = StreetscapeR5.plan(city.cityCenter());
            var expected = VehiclePublicSource52.identity(IDENTITY_ROOT, city, OWNER);
            var journal = journal(plan, expected, StreetscapeTransaction.Phase.COMMITTED);
            CompoundTag original = journal.copy(), identity = expected.copy();
            require(VehiclePublicSource52.validate(journal, plan, expected), "Committed public revision not recognized");
            require(journal.equals(original) && expected.equals(identity), "Read-only validation modified history");
            journal.putString("phase", StreetscapeTransaction.Phase.ROLLED_BACK.name()); original = journal.copy();
            require(!VehiclePublicSource52.validate(journal, plan, expected), "Rolled-back history selected after-state");
            require(journal.equals(original), "Rollback baseline recognition modified history");
            cities++; rows += journal.getList("blocks", Tag.TAG_COMPOUND).size();
        }
        require(cities == 16 && rows == 26576, "All-city ordered-row coverage incomplete");
        CatanCraft.LOGGER.info("VEHICLE52_PUBLIC_HISTORY cities={} orderedRowsPerPhase={} phases=COMMITTED,ROLLED_BACK readOnly=true", cities, rows);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_identity", timeoutTicks=300)
    public static void foreign_identity_contract_schema_and_pending_phases_refuse(GameTestHelper h) throws Exception {
        controlled(h); var city = MapDefinitionManager.territory("d"); var plan = StreetscapeR5.plan(city.cityCenter());
        var expected = VehiclePublicSource52.identity(IDENTITY_ROOT, city, OWNER);
        var valid = journal(plan, expected, StreetscapeTransaction.Phase.COMMITTED);
        List<Consumer<CompoundTag>> changes = List.of(
                n -> n.putInt("schema", 26), n -> n.putString("schema", "27"), n -> n.remove("id"),
                n -> n.putString("contract", "0".repeat(64)), n -> n.getCompound("identity").putString("city", "a"),
                n -> n.getCompound("identity").putString("owner", UUID.randomUUID().toString()),
                n -> n.getCompound("identity").putString("world", "/foreign-world"),
                n -> n.getCompound("identity").putString("dimension", "minecraft:the_nether"),
                n -> n.getCompound("identity").putIntArray("center", new int[]{750,81,-749,0}),
                n -> n.putString("phase", "UNKNOWN"), n -> n.putString("extra", "unreviewed"));
        int refused = 0;
        for (var change : changes) {
            CompoundTag changed = valid.copy(); change.accept(changed); CompoundTag before = changed.copy();
            refuse(() -> VehiclePublicSource52.validate(changed, plan, expected));
            require(changed.equals(before), "Refusal changed foreign history"); refused++;
        }
        for (var phase : List.of(StreetscapeTransaction.Phase.PREPARED, StreetscapeTransaction.Phase.APPLYING, StreetscapeTransaction.Phase.ROLLING_BACK)) {
            var changed = valid.copy(); changed.putString("phase", phase.name());
            refuse(() -> VehiclePublicSource52.validate(changed, plan, expected)); refused++;
        }
        require(refused == 14, "Identity and pending refusal coverage incomplete"); h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_rows", timeoutTicks=300)
    public static void missing_reordered_and_noncanonical_before_after_images_refuse(GameTestHelper h) throws Exception {
        controlled(h); var city = MapDefinitionManager.territory("i"); var plan = StreetscapeR5.plan(city.cityCenter());
        var expected = VehiclePublicSource52.identity(IDENTITY_ROOT, city, OWNER);
        var valid = journal(plan, expected, StreetscapeTransaction.Phase.COMMITTED);
        List<Consumer<CompoundTag>> changes = List.of(
                n -> n.getList("blocks", Tag.TAG_COMPOUND).remove(0),
                n -> n.getList("blocks", Tag.TAG_COMPOUND).add(row(n,0).copy()),
                n -> n.getList("blocks", Tag.TAG_COMPOUND).set(0, row(n,1).copy()),
                n -> row(n,0).remove("index"), n -> row(n,0).putLong("index", row(n,0).getInt("index")),
                n -> row(n,0).putInt("index", row(n,0).getInt("index")+1),
                n -> row(n,0).putLong("pos", row(n,0).getLong("pos")+1),
                n -> row(n,0).put("before", NbtUtils.writeBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState())),
                n -> row(n,0).getCompound("before").putString("extra", "noncanonical"),
                n -> row(n,0).put("after", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState())),
                n -> row(n,0).remove("after"), n -> n.put("blocks", new ListTag()));
        for (var change : changes) {
            var changed = valid.copy(); change.accept(changed); var before = changed.copy();
            refuse(() -> VehiclePublicSource52.validate(changed, plan, expected));
            require(changed.equals(before), "Malformed row refusal modified stored input");
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_files", timeoutTicks=600)
    public static void absent_sealed_and_symlinked_histories_are_read_only(GameTestHelper h) throws Exception {
        Path world = controlled(h); Path root = Files.createDirectory(world.resolve("vehicle52-public-"+UUID.randomUUID()));
        var city = MapDefinitionManager.territory("d"); var site = site(city, "plot_3");
        require(!VehiclePublicSource52.prepared(root,site,OWNER), "Absent history did not select baseline");
        try (var entries = Files.list(root)) {
            require(entries.findAny().isEmpty(), "Absent-history probe created a directory or file");
        }
        Path data = Files.createDirectory(root.resolve("data")); Path file = data.resolve("catancraft_r5_public_d_v1.dat");
        var plan = StreetscapeR5.plan(city.cityCenter()); var expected = VehiclePublicSource52.identity(root,city,OWNER);
        var valid = journal(plan, expected, StreetscapeTransaction.Phase.COMMITTED); StreetscapeJournal.write(file,valid,false);
        byte[] original = Files.readAllBytes(file);
        require(VehiclePublicSource52.prepared(root,site,OWNER), "Sealed committed history was refused");
        require(Arrays.equals(original,Files.readAllBytes(file)), "Committed probe rewrote history");
        refuse(() -> VehiclePublicSource52.prepared(root,site,UUID.randomUUID()));
        require(Arrays.equals(original,Files.readAllBytes(file)), "Foreign-owner refusal rewrote history");
        Path linkedRoot = Files.createDirectory(world.resolve("vehicle52-public-linked-"+UUID.randomUUID()));
        Files.createSymbolicLink(linkedRoot.resolve("data"),data);
        refuse(() -> VehiclePublicSource52.prepared(linkedRoot,site,OWNER));
        require(Arrays.equals(original,Files.readAllBytes(file)), "Symlink refusal modified target history");
        // Corrupt only this test's newly created copy; preserve original sealed evidence above.
        Path corruptRoot = Files.createDirectory(world.resolve("vehicle52-public-corrupt-"+UUID.randomUUID()));
        Files.createDirectory(corruptRoot.resolve("data")); byte[] corrupt = original.clone(); corrupt[corrupt.length-1] ^= 1;
        Path corruptFile = corruptRoot.resolve("data/catancraft_r5_public_d_v1.dat"); Files.write(corruptFile,corrupt,StandardOpenOption.CREATE_NEW);
        refuse(() -> VehiclePublicSource52.prepared(corruptRoot,site,OWNER));
        require(Arrays.equals(corrupt,Files.readAllBytes(corruptFile)), "Corrupt-history refusal rewrote input");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_geometry", timeoutTicks=1200)
    public static void all_48_real_plot_envelopes_include_public_deltas_but_preserve_service_roads(GameTestHelper h) throws Exception {
        controlled(h); int sites = 0, deltas = 0, roadFloor = 0, roadAir = 0;
        for (var city : MapDefinitionManager.territories()) {
            var overlay = VehiclePublicSource52.after(city);
            require(overlay.size() == StreetscapeR5.WRITES, "Incomplete immutable public source map");
            try { overlay.clear(); throw new AssertionError("Public source map is mutable"); } catch (UnsupportedOperationException expected) {}
            for (String plot : IndustrialPlotService.INDUSTRIAL_PLOTS) {
                var site = site(city,plot); var anchor = site.anchor(); int s = anchor.rotation()==0?1:-1, count = 0;
                for (var p : overlay.keySet()) {
                    int x=s*(p.getX()-anchor.x()), y=p.getY()-anchor.y(), z=s*(p.getZ()-anchor.z());
                    if (x < -29 || x > 10 || y < -2 || y > 7 || z < -22 || z > 23) continue;
                    require(x<=1 && y>=-1 && y<=4, "Public change entered roadside drive columns");
                    require(plot.equals("plot_3")?z>=-22&&z<=-17:z>=17&&z<=23, "Public change entered factory service lane");
                    count++;
                }
                require(count==(plot.equals("plot_3")?160:165), "Actual plot public delta inventory changed");
                deltas+=count; sites++;
                var starter = SchematicAssetRegistry.territoryAnchor("starter_settlement",city);
                for (int x=2;x<8;x++) for (int z=-10;z<10;z++) {
                    var floor = NativeVehicleBay46.sourceCell(anchor,x,-1,z);
                    require(!overlay.containsKey(floor), "Public bootstrap changed service-road floor");
                    require(BasicConstructionAssets.baseline(starter,floor).is(Blocks.GRAY_CONCRETE), "Actual city service-road floor differs"); roadFloor++;
                    for(int y=0;y<5;y++) {
                        var p=NativeVehicleBay46.sourceCell(anchor,x,y,z);
                        require(!overlay.containsKey(p)&&BasicConstructionAssets.baseline(starter,p).isAir(), "Actual city service-road clearance differs"); roadAir++;
                    }
                }
            }
        }
        require(sites==48 && deltas==7840 && roadFloor==5760 && roadAir==28800, "Actual site composition coverage incomplete");
        CatanCraft.LOGGER.info("VEHICLE52_PUBLIC_SOURCE sites={} envelopeDeltas={} roadFloorCells={} roadAirCells={} nativeWorldPlacement=false",sites,deltas,roadFloor,roadAir);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="vehicle52_public_cache", timeoutTicks=200)
    public static void changed_map_cannot_reuse_cached_public_source(GameTestHelper h) throws Exception {
        controlled(h); var city=MapDefinitionManager.territory("d"); var original=VehiclePublicSource52.after(city);
        Gson gson=new Gson();JsonObject changed=gson.toJsonTree(city).getAsJsonObject();
        changed.getAsJsonObject("cityCenter").addProperty("x",city.cityCenter().x()+1);
        var moved=gson.fromJson(changed,TerritoryDefinition.class);
        refuse(() -> VehiclePublicSource52.after(moved));
        require(VehiclePublicSource52.after(city).equals(original), "Refused changed map polluted public-source cache");h.succeed();
    }

    private static VehicleSites52.Site site(TerritoryDefinition city,String plot) throws IOException {
        var anchor=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,plot,city);
        return new VehicleSites52.Site(city,plot,anchor,ShopFacility40.local(anchor,18,3,9));
    }
    private static CompoundTag journal(StreetscapeR5.Plan plan,CompoundTag expected,StreetscapeTransaction.Phase phase) {
        CompoundTag n=new CompoundTag(); n.putInt("schema",27);n.putUUID("id",UUID.randomUUID());n.putString("contract",StreetscapeR5.HASH);
        n.put("identity",expected.copy());n.putString("phase",phase.name());ListTag rows=new ListTag();
        for(int i=0;i<plan.cells().size();i++) {
            var c=plan.cells().get(i);if(!c.changes())continue;CompoundTag row=new CompoundTag();
            row.putInt("index",i);row.putLong("pos",c.pos().asLong());row.put("before",NbtUtils.writeBlockState(c.before()));
            row.put("after",NbtUtils.writeBlockState(c.after()));rows.add(row);
        }
        n.put("blocks",rows);return n;
    }
    private static CompoundTag row(CompoundTag n,int index){return n.getList("blocks",Tag.TAG_COMPOUND).getCompound(index);}
    @FunctionalInterface private interface Attempt { void run() throws Exception; }
    private static void refuse(Attempt call) throws Exception {try{call.run();}catch(IOException expected){return;}throw new AssertionError("Expected public-source refusal");}
    private static void require(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static Path controlled(GameTestHelper h)throws IOException {
        var server=h.getLevel().getServer();Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(server instanceof GameTestServer)||!server.isSameThread()||!server.getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-catancraft_vehicle52_public-run")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
            throw new IOException("Vehicle52 public tests require their exact marked disposable native world");
        return root;
    }
}
