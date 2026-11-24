package com.quin.catancraft.delivery;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.quin.catancraft.map.CatanMapDefinition;
import com.quin.catancraft.map.MapZoneDefinition;
import com.quin.catancraft.map.MonumentDefinition;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Run inside a bootstrapped Forge data process. Supply repository root and the packaged map.json. */
public final class DeliveryActionsTest {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        checkCommands();
        checkForgeWiring();
        Path bundled = Path.of(args[0], "src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json");
        CatanMapDefinition oldMap = GSON.fromJson(Files.readString(bundled), CatanMapDefinition.class);
        CatanMapDefinition map = GSON.fromJson(Files.readString(Path.of(args[1])), CatanMapDefinition.class);
        check(resolve(oldMap).isEmpty(), "unmodified R7 map offered the full-map job");
        DeliveryRoute route = resolve(map).orElseThrow();
        var from = route.pickup();
        var to = route.dropoff();
        check(from.counter().equals(new BlockPos(74, 82, -30)) && to.counter().equals(new BlockPos(28, 82, 75)),
                "counter location changed");
        check(from.contains(from.dimension(), 74.5, 81, -28.5) && to.contains(to.dimension(), 29.5, 81, 75.5),
                "authored walk-up positions are in reach");
        check(!to.contains("minecraft:the_nether", 29.5, 81, 75.5) && !to.contains(to.dimension(), 90, 81, 75.5),
                "cross-dimension or remote reach");

        MapZoneDefinition district = district(map);
        var depot = monument(map, "depot");
        var refinery = monument(map, "refinery");
        check(DeliveryRoute.resolve("other", district, depot, refinery).isEmpty(), "foreign map");
        check(DeliveryRoute.resolve(map.mapId(), null, depot, refinery).isEmpty(), "missing public district");
        JsonObject moved = GSON.toJsonTree(refinery).getAsJsonObject();
        moved.addProperty("z", refinery.z() + 1);
        check(DeliveryRoute.resolve(map.mapId(), district, depot, GSON.fromJson(moved, MonumentDefinition.class))
                .isEmpty(), "unsurveyed refinery position");

