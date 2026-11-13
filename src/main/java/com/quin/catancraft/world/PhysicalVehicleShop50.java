package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.network.NationNetwork;
import com.quin.catancraft.ui.DefenseMenuRows;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Physical, expiring factory sessions. Commands carry choices, never authority or prices. */
public final class PhysicalVehicleShop50 {
    static final long SESSION_TICKS = 1200;
    static final int PAGE_SIZE = 6;
    private static final int MAX_SESSIONS = 256;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static MinecraftServer owner;
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final long opened;
        final VehicleFactory50.Binding factory;
        VehicleService50.Quote quote;
        int page;
        Session(ServerPlayer player, VehicleFactory50.Binding factory) {
            this.player = player; this.factory = factory; opened = now(player.server);
        }
    }
    record RefuelSelection(UUID vehicle, VehicleFuel49.Mode mode) {}
    private PhysicalVehicleShop50() {}
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    static boolean withinLifetime(long opened, long tick) {
        return opened >= 0 && tick >= opened && tick - opened < SESSION_TICKS;
    }
    private static void bind(MinecraftServer server) {
        if (owner != server) { SESSIONS.clear(); owner = server; }
        long tick = now(server);
        SESSIONS.values().removeIf(s -> !ShopFacility40.connected(s.player) || !withinLifetime(s.opened, tick));
    }
    public static void stopped(MinecraftServer server) {
        if (owner == server) { SESSIONS.clear(); owner = null; }
    }
    /** Called only by the uncanceled main-hand native block interaction handler. */
    public static boolean openCounter(ServerPlayer player, BlockPos pos) {
        if (!VehicleFactory50.recognizes(player, pos)) return false;
        bind(player.server); SESSIONS.remove(player.getUUID());
        try {
            VehicleFactory50.Binding factory = VehicleFactory50.open(player, pos);
            if (SESSIONS.size() >= MAX_SESSIONS) throw new IOException("The factory is busy. Try the counter again shortly.");
            Session session = new Session(player, factory);
            SESSIONS.put(player.getUUID(), session);
            menu(session, "");
        } catch (IOException | RuntimeException failure) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(message(failure)));
        }
        return true;
    }
    public static LiteralArgumentBuilder<CommandSourceStack> addRoutes(LiteralArgumentBuilder<CommandSourceStack> procure) {
        var vehicle = literal("vehicle");
        for (String action : List.of("review", "refuel", "confirm", "cancel", "menu", "page", "recover", "close"))
            vehicle.then(literal(action).then(argument("selection", StringArgumentType.word()).executes(ctx ->
                    command(ctx.getSource(), action, StringArgumentType.getString(ctx, "selection")))));
        return procure.then(vehicle);
    }
    /** Existing aliases may review only an already-open physical session. */
    public static int review(CommandSourceStack source, String offer) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Visit the Vehicle Factory counter in person.")); return 0;
        }
        try { reviewPurchase(session(player), offer); return 1; }
        catch (IOException | RuntimeException failure) { return failed(source, player, failure); }
    }
    public static int command(CommandSourceStack source, String action, String selection) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Visit the Vehicle Factory counter in person.")); return 0;
        }
        try {
            Session session = session(player);
            switch (action) {
                case "review" -> reviewPurchase(session, selectedOffer(session.id, selection));
                case "refuel" -> {
                    RefuelSelection chosen = selectedRefuel(session.id, selection);
                    clear(session);
                    review(session, VehicleService50.quoteRefuel(player, session.factory, chosen.vehicle(), chosen.mode()));
                }
                case "confirm" -> {
                    VehicleService50.Quote quote = requireQuote(session, selection);
                    clear(session); // Consume the visible action BEFORE any durable mutation.
                    check(session);
                    menu(session, VehicleService50.confirm(player, quote));
                }
                case "cancel" -> {
                    requireQuote(session, selection); clear(session);
                    menu(session, "Review canceled. No new order was placed.");
                }
                case "menu" -> { requireSession(session, selection); clear(session); menu(session, ""); }
                case "page" -> {
                    int page = selectedPage(session.id, selection);
                    List<Entity> vehicles = VehicleFactory50.serviceVehicles(player, session.factory);
                    page(vehicles, page); session.page = page; clear(session); menu(session, "");
                }
                case "recover" -> {
                    UUID operation = selectedOperation(session.id, selection);
                    if (VehicleService50.pending(player, session.factory).stream().noneMatch(p -> p.operation().equals(operation)))
                        throw new IOException("That unfinished operation is not available at this counter.");
                    clear(session); menu(session, VehicleService50.recover(player, session.factory, operation));
                }
                case "close" -> { requireSession(session, selection); SESSIONS.remove(player.getUUID()); }
                default -> throw new IOException("Choose an action at the Vehicle Factory counter.");
            }
            return 1;
        } catch (IOException | RuntimeException failure) { return failed(source, player, failure); }
    }
    private static int failed(CommandSourceStack source, ServerPlayer player, Throwable failure) {
        source.sendFailure(Component.literal(message(failure)));
        Session retained = SESSIONS.get(player.getUUID());
        if (retained != null) {
            clear(retained);
            try { menu(retained, message(failure)); }
            catch (IOException | RuntimeException stale) { SESSIONS.remove(player.getUUID()); }
        }
        return 0;
    }
    private static Session session(ServerPlayer player) throws IOException {
        bind(player.server);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player) throw new IOException("Right-click your Vehicle Factory counter first. Its menu lasts one minute.");
        check(session); return session;
    }
    private static void check(Session session) throws IOException {
        if (owner != session.player.server || SESSIONS.get(session.player.getUUID()) != session
                || !withinLifetime(session.opened, now(session.player.server)))
            throw new IOException("The factory session expired. Right-click the counter again.");
        VehicleFactory50.revalidate(session.player, session.factory);
    }
    private static void clear(Session session) { session.quote = null; }
    private static void requireSession(Session session, String text) throws IOException {
        if (!session.id.equals(token(text))) throw new IOException("That factory session has expired.");
    }
    private static VehicleService50.Quote requireQuote(Session session, String text) throws IOException {
        if (session.quote == null || !session.quote.id().equals(token(text)))
            throw new IOException("That review has expired. Choose the vehicle again.");
        return session.quote;
    }
    static UUID token(String text) throws IOException {
        try {
            UUID id = UUID.fromString(text);
            if (!id.toString().equals(text)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException invalid) { throw new IOException("Invalid factory selection."); }
    }
    static String selectedOffer(UUID session, String text) throws IOException {
        String[] parts = parts(session, text, 2);
        if (!Set.of("pickup", "lav150").contains(parts[1])) throw new IOException("Unknown vehicle offer.");
        return parts[1];
    }
    static RefuelSelection selectedRefuel(UUID session, String text) throws IOException {
        String[] parts = parts(session, text, 3);
        VehicleFuel49.Mode mode = switch (parts[2]) {
            case "quarter" -> VehicleFuel49.Mode.ADD_QUARTER;
            case "fill" -> VehicleFuel49.Mode.FILL;
            default -> throw new IOException("Invalid refueling action.");
        };
        return new RefuelSelection(token(parts[1]), mode);
    }
    static UUID selectedOperation(UUID session, String text) throws IOException {
        return token(parts(session, text, 2)[1]);
    }
    static int selectedPage(UUID session, String text) throws IOException {
        String number = parts(session, text, 2)[1];
        if (!number.matches("0|[1-9][0-9]{0,3}")) throw new IOException("Invalid vehicle page.");
        return Integer.parseInt(number);
    }
    private static String[] parts(UUID session, String text, int count) throws IOException {
        if (text == null || text.length() > 100) throw new IOException("Invalid factory selection.");
        String[] parts = text.split("\\.", -1);
        if (parts.length != count || !session.equals(token(parts[0]))) throw new IOException("That factory session has expired.");
        return parts;
    }
    static <T> List<T> page(List<T> items, int requested) throws IOException {
        long start = (long) requested * PAGE_SIZE;
        if (requested < 0 || (items.isEmpty() ? requested != 0 : start >= items.size()))
            throw new IOException("That vehicle page is unavailable.");
        return List.copyOf(items.subList((int)start, (int)Math.min(start + PAGE_SIZE, items.size())));
    }
    static String percent(long energy, long capacity) throws IOException {
        if (capacity <= 0 || energy < 0 || energy > capacity) throw new IOException("Invalid vehicle fuel reading.");
        return BigDecimal.valueOf(energy).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacity), 1, RoundingMode.DOWN).toPlainString() + "%";
    }
    static String credit(long numerator, long capacity) throws IOException {
        if (capacity <= 0 || numerator < 0 || numerator >= capacity) throw new IOException("Invalid prepaid Fuel credit.");
        if (numerator == 0) return "0";
        String display = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(capacity), 4, RoundingMode.DOWN).toPlainString();
        return display.equals("0.0000") ? "less than 0.0001" : display;
    }
    private static String title(String offer) throws IOException {
        return switch (offer) { case "pickup" -> "Unarmed Pickup"; case "lav150" -> "LAV-150"; default -> throw new IOException("Unsupported vehicle."); };
    }
    private static List<String> base(Session session, String heading) {
        List<String> rows = new ArrayList<>(); rows.add("V|vehicle_shop"); rows.add("H|" + heading);
        rows.add("X|nation procure vehicle close " + session.id); return rows;
    }
    private static void menu(Session session, String notice) throws IOException {
        check(session); clear(session);
        List<String> rows = base(session, "Vehicle Factory");
        if (!notice.isEmpty()) rows.add("Y|" + DefenseMenuRows.safe(notice));
        rows.add("D|" + DefenseMenuRows.safe(session.factory.title()));
        rows.add("G|National Fuel available: " + ShopFacility40.leader(session.player).resource(ResourceType.FUEL));
        List<VehicleService50.Pending> pending = VehicleService50.pending(session.player, session.factory);
        if (!pending.isEmpty()) {
            rows.add("Y|Continue unfinished operations before placing another order.");
            for (var operation : pending) {
                rows.add("P|" + DefenseMenuRows.safe(operation.description()));
                rows.add("A|nation procure vehicle recover " + session.id + "." + operation.operation() + "|Continue original operation");
            }
        } else {
            boolean deliveryHere = "plot_1".equals(session.factory.identity().getString("plot"));
            if (deliveryHere) {
                rows.add("P|Buy a vehicle — arrives with 25% fuel");
                for (String offer : List.of("pickup", "lav150"))
                    rows.add("A|nation procure vehicle review " + session.id + "." + offer + "|Review " + title(offer) + " purchase");
            } else rows.add("D|Refueling is available here. New deliveries require the original Vehicle Factory location.");
            rows.add("P|Refuel a parked vehicle");
            rows.add("D|" + (deliveryHere ? "Park in the service lane or on the road immediately outside this factory." : "Park in this factory’s service lane.") + " Stop and exit, then use this counter.");
            List<Entity> vehicles = VehicleFactory50.serviceVehicles(session.player, session.factory);
            int pages = Math.max(1, (vehicles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (session.page >= pages) session.page = pages - 1;
            if (vehicles.isEmpty()) rows.add("D|No eligible owned vehicle is parked in the service area.");
            for (Entity vehicle : page(vehicles, session.page)) {
                String label = title(VehicleService50.offer(vehicle)) + " · " + vehicle.getUUID().toString().substring(0, 8);
                long energy = VehicleService50.energy(vehicle), capacity = VehicleService50.capacity(vehicle);
                rows.add("P|" + label + " · Fuel " + percent(energy, capacity));
                if (energy < capacity) {
                    rows.add("A|nation procure vehicle refuel " + session.id + "." + vehicle.getUUID() + ".quarter|Review Add 25% — " + label);
                    rows.add("A|nation procure vehicle refuel " + session.id + "." + vehicle.getUUID() + ".fill|Review Fill tank — " + label);
                } else rows.add("G|Tank full.");
            }
            if (pages > 1) rows.add("D|Vehicle page " + (session.page + 1) + " of " + pages);
            if (session.page > 0) rows.add("A|nation procure vehicle page " + session.id + "." + (session.page - 1) + "|Previous vehicles");
            if (session.page + 1 < pages) rows.add("A|nation procure vehicle page " + session.id + "." + (session.page + 1) + "|Next vehicles");
        }
        rows.add("D|Stay beside the counter. Reopen it after one minute.");
        rows.add("A|nation procure vehicle menu " + session.id + "|Refresh factory");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static void reviewPurchase(Session session, String offer) throws IOException {
        check(session); clear(session);
        review(session, VehicleService50.quotePurchase(session.player, session.factory, offer));
    }
    private static void review(Session session, VehicleService50.Quote quote) throws IOException {
        check(session); session.quote = quote;
        boolean purchase = quote.kind() == VehicleService50.Kind.PURCHASE;
        List<String> rows = base(session, purchase ? "Review vehicle purchase" : "Review refueling");
        rows.add("P|" + title(quote.offer()) + (purchase ? "" : " · " + quote.vehicle().toString().substring(0, 8)));
        if (purchase) {
            rows.add("G|National cost: " + DefenseMenuRows.safe(quote.price().describe()));
            rows.add("D|Includes 25% fuel. Your vehicle arrives facing along the road outside this factory.");
        } else {
            rows.add("P|Fuel: " + percent(quote.beforeEnergy(), quote.capacity()) + " → " + percent(quote.afterEnergy(), quote.capacity()));
            rows.add("G|National Fuel charge: " + quote.fuelCost());
            rows.add("D|Prepaid Fuel credit: " + credit(quote.creditBeforeNumerator(), quote.capacity())
                    + " → " + credit(quote.creditAfterNumerator(), quote.capacity()) + ". Small fractions remain saved for this vehicle.");
            rows.add("D|Only the fuel added is billed. Keep this vehicle parked and unoccupied.");
        }
        rows.add("A|nation procure vehicle confirm " + quote.id() + "|" + (purchase ? "Confirm purchase" : "Confirm refueling"));
        rows.add("A|nation procure vehicle cancel " + quote.id() + "|Cancel");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static String message(Throwable failure) {
        return DefenseMenuRows.safe(failure.getMessage() == null ? "The factory operation could not be verified. Reopen the counter." : failure.getMessage());
    }
}
