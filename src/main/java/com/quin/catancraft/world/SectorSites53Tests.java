package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Native registry/contract validation of each independently audited actual site.
 * This suite writes no terrain and is not a substitute for the authored-world purchase suite. */
@GameTestHolder("catancraft_sector53") @PrefixGameTestTemplate(false)
public final class SectorSites53Tests {
    private interface Checked { void run()throws Exception; }
    private static void checked(GameTestHelper h,Checked action){try{action.run();h.succeed();}catch(Exception failure){throw new RuntimeException(failure);}}
    @GameTest(template="townhall_state_empty",batch="sector53_00",timeoutTicks=24000)
    public static void city_a_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"a"));}
    @GameTest(template="townhall_state_empty",batch="sector53_01",timeoutTicks=24000)
    public static void city_b_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"b"));}
    @GameTest(template="townhall_state_empty",batch="sector53_02",timeoutTicks=24000)
    public static void city_c_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"c"));}
    @GameTest(template="townhall_state_empty",batch="sector53_03",timeoutTicks=24000)
    public static void city_d_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"d"));}
    @GameTest(template="townhall_state_empty",batch="sector53_04",timeoutTicks=24000)
    public static void city_e_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"e"));}
    @GameTest(template="townhall_state_empty",batch="sector53_05",timeoutTicks=24000)
    public static void city_f_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"f"));}
    @GameTest(template="townhall_state_empty",batch="sector53_06",timeoutTicks=24000)
    public static void city_g_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"g"));}
    @GameTest(template="townhall_state_empty",batch="sector53_07",timeoutTicks=24000)
    public static void city_h_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"h"));}
    @GameTest(template="townhall_state_empty",batch="sector53_08",timeoutTicks=24000)
    public static void city_i_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"i"));}
    @GameTest(template="townhall_state_empty",batch="sector53_09",timeoutTicks=24000)
    public static void city_j_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"j"));}
    @GameTest(template="townhall_state_empty",batch="sector53_10",timeoutTicks=24000)
    public static void city_k_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"k"));}
    @GameTest(template="townhall_state_empty",batch="sector53_11",timeoutTicks=24000)
    public static void city_l_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"l"));}
    @GameTest(template="townhall_state_empty",batch="sector53_12",timeoutTicks=24000)
    public static void city_m_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"m"));}
    @GameTest(template="townhall_state_empty",batch="sector53_13",timeoutTicks=24000)
    public static void city_n_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"n"));}
    @GameTest(template="townhall_state_empty",batch="sector53_14",timeoutTicks=24000)
    public static void city_o_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"o"));}
    @GameTest(template="townhall_state_empty",batch="sector53_15",timeoutTicks=24000)
    public static void city_p_all_levels_corners_transitions_and_rollback(GameTestHelper h){checked(h,()->geometry(h,"p"));}
    private static void geometry(GameTestHelper h,String city)throws Exception {
        var site=SectorSites53.site(city);SectorSites53.canonical(site.definition());
        Map<BlockPos,SectorContracts.Direction> owners=SectorSites53.ownership(site);
        int vectors=0,histories=0,transitions=0;
        for(var side:SectorContracts.Direction.values()){
            long sum=0;int count=0;
            for(var row:owners.entrySet())if(row.getValue()==side){BlockPos p=row.getKey().subtract(site.center());sum+=(long)p.getX()*side.dx+(long)p.getZ()*side.dz;count++;}
            h.assertTrue(count>0&&sum>0,"Wall side is not its actual world compass side: "+city+" "+side);
        }
        for(int n=0;n<3;n++)for(int e=0;e<3;e++)for(int s=0;s<3;s++)for(int w=0;w<3;w++){
            var levels=SectorCityAP3.levels(n,e,s,w);var available=SectorSites53.availableConnections(site,levels);
            h.assertTrue(available.size()==((n>0&&e>0?1:0)+(n>0&&w>0?1:0)+(s>0&&e>0?1:0)+(s>0&&w>0?1:0)),"Physical compass corner availability differs");
            List<String> ordered=available.stream().sorted().toList();
            for(int mask=0;mask<(1<<ordered.size());mask++){
                Set<String> open=new HashSet<>();for(int i=0;i<ordered.size();i++)if((mask&(1<<i))!=0)open.add(ordered.get(i));
                Map<BlockPos,BlockState> assembly=SectorSites53.assembly(site,levels,open);
                h.assertTrue(assembly.keySet().containsAll(owners.keySet()),"Missing owned wall cell");
                for(var row:site.sourceOverrides().entrySet())h.assertTrue(row.getValue().equals(assembly.get(row.getKey())),"Wall assembly changes qualified surrounding context");
                for(var row:site.ownedSourceOverrides().entrySet()){
                    int tier=levels.get(owners.get(row.getKey()));var expected=tier==0?Blocks.GRASS_BLOCK.defaultBlockState():tier==1?Blocks.STONE_BRICKS.defaultBlockState():Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                    h.assertTrue(expected.equals(assembly.get(row.getKey())),"Exact native soil source was erased or carried into a built tier");
                }
                histories++;
            }
            var before=SectorSites53.assembly(site,levels,available);
            for(var side:SectorContracts.Direction.values())if(levels.get(side)<2){
                var transition=SectorSites53.transition(site,side,levels,available);var plan=transition.plan();
                h.assertTrue(plan.sector().equals(side.name().toLowerCase(Locale.ROOT))&&plan.sourceLevel()==levels.get(side)&&plan.targetLevel()==levels.get(side)+1,"Plan is bound to a local instead of physical side");
                h.assertTrue(SectorNaturalSupportPolicy.geometry(plan,false).equals(before),"Plan does not preserve exact actual-site source");
                var after=SectorSites53.assembly(site,transition.afterLevels(),transition.afterConnections());
                h.assertTrue(SectorNaturalSupportPolicy.geometry(plan,true).equals(after),"Plan target differs from qualified whole-city composition");
                Map<BlockPos,BlockState> replay=new HashMap<>(before);
                for(var cell:plan.cells()){
                    h.assertTrue(owners.containsKey(cell.pos())&&!site.protectedRoads().contains(cell.pos()),"Wall write leaves exclusive ownership or enters protected road");
                    h.assertTrue(replay.get(cell.pos()).equals(cell.before()),"Wrong source at wall write");replay.put(cell.pos(),cell.after());
                }
                h.assertTrue(replay.equals(after),"Wall transition changes unrelated city state");
                for(var cell:plan.cells())replay.put(cell.pos(),cell.before());
                h.assertTrue(replay.equals(before),"Rollback fails to restore exact native source");
                plan.naturalSupport().validate(plan);transitions++;
            }
            vectors++;
        }
        h.assertTrue(vectors==81&&histories==433&&transitions==216,"Incomplete wall level/corner/transition matrix");
        com.mojang.logging.LogUtils.getLogger().info("Sector53 city {}: {} level vectors, {} supported corner histories, {} forward/reverse transitions",city,vectors,histories,transitions);
    }

    @GameTest(template="townhall_state_empty",batch="sector53_16",timeoutTicks=24000)
    public static void legacy_a_unchanged_and_all_new_city_contracts_bound(GameTestHelper h){checked(h,()->{
        var a=context(h,"a");var b=context(h,"b");
        var original=SectorCityAP3Group.fixtureContract(a,SectorContracts.Direction.NORTH,0,BlockPos.ZERO);
        h.assertTrue(original.cityGroup().getInt("schema")==1&&!original.cityGroup().contains("siteCity"),"City A journal protocol was rewritten");
        h.assertTrue(SectorGroups53.approvedWrite(a,original,original.plan().cells().get(0).pos()),"Historical City A dispatch no longer works");
        refuse(()->SectorGroup53.fixtureContract(a,SectorContracts.Direction.NORTH,0,BlockPos.ZERO));
        refuse(()->SectorGroups53.approvedWrite(b,original,original.plan().cells().get(0).pos()));
        int cities=0;
        for(char c='b';c<='p';c++){
            String city=String.valueOf(c);var context=context(h,city);
            var contract=SectorGroup53.fixtureContract(context,SectorContracts.Direction.NORTH,0,BlockPos.ZERO);
            CompoundTag group=contract.cityGroup();SectorGroup53.supportHeader(group);
            var wrongCorners=new EnumMap<SectorContracts.Corner,SectorContracts.Direction>(SectorContracts.Corner.class);wrongCorners.putAll(contract.corners());
            wrongCorners.put(SectorContracts.Corner.NORTH_EAST,SectorContracts.Direction.EAST);
            var corrupted=new SectorContracts.Contract(contract.world(),contract.city(),contract.sector(),contract.plan(),wrongCorners,contract.neighbors(),contract.roads(),contract.cost(),group);
            refuse(()->SectorGroups53.approvedWrite(context,corrupted,contract.plan().cells().get(0).pos()));
            h.assertTrue(group.getInt("schema")==2&&group.getString("siteCity").equals(city)&&group.getLong("translation")==BlockPos.ZERO.asLong(),"New group missed its actual site binding");
            h.assertTrue(SectorGroups53.approvedWrite(context,contract,contract.plan().cells().get(0).pos()),"Own exact-site write refused");
            h.assertTrue(!SectorGroups53.approvedWrite(context,contract,SectorSites53.site(city).center()),"City-center context became a wall write");
            for(var other:List.of(a,context(h,city.equals("b")?"d":"b")))refuse(()->SectorGroups53.approvedWrite(other,contract,contract.plan().cells().get(0).pos()));
            for(String key:List.of("siteCity","qualificationSHA256","mapSHA256","archiveSHA256","resourceSHA256")){
                var changed=group.copy();changed.putString(key,"foreign");refuse(()->SectorGroup53.supportHeader(changed));
            }
            var wrongCenter=group.copy();wrongCenter.putLong("siteCenter",SectorSites53.site(city).center().above().asLong());refuse(()->SectorGroup53.supportHeader(wrongCenter));
            var wrongRotation=group.copy();wrongRotation.putInt("siteRotation",90);refuse(()->SectorGroup53.supportHeader(wrongRotation));
            var wrongType=group.copy();ListTag ints=new ListTag();ints.add(IntTag.valueOf(1));wrongType.put("beforeOpenCorners",ints);refuse(()->SectorGroup53.supportHeader(wrongType));
            var orphan=group.copy();orphan.putString("parentFile","catancraft_sector_"+city+"_north_"+UUID.randomUUID()+"_v1.dat");refuse(()->SectorGroup53.supportHeader(orphan));
            h.assertTrue(SectorGroups53.inheritedSupportProof(context,group).isEmpty(),"Fresh group adopted unrelated historical proof");
            cities++;
        }
        h.assertTrue(cities==15,"New group site isolation incomplete");
    });}
    @GameTest(template="townhall_state_empty",batch="sector53_17",timeoutTicks=12000)
    public static void changed_map_and_unsupported_geometry_refuse(GameTestHelper h){checked(h,()->{
        Gson gson=new Gson();int cases=0;
        for(char c='a';c<='p';c++){
            var site=SectorSites53.site(String.valueOf(c));var tree=gson.toJsonTree(site.definition()).getAsJsonObject();
            tree.getAsJsonObject("cityCenter").addProperty("x",site.center().getX()+1);
            TerritoryDefinition changed=gson.fromJson(tree,TerritoryDefinition.class);refuse(()->SectorSites53.canonical(changed));
            refuse(()->SectorSites53.assembly(site,SectorCityAP3.levels(1,0,0,0),Set.of("NE")));
            refuse(()->SectorSites53.assembly(site,SectorCityAP3.levels(1,1,1,1),Set.of("UNKNOWN")));
            refuse(()->SectorSites53.transition(site,SectorContracts.Direction.NORTH,SectorCityAP3.levels(2,1,1,1),Set.of()));
            for(var side:SectorContracts.Direction.values())h.assertTrue(SectorPriceCatalog.cost(side,0).money()==1500&&SectorPriceCatalog.cost(side,1).money()==4000,"Physical side price changed");
            cases++;
        }
        h.assertTrue(cases==16,"Changed-map refusal roster incomplete");
        refuse(()->SectorSites53.site("q"));
    });}
    @GameTest(template="townhall_state_empty",batch="sector53_18",timeoutTicks=24000)
    public static void all_new_cities_inherit_only_the_exact_paid_group_parent(GameTestHelper h){checked(h,()->{
        int cases=0;
        for(char c='b';c<='p';c++){
            String city=String.valueOf(c);var context=context(h,city);var side=SectorContracts.Direction.NORTH;
            var parent=SectorGroup53.fixtureContract(context,side,0,BlockPos.ZERO);UUID transaction=UUID.randomUUID();
            String parentFile="catancraft_sector_"+city+"_north_"+UUID.randomUUID()+"_v1.dat";
            var previousId=identity(context,"north",parentFile,parent.cityGroup());
            CompoundTag proof=new CompoundTag();proof.putInt("schema",1);proof.putUUID("id",transaction);proof.putUUID("nation",context.owner());proof.putString("phase","COMMITTED");proof.put("identity",previousId);proof.put("spec",SectorTransaction.describe(parent.plan()));
            var levels=SectorCityAP3.levels(1,0,0,0);var next=SectorGroup53.plan(city,SectorContracts.Direction.EAST,levels,Set.of(),BlockPos.ZERO);
            CompoundTag child=parent.cityGroup();child.putString("parentFile",parentFile);child.putString("parentCityReceipt",transaction+":paid");child.putUUID("parentId",transaction);
            var north=child.getCompound("beforeStates").getCompound("NORTH");north.putInt("level",1);north.putString("receipt",transaction+":paid");north.putString("revision",parent.plan().targetRevision());
            ListTag afterOpen=new ListTag();afterOpen.add(StringTag.valueOf("NE"));child.put("afterOpenCorners",afterOpen);
            var currentId=identity(context,"east","catancraft_sector_"+city+"_east_"+UUID.randomUUID()+"_v1.dat",child);
            CompoundTag originalProof=proof.copy(),originalIdentity=currentId.copy();
            var inherited=SectorNaturalSupportPolicy.inherited(next,proof,"",context.owner(),currentId);
            h.assertTrue(!inherited.isEmpty(),"Native qualified site inherited no previous owned cover");
            h.assertTrue(proof.equals(originalProof)&&currentId.equals(originalIdentity),"Support proof validation changed retained metadata");
            var wrongOwner=proof.copy();wrongOwner.putUUID("nation",UUID.randomUUID());refuse(()->SectorNaturalSupportPolicy.inherited(next,wrongOwner,"",context.owner(),currentId));
            var wrongCity=proof.copy();wrongCity.getCompound("identity").putString("city",city.equals("b")?"d":"b");refuse(()->SectorNaturalSupportPolicy.inherited(next,wrongCity,"",context.owner(),currentId));
            var missingHistory=new CompoundTag();refuse(()->SectorNaturalSupportPolicy.inherited(next,missingHistory,"",context.owner(),currentId));
            var wrongReceipt=currentId.copy();wrongReceipt.getCompound("cityGroup").getCompound("beforeStates").getCompound("NORTH").putString("receipt",UUID.randomUUID()+":paid");refuse(()->SectorNaturalSupportPolicy.inherited(next,proof,"",context.owner(),wrongReceipt));
            var wrongSource=proof.copy();var changed=SectorGroup53.plan(city,SectorContracts.Direction.SOUTH,SectorCityAP3.levels(0,0,0,0),Set.of(),BlockPos.ZERO);wrongSource.put("spec",SectorTransaction.describe(changed));refuse(()->SectorNaturalSupportPolicy.inherited(next,wrongSource,"",context.owner(),currentId));
            cases++;
        }
        h.assertTrue(cases==15,"All new native site parent contracts required");
    });}
    private static CompoundTag identity(SectorService.Context context,String side,String filename,CompoundTag group) {
        CompoundTag id=new CompoundTag();id.putString("world",context.root().toString());id.putString("worldId",context.worldId());id.putString("dimension",context.server().overworld().dimension().location().toString());id.putString("city",context.city());id.putString("sector",side);id.putUUID("owner",context.owner());id.putString("approval",context.approval());id.putString("operationFile",filename);id.put("cityGroup",group);return id;
    }
    private static SectorService.Context context(GameTestHelper h,String city)throws IOException {
        var data=new CatanSavedData();UUID actor=UUID.randomUUID();var nation=data.createNation("Sector53 "+city,actor);
        data.createTerritory(city,"Sector53 "+city,ResourceType.WOOD).setOwnerNationId(nation.id());
        Path root=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/sector53-contract/"+UUID.randomUUID());
        return SectorService.fixtureContext(h.getLevel().getServer(),root,data,city,actor,UUID.randomUUID().toString(),()->100L);
    }
    private static void refuse(Checked work)throws Exception {try{work.run();}catch(IOException expected){return;}throw new AssertionError("Expected strict all-city wall refusal");}
}
