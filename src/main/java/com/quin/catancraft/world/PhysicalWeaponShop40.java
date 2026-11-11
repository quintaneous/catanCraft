package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.network.NationNetwork;
import com.quin.catancraft.ui.DefenseMenuRows;
import com.quin.catancraft.ui.NationDashboard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Counter-created sessions only. Buttons send commands, but commands cannot create a session.
 * Provisional lifetime is 1,200 server game ticks (one minute at 20 TPS), never extended by
 * reviews. Reopening the physical counter invalidates both old session and old quote. */
public final class PhysicalWeaponShop40 {
    static final long SESSION_TICKS = 1200;
    private static final int MAX_SESSIONS = 256;
    static final int PAGE_SIZE = 8;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static MinecraftServer owner;
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final long opened;
        final ShopFacility40.Binding facility;
        Quote quote;
        RewardQuote rewardQuote;
        int page;
        int rewardPage;
        Session(ServerPlayer player, long opened, ShopFacility40.Binding facility) {
            this.player = player; this.opened = opened; this.facility = facility;
        }
    }
    private record Quote(UUID id, WeaponStock42.Offer offer, CompoundTag facility, NativeWeapon42.Supply supply) {
        Quote { facility = facility.copy(); Objects.requireNonNull(supply); }
        public CompoundTag facility() { return facility.copy(); }
    }
    private record RewardQuote(UUID id, RewardClaim43.Selection selection,
                               RewardTable41.Entitlement entitlement, NativeWeapon42.Supply supply) {}
    private PhysicalWeaponShop40() {}
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    static boolean withinLifetime(long opened, long now) {
        return opened >= 0 && now >= opened && now - opened < SESSION_TICKS;
    }
    private static void bind(MinecraftServer server) {
        if (owner != server) { SESSIONS.clear(); owner = server; }
        long tick = now(server);
        SESSIONS.values().removeIf(s -> !ShopFacility40.connected(s.player) || !withinLifetime(s.opened, tick));
    }
    public static void stopped(MinecraftServer server) { if (owner == server) { SESSIONS.clear(); owner = null; } }

    /** Called only from an uncanceled main-hand native block interaction. Does not place or edit blocks. */
    public static boolean openCounter(ServerPlayer player, BlockPos pos) {
        if (!ShopFacility40.recognizes(player, pos)) return false;
        // Ordinary members retain the existing management lectern/dashboard interaction.
        if (pos.equals(ShopFacility40.STARTER)) {
            var nation = CatanSavedData.get(player.server).nationForPlayer(player.getUUID());
            if (nation == null || !nation.isLeader(player.getUUID())) return false;
        }
        bind(player.server); SESSIONS.remove(player.getUUID());
        try {
            ShopFacility40.Binding facility = ShopFacility40.open(player, pos);
            if (SESSIONS.size() >= MAX_SESSIONS) throw new IOException("The shop is busy; try the counter again shortly.");
            Session session = new Session(player, now(player.server), facility);
            SESSIONS.put(player.getUUID(), session);
            menu(session, "");
        } catch (IOException | RuntimeException failure) {
            String message = message(failure);
            if (pos.equals(ShopFacility40.STARTER)) NationDashboard.open(player, message);
            else player.sendSystemMessage(Component.literal(message));
        }
        return true;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> addRoutes(LiteralArgumentBuilder<CommandSourceStack> procure) {
        LiteralArgumentBuilder<CommandSourceStack> shop = literal("shop");
        for (String action : List.of("review", "confirm", "cancel", "menu", "page", "recover", "close",
                "rewards", "claimreview", "claimconfirm", "claimcancel", "claimrecover")) {
            shop.then(literal(action).then(argument("selection", StringArgumentType.word()).executes(context ->
                    command(context.getSource(), action, StringArgumentType.getString(context, "selection")))));
        }
        return procure.then(shop);
    }
    /** Legacy gun/ammo commands may request review of an already-open physical session only. */
    public static int review(CommandSourceStack source, String offer) { return command(source, "review", offer); }
    /** Read-only discovery rows for the legacy catalog. Listing stock never grants shopping authority. */
    public static List<String> catalogRows() {
        return WeaponShopCatalog40.offers().stream().map(offer -> offer.kindName() + ": " + offer.description()
                + (offer.requiredLevel() == 0 ? " · Starter or Weapons Factory counter"
                : " · Local Weapons Factory Level " + offer.requiredLevel())).toList();
    }
    public static int command(CommandSourceStack source, String action, String selection) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Visit the weapon counter in person.")); return 0;
        }
        try {
            Session session = session(player);
            switch (action) {
                case "review" -> review(session, ActiveWeaponStock44.offer(selection));
                case "confirm" -> confirm(session, token(selection));
                case "cancel" -> {
                    if (session.quote == null || !session.quote.id().equals(token(selection)))
                        throw new IOException("That review has expired; open the counter again.");
                    session.quote = null; menu(session, "Purchase canceled. No new order was placed.");
                }
                case "menu" -> {
                    if (!session.id.equals(token(selection))) throw new IOException("That counter session has expired.");
                    session.quote = null; menu(session, "");
                }
                case "page" -> {
                    int requested = selectedPage(session.id, selection);
                    ActiveWeaponStock44.page(requested, PAGE_SIZE); // Validate before changing the retained session.
                    session.quote = null; session.page = requested; menu(session, "");
                }
                case "recover" -> recover(session, token(selection));
                case "close" -> {
                    if (!session.id.equals(token(selection))) throw new IOException("That counter session has expired.");
                    SESSIONS.remove(player.getUUID());
                }
                case "rewards" -> {
                    int page = selectedPage(session.id, selection);
                    rewardPage(rewardViews(session), page); // Validate before changing the current review.
                    session.rewardPage = page; clearReviews(session); rewards(session, "");
                }
                case "claimreview" -> reviewReward(session, selectedReward(session.id, selection));
                case "claimconfirm" -> confirmReward(session, token(selection));
                case "claimcancel" -> {
                    if (session.rewardQuote == null || !session.rewardQuote.id().equals(token(selection)))
                        throw new IOException("That reward review has expired; open the counter again.");
                    clearReviews(session); rewards(session, "Reward review canceled. No new claim was reserved.");
                }
                case "claimrecover" -> recoverReward(session, selectedReward(session.id, selection));
                default -> throw new IOException("Open the weapon counter to choose an item.");
            }
            return 1;
        } catch (IOException | RuntimeException failure) {
            source.sendFailure(Component.literal(message(failure)));
            Session retained = SESSIONS.get(player.getUUID());
            if (retained != null) {
                clearReviews(retained);
                try { menu(retained, message(failure)); }
                catch (IOException | RuntimeException stale) { SESSIONS.remove(player.getUUID()); }
            }
            return 0;
        }
    }
    private static UUID token(String text) throws IOException {
        try { UUID id = UUID.fromString(text); if (!id.toString().equals(text)) throw new IllegalArgumentException(); return id; }
        catch (IllegalArgumentException failure) { throw new IOException("That counter selection is invalid."); }
    }
    private static Session session(ServerPlayer player) throws IOException {
        bind(player.server);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player) throw new IOException("Right-click the weapon counter first. Its menu lasts one minute.");
        check(session); return session;
    }
    private static void check(Session session) throws IOException {
        if (owner != session.player.server || SESSIONS.get(session.player.getUUID()) != session
                || !withinLifetime(session.opened, now(session.player.server)))
            throw new IOException("The counter session expired; right-click the counter again.");
        ShopFacility40.recheck(session.player, session.facility.identity(), true);
    }
    static int selectedPage(UUID session, String selection) throws IOException {
        if (selection == null || selection.length() > 48) throw new IOException("Invalid stock page.");
        int separator = selection.indexOf('.');
        if (separator != 36 || !session.equals(token(selection.substring(0, separator))))
            throw new IOException("That counter session has expired.");
        String number = selection.substring(separator + 1);
        if (!number.matches("0|[1-9][0-9]{0,3}")) throw new IOException("Invalid stock page.");
        return Integer.parseInt(number);
    }
    static RewardClaim43.Selection selectedReward(UUID session, String text) throws IOException {
        if (text == null || text.length() > 77) throw new IOException("Invalid reward selection.");
        String[] parts = text.split("\\.", -1);
        if (parts.length != 3 || !session.equals(token(parts[0])) || !parts[2].matches("[0-9]|1[0-5]"))
            throw new IOException("That reward selection or counter session is invalid.");
        try { return new RewardClaim43.Selection(token(parts[1]), Integer.parseInt(parts[2])); }
        catch (IllegalArgumentException failure) { throw new IOException("Invalid reward selection.", failure); }
    }
    private static void clearReviews(Session session) { session.quote = null; session.rewardQuote = null; }
    private static ShopPurchase40.Environment environment(Session session) { return environment(session, null, null); }
    private static ShopPurchase40.Environment environment(Session session, WeaponStock42.Offer offer, NativeWeapon42.Supply supply) {
        return ShopPurchase40.runtime(session.player, original -> {
            check(session);
            // Historic facility and price stay frozen; the current physical counter must still be valid.
            ShopFacility40.guard(session.player, original);
            if (supply != null) { ActiveWeaponStock44.requireCurrent(offer); NativeWeapon42.recheck(session.player.server, offer, supply); }
        });
    }
    private static void menu(Session session, String notice) throws IOException {
        check(session);
        clearReviews(session);
        var nation = ShopFacility40.leader(session.player);
        List<UUID> pending = ShopPurchase40.pending(environment(session));
        List<String> rows = base(session, session.facility.title());
        if (!notice.isBlank()) rows.add("Y|" + DefenseMenuRows.safe(notice));
        rows.add("G|Nation treasury: $" + nation.treasury());
        rows.add("D|Purchases use nation money and supplies. Guns arrive unloaded.");
        rows.add("D|" + (session.facility.starter() ? "Starter stock: Glock 17 and 9mm. Other products require the local Weapons Factory."
                : "Factory stock is unlocked by this local factory's completed level."));
        if (!pending.isEmpty()) {
            rows.add("Y|An earlier order is unfinished. Continue it before buying again.");
            for (UUID id : pending) rows.add("A|nation procure shop recover " + id + "|Continue unfinished order");
        } else {
            int pages = (ActiveWeaponStock44.offers().size() + PAGE_SIZE - 1) / PAGE_SIZE;
            rows.add("G|Stock page " + (session.page + 1) + " of " + pages);
            rows.add("D|New stock uses provisional playtest prices. Native availability is checked at review.");
            for (var offer : ActiveWeaponStock44.page(session.page, PAGE_SIZE)) {
                rows.add("P|" + offer.kindName() + ": " + offer.description());
                if (!net.minecraftforge.fml.ModList.get().isLoaded(offer.provider())) rows.add("Y|This weapon mod is unavailable.");
                else if (offer.requiredLevel() > session.facility.level()) rows.add("Y|Requires a local Weapons Factory (Level " + offer.requiredLevel() + ").");
                else if (!new EconomyCost(offer.price().money(), offer.price().resources()).canAfford(nation))
                    rows.add("Y|Your nation needs more money or supplies for this item.");
                else rows.add("A|nation procure shop review " + offer.id() + "|Review " + offer.title());
            }
            if (session.page > 0) rows.add("A|nation procure shop page " + session.id + "." + (session.page - 1) + "|Previous stock page");
            if (session.page + 1 < pages) rows.add("A|nation procure shop page " + session.id + "." + (session.page + 1) + "|Next stock page");
        }
        rows.add("A|nation procure shop rewards " + session.id + ".0|Monument supply rewards");
        rows.add("D|Stay within four blocks with a clear path to the counter. Reopen it after one minute.");
        rows.add("A|nation|City management");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static List<String> base(Session session, String title) {
        List<String> rows = new ArrayList<>(); rows.add("V|weapon_shop"); rows.add("H|" + title);
        rows.add("X|nation procure shop close " + session.id); return rows;
    }
    private static RewardClaim43.Environment rewardEnvironment(Session session) {
        return RewardClaim43.runtime(session.player, original -> {
            check(session); ShopFacility40.guard(session.player, original);
        });
    }
    private static List<RewardClaim43.View> rewardViews(Session session) throws IOException {
        check(session);
        return RewardClaim43.list(rewardEnvironment(session));
    }
    static List<RewardClaim43.View> rewardPage(List<RewardClaim43.View> views, int page) throws IOException {
        long start = (long) page * PAGE_SIZE;
        if (page < 0 || (views.isEmpty() ? page != 0 : start >= views.size()))
            throw new IOException("That reward page is unavailable.");
        return List.copyOf(views.subList((int) start, (int) Math.min(start + PAGE_SIZE, views.size())));
    }
    private static String rewardToken(Session session, RewardClaim43.Selection selection) {
        return session.id + "." + selection.rewardId() + "." + selection.index();
    }
    private static RewardClaim43.View rewardView(Session session, RewardClaim43.Selection selection) throws IOException {
        return rewardViews(session).stream().filter(view -> view.selection().equals(selection)).findFirst()
                .orElseThrow(() -> new IOException("Your nation has no settled equipment reward for that selection."));
    }
    private static void noPendingPurchase(Session session) throws IOException {
        if (!ShopPurchase40.pending(environment(session)).isEmpty())
            throw new IOException("Continue your unfinished weapon order before collecting equipment rewards.");
    }
    private static void rewards(Session session, String notice) throws IOException {
        check(session); clearReviews(session); noPendingPurchase(session);
        List<RewardClaim43.View> views = rewardViews(session);
        int pages = Math.max(1, (views.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        session.rewardPage = Math.min(session.rewardPage, pages - 1);
        List<String> rows = base(session, "Monument supply rewards");
        if (!notice.isBlank()) rows.add("Y|" + DefenseMenuRows.safe(notice));
        rows.add("D|These are your nation's captured prizes. Claiming equipment costs no money or supplies.");
        rows.add("D|Money and materials were credited at capture. Guns arrive unloaded; claim their ammo separately.");
        rows.add("G|Reward page " + (session.rewardPage + 1) + " of " + pages);
        if (views.isEmpty()) rows.add("P|Your nation has no equipment rewards to collect yet.");
        for (RewardClaim43.View view : rewardPage(views, session.rewardPage)) {
            String title;
            try { title = NativeReward43.description(view.entitlement()); }
            catch (IOException failure) {
                rows.add("P|" + DefenseMenuRows.safe(view.entitlement().objectId()) + " x" + view.entitlement().count());
                rows.add("Y|This saved reward is retained; its delivery type is not available yet.");
                continue;
            }
            rows.add("P|" + DefenseMenuRows.safe(title));
            switch (view.status()) {
                case "AVAILABLE" -> rows.add("A|nation procure shop claimreview " + rewardToken(session, view.selection()) + "|Review reward");
                case "COMMITTED" -> rows.add("G|Already claimed. It will not be granted again.");
                case "HELD" -> rows.add("Y|This claim needs recovery review. Its original record is preserved.");
                case "RESERVED" -> {
                    if (session.player.getUUID().equals(view.reservedActor()))
                        rows.add("A|nation procure shop claimrecover " + rewardToken(session, view.selection()) + "|Continue original claim");
                    else rows.add("Y|Reserved by the original claimant; it cannot be reassigned here.");
                }
                default -> throw new IOException("Unknown saved reward claim state.");
            }
        }
        if (session.rewardPage > 0) rows.add("A|nation procure shop rewards " + session.id + "." + (session.rewardPage - 1) + "|Previous rewards");
        if (session.rewardPage + 1 < pages) rows.add("A|nation procure shop rewards " + session.id + "." + (session.rewardPage + 1) + "|Next rewards");
        rows.add("A|nation procure shop menu " + session.id + "|Back to weapon shop");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static void reviewReward(Session session, RewardClaim43.Selection selection) throws IOException {
        check(session); clearReviews(session); noPendingPurchase(session);
        RewardClaim43.View view = rewardView(session, selection);
        if (!view.status().equals("AVAILABLE")) throw new IOException("This reward already has a claim record. Use its recovery action.");
        if (!RewardClaim43.pending(rewardEnvironment(session)).isEmpty()) throw new IOException("Continue your original equipment claim first.");
        NativeWeapon42.Supply supply = NativeReward43.quote(session.player.server, view.entitlement());
        if (session.player.getInventory().getFreeSlot() < 0) throw new IOException("Keep one empty inventory slot to collect this reward.");
        check(session);
        RewardQuote quote = new RewardQuote(UUID.randomUUID(), selection, view.entitlement(), supply);
        session.rewardQuote = quote;
        List<String> rows = base(session, "Review monument reward");
        rows.add("P|" + DefenseMenuRows.safe(NativeReward43.description(view.entitlement())));
        rows.add("G|Claim cost: no money or national supplies.");
        rows.add("D|This prize belongs to your winning nation. Factory purchase tiers do not block earned rewards.");
        rows.add("Y|One empty inventory slot is required. Confirmation reserves this prize to you for recovery.");
        rows.add("A|nation procure shop claimconfirm " + quote.id() + "|Collect reward");
        rows.add("A|nation procure shop claimcancel " + quote.id() + "|Cancel");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static void confirmReward(Session session, UUID id) throws IOException {
        RewardQuote quote = session.rewardQuote;
        if (quote == null || !quote.id().equals(id)) throw new IOException("That reward review has expired. Open the counter again.");
        clearReviews(session); check(session); noPendingPurchase(session);
        RewardClaim43.View view = rewardView(session, quote.selection());
        if (!view.status().equals("AVAILABLE") || !view.entitlement().equals(quote.entitlement()))
            throw new IOException("That original reward or reservation changed. Review it again.");
        RewardClaim43.Result result;
        try (var lease = NativeWeapon42.transaction(session.player.server)) {
            NativeReward43.recheck(session.player.server, quote.entitlement(), quote.supply());
            RewardClaim43.Environment env = rewardEnvironment(session);
            RewardClaim43.prepare(env, quote.selection(), session.facility.forOffer(0), quote.supply());
            result = RewardClaim43.claim(env, quote.selection());
        }
        rewards(session, result.message());
    }
    private static void recoverReward(Session session, RewardClaim43.Selection selection) throws IOException {
        clearReviews(session); check(session); noPendingPurchase(session);
        RewardClaim43.Result result;
        try (var lease = NativeWeapon42.transaction(session.player.server)) {
            result = RewardClaim43.recover(rewardEnvironment(session), selection);
        }
        rewards(session, result.message());
    }
    private static void review(Session session, WeaponStock42.Offer offer) throws IOException {
        check(session); clearReviews(session);
        if (!ShopPurchase40.pending(environment(session)).isEmpty()) throw new IOException("Continue your unfinished order before buying again.");
        if (!RewardClaim43.pending(rewardEnvironment(session)).isEmpty()) throw new IOException("Continue your reserved monument equipment claim before buying again.");
        CompoundTag facility = session.facility.forOffer(offer.requiredLevel());
        ActiveWeaponStock44.requireCurrent(offer);
        NativeWeapon42.Supply supply = NativeWeapon42.quote(session.player.server, offer);
        ShopFacility40.recheck(session.player, facility, true);
        var nation = ShopFacility40.leader(session.player);
        if (!new EconomyCost(offer.price().money(), offer.price().resources()).canAfford(nation))
            throw new IOException("Your nation cannot afford this item yet.");
        Quote quote = new Quote(UUID.randomUUID(), offer, facility, supply); session.quote = quote;
        List<String> rows = base(session, "Review weapon purchase");
        rows.add("P|" + offer.title() + " x" + offer.count());
        rows.add("G|Nation cost: " + offer.price().describe());
        rows.add("D|From: " + session.facility.title());
        rows.add("D|" + (offer.kind() == WeaponShopCatalog40.Kind.GUN ? "The gun arrives unloaded. Ammunition is sold separately." : "Ammunition is delivered to your inventory."));
        rows.add("Y|Leave one empty inventory slot for delivery. Stay beside the counter.");
        rows.add("A|nation procure shop confirm " + quote.id() + "|Confirm purchase");
        rows.add("A|nation procure shop cancel " + quote.id() + "|Cancel");
        NationNetwork.openDashboard(session.player, rows);
    }
    private static void confirm(Session session, UUID token) throws IOException {
        Quote quote = session.quote;
        if (quote == null || !quote.id().equals(token)) throw new IOException("That purchase review has expired. Open the counter again.");
        session.quote = null; // Consume BEFORE rechecks, persistence or any possible debit.
        check(session); ShopFacility40.recheck(session.player, quote.facility(), true);
        ShopPurchase40.Result result;
        try (var lease = NativeWeapon42.transaction(session.player.server)) {
            ActiveWeaponStock44.requireCurrent(quote.offer());
            NativeWeapon42.recheck(session.player.server, quote.offer(), quote.supply());
            ShopPurchase40.Environment env = environment(session, quote.offer(), quote.supply());
            UUID operation = ShopPurchase40.create(env, new ShopPurchase40.Request(quote.id(), quote.offer().id(),
                    NativeWeapon42.frozenCatalog(quote.offer(), quote.supply()), quote.facility(), quote.offer().price(), quote.supply().item()));
            result = ShopPurchase40.resume(env, operation);
        }
        menu(session, result.message());
    }
    private static void recover(Session session, UUID operation) throws IOException {
        check(session); session.quote = null;
        ShopPurchase40.Environment env = environment(session);
        if (!ShopPurchase40.pending(env).contains(operation)) throw new IOException("That unfinished order is not yours to continue.");
        ShopPurchase40.Request original = ShopPurchase40.inspect(env, operation);
        NativeWeapon42.Supply supply = NativeWeapon42.recovered(original);
        ShopPurchase40.Result result;
        if (supply == null) result = ShopPurchase40.resume(env, operation);
        else try (var lease = NativeWeapon42.transaction(session.player.server)) {
            env = environment(session, ActiveWeaponStock44.offer(original.offer()), supply);
            result = ShopPurchase40.resume(env, operation);
        }
        menu(session, result.message());
    }
    private static String message(Throwable failure) {
        return DefenseMenuRows.safe(failure.getMessage() == null ? "The purchase could not be verified. Reopen the counter." : failure.getMessage());
    }
}
