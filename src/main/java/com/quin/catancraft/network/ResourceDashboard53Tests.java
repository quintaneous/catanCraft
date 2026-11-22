package com.quin.catancraft.network;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.ui.NationResourceRows;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("catancraft_resource_dashboard53")
@PrefixGameTestTemplate(false)
public final class ResourceDashboard53Tests {
    @GameTest(template = "townhall_state_empty")
    public static void all_resources_show_stock_and_signed_current_cycle_forecast(GameTestHelper helper) {
        verifyResourceRows();
        helper.succeed();
    }

    static void verifyResourceRows() {
        CatanSavedData data = new CatanSavedData();
        NationData nation = data.createNation("resource rows", UUID.randomUUID());
        TerritoryData territory = data.createTerritory("resource_rows_wood", "Forest", ResourceType.WOOD);
        territory.setOwnerNationId(nation.id());
        nation.addResource(ResourceType.WOOD, 27);
        nation.addResource(ResourceType.AGRICULTURE, 2);
        nation.addResource(ResourceType.MECHANICAL_PARTS, 4_000_000_000L);

        List<String> rows = NationResourceRows.section(data, nation);
        require(rows.stream().filter(row -> row.startsWith("Q|"))
                .map(row -> row.substring(2, row.indexOf('|', 2))).distinct().count() == ResourceType.values().length,
                "Each resource must have its own stock and movement row");
        require(rows.contains("Q|Wood|27|+10") && rows.contains("Q|Agriculture|2|-1")
                        && rows.contains("Q|Oil|0|0") && rows.contains("Q|Mechanical Parts|4000000000|0"),
                "Missing signed growth, consumption, unchanged stock, or long stock count");
        require(rows.indexOf("H|RAW RESOURCES") < rows.indexOf("Q|Wood|27|+10")
                        && rows.indexOf("Q|Wood|27|+10") < rows.indexOf("H|PROCESSED RESOURCES")
                        && rows.indexOf("H|PROCESSED RESOURCES") < rows.indexOf("Q|Mechanical Parts|4000000000|0"),
                "Resource category placement changed");

        territory.setProducerLevel(2);
        rows = NationResourceRows.section(data, nation);
        require(rows.contains("Q|Wood|27|+18") && rows.contains("Q|Agriculture|2|-2"),
                "Changing production must update the forecast before the next settlement");
        nation.addResource(ResourceType.AGRICULTURE, -2);
        rows = NationResourceRows.section(data, nation);
        require(rows.contains("Q|Wood|27|0") && rows.contains("Q|Agriculture|0|0"),
                "A shortage must update stock and both affected forecasts");
    }

    @GameTest(template = "townhall_state_empty")
    public static void resource_refresh_wire_and_nation_membership_are_authoritative(GameTestHelper helper) {
        verifyRefresh();
        helper.succeed();
    }

    static void verifyRefresh() {
        CatanSavedData data = new CatanSavedData();
        NationData first = data.createNation("first", UUID.randomUUID());
        NationData second = data.createNation("second", UUID.randomUUID());
        UUID member = UUID.randomUUID();
        first.addMember(member);
        first.addResource(ResourceType.STEEL, 47);
        second.addResource(ResourceType.STEEL, 91);

        UUID view = UUID.randomUUID();
        ResourceRefreshRequest request = new ResourceRefreshRequest(first.id(), view);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ResourceRefreshRequest.encode(request, buffer);
            require(ResourceRefreshRequest.decode(buffer).equals(request) && !buffer.isReadable(), "Request round trip");
            buffer.clear();
            List<String> rows = request.rowsFor(data, member);
            require(rows.contains("Q|Steel|47|0") && rows.contains("I|" + first.id())
                    && rows.contains("U|" + view), "Current member view");
            NationDashboardPacket.encode(new NationDashboardPacket(rows), buffer);
            NationDashboardPacket decoded = NationDashboardPacket.decode(buffer);
            buffer.clear();
            NationDashboardPacket.encode(decoded, buffer);
            require(buffer.readVarInt() == rows.size(), "Stock row count changed on the wire");
            for (String row : rows) require(row.equals(buffer.readUtf(512)), "Stock row changed on the wire");
            require(!buffer.isReadable(), "Unexpected wire data");
        } finally {
            buffer.release();
        }

        List<String> unavailable = List.of("V|resource_unavailable", "I|" + first.id(), "U|" + view);
        require(request.rowsFor(data, second.leaderId()).equals(unavailable), "Foreign nation data exposed");
        first.removeMember(member);
        require(request.rowsFor(data, member).equals(unavailable), "Former member saw nation data");
        second.addMember(member);
        require(request.rowsFor(data, member).equals(unavailable), "Stale dashboard crossed nations");
        require(new ResourceRefreshRequest(second.id(), UUID.randomUUID()).rowsFor(data, member).contains("Q|Steel|91|0"),
                "Member could not open the newly joined nation's own view");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
