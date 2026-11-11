package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Native stock qualification is rechecked at every purchase boundary. Frozen
 * provider fingerprints travel in the existing immutable catalog field; old
 * unbound 040 intents keep their original recovery semantics and price. */
public final class NativeWeapon42 {
    private static final AtomicLong RELOAD_GENERATION = new AtomicLong();
    private static final AtomicInteger RELOADS = new AtomicInteger();
    private static final ReentrantLock TRANSACTION = new ReentrantLock();
    private static final int MAX_CANONICAL = 8 * 1024 * 1024;
    private NativeWeapon42() {}

    record Supply(ItemStack item, String fingerprint) {
        Supply {
            Objects.requireNonNull(item);
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("A native supply needs its exact configuration fingerprint");
            item = item.copy();
        }
        public ItemStack item() { return item.copy(); }
    }

    /** Count overlapping reloads before asynchronous provider preparation starts.
     * Only successful server tag publication retires one. Failed reloads retain a
     * hold until server stop; a later success cannot release an unfinished reload. */
    public static void addReloadListener(AddReloadListenerEvent event) {
        if (TRANSACTION.isHeldByCurrentThread()) throw new IllegalStateException("Resource reload cannot start inside a native weapon purchase");
        TRANSACTION.lock();
        try { RELOAD_GENERATION.incrementAndGet(); RELOADS.incrementAndGet(); }
        finally { TRANSACTION.unlock(); }
    }
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)
            RELOADS.updateAndGet(count -> Math.max(0, count - 1));
    }
    public static void stopped(MinecraftServer server) {
        RELOAD_GENERATION.incrementAndGet(); RELOADS.set(0);
    }

    /** Serialize an entire payment/delivery operation with the beginning of reload.
     * The server thread never waits for another transaction; reload preparation
     * cannot begin until a previously admitted operation has released this lease. */
    static Lease transaction(MinecraftServer server) throws IOException {
        if (server == null || !server.isSameThread() || TRANSACTION.isHeldByCurrentThread() || !TRANSACTION.tryLock())
            throw new IOException("Native weapon processing is busy; reopen the counter.");
        try { ready(server); return new Lease(); }
        catch (IOException | RuntimeException failure) { TRANSACTION.unlock(); throw failure; }
    }
    static final class Lease implements AutoCloseable {
        private boolean closed;
        private Lease() {}
        public void close() { if (!closed) { closed = true; TRANSACTION.unlock(); } }
    }

    private static long ready(MinecraftServer server) throws IOException {
        if (server == null || !server.isSameThread()) throw new IOException("The weapon counter requires the server thread.");
        long generation = RELOAD_GENERATION.get();
        if (RELOADS.get() != 0) throw new IOException("Weapon data is loading or a reload failed. Wait for loading; a failed reload requires a server restart.");
        return generation;
    }
    private static void unchanged(MinecraftServer server, long generation) throws IOException {
        if (ready(server) != generation) throw new IOException("Weapon packs changed during review. Open the counter again.");
    }
    /** Reward qualification shares the same reload gate as paid stock. */
    static long qualificationEpoch(MinecraftServer server) throws IOException { return ready(server); }
    static void requireEpoch(MinecraftServer server, long generation) throws IOException { unchanged(server, generation); }
    private static void protectedProvider(String provider) throws IOException {
        try { CombatTerrainSafety.requireProtected(provider); }
        catch (IllegalStateException | IllegalArgumentException failure) {
            throw new IOException("Native combat protection is not ready; the equipment review is held.", failure);
        }
    }
    static Supply quote(MinecraftServer server, WeaponStock42.Offer offer) throws IOException {
        long generation = ready(server); WeaponStock42.requireCurrent(offer);
        protectedProvider(offer.provider());
        Supply result = switch (offer.provider()) {
            case "tacz" -> NativeTacz42.quote(server, offer);
            case "superbwarfare" -> NativeSbw42.quote(server, offer);
            default -> throw new IOException("This weapon provider is not qualified.");
        };
        item(offer, result.item()); unchanged(server, generation); return result;
    }
    static void recheck(MinecraftServer server, WeaponStock42.Offer offer, Supply supply) throws IOException {
        long generation = ready(server); WeaponStock42.requireCurrent(offer);
        protectedProvider(offer.provider());
        if (supply == null) throw new IOException("The native weapon review is missing.");
        item(offer, supply.item());
        switch (offer.provider()) {
            case "tacz" -> NativeTacz42.recheck(server, offer, supply);
            case "superbwarfare" -> NativeSbw42.recheck(server, offer, supply);
            default -> throw new IOException("This weapon provider is not qualified.");
        }
        unchanged(server, generation);
    }
    private static void item(WeaponStock42.Offer offer, ItemStack item) throws IOException {
        CompoundTag saved = item.save(new CompoundTag());
        if (item.isEmpty() || item.getCount() != offer.count() || item.getCount() > item.getMaxStackSize()
                || !ItemStack.of(saved).save(new CompoundTag()).equals(saved))
            throw new IOException("The native item did not retain its exact identity and amount.");
    }
    static String frozenCatalog(WeaponStock42.Offer offer, Supply supply) throws IOException {
        WeaponStock42.requireCurrent(offer);
        String value = offer.revision() + "@" + supply.fingerprint();
        if (value.length() > 160) throw new IOException("Native catalog identity is too long.");
        return value;
    }
    static Supply recovered(ShopPurchase40.Request original) throws IOException {
        // Existing paid040 operations cannot acquire a new price or fabricated fingerprint.
        if (original.catalog().equals(WeaponShopCatalog40.REVISION)) return null;
        WeaponStock42.Offer offer = WeaponStock42.offer(original.offer());
        String prefix = offer.revision() + "@";
        if (!original.catalog().startsWith(prefix)) throw new IOException("This historical native catalog requires its original recovery implementation.");
        String digest = original.catalog().substring(prefix.length());
        if (!digest.matches("[0-9a-f]{64}")) throw new IOException("The original native configuration proof is malformed.");
        Supply result = new Supply(original.item(), digest); item(offer, result.item()); return result;
    }
    static JsonObject reference(String resource, String expectedSha, int maximum) throws IOException {
        if (maximum < 1 || maximum > MAX_CANONICAL) throw new IOException("Invalid native reference limit.");
        try (InputStream in = NativeWeapon42.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing bundled native weapon reference.");
            byte[] bytes = in.readNBytes(maximum + 1);
            if (bytes.length > maximum || !sha(bytes).equals(expectedSha)) throw new IOException("The bundled native weapon reference changed.");
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Invalid native weapon reference.");
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException failure) {
            throw new IOException("Invalid native weapon reference.", failure);
        }
    }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String canonical(JsonElement value) throws IOException {
        StringBuilder out = new StringBuilder(); append(value, out, 0, new int[]{0}); return out.toString();
    }
    private static void append(JsonElement value, StringBuilder out, int depth, int[] nodes) throws IOException {
        if (depth > 48 || ++nodes[0] > 300_000 || out.length() > MAX_CANONICAL)
            throw new IOException("Native weapon configuration exceeds its bounded snapshot.");
        if (value == null || value.isJsonNull()) out.append("null");
        else if (value.isJsonObject()) {
            out.append('{'); boolean first = true;
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) {
                if (!first) out.append(','); first = false;
                out.append(new JsonPrimitive(key)).append(':'); append(value.getAsJsonObject().get(key), out, depth + 1, nodes);
            }
            out.append('}');
        } else if (value.isJsonArray()) {
            out.append('['); boolean first = true;
            for (JsonElement child : value.getAsJsonArray()) {
                if (!first) out.append(','); first = false; append(child, out, depth + 1, nodes);
            }
            out.append(']');
        } else {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isNumber()) {
                try {
                    BigDecimal number = p.getAsBigDecimal();
                    if (Math.abs((long) number.scale()) > 10_000 || number.precision() > 10_000) throw new NumberFormatException("Oversized number");
                    out.append(number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString());
                } catch (NumberFormatException failure) { throw new IOException("Non-finite native configuration number.", failure); }
            } else out.append(p);
        }
        if (out.length() > MAX_CANONICAL) throw new IOException("Native configuration snapshot is too large.");
    }
}
