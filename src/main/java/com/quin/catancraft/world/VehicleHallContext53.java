package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.util.*;

/** Paid Hall foundations replace only surveyed vehicle-envelope context below
 * the support floor. No factory cell, bay identity, clearance or price changes. */
final class VehicleHallContext53 {
    private static final Map<VehicleSites52.Site,Map<BlockPos,BlockState>> MASKS=new HashMap<>();
    private static List<BlockState[]> targets;
    private VehicleHallContext53(){}
    static final class Context {
        private final VehicleSites52.Site site;
        private final SectorHallContext53.Context paid;
        private final Map<BlockPos,BlockState> reads;
        private Context(VehicleSites52.Site site,SectorHallContext53.Context paid,Map<BlockPos,BlockState> reads){this.site=site;this.paid=paid;this.reads=reads;}
        Map<BlockPos,BlockState> reads(){return reads;}
        void verifyUnchanged()throws IOException{VehicleSites52.canonical(site);paid.verifyUnchanged();}
    }
    static Context capture(ServerLevel level,VehicleSites52.Site site,UUID owner)throws IOException {
        VehicleSites52.canonical(site);
        if(!site.definition().dimension().equals(level.dimension().location().toString()))throw new IOException("Vehicle Hall context dimension changed");
        var server=level.getServer();var root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        var paid=SectorHallContext53.capture(server,root,CatanSavedData.get(server),site.city(),owner);
        var result=new Context(site,paid,paid.tier()<4?Map.of():qualifiedPositions(site));result.verifyUnchanged();return result;
    }
    /** Full pinned qualification, independent of current tier/payment. Calling
     * this method alone grants no permission to replace a live expectation. */
    static synchronized Map<BlockPos,BlockState> qualifiedPositions(VehicleSites52.Site site)throws IOException {
        VehicleSites52.canonical(site);Map<BlockPos,BlockState> cached=MASKS.get(site);if(cached!=null)return cached;
        MapAnchor hall=SchematicAssetRegistry.territoryAnchor("th3",site.definition()),factory=site.anchor();
        if(hall==null)throw new IOException("Missing canonical vehicle Hall context");
        int hallSign=hall.rotation()==0?1:-1;Map<BlockPos,BlockState> mask=new HashMap<>();int overlap=0;
        var early=List.of(BasicConstructionAssets.load("th1"),BasicConstructionAssets.load("th2"),BasicConstructionAssets.load("th3"));
        var targets=targets();
        for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
            BlockPos p=NativeVehicleBay46.sourceCell(factory,x,y,z);
            int hx=hallSign*(p.getX()-hall.x())+37,hy=p.getY()-hall.y()+2,hz=hallSign*(p.getZ()-hall.z())+1;
            if(hx<0||hx>=75||hy<0||hy>=47||hz<0||hz>=65)continue;
            overlap++;if(x>=-29&&x<=1&&z>=-15&&z<=15)throw new IOException("Hall context overlaps owned vehicle factory");
            BlockState source=VehicleSites52.source(site,p,false);
            if(!source.equals(VehicleSites52.source(site,p,true)))throw new IOException("Public preparation changes vehicle Hall intersection");
            for(var volume:early)if(!source.equals(volume.at(hx,hy,hz).rotate(ProducerExteriorR2.rotation(hall))))throw new IOException("Early Hall differs from vehicle context");
            BlockState replacement=null;boolean changed=false;int index=(hy*65+hz)*75+hx;
            for(var target:targets){
                BlockState state=target[index].rotate(ProducerExteriorR2.rotation(hall));
                if(replacement!=null&&!replacement.equals(state))throw new IOException("Prestige revisions disagree on vehicle Hall context");
                replacement=state;if(!state.equals(source))changed=true;
            }
            if(changed){
                if(y!=-2||p.getY()!=79||!source.equals(Blocks.STONE.defaultBlockState())||!replacement.equals(Blocks.DIRT.defaultBlockState()))throw new IOException("Unqualified vehicle Hall source replacement");
                mask.put(p.immutable(),replacement);
            }
        }
        int expectedOverlap=switch(site.plot()){case "plot_1"->1200;case "plot_3"->930;case "plot_4"->960;default->throw new IOException("Unknown vehicle Hall plot");};
        if(overlap!=expectedOverlap||mask.size()!=expectedOverlap/10)throw new IOException("Vehicle Hall context mask changed");
        Map<BlockPos,BlockState> result=Map.copyOf(mask);MASKS.put(site,result);return result;
    }
    /** Cache only immutable local target states. World geometry and ownership
     * are resolved and qualified separately for each of the 48 reviewed sites. */
    private static List<BlockState[]> targets()throws IOException {
        if(targets!=null)return targets;
        List<BlockState[]> result=new ArrayList<>();MapAnchor local=new MapAnchor("town_hall",0,0,0,0);
        for(int tier=4;tier<=5;tier++)for(int revision=1;revision<=2;revision++){
            var plan=TownHallRevisions.plan(tier,revision,local);BlockState[] states=new BlockState[TownHallRevisions.CELLS];
            for(int i=0;i<states.length;i++)states[i]=plan.get(i).state();result.add(states);
        }
        targets=List.copyOf(result);return targets;
    }
}