        checkCityOrigins(map, route);
        checkBearerAndPersistence(route);
        System.out.println("PASS: Full Map Review 02 physical pickup and bearer hand-in, real ItemStack NBT, "
                + "keepInventory cargo extraction, saved ledger and stack reload, stale/duplicate hand-in, "
                + "read-only command tree, 16 city origins and four founding slots.");
    }

    private static void checkCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        DeliveryCommands.register(dispatcher);
        var board = dispatcher.getRoot().getChild("delivery");
        check(board != null && board.getChildren().size() == 2, "delivery board command branches");
        for (String command : List.of("delivery", "delivery status", "delivery receipt")) {
            var parse = dispatcher.parse(command, (CommandSourceStack) null);
            check(!parse.getReader().canRead() && parse.getContext().getCommand() != null, command);
        }
        for (String verb : List.of("pickup", "turnin", "abandon")) {
            check(board.getChild(verb) == null, "remote mutation path " + verb);
        }
    }

    private static void checkForgeWiring() throws Exception {
        var mod = new ClassNode();
        try (var stream = DeliveryActionsTest.class.getResourceAsStream("/com/quin/catancraft/CatanCraft.class")) {
            check(stream != null, "mod entry point is packaged");
            new ClassReader(stream).accept(mod, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var method = mod.methods.stream().filter(m -> m.name.equals("onRegisterCommands")
                && m.desc.equals("(Lnet/minecraftforge/event/RegisterCommandsEvent;)V")).findFirst().orElseThrow();
        check(method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")),
                "Forge command event subscription");
        boolean installsBoard = false;
        for (var op : method.instructions) {
            if (op instanceof MethodInsnNode call && call.owner.equals("com/quin/catancraft/delivery/DeliveryCommands")
                    && call.name.equals("register")) installsBoard = true;
        }
        check(installsBoard, "Forge registers the tested delivery board");
        check(PhysicalDelivery.class.getMethod("rightClick", PlayerInteractEvent.RightClickBlock.class)
                .isAnnotationPresent(SubscribeEvent.class), "physical counter event is subscribed");
        check(PhysicalDelivery.class.getMethod("dropKeptCargo", LivingDropsEvent.class)
                .isAnnotationPresent(SubscribeEvent.class), "death drops event is subscribed");
    }

    private static void checkCityOrigins(CatanMapDefinition map, DeliveryRoute route) {
        DeliveryLedger ledger = new DeliveryLedger();
        var from = route.pickup();
        var to = route.dropoff();
        Set<String> cities = new TreeSet<>();
        Set<String> starters = new TreeSet<>();
        for (TerritoryDefinition territory : map.territories()) {
            check(cities.add(territory.id()), "duplicate territory");
            if (territory.startSlot() > 0) starters.add(territory.id());
            UUID player = UUID.nameUUIDFromBytes(territory.id().getBytes(StandardCharsets.UTF_8));
            var center = territory.cityCenter();
            reject(() -> DeliveryActions.pickup(ledger, player, route, territory.dimension(),
                    new BlockPos(center.x(), center.y(), center.z()), 60), "pickup remotely from " + territory.id());
            var stack = DeliveryActions.pickup(ledger, player, route, from.dimension(), from.counter(), 60);
            var id = DeliveryActions.crateId(stack);
            var receipt = DeliveryActions.turnIn(ledger, player, route, to.dimension(), to.counter(), stack);
            check(stack.isEmpty() && id.equals(receipt.id()) && ledger.latestReceipt(player).equals(receipt),
                    "local handoff for " + territory.id());
        }
        check(cities.equals(Set.of("a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p")),
                "coverage of all city origins");
        check(starters.equals(Set.of("a", "d", "m", "p")), "four founding choices");
    }

    private static void checkBearerAndPersistence(DeliveryRoute route) throws Exception {
        var from = route.pickup();
        var to = route.dropoff();
        UUID courier = UUID.randomUUID(), interceptor = UUID.randomUUID();
        DeliveryLedger first = new DeliveryLedger();
        reject(() -> DeliveryActions.pickup(first, courier, route, "minecraft:the_nether", from.counter(), 100),
                "cross-dimension dispatch");
        ItemStack cargo = DeliveryActions.pickup(first, courier, route, from.dimension(), from.counter(), 100);
        var id = DeliveryActions.crateId(cargo);
        check(id != null && cargo.is(Items.BARREL) && cargo.getCount() == 1, "physical sealed crate");
        reject(() -> DeliveryActions.pickup(first, courier, route, from.dimension(), from.counter(), 101),
                "duplicate click on dispatch");
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        var inventory = new ArrayList<>(List.of(tool, cargo, ItemStack.EMPTY));
        List<ItemStack> drops = DeliveryActions.takeKeptCargo(inventory);
        check(drops.size() == 1 && drops.get(0) == cargo && inventory.get(0) == tool && inventory.get(1).isEmpty(),
                "keepInventory removes exactly the physical cargo");
        check(DeliveryActions.takeKeptCargo(inventory).isEmpty(), "repeated removal duplicated the drop");

        var items = new ListTag();
        var ordinary = tool.save(new CompoundTag());
        ordinary.putByte("Slot", (byte) 9);
        var sealed = cargo.save(new CompoundTag());
        sealed.putByte("Slot", (byte) 13);
        items.add(ordinary);
        items.add(sealed);
        var shulker = new ItemStack(Items.BLUE_SHULKER_BOX);
        shulker.getOrCreateTagElement("BlockEntityTag").put("Items", items);
        var bagItems = new ListTag();
        bagItems.add(shulker.save(new CompoundTag()));
        var bundle = new ItemStack(Items.BUNDLE);
        bundle.getOrCreateTag().put("Items", bagItems);
        var nestedSlots = new ArrayList<>(List.of(bundle));
        var nestedDrops = DeliveryActions.takeKeptCargo(nestedSlots);
        check(nestedDrops.size() == 1 && id.equals(DeliveryActions.crateId(nestedDrops.get(0))),
                "sealed freight inside a carried container remains interceptable");
        var savedShulker = ItemStack.of(bundle.getTag().getList("Items", Tag.TAG_COMPOUND).getCompound(0));
        var afterItems = savedShulker.getTagElement("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND);
        check(nestedSlots.get(0) == bundle && afterItems.size() == 1 && ordinary.equals(afterItems.getCompound(0))
                && DeliveryActions.takeKeptCargo(nestedSlots).isEmpty(), "personal cargo and its slot must survive");

        DeliveryLedger restored = DeliveryLedger.load(roundtrip(first.save(new CompoundTag())));
        ItemStack held = ItemStack.of(roundtrip(drops.get(0).save(new CompoundTag())));
        check(id.equals(DeliveryActions.crateId(held)), "seal survives save and reopen");
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, from.dimension(), from.counter(), held),
                "turn-in at dispatch");
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, to.dimension(), to.counter().above(), held),
                "turn-in at adjacent block");
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, "minecraft:the_end", to.counter(), held),
                "cross-dimension turn-in");
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, to.dimension(), to.counter(), ItemStack.EMPTY),
                "empty hand");
        var imitation = new ItemStack(Items.BARREL);
        imitation.setHoverName(held.getHoverName());
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, to.dimension(), to.counter(), imitation),
                "renamed ordinary barrel");
        var fake = new ItemStack(Items.STICK);
        fake.setTag(held.getTag().copy());
        check(DeliveryActions.crateId(fake) == null, "valid seal on wrong item");
        var wrong = new DeliveryRoute.Endpoint("other-route", to.name(), to.dimension(), to.x(), to.y(), to.z());
        reject(() -> DeliveryActions.turnIn(restored, interceptor, new DeliveryRoute(from, wrong), to.dimension(),
                to.counter(), held), "misrouted shipment");
        check(held.getCount() == 1 && !restored.shipment(id).delivered(), "rejected hand-ins changed freight");

        var copy = held.copy();
        var next = DeliveryActions.pickup(restored, courier, route, from.dimension(), from.counter(), 141);
        var nextId = DeliveryActions.crateId(next);
        check(!id.equals(nextId), "next dispatch has a fresh seal");
        var signed = DeliveryActions.turnIn(restored, interceptor, route, to.dimension(), to.counter(), held);
        check(held.isEmpty() && signed.id().equals(id) && signed.player().equals(courier)
                && signed.recipient().equals(interceptor) && restored.latestReceipt(courier) == null,
                "interception credits actual bearer and consumes one crate");
        reject(() -> DeliveryActions.turnIn(restored, interceptor, route, to.dimension(), to.counter(), held),
                "repeated consumed-hand click");
        DeliveryLedger settled = DeliveryLedger.load(roundtrip(restored.save(new CompoundTag())));
        reject(() -> DeliveryActions.turnIn(settled, courier, route, to.dimension(), to.counter(), copy),
                "copied stale seal after reload");
        check(signed.equals(settled.latestReceipt(interceptor)) && settled.latestReceipt(courier) == null
                && copy.getCount() == 1 && !settled.shipment(nextId).delivered(), "stale attempt mutated ledger");
        var courierReceipt = DeliveryActions.turnIn(settled, courier, route, to.dimension(), to.counter(), next);
        check(courierReceipt.id().equals(nextId) && settled.latestReceipt(interceptor).equals(signed),
                "new cargo completes independently");
    }

    private static CompoundTag roundtrip(CompoundTag tag) throws Exception {
        var out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()));
    }

    private static MapZoneDefinition district(CatanMapDefinition map) {
        return map.publicZones().stream().filter(s -> s.id().equals("central_objective_district")).findFirst().orElseThrow();
    }
    private static MonumentDefinition monument(CatanMapDefinition map, String id) {
        return map.monuments().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
    private static java.util.Optional<DeliveryRoute> resolve(CatanMapDefinition map) {
        return DeliveryRoute.resolve(map.mapId(), district(map), monument(map, "depot"), monument(map, "refinery"));
    }
    private static void reject(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(label + " should have been refused");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
