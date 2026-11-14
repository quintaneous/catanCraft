package com.quin.catancraft.world;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Actual A-P coordinates with their own reviewed starter/factory source overlays.
 * Each unique batch stages one alternative factory layout in an exact disposable
 * flat world. This is native geometry/driving qualification, not an authenticated
 * purchase or a whole authored-world progression run. Never launches outside the
 * explicit namespace and never modifies pose after the initial native spawn.
 */
@GameTestHolder("catancraft_vehicle52_native") @PrefixGameTestTemplate(false)
public final class NativeVehicleSites52Tests {
    private static final String NS="catancraft_vehicle52_native";
    private static final UUID FIXTURE_NATION=UUID.fromString("52000000-0000-0000-0000-000000000052");
    private NativeVehicleSites52Tests(){}
    @GameTest(template="townhall_state_empty",batch="sites52_000",timeoutTicks=1600)
    public static void a_plot_1_pickup(GameTestHelper h)throws Exception{start(h,0,"a","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_001",timeoutTicks=1600)
    public static void a_plot_1_lav(GameTestHelper h)throws Exception{start(h,1,"a","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_002",timeoutTicks=1600)
    public static void a_plot_3_pickup(GameTestHelper h)throws Exception{start(h,2,"a","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_003",timeoutTicks=1600)
    public static void a_plot_3_lav(GameTestHelper h)throws Exception{start(h,3,"a","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_004",timeoutTicks=1600)
    public static void a_plot_4_pickup(GameTestHelper h)throws Exception{start(h,4,"a","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_005",timeoutTicks=1600)
    public static void a_plot_4_lav(GameTestHelper h)throws Exception{start(h,5,"a","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_006",timeoutTicks=1600)
    public static void b_plot_1_pickup(GameTestHelper h)throws Exception{start(h,6,"b","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_007",timeoutTicks=1600)
    public static void b_plot_1_lav(GameTestHelper h)throws Exception{start(h,7,"b","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_008",timeoutTicks=1600)
    public static void b_plot_3_pickup(GameTestHelper h)throws Exception{start(h,8,"b","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_009",timeoutTicks=1600)
    public static void b_plot_3_lav(GameTestHelper h)throws Exception{start(h,9,"b","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_010",timeoutTicks=1600)
    public static void b_plot_4_pickup(GameTestHelper h)throws Exception{start(h,10,"b","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_011",timeoutTicks=1600)
    public static void b_plot_4_lav(GameTestHelper h)throws Exception{start(h,11,"b","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_012",timeoutTicks=1600)
    public static void c_plot_1_pickup(GameTestHelper h)throws Exception{start(h,12,"c","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_013",timeoutTicks=1600)
    public static void c_plot_1_lav(GameTestHelper h)throws Exception{start(h,13,"c","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_014",timeoutTicks=1600)
    public static void c_plot_3_pickup(GameTestHelper h)throws Exception{start(h,14,"c","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_015",timeoutTicks=1600)
    public static void c_plot_3_lav(GameTestHelper h)throws Exception{start(h,15,"c","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_016",timeoutTicks=1600)
    public static void c_plot_4_pickup(GameTestHelper h)throws Exception{start(h,16,"c","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_017",timeoutTicks=1600)
    public static void c_plot_4_lav(GameTestHelper h)throws Exception{start(h,17,"c","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_018",timeoutTicks=1600)
    public static void d_plot_1_pickup(GameTestHelper h)throws Exception{start(h,18,"d","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_019",timeoutTicks=1600)
    public static void d_plot_1_lav(GameTestHelper h)throws Exception{start(h,19,"d","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_020",timeoutTicks=1600)
    public static void d_plot_3_pickup(GameTestHelper h)throws Exception{start(h,20,"d","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_021",timeoutTicks=1600)
    public static void d_plot_3_lav(GameTestHelper h)throws Exception{start(h,21,"d","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_022",timeoutTicks=1600)
    public static void d_plot_4_pickup(GameTestHelper h)throws Exception{start(h,22,"d","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_023",timeoutTicks=1600)
    public static void d_plot_4_lav(GameTestHelper h)throws Exception{start(h,23,"d","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_024",timeoutTicks=1600)
    public static void e_plot_1_pickup(GameTestHelper h)throws Exception{start(h,24,"e","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_025",timeoutTicks=1600)
    public static void e_plot_1_lav(GameTestHelper h)throws Exception{start(h,25,"e","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_026",timeoutTicks=1600)
    public static void e_plot_3_pickup(GameTestHelper h)throws Exception{start(h,26,"e","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_027",timeoutTicks=1600)
    public static void e_plot_3_lav(GameTestHelper h)throws Exception{start(h,27,"e","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_028",timeoutTicks=1600)
    public static void e_plot_4_pickup(GameTestHelper h)throws Exception{start(h,28,"e","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_029",timeoutTicks=1600)
    public static void e_plot_4_lav(GameTestHelper h)throws Exception{start(h,29,"e","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_030",timeoutTicks=1600)
    public static void f_plot_1_pickup(GameTestHelper h)throws Exception{start(h,30,"f","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_031",timeoutTicks=1600)
    public static void f_plot_1_lav(GameTestHelper h)throws Exception{start(h,31,"f","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_032",timeoutTicks=1600)
    public static void f_plot_3_pickup(GameTestHelper h)throws Exception{start(h,32,"f","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_033",timeoutTicks=1600)
    public static void f_plot_3_lav(GameTestHelper h)throws Exception{start(h,33,"f","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_034",timeoutTicks=1600)
    public static void f_plot_4_pickup(GameTestHelper h)throws Exception{start(h,34,"f","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_035",timeoutTicks=1600)
    public static void f_plot_4_lav(GameTestHelper h)throws Exception{start(h,35,"f","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_036",timeoutTicks=1600)
    public static void g_plot_1_pickup(GameTestHelper h)throws Exception{start(h,36,"g","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_037",timeoutTicks=1600)
    public static void g_plot_1_lav(GameTestHelper h)throws Exception{start(h,37,"g","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_038",timeoutTicks=1600)
    public static void g_plot_3_pickup(GameTestHelper h)throws Exception{start(h,38,"g","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_039",timeoutTicks=1600)
    public static void g_plot_3_lav(GameTestHelper h)throws Exception{start(h,39,"g","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_040",timeoutTicks=1600)
    public static void g_plot_4_pickup(GameTestHelper h)throws Exception{start(h,40,"g","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_041",timeoutTicks=1600)
    public static void g_plot_4_lav(GameTestHelper h)throws Exception{start(h,41,"g","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_042",timeoutTicks=1600)
    public static void h_plot_1_pickup(GameTestHelper h)throws Exception{start(h,42,"h","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_043",timeoutTicks=1600)
    public static void h_plot_1_lav(GameTestHelper h)throws Exception{start(h,43,"h","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_044",timeoutTicks=1600)
    public static void h_plot_3_pickup(GameTestHelper h)throws Exception{start(h,44,"h","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_045",timeoutTicks=1600)
    public static void h_plot_3_lav(GameTestHelper h)throws Exception{start(h,45,"h","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_046",timeoutTicks=1600)
    public static void h_plot_4_pickup(GameTestHelper h)throws Exception{start(h,46,"h","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_047",timeoutTicks=1600)
    public static void h_plot_4_lav(GameTestHelper h)throws Exception{start(h,47,"h","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_048",timeoutTicks=1600)
    public static void i_plot_1_pickup(GameTestHelper h)throws Exception{start(h,48,"i","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_049",timeoutTicks=1600)
    public static void i_plot_1_lav(GameTestHelper h)throws Exception{start(h,49,"i","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_050",timeoutTicks=1600)
    public static void i_plot_3_pickup(GameTestHelper h)throws Exception{start(h,50,"i","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_051",timeoutTicks=1600)
    public static void i_plot_3_lav(GameTestHelper h)throws Exception{start(h,51,"i","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_052",timeoutTicks=1600)
    public static void i_plot_4_pickup(GameTestHelper h)throws Exception{start(h,52,"i","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_053",timeoutTicks=1600)
    public static void i_plot_4_lav(GameTestHelper h)throws Exception{start(h,53,"i","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_054",timeoutTicks=1600)
    public static void j_plot_1_pickup(GameTestHelper h)throws Exception{start(h,54,"j","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_055",timeoutTicks=1600)
    public static void j_plot_1_lav(GameTestHelper h)throws Exception{start(h,55,"j","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_056",timeoutTicks=1600)
    public static void j_plot_3_pickup(GameTestHelper h)throws Exception{start(h,56,"j","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_057",timeoutTicks=1600)
    public static void j_plot_3_lav(GameTestHelper h)throws Exception{start(h,57,"j","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_058",timeoutTicks=1600)
    public static void j_plot_4_pickup(GameTestHelper h)throws Exception{start(h,58,"j","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_059",timeoutTicks=1600)
    public static void j_plot_4_lav(GameTestHelper h)throws Exception{start(h,59,"j","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_060",timeoutTicks=1600)
    public static void k_plot_1_pickup(GameTestHelper h)throws Exception{start(h,60,"k","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_061",timeoutTicks=1600)
    public static void k_plot_1_lav(GameTestHelper h)throws Exception{start(h,61,"k","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_062",timeoutTicks=1600)
    public static void k_plot_3_pickup(GameTestHelper h)throws Exception{start(h,62,"k","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_063",timeoutTicks=1600)
    public static void k_plot_3_lav(GameTestHelper h)throws Exception{start(h,63,"k","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_064",timeoutTicks=1600)
    public static void k_plot_4_pickup(GameTestHelper h)throws Exception{start(h,64,"k","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_065",timeoutTicks=1600)
    public static void k_plot_4_lav(GameTestHelper h)throws Exception{start(h,65,"k","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_066",timeoutTicks=1600)
    public static void l_plot_1_pickup(GameTestHelper h)throws Exception{start(h,66,"l","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_067",timeoutTicks=1600)
    public static void l_plot_1_lav(GameTestHelper h)throws Exception{start(h,67,"l","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_068",timeoutTicks=1600)
    public static void l_plot_3_pickup(GameTestHelper h)throws Exception{start(h,68,"l","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_069",timeoutTicks=1600)
    public static void l_plot_3_lav(GameTestHelper h)throws Exception{start(h,69,"l","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_070",timeoutTicks=1600)
    public static void l_plot_4_pickup(GameTestHelper h)throws Exception{start(h,70,"l","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_071",timeoutTicks=1600)
    public static void l_plot_4_lav(GameTestHelper h)throws Exception{start(h,71,"l","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_072",timeoutTicks=1600)
    public static void m_plot_1_pickup(GameTestHelper h)throws Exception{start(h,72,"m","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_073",timeoutTicks=1600)
    public static void m_plot_1_lav(GameTestHelper h)throws Exception{start(h,73,"m","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_074",timeoutTicks=1600)
    public static void m_plot_3_pickup(GameTestHelper h)throws Exception{start(h,74,"m","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_075",timeoutTicks=1600)
    public static void m_plot_3_lav(GameTestHelper h)throws Exception{start(h,75,"m","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_076",timeoutTicks=1600)
    public static void m_plot_4_pickup(GameTestHelper h)throws Exception{start(h,76,"m","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_077",timeoutTicks=1600)
    public static void m_plot_4_lav(GameTestHelper h)throws Exception{start(h,77,"m","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_078",timeoutTicks=1600)
    public static void n_plot_1_pickup(GameTestHelper h)throws Exception{start(h,78,"n","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_079",timeoutTicks=1600)
    public static void n_plot_1_lav(GameTestHelper h)throws Exception{start(h,79,"n","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_080",timeoutTicks=1600)
    public static void n_plot_3_pickup(GameTestHelper h)throws Exception{start(h,80,"n","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_081",timeoutTicks=1600)
    public static void n_plot_3_lav(GameTestHelper h)throws Exception{start(h,81,"n","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_082",timeoutTicks=1600)
    public static void n_plot_4_pickup(GameTestHelper h)throws Exception{start(h,82,"n","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_083",timeoutTicks=1600)
    public static void n_plot_4_lav(GameTestHelper h)throws Exception{start(h,83,"n","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_084",timeoutTicks=1600)
    public static void o_plot_1_pickup(GameTestHelper h)throws Exception{start(h,84,"o","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_085",timeoutTicks=1600)
    public static void o_plot_1_lav(GameTestHelper h)throws Exception{start(h,85,"o","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_086",timeoutTicks=1600)
    public static void o_plot_3_pickup(GameTestHelper h)throws Exception{start(h,86,"o","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_087",timeoutTicks=1600)
    public static void o_plot_3_lav(GameTestHelper h)throws Exception{start(h,87,"o","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_088",timeoutTicks=1600)
    public static void o_plot_4_pickup(GameTestHelper h)throws Exception{start(h,88,"o","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_089",timeoutTicks=1600)
    public static void o_plot_4_lav(GameTestHelper h)throws Exception{start(h,89,"o","plot_4",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_090",timeoutTicks=1600)
    public static void p_plot_1_pickup(GameTestHelper h)throws Exception{start(h,90,"p","plot_1",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_091",timeoutTicks=1600)
    public static void p_plot_1_lav(GameTestHelper h)throws Exception{start(h,91,"p","plot_1",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_092",timeoutTicks=1600)
    public static void p_plot_3_pickup(GameTestHelper h)throws Exception{start(h,92,"p","plot_3",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_093",timeoutTicks=1600)
    public static void p_plot_3_lav(GameTestHelper h)throws Exception{start(h,93,"p","plot_3",NativeVehicleBay46.LAV);}

    @GameTest(template="townhall_state_empty",batch="sites52_094",timeoutTicks=1600)
    public static void p_plot_4_pickup(GameTestHelper h)throws Exception{start(h,94,"p","plot_4",NativeVehicleBay46.PICKUP);}

    @GameTest(template="townhall_state_empty",batch="sites52_095",timeoutTicks=1600)
    public static void p_plot_4_lav(GameTestHelper h)throws Exception{start(h,95,"p","plot_4",NativeVehicleBay46.LAV);}

    private static void start(GameTestHelper h,int id,String city,String plot,NativeVehicleBay46.Spec spec)throws Exception {
        ServerLevel level=h.getLevel();Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        String source=System.getProperty("catancraft.vehicle52Source",""),runtime=System.getProperty("catancraft.sbwRuntimeManifestSHA256","");
        check(level.getServer() instanceof GameTestServer&&level.getServer().isSameThread()
                &&level.getServer().getPlayerList().getPlayers().isEmpty()&&level.getSeed()==0
                &&level.dimension().location().toString().equals("minecraft:overworld")
                &&root.getFileName().toString().equals("world")
                &&root.getParent().getFileName().toString().equals("controlled-"+NS+"-run")
                &&Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                &&source.matches("[0-9a-f]{40}")&&runtime.matches("[0-9a-f]{64}"),"Exact no-player source-bound native fixture required");
        var mods=net.minecraftforge.fml.ModList.get();
        check("15".equals(System.getProperty("catancraft.native42CombinedRuntimeArtifacts",""))
                &&mods.isLoaded("tacz")&&mods.isLoaded("superbwarfare")
                &&mods.getModContainerById("simplebedrockmodel").orElseThrow().getModInfo().getVersion().toString().equals("2.6.2-forge-mc1.20.1"),"Exact combined native provider/runtime selection required");
        for(Path ancestor=root;ancestor!=null;ancestor=ancestor.getParent())check(!Files.isSymbolicLink(ancestor),"Symlink fixture refused");
        var definition=MapDefinitionManager.territory(city);
        List<VehicleSites52.Site> matches=VehicleSites52.sites(definition).stream().filter(s->s.plot().equals(plot)).toList();
        check(matches.size()==1,"Actual reviewed factory site is missing or ambiguous");
        var site=matches.get(0);VehicleSites52.canonical(site);check(site.city().equals(city),"Resolved city differs");
        AABB envelope=VehicleSites52.envelope(site);
        check(envelope.getXsize()>0&&envelope.getYsize()>0&&envelope.getZsize()>0
                &&envelope.getXsize()*envelope.getYsize()*envelope.getZsize()<=65536,"Unbounded site envelope");
        SectorChunkLease lease=SectorChunkLease.acquire(level,envelope);
        Run run=new Run(h,id,site,spec,root,envelope,lease,source,runtime);
        run.await(0);
    }

    private static final class Run {
        final GameTestHelper h;final int id;final VehicleSites52.Site site;final NativeVehicleBay46.Spec spec;
        final Path root;final AABB envelope;final SectorChunkLease lease;final String source,runtime;
        final NativeVehicleRoad50.Controller controller=new NativeVehicleRoad50.Controller();
        final JsonArray rows=new JsonArray(),residency=new JsonArray(),seatRows=new JsonArray();
        final Map<BlockPos,CompoundTag> blockEntities=new LinkedHashMap<>();
        final long residencyStarted=System.nanoTime();
        Entity vehicle;ServerPlayer driver;VehicleBay45.Bay bay;Vec3 origin;float initialYaw;int initialEnergy;boolean legacyBayEqual;
        List<AABB> previous;double previousYaw,previousPitch,previousRoll,maxDistance,maxYaw;
        int stagedCells,unchangedCells,clearanceCells,residencyWaitTicks;long residencyElapsedNanos;
        String sourceBefore="",sourceAfter="",stop="";boolean driverExited;
        Run(GameTestHelper h,int id,VehicleSites52.Site site,NativeVehicleBay46.Spec spec,Path root,
                AABB envelope,SectorChunkLease lease,String source,String runtime){
            this.h=h;this.id=id;this.site=site;this.spec=spec;this.root=root;this.envelope=envelope;
            this.lease=lease;this.source=source;this.runtime=runtime;
        }
        void await(int ticks){try{
            lease.keepAlive();boolean ready=lease.ready();residencyWaitTicks=ticks;residencyElapsedNanos=System.nanoTime()-residencyStarted;
            JsonObject row=new JsonObject();row.addProperty("ticks",ticks);row.addProperty("ready",ready);
            row.addProperty("elapsedMillis",residencyElapsedNanos/1000000d);JsonArray blocks=new JsonArray(),entities=new JsonArray();
            for(var chunk:lease.positions()){
                if(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null)blocks.add(chunk.x+","+chunk.z);
                if(!h.getLevel().areEntitiesLoaded(chunk.toLong()))entities.add(chunk.x+","+chunk.z);
            }
            row.add("pendingBlockChunks",blocks);row.add("pendingEntityChunks",entities);residency.add(row);
            if(ready||ticks%100==0)CatanCraft.LOGGER.info("VEHICLE52_RESIDENCY case={} city={} plot={} ticks={} ready={} blocks={} entities={}",id,site.city(),site.plot(),ticks,ready,blocks,entities);
            if(!ready){check(ticks<900,"Native site block/entity residency timed out");
                java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25));
                h.runAfterDelay(5,()->await(ticks+5));return;}
            check(h.getLevel().getEntities((Entity)null,envelope,e->e.isAlive()).isEmpty(),"Previous fixture left an entity in this site's envelope");
            for(BlockPos p:cells(envelope)){
                BlockPos at=p.immutable();h.getLevel().setBlock(at,VehicleSites52.source(site,at),2);stagedCells++;
            }
            sourceBefore=sourceFingerprint(true);
            var construction=new VehicleFactory50.Construction(SchematicAssetRegistry.asset("vehicle_factory").placementToken(),1);
            CompoundTag identity=VehicleFactory50.identity(site,construction,FIXTURE_NATION,root,"vehicle52_native_fixture",h.getLevel().getServer().getWorldData().getLevelName(),h.getLevel().getSeed());
            var binding=new VehicleFactory50.Binding(identity,"Native qualification fixture",1,site.counter(),site.anchor(),FIXTURE_NATION,root);
            bay=VehicleFactory50.buildBay(h.getLevel(),binding,spec);
            check(bay.facility().city().equals(site.city())&&bay.facility().plot().equals(site.plot())&&bay.facility().nation().equals(FIXTURE_NATION),"Production bay selected a foreign facility");
            var spawn=bay.worldSpawn();Vec3 initialPosition=new Vec3(spawn.x(),spawn.y(),spawn.z());
            check(initialPosition.distanceTo(NativeVehicleBay46.spawn(site.anchor(),16))<.0000001&&bay.worldHeading()==site.anchor().rotation(),"Production spawn differs from qualified road-aligned pose");
            if(site.city().equals("a")&&site.plot().equals("plot_1")){
                check(legacyBay(h.getLevel(),site,spec).equals(bay),"Legacy City A plot1 bay or receipt masks changed");legacyBayEqual=true;
            }
            vehicle=NativeVehicleBay46.create(h.getLevel(),spec);
            NativeVehicleBay46.pose(vehicle,initialPosition,bay.worldHeading());
            vehicle.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);
            vehicle.getPersistentData().putString(NationProcurement.NATION_TAG,FIXTURE_NATION.toString());
            vehicle.getClass().getMethod("setEnergy",int.class).invoke(vehicle,1250000);
            initialEnergy=NativeVehicleDriving48.energy(vehicle);check(initialEnergy==1250000,"Native quarter tank seed refused");
            check(((Number)vehicle.getClass().getMethod("getMaxPassengers").invoke(vehicle)).intValue()==spec.seats(),"Native seat inventory changed");
            check(h.getLevel().addFreshEntity(vehicle),"Native entity insertion refused");
            List<AABB> initialBounds=NativeVehicleDriving48.bounds(vehicle);
            check(initialBounds.size()==spec.boxes(),"Initial native part inventory changed");
            for(AABB b:initialBounds)check(contains(envelope,b),"Initial native body leaves site envelope");
            check(VehicleFactory50.insideOneServiceArea(VehicleSites52.serviceAreas(site),initialBounds),"Road-aligned spawn is outside qualified service areas");
            clearanceCells+=clear(h.getLevel(),vehicle,null,site,envelope,initialBounds);
            for(int seat=0;seat<spec.seats();seat++){
                ServerPlayer passenger=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Site52Seat"));
                try{
                    passenger.getInventory().clearContent();passenger.setPos(vehicle.position());
                    check(!passenger.isCreative()&&passenger.startRiding(vehicle,true),"Native seat boarding failed");
                    vehicle.getClass().getMethod("changeSeat",Entity.class,int.class).invoke(vehicle,passenger,seat);
                    check(((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,passenger)).intValue()==seat,"Native seat selection failed");
                    seatRows.add(exit(h.getLevel(),vehicle,passenger,site,envelope,seat));
                }finally{if(passenger.getVehicle()!=null)vehicle.ejectPassengers();passenger.discard();}
            }
            driver=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Site52Driver"));
            driver.getInventory().clearContent();driver.setPos(vehicle.position());
            check(!driver.isCreative()&&driver.startRiding(vehicle,true)&&vehicle.getFirstPassenger()==driver,"Native driver boarding failed");
            origin=vehicle.position();initialYaw=vehicle.getYRot();step(0);
        }catch(Exception|AssertionError failure){fail(failure);}}

        void step(int tick){try{
            lease.keepAlive();check(lease.ready(),"Lost native site residency");
            check(vehicle.isAlive()&&vehicle.getFirstPassenger()==driver,"Lost native vehicle or driver");
            Vec3 position=vehicle.position(),velocity=vehicle.getDeltaMovement();
            double distance=position.subtract(origin).horizontalDistance();
            double heading=Math.abs(Math.IEEEremainder(vehicle.getYRot()-initialYaw,360));
            double roll=((Number)vehicle.getClass().getMethod("getRoll",float.class).invoke(vehicle,1f)).doubleValue();
            for(double value:new double[]{position.x,position.y,position.z,velocity.x,velocity.y,velocity.z,vehicle.getYRot(),vehicle.getXRot(),roll,distance,heading})check(Double.isFinite(value),"Nonfinite native trajectory");
            maxDistance=Math.max(maxDistance,distance);maxYaw=Math.max(maxYaw,heading);
            JsonObject row=new JsonObject();row.addProperty("tick",tick);row.addProperty("entityTick",vehicle.tickCount);
            row.addProperty("gameTime",h.getLevel().getGameTime());row.addProperty("x",position.x);row.addProperty("y",position.y);row.addProperty("z",position.z);
            row.addProperty("yaw",vehicle.getYRot());row.addProperty("pitch",vehicle.getXRot());row.addProperty("roll",roll);
            row.addProperty("vx",velocity.x);row.addProperty("vy",velocity.y);row.addProperty("vz",velocity.z);
            row.addProperty("speed",velocity.horizontalDistance());row.addProperty("energy",NativeVehicleDriving48.energy(vehicle));
            row.addProperty("onGround",vehicle.onGround());row.addProperty("horizontalCollision",vehicle.horizontalCollision);row.addProperty("verticalCollision",vehicle.verticalCollision);
            List<AABB> nativeBounds=NativeVehicleDriving48.bounds(vehicle);check(nativeBounds.size()==spec.boxes(),"Native part inventory changed");
            JsonArray parts=new JsonArray();for(AABB box:nativeBounds)parts.add(boxJson(box));row.add("nativeBounds",parts);rows.add(row);
            if(tick<=20)check(distance<.1,"Neutral native controls drifted horizontally before propulsion");
            for(AABB box:nativeBounds){
                check(contains(envelope,box),"Native route left actual site source envelope");
                check(box.getCenter().distanceTo(position)+box.getSize()*2<15,"Native geometry exceeds angular sweep bound");
            }
            check(!vehicle.horizontalCollision,"Native horizontal collision during departure");
            double angular=previous==null?0:Math.toRadians(Math.abs(Math.IEEEremainder(vehicle.getYRot()-previousYaw,360))+Math.abs(vehicle.getXRot()-previousPitch)+Math.abs(roll-previousRoll));
            List<AABB> swept=previous==null?nativeBounds:NativeVehicleBay46.swept(previous,nativeBounds,15*angular*angular/8+.00001);
            clearanceCells+=clear(h.getLevel(),vehicle,driver,site,envelope,swept);
            previous=nativeBounds;previousYaw=vehicle.getYRot();previousPitch=vehicle.getXRot();previousRoll=roll;
            var decision=controller.next(tick,position,origin,vehicle.getYRot(),initialYaw,velocity.horizontalDistance());row.addProperty("phase",decision.phase());row.addProperty("keys",decision.keys());
            if(decision.complete()){stop="QUALIFIED_DEPARTURE";finish();return;}
            check(tick<600,"Native departure exceeded tick budget in "+decision.phase());
            NativeVehicleDriving48.input(driver,decision.keys());h.runAfterDelay(1,()->step(tick+1));
        }catch(Exception|AssertionError failure){fail(failure);}}

        String sourceFingerprint(boolean before)throws Exception {
            StringBuilder inventory=new StringBuilder();int checked=0;
            for(BlockPos mutable:cells(envelope)){
                BlockPos p=mutable.immutable();checkSource(h.getLevel(),site,envelope,p);
                var state=h.getLevel().getBlockState(p);var entity=h.getLevel().getBlockEntity(p);
                check(state.hasBlockEntity()?entity!=null&&entity.getType().isValid(state):entity==null,"Unexpected native block entity at "+p);
                CompoundTag tag=entity==null?null:entity.saveWithFullMetadata();
                if(before){if(tag!=null)blockEntities.put(p,tag.copy());}
                else check(Objects.equals(blockEntities.get(p),tag),"Native driving changed block entity contents at "+p);
                inventory.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append('=').append(state).append('\n');
                if(tag!=null)inventory.append(VehicleService50.digest(tag)).append('\n');checked++;
            }
            if(!before)unchangedCells=checked;
            return TownHallRevisions.sha(inventory.toString().getBytes(StandardCharsets.UTF_8));
        }
        void finish()throws Exception {
            NativeVehicleDriving48.input(driver,0);
            check(stop.equals("QUALIFIED_DEPARTURE")&&maxDistance>=10,"Native propulsion did not complete ten-block departure");
            check(maxYaw<1,"Straight road departure changed heading");
            check(NativeVehicleDriving48.energy(vehicle)<initialEnergy,"Native powered motion consumed no energy");
            JsonObject finalExit=exit(h.getLevel(),vehicle,driver,site,envelope,0);finalExit.addProperty("afterDeparture",true);seatRows.add(finalExit);driverExited=true;
            sourceAfter=sourceFingerprint(false);check(sourceBefore.equals(sourceAfter)&&stagedCells==unchangedCells,"Source fingerprint changed during native case");
            write("PASS","");
            CatanCraft.LOGGER.info("VEHICLE52_NATIVE_PASS case={} city={} plot={} vehicle={} rotation={} sourceCells={} seatExits={} samples={} distance={} heading={} sourceSHA256={} postSpawnPoseWrites=0",id,site.city(),site.plot(),spec.id(),site.anchor().rotation(),unchangedCells,seatRows.size(),rows.size(),maxDistance,maxYaw,sourceAfter);
            close();h.succeed();
        }
        void write(String result,String failure)throws Exception {
            JsonObject proof=new JsonObject();proof.addProperty("schema",1);proof.addProperty("source",source);proof.addProperty("namespace",NS);
            proof.addProperty("combinedRuntimeArtifacts",15);proof.addProperty("sbwRuntimeManifestSHA256",runtime);proof.addProperty("taczRuntimeManifestSHA256",System.getProperty("catancraft.taczRuntimeManifestSHA256",""));
            proof.addProperty("mapSHA256",ShopSites51.MAP_SHA256);proof.addProperty("nativeDefinitionSHA256",spec.sha());
            proof.addProperty("productionBayConstructed",bay!=null);proof.addProperty("legacyCityAPlot1BayExactlyEqual",legacyBayEqual);
            if(bay!=null){proof.addProperty("bayId",bay.id());proof.addProperty("bayRevision",bay.facility().revision());proof.addProperty("baySupportCells",bay.support().size());proof.addProperty("bayClearanceCells",bay.clearance().size());}
            proof.addProperty("case",id);proof.addProperty("city",site.city());proof.addProperty("plot",site.plot());proof.addProperty("vehicle",spec.id());proof.addProperty("rotation",site.anchor().rotation());
            proof.addProperty("anchorX",site.anchor().x());proof.addProperty("anchorY",site.anchor().y());proof.addProperty("anchorZ",site.anchor().z());
            proof.addProperty("counterX",site.counter().getX());proof.addProperty("counterY",site.counter().getY());proof.addProperty("counterZ",site.counter().getZ());
            proof.add("sourceEnvelope",boxJson(envelope));proof.addProperty("result",result);proof.addProperty("failure",failure);proof.addProperty("stop",stop);
            proof.addProperty("controlPath","VehicleMovementMessage.handler");proof.addProperty("seatExitPath","PlayerStopRidingMessage.handler");proof.addProperty("naturalServerTicks",true);
            proof.addProperty("postSpawnPoseWrites",0);proof.addProperty("actualCanonicalCoordinates",true);proof.addProperty("authoredWorldCampaign",false);proof.addProperty("authenticatedClient",false);
            proof.addProperty("stagedCells",stagedCells);proof.addProperty("sourceCellsUnchanged",unchangedCells);proof.addProperty("blockEntityCount",blockEntities.size());
            proof.addProperty("sourceBeforeSHA256",sourceBefore);proof.addProperty("sourceAfterSHA256",sourceAfter);proof.addProperty("initialEnergy",initialEnergy);proof.addProperty("initialYaw",initialYaw);
            proof.addProperty("maxDistance",maxDistance);proof.addProperty("maxYaw",maxYaw);proof.addProperty("clearanceCells",clearanceCells);proof.addProperty("driverExitedAfterDeparture",driverExited);
            proof.addProperty("entityUUID",vehicle==null?"":vehicle.getUUID().toString());proof.addProperty("driverUUID",driver==null?"":driver.getUUID().toString());
            proof.addProperty("residencyWaitTicks",residencyWaitTicks);proof.addProperty("residencyElapsedMillis",residencyElapsedNanos/1000000d);
            proof.add("seats",seatRows);proof.add("trajectory",rows);proof.add("residency",residency);
            Files.createDirectories(root.resolve("data"));
            Files.writeString(root.resolve("data/vehicle52-native-"+String.format(Locale.ROOT,"%03d",id)+"-"+site.city()+"-"+site.plot()+"-"+spec.id()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof),StandardOpenOption.CREATE_NEW);
        }
        void fail(Throwable failure){try{write("FAIL",failure.toString());}catch(Exception evidence){failure.addSuppressed(evidence);}finally{close();}throw new RuntimeException(failure);}
        void close(){if(vehicle!=null){vehicle.ejectPassengers();vehicle.discard();}if(driver!=null)driver.discard();lease.close();}
    }

    /** Independent frozen original A plot1 construction: no new source provider or revision. */
    private static VehicleBay45.Bay legacyBay(ServerLevel level,VehicleSites52.Site site,NativeVehicleBay46.Spec spec)throws Exception {
        MapAnchor a=site.anchor();check(a.x()==-795&&a.y()==81&&a.z()==-774&&a.rotation()==0,"Legacy anchor changed");
        Set<VehicleBay45.Cell> support=new HashSet<>(),clearance=new HashSet<>();
        for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
            BlockPos floor=NativeVehicleBay46.sourceCell(a,x,-1,z);var floorSource=NativeVehicleBay46.source(a,floor);
            if(floorSource.isCollisionShapeFullBlock(level,floor)&&floorSource.getFluidState().isEmpty())support.add(new VehicleBay45.Cell(x,-1,z));
            for(int y=0;y<=NativeVehicleBay46.MAX_Y;y++){
                BlockPos at=NativeVehicleBay46.sourceCell(a,x,y,z);var state=NativeVehicleBay46.source(a,at);
                if(state.getCollisionShape(level,at).isEmpty()&&state.getFluidState().isEmpty())clearance.add(new VehicleBay45.Cell(x,y,z));
            }
        }
        var facility=new VehicleBay45.Facility("a","plot_1",FIXTURE_NATION,"minecraft:overworld","city_a_vehicle_factory50_v1",
                "793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649",new VehicleBay45.Cell(-795,81,-774),0,new VehicleBay45.Cell(-11,1,-6),1);
        return new VehicleBay45.Bay("plot1_road_south50",facility,"superbwarfare:"+spec.id(),spec.sha(),new VehicleBay45.Position(4.5,.1,.5),0,support,clearance,clearance,clearance);
    }

    private static Iterable<BlockPos> cells(AABB b){
        return BlockPos.betweenClosed((int)Math.floor(b.minX),(int)Math.floor(b.minY),(int)Math.floor(b.minZ),(int)Math.ceil(b.maxX)-1,(int)Math.ceil(b.maxY)-1,(int)Math.ceil(b.maxZ)-1);
    }
    private static boolean contains(AABB outer,AABB inner){return inner.minX>=outer.minX&&inner.minY>=outer.minY&&inner.minZ>=outer.minZ&&inner.maxX<=outer.maxX&&inner.maxY<=outer.maxY&&inner.maxZ<=outer.maxZ;}
    private static JsonArray boxJson(AABB box){JsonArray row=new JsonArray();for(double d:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})row.add(d);return row;}
    private static void checkSource(ServerLevel level,VehicleSites52.Site site,AABB envelope,BlockPos p)throws Exception {
        check(p.getX()>=envelope.minX&&p.getY()>=envelope.minY&&p.getZ()>=envelope.minZ&&p.getX()<envelope.maxX&&p.getY()<envelope.maxY&&p.getZ()<envelope.maxZ,"Probe leaves actual site envelope at "+p);
        VehicleFactory50.loaded(level,p);
        check(level.getBlockState(p).equals(VehicleSites52.source(site,p)),"Actual site source drift at "+p);
    }
    private static int clear(ServerLevel level,Entity vehicle,ServerPlayer driver,VehicleSites52.Site site,AABB envelope,List<AABB> boxes)throws Exception {
        Set<BlockPos> checked=new HashSet<>();
        for(AABB box:boxes){
            check(contains(envelope,box),"Native swept body leaves the actual site envelope");
            for(BlockPos mutable:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
                BlockPos p=mutable.immutable();checkSource(level,site,envelope,p);checked.add(p);var state=level.getBlockState(p);
                check(state.getFluidState().isEmpty()&&!state.getCollisionShape(level,p).toAabbs().stream().anyMatch(b->b.move(p).intersects(box)),"Native route blocked at "+p+" bounds="+box);
            }
            for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
                BlockPos floor=new BlockPos(x,site.anchor().y()-1,z);checkSource(level,site,envelope,floor);checked.add(floor);
                check(level.getBlockState(floor).isCollisionShapeFullBlock(level,floor)&&level.getFluidState(floor).isEmpty(),"Native route unsupported at "+floor);
            }
            check(level.getEntities(vehicle,box,e->e.isAlive()&&e!=driver).isEmpty(),"Native route occupied by another entity");
        }
        return checked.size();
    }
    private static JsonObject exit(ServerLevel level,Entity vehicle,ServerPlayer player,VehicleSites52.Site site,AABB envelope,int seat)throws Exception {
        Vec3 before=vehicle.position();float yaw=vehicle.getYRot();
        Vec3 expected=NativeVehicleExit47.managed(vehicle)?NativeVehicleExit47.resolve(vehicle,player).exit():vehicle.getDismountLocationForPassenger(player);
        packet(player);
        check(player.getVehicle()==null&&player.position().distanceTo(expected)<=.0001&&vehicle.position().distanceTo(before)<=.000001&&vehicle.getYRot()==yaw,"Actual site dismount differed or moved vehicle");
        Vec3 feet=player.position();check(feet.y>=site.anchor().y()-.001&&feet.y<=site.anchor().y()+1,"Native exit landing height unsupported");
        AABB body=new AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);
        NativeVehicleBay46.clearOwnVehicle(vehicle,body);int checked=clear(level,vehicle,player,site,envelope,List.of(body));
        JsonObject row=new JsonObject();row.addProperty("seat",seat);row.addProperty("actualPacket",true);row.addProperty("ownGeometryClear",true);row.addProperty("checkedCells",checked);
        row.addProperty("x",feet.x);row.addProperty("y",feet.y);row.addProperty("z",feet.z);return row;
    }
    private static void packet(ServerPlayer player)throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(player.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);constructor.setAccessible(true);
        var context=constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,0);check(context.getSender()==player,"Foreign site dismount sender");
        Class<?> type=Class.forName("com.atsuishio.superbwarfare.network.message.send.PlayerStopRidingMessage");
        Object message=type.getConstructor(boolean.class).newInstance(false);
        type.getMethod("handler",Supplier.class).invoke(message,(Supplier<NetworkEvent.Context>)()->context);
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
