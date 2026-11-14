package com.quin.catancraft.world;

import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.*;

/** Reviewed map sites and exact authored source. No world writes, chunk loading or
 * implicit City A translation. Current native clearance remains mandatory. */
final class VehicleSites52 {
    static final String REVISION = "all_city_vehicle_factory52_v1";
    private record Source(MapAnchor base, Map<BlockPos,BlockState> prepared) {}
    // Short-lived resolved site snapshots avoid re-parsing the entire reviewed map
    // for each of the 18,400 block reads. Every new authority resolution is validated.
    private static final Map<Site,Source> SOURCES = new WeakHashMap<>();
    record Site(TerritoryDefinition definition, String plot, MapAnchor anchor, BlockPos counter) {
        String city() { return definition.id(); }
        boolean legacyPurchase() { return city().equals("a") && plot.equals("plot_1"); }
    }
    private VehicleSites52() {}

    static List<Site> sites(TerritoryDefinition definition) throws IOException {
        if (definition == null) throw new IOException("Vehicle factory city is unavailable.");
        List<Site> result = new ArrayList<>();
        for (String plot : IndustrialPlotService.INDUSTRIAL_PLOTS) {
            if (definition.anchor(plot) == null || definition.reservedPlotIds().contains(plot)) continue;
            MapAnchor anchor = IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY, plot, definition);
            if (anchor == null) throw new IOException("Vehicle factory placement is unavailable.");
            result.add(new Site(definition, plot, anchor, ShopFacility40.local(anchor,18,3,9)));
        }
        return List.copyOf(result);
    }
    static boolean recognizes(String dimension, BlockPos counter) {
        try {
            List<TerritoryDefinition> definitions = new ArrayList<>(ShopSites51.reviewedDefinitions());
            definitions.addAll(MapDefinitionManager.territories());
            for (var definition : definitions) {
                if (!definition.dimension().equals(dimension)) continue;
                try { for (Site site : sites(definition)) if (site.counter().equals(counter)) return true; }
                catch (IOException | RuntimeException invalid) { /* reviewed coordinates remain recognized */ }
            }
        } catch (IOException invalid) { return false; }
        return false;
    }
    static Site resolve(String dimension, BlockPos counter) throws IOException {
        return resolve(MapDefinitionManager.territories(), dimension, counter);
    }
    static Site resolve(Collection<TerritoryDefinition> definitions, String dimension, BlockPos counter) throws IOException {
        List<Site> matches = new ArrayList<>();
        for (var definition : definitions) if (definition.dimension().equals(dimension))
            for (Site site : sites(definition)) if (site.counter().equals(counter)) matches.add(site);
        if (matches.size()!=1) throw new IOException("This vehicle counter has no unique city/factory placement.");
        Site site=matches.get(0); canonical(site); return site;
    }
    static Site byAnchor(MapAnchor anchor) throws IOException {
        List<Site> matches=new ArrayList<>();
        for (var definition : MapDefinitionManager.territories()) for (var site : sites(definition))
            if (sameAnchor(site.anchor(),anchor)) matches.add(site);
        if(matches.size()!=1)throw new IOException("Vehicle factory anchor has no unique reviewed site.");
        Site site=matches.get(0);canonical(site);return site;
    }
    static void canonical(Site site) throws IOException {
        ShopSites51.canonical(site.definition());
        MapAnchor expected=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,site.plot(),site.definition());
        if(!IndustrialPlotService.INDUSTRIAL_PLOTS.contains(site.plot())||site.definition().reservedPlotIds().contains(site.plot())
                ||!sameAnchor(expected,site.anchor())||!ShopFacility40.local(expected,18,3,9).equals(site.counter()))
            throw new IOException("Vehicle factory site differs from its reviewed construction placement.");
    }
    static boolean sameAnchor(MapAnchor a,MapAnchor b) {
        return a!=null&&b!=null&&a.id().equals(b.id())&&a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()&&a.rotation()==b.rotation();
    }
    /** Normal prepared-city source used by qualification fixtures. */
    static BlockState source(Site site,BlockPos p)throws IOException { return source(site,p,true); }
    static BlockState source(Site site,BlockPos p,boolean prepared)throws IOException {
        Source source=sourceFor(site);
        MapAnchor a=site.anchor();int sign=a.rotation()==0?1:-1;
        if(a.rotation()!=0&&a.rotation()!=180)throw new IOException("Unsupported vehicle source rotation.");
        int x=sign*(p.getX()-a.x()),y=p.getY()-a.y(),z=sign*(p.getZ()-a.z());
        if(x<NativeVehicleBay46.MIN_X||x>NativeVehicleBay46.MAX_X||y<NativeVehicleBay46.MIN_Y||y>NativeVehicleBay46.MAX_Y
                ||z<NativeVehicleBay46.MIN_Z||z>NativeVehicleBay46.MAX_Z)throw new IOException("Vehicle space leaves the reviewed factory envelope: "+p);
        if(x>=-29&&x<=1&&z>=-15&&z<=15) {
            var factory=BasicConstructionAssets.load("vehicle_factory");
            return factory.at(x+factory.ax(),y+factory.ay(),z+factory.az()).rotate(ProducerExteriorR2.rotation(a));
        }
        if(prepared) {
            BlockState after=source.prepared().get(p);
            if(after!=null)return after;
        }
        // Resolve this city's actual base placement; plot 3/4 do not share plot 1's offset.
        return BasicConstructionAssets.baseline(source.base(),p);
    }
    private static synchronized Source sourceFor(Site site)throws IOException {
        Source source=SOURCES.get(site);if(source!=null)return source;
        canonical(site);
        MapAnchor base=SchematicAssetRegistry.territoryAnchor("starter_settlement",site.definition());
        if(base==null)throw new IOException("Vehicle factory has no reviewed city source.");
        source=new Source(base,VehiclePublicSource52.after(site.definition()));SOURCES.put(site,source);return source;
    }
    static AABB envelope(Site site) {
        MapAnchor a=site.anchor();
        BlockPos one=NativeVehicleBay46.sourceCell(a,NativeVehicleBay46.MIN_X,NativeVehicleBay46.MIN_Y,NativeVehicleBay46.MIN_Z);
        BlockPos two=NativeVehicleBay46.sourceCell(a,NativeVehicleBay46.MAX_X,NativeVehicleBay46.MAX_Y,NativeVehicleBay46.MAX_Z);
        return new AABB(Math.min(one.getX(),two.getX()),one.getY(),Math.min(one.getZ(),two.getZ()),
                Math.max(one.getX(),two.getX())+1,two.getY()+1,Math.max(one.getZ(),two.getZ())+1);
    }
    static List<AABB> serviceAreas(Site site) {
        MapAnchor a=site.anchor();
        AABB road=a.rotation()==0?new AABB(a.x()+2,a.y(),a.z()-10,a.x()+8,a.y()+5,a.z()+10)
                :new AABB(a.x()-7,a.y(),a.z()-9,a.x()-1,a.y()+5,a.z()+11);
        // Never take the bounding union: the gap does not grant parking permission.
        return List.of(VehicleFactory50.serviceArea(a),road);
    }
    static String origin(net.minecraft.nbt.CompoundTag identity) {
        String city=identity.getString("city"),plot=identity.getString("plot");
        return city.matches("[a-p]")&&IndustrialPlotService.INDUSTRIAL_PLOTS.contains(plot)
                ? "City "+city.toUpperCase(Locale.ROOT)+" Vehicle Factory ("+plot.replace("plot_","plot ")+")"
                : "the original Vehicle Factory";
    }
}
