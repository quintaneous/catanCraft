package com.quin.catancraft.delivery;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import com.quin.catancraft.map.CatanMapDefinition;
import com.quin.catancraft.map.MapZoneDefinition;
import com.quin.catancraft.map.MonumentDefinition;
import com.quin.catancraft.map.TerritoryDefinition;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Executes production delivery transitions on the bundled map definitions, without opening a world. */
public final class DeliveryActionsTest {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        DeliveryCommands.register(dispatcher);
        var board = dispatcher.getRoot().getChild("delivery");
        check(board != null && board.getCommand() != null, "public delivery board command");
        for (String name : new String[]{"status", "pickup", "turnin"}) {
            check(board.getChild(name) != null && board.getChild(name).getCommand() != null,
                    "missing usable command " + name);
        }
        check(board.getChild("turnin").getChild("waybill").getCommand() != null, "receipt-scoped turn-in");
        check(board.getChild("abandon").getCommand() == null
                && board.getChild("abandon").getChild("waybill").getCommand() != null, "bill-scoped abandon");

        UUID clickId = UUID.randomUUID();
        for (String command : new String[]{"delivery", "delivery status", "delivery receipt", "delivery pickup", "delivery turnin",
                "delivery turnin " + clickId, "delivery abandon " + clickId}) {
            // A null source has no permission methods. Every branch here must have the default requirement.
            var parse = dispatcher.parse(command, (CommandSourceStack) null);
            check(!parse.getReader().canRead() && parse.getContext().getCommand() != null,
                    "ordinary player command path " + command);
        }
        for (String invalid : new String[]{"delivery turnin bad-id", "delivery abandon bad-id", "delivery abandon"}) {
            var parse = dispatcher.parse(invalid, (CommandSourceStack) null);
            check(parse.getReader().canRead() || parse.getContext().getCommand() == null,
                    "malformed click target was accepted " + invalid);
        }
        verifyForgeWiring();

        Path root = Path.of(args[0]);
        Path mapFile = root.resolve("src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json");
        CatanMapDefinition map = GSON.fromJson(Files.readString(mapFile), CatanMapDefinition.class);
        MapZoneDefinition district = map.publicZones().stream()
                .filter(site -> site.id().equals("central_objective_district")).findFirst().orElseThrow();
        MonumentDefinition depot = monument(map, "depot");
        MonumentDefinition refinery = monument(map, "refinery");
        DeliveryRoute route = DeliveryRoute.resolve(map.mapId(), district, depot, refinery).orElseThrow();

        check(DeliveryRoute.resolve("unrelated-map", district, depot, refinery).isEmpty(), "foreign map");
        check(DeliveryRoute.resolve(map.mapId(), null, depot, refinery).isEmpty(), "missing public district");
        check(DeliveryRoute.resolve(map.mapId(), district, null, refinery).isEmpty(), "missing depot");
        JsonObject moved = GSON.toJsonTree(refinery).getAsJsonObject();
        moved.addProperty("z", refinery.z() + 1);
        check(DeliveryRoute.resolve(map.mapId(), district, depot, GSON.fromJson(moved, MonumentDefinition.class))
                .isEmpty(), "unsurveyed refinery position");

        DeliveryLedger ledger = new DeliveryLedger();
        var from = route.pickup();
        var to = route.dropoff();
        Set<String> territories = new TreeSet<>();
        Set<String> starters = new TreeSet<>();
        for (TerritoryDefinition territory : map.territories()) {
            check(territories.add(territory.id()), "duplicate territory");
            if (territory.startSlot() > 0) starters.add(territory.id());
            UUID player = UUID.nameUUIDFromBytes(territory.id().getBytes(StandardCharsets.UTF_8));
            var center = territory.cityCenter();
            reject(() -> DeliveryActions.pickup(ledger, player, route, territory.dimension(),
                    center.x() + 0.5, center.y(), center.z() + 0.5), "remote pickup from city " + territory.id());
            check(ledger.active(player) == null, "remote attempt allocated cargo");

            var load = DeliveryActions.pickup(ledger, player, route, from.dimension(),
                    from.x() + 0.5, from.y(), from.z() + 0.5);
            check(DeliveryActions.turnIn(ledger, player, load.id(), route, to.dimension(),
                    to.x() + 0.5, to.y(), to.z() + 0.5), "common route turn-in for " + territory.id());
            check(ledger.receipt(player, load.id()) != null, "missing player receipt");
        }
        check(territories.equals(Set.of("a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p")),
                "expected all 16 bundled city origins");
        check(starters.equals(Set.of("a", "d", "m", "p")), "founding choices changed");

