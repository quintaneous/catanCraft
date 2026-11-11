package com.quin.catancraft.world;

import java.io.IOException;
import java.util.*;

/** Unregistered fixed-bay policy. Native survey, world probes and durable vehicle delivery
 * must be supplied before activation. This class never charges, spawns or repairs. */
final class VehicleBay45 {
    // Provisional policy reused from the existing weapon shop; no vehicle route is activated.
    static final long SESSION_TICKS = 1200;
    record Cell(int x, int y, int z) {
        Cell transform(Cell anchor, int rotation) {
            if (rotation != 0 && rotation != 180) throw new IllegalArgumentException("Unsupported factory rotation");
            int sign = rotation == 0 ? 1 : -1;
            return new Cell(Math.addExact(anchor.x, Math.multiplyExact(x, sign)),
                    Math.addExact(anchor.y, y), Math.addExact(anchor.z, Math.multiplyExact(z, sign)));
        }
    }
    record Position(double x, double y, double z) {
        Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 256 || Math.abs(z) > 256 || Math.abs(y) > 128)
                throw new IllegalArgumentException("Finite bounded local spawn required");
        }
        Cell cell() { return new Cell((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)); }
    }
    /** World-space coordinates deliberately have no local-envelope constructor restriction. */
    record WorldPosition(double x, double y, double z) {}
    record Facility(String city, String plot, UUID nation, String dimension, String revision,
                    String sourceSHA256, Cell anchor, int rotation, Cell counter, int level) {
        Facility {
            label(city); label(plot); label(dimension); label(revision); hash(sourceSHA256);
            Objects.requireNonNull(nation); Objects.requireNonNull(anchor); Objects.requireNonNull(counter);
            if ((rotation != 0 && rotation != 180) || level < 1 || level > 5)
                throw new IllegalArgumentException("Unsupported completed facility");
        }
    }
    /** Masks are local offsets from the resolved facility anchor, not guessed world locations.
     * Clearance includes the certified body, boarding and full departure sweep. */
    record Bay(String id, Facility facility, String vehicle, String nativeGeometrySHA256,
               Position spawn, int heading, Set<Cell> support, Set<Cell> clearance,
               Set<Cell> boarding, Set<Cell> departure) {
        Bay {
            label(id); Objects.requireNonNull(facility); label(vehicle); hash(nativeGeometrySHA256);
            if (!vehicle.startsWith("superbwarfare:") || heading < 0 || heading >= 360 || heading % 90 != 0)
                throw new IllegalArgumentException("Unqualified vehicle/heading");
            Objects.requireNonNull(spawn);
            support = mask(support); clearance = mask(clearance); boarding = mask(boarding); departure = mask(departure);
            if (!clearance.contains(spawn.cell()) || !clearance.containsAll(boarding) || !clearance.containsAll(departure)
                    || !Collections.disjoint(support, clearance))
                throw new IllegalArgumentException("Incomplete or contradictory bay masks");
        }
        Cell world(Cell local) { return local.transform(facility.anchor(), facility.rotation()); }
        WorldPosition worldSpawn() {
            int sign = facility.rotation() == 0 ? 1 : -1;
            // Rotate continuous positions around the anchor block center so a fractional
            // position remains inside the same cell as the discrete schematic transform.
            return new WorldPosition(facility.anchor().x() + 0.5 + sign * (spawn.x() - 0.5),
                    facility.anchor().y() + spawn.y(), facility.anchor().z() + 0.5 + sign * (spawn.z() - 0.5));
        }
        int worldHeading() { return (heading + facility.rotation()) % 360; }
    }
    /** All checks must be real current native observations; a policy fixture is not a geometry certificate. */
    interface Probe {
        boolean geometryMatches(Bay bay);
        boolean loadedAndOwned(Cell worldCell, Facility facility);
        boolean clearOfBlocksFluidsAndEntities(Cell worldCell);
        boolean fullSupport(Cell worldCell);
    }
    record Presence(UUID actor, UUID nation, String dimension, Facility facility,
                    boolean authenticatedAlive, boolean leader, boolean counterReachAndRay,
                    boolean completedSourceMatches, boolean pendingConstructionOrSiege) {
        Presence { Objects.requireNonNull(actor); Objects.requireNonNull(nation); label(dimension); Objects.requireNonNull(facility); }
    }
    record Quote(UUID actor, Facility facility, Bay bay, ShopPurchase40.Price price, long createdTick) {
        Quote {
            Objects.requireNonNull(actor); Objects.requireNonNull(facility); Objects.requireNonNull(bay); Objects.requireNonNull(price);
            if (createdTick < 0 || !facility.equals(bay.facility())) throw new IllegalArgumentException("Mismatched quote facility");
        }
    }
    private VehicleBay45() {}
    static Quote quote(Presence current, List<Bay> registered, String vehicle,
                       ShopPurchase40.Price price, long tick, Probe probe) throws IOException {
        guard(current);
        if (tick < 0) throw new IOException("Invalid server clock");
        return new Quote(current.actor(), current.facility(), select(current.facility(), registered, vehicle, probe), price, tick);
    }
    /** Confirmation never moves a quote into a newly available bay. Requote explicitly instead. */
    static void confirm(Quote quote, Presence current, long tick, List<Bay> registered, Probe probe) throws IOException {
        Objects.requireNonNull(quote); guard(current); validateRegistry(current.facility(), registered);
        if (!quote.actor().equals(current.actor()) || !quote.facility().equals(current.facility())
                || tick < quote.createdTick() || tick - quote.createdTick() >= SESSION_TICKS
                || !registered.contains(quote.bay()) || !available(quote.bay(), probe))
            throw new IOException("Stale, foreign, changed or obstructed fixed-bay quote");
    }
    static Bay select(Facility facility, List<Bay> registered, String vehicle, Probe probe) throws IOException {
        validateRegistry(facility, registered);
        for (Bay bay : registered) if (bay.vehicle().equals(vehicle) && available(bay, probe)) return bay;
        throw new IOException("No registered compatible fixed bay is clear; no nearby fallback");
    }
    private static void validateRegistry(Facility facility, List<Bay> registered) throws IOException {
        if (registered == null || registered.isEmpty() || registered.size() > 16) throw new IOException("Missing/bounded bay registry required");
        Set<String> ids = new HashSet<>();
        for (Bay bay : registered) if (bay == null || !facility.equals(bay.facility()) || !ids.add(bay.id()))
            throw new IOException("Duplicate or foreign bay registration");
    }
    private static boolean available(Bay bay, Probe probe) {
        Objects.requireNonNull(probe);
        if (!probe.geometryMatches(bay)) return false;
        for (Cell cell : bay.clearance()) {
            Cell world = bay.world(cell);
            if (!probe.loadedAndOwned(world, bay.facility()) || !probe.clearOfBlocksFluidsAndEntities(world)) return false;
        }
        for (Cell cell : bay.support()) {
            Cell world = bay.world(cell);
            if (!probe.loadedAndOwned(world, bay.facility()) || !probe.fullSupport(world)) return false;
        }
        return true;
    }
    private static void guard(Presence p) throws IOException {
        Objects.requireNonNull(p);
        if (!p.authenticatedAlive() || !p.leader() || !p.counterReachAndRay() || !p.completedSourceMatches()
                || p.pendingConstructionOrSiege() || !p.nation().equals(p.facility().nation())
                || !p.dimension().equals(p.facility().dimension())) throw new IOException("Physical vehicle facility unavailable");
    }
    private static Set<Cell> mask(Set<Cell> cells) {
        if (cells == null || cells.isEmpty() || cells.size() > 65536) throw new IllegalArgumentException("Explicit bounded mask required");
        for (Cell p : cells) if (p == null || Math.abs((long)p.x()) > 256 || Math.abs((long)p.z()) > 256 || Math.abs((long)p.y()) > 128)
            throw new IllegalArgumentException("Mask outside bounded local facility envelope");
        return Set.copyOf(cells);
    }
    private static void label(String text) { if (text == null || text.isBlank() || text.length() > 160) throw new IllegalArgumentException("Explicit identity required"); }
    private static void hash(String text) { if (text == null || !text.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Pinned source/geometry hash required"); }
}