        UUID courier = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        double fx = from.x() + 0.5, fz = from.z() + 0.5;
        double tx = to.x() + 0.5, tz = to.z() + 0.5;
        reject(() -> DeliveryActions.pickup(ledger, courier, route, "minecraft:the_nether",
                fx, from.y(), fz), "pickup in another dimension");
        reject(() -> DeliveryActions.pickup(ledger, courier, route, from.dimension(),
                fx + 2.01, from.y(), fz), "outside pickup disk");
        reject(() -> DeliveryActions.pickup(ledger, courier, route, from.dimension(),
                fx, from.y() + 1, fz), "above pickup");
        check(ledger.active(courier) == null, "blocked pickups created cargo");

        var bill = DeliveryActions.pickup(ledger, courier, route, from.dimension(), fx, from.y(), fz);
        var otherBill = DeliveryActions.pickup(ledger, other, route, from.dimension(), fx, from.y(), fz);
        check(DeliveryActions.pickup(ledger, courier, route, from.dimension(), fx, from.y(), fz)
                .id().equals(bill.id()), "repeated pickup allocated a second bill");
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), route, from.dimension(),
                fx, from.y(), fz), "turn-in at origin");
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), route, to.dimension(),
                tx, to.y() - 1, tz), "turn-in below destination");
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), route, to.dimension(),
                tx, to.y(), tz + 2.01), "outside handoff disk");
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), route, "minecraft:the_end",
                tx, to.y(), tz), "cross-dimension turn-in");
        reject(() -> DeliveryActions.turnIn(ledger, other, bill.id(), route, to.dimension(),
                tx, to.y(), tz), "turn-in using another courier's bill");
        var newPickup = new DeliveryRoute.Endpoint("replacement-origin", from.name(), from.dimension(),
                from.x(), from.y(), from.z());
        var newDropoff = new DeliveryRoute.Endpoint("replacement-destination", to.name(), to.dimension(),
                to.x(), to.y(), to.z());
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), new DeliveryRoute(newPickup, to),
                to.dimension(), tx, to.y(), tz), "pickup identity changed while carrying cargo");
        reject(() -> DeliveryActions.turnIn(ledger, courier, bill.id(), new DeliveryRoute(from, newDropoff),
                to.dimension(), tx, to.y(), tz), "destination identity changed while carrying cargo");
        check(ledger.active(courier).id().equals(bill.id()) && ledger.active(other).id().equals(otherBill.id()),
                "rejected actions changed an active load");

        check(DeliveryActions.turnIn(ledger, courier, bill.id(), route, to.dimension(), tx, to.y(), tz),
                "first handoff");
        var next = DeliveryActions.pickup(ledger, courier, route, from.dimension(), fx, from.y(), fz);
        check(!DeliveryActions.turnIn(ledger, courier, bill.id(), route, to.dimension(), tx, to.y(), tz),
                "a stale confirmation repeated its first handoff");
        check(ledger.active(courier).id().equals(next.id()), "stale click closed new cargo");
        check(ledger.active(other).id().equals(otherBill.id()), "neighboring courier's cargo changed");
        System.out.println("PASS: Brigadier action registration, route pins, 16 city-origin authorization checks, four founding origins, "
                + "dimensional and spatial handoff checks, distinct couriers, duplicate and stale action checks.");
    }

    /** Verify the built mod subscribes to Forge registration and installs this exact command tree. */
    private static void verifyForgeWiring() throws Exception {
        ClassNode mod = new ClassNode();
        try (InputStream stream = DeliveryActionsTest.class
                .getResourceAsStream("/com/quin/catancraft/CatanCraft.class")) {
            check(stream != null, "missing built mod entrypoint");
            new ClassReader(stream).accept(mod, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var callback = mod.methods.stream().filter(method -> method.name.equals("onRegisterCommands")
                && method.desc.equals("(Lnet/minecraftforge/event/RegisterCommandsEvent;)V")).findFirst().orElseThrow();
        check(callback.visibleAnnotations != null && callback.visibleAnnotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")),
                "missing Forge event subscription");
        for (var insn : callback.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("com/quin/catancraft/delivery/DeliveryCommands")
                    && call.name.equals("register")
                    && call.desc.equals("(Lcom/mojang/brigadier/CommandDispatcher;)V")) return;
        }
        throw new AssertionError("Forge registration did not install /delivery");
    }

    private static MonumentDefinition monument(CatanMapDefinition map, String id) {
        return map.monuments().stream().filter(site -> site.id().equals(id)).findFirst().orElseThrow();
    }

    private static void reject(Runnable action, String check) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(check + " should have been refused");
    }

    private static void check(boolean value, String check) {
        if (!value) throw new AssertionError(check);
    }
}
