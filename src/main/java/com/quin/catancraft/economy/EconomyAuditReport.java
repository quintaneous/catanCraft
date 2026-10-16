package com.quin.catancraft.economy;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only economic audit. Simulates the same four phase order as
 * EconomyEngine.runCycle using an NBT copy of the owning nation's stockpile.
 * Unlike an independent per-city preview, the simulation respects inputs
 * shared with every city owned by the same nation.
 */
public final class EconomyAuditReport {
    public record Report(String territoryId, String title, List<String> lines) {}

    private static final class Ledger {
        final List<String> raw = new ArrayList<>();
        final List<String> processors = new ArrayList<>();
        final List<String> cash = new ArrayList<>();
        final List<String> utilities = new ArrayList<>();
        final EnumMap<ResourceType, Long> net = new EnumMap<>(ResourceType.class);
        long treasury;
        void amount(ResourceType type, long change) {
            net.merge(type, change, Long::sum);
        }
    }

    private EconomyAuditReport() {}

    public static Report build(CatanSavedData data, TerritoryData selected) {
        if (selected.ownerNationId() == null) {
            return new Report(selected.id(), selected.name(),
                    List.of("UNOWNED: no production, upkeep, buildings or income execute.",
                            "Claim/activate this territory before economic testing."));
        }

        NationData liveNation = data.nation(selected.ownerNationId());
        if (liveNation == null) {
            return new Report(selected.id(), selected.name(),
                    List.of("Owner nation record unavailable; no production runs."));
        }

        // Simulate from a copied stockpile. This must never touch the live
        // treasury, live national stockpile, city buildings or persistence.
        NationData simulated = NationData.load(liveNation.save());
        List<TerritoryData> owned = data.territories().stream()
                .filter(t -> liveNation.id().equals(t.ownerNationId())).toList();
        Map<String, Ledger> ledgers = new LinkedHashMap<>();
        for (TerritoryData territory : owned)
            ledgers.put(territory.id(), new Ledger());

        // Phase 1: All owned Agriculture is produced before any upkeep.
        for (TerritoryData territory : owned) {
            if (!territory.rawResources().contains(ResourceType.AGRICULTURE)) continue;
            int amount = EconomyEngine.rawOutputPerCycle(territory, ResourceType.AGRICULTURE);
            simulated.addResource(ResourceType.AGRICULTURE, amount);
            Ledger ledger = ledgers.get(territory.id());
            ledger.amount(ResourceType.AGRICULTURE, amount);
            ledger.raw.add("Agriculture +" + amount + " /15m (" +
                    territory.rawResourceYieldPercent(ResourceType.AGRICULTURE) +
                    "% raw yield)");
        }

        // Phase 2: One Agriculture charge per non-food territory, not per raw
        // resource. Insufficient food partially scales every non-food output.
        for (TerritoryData territory : owned) {
            List<ResourceType> nonFood = territory.rawResources().stream()
                    .filter(type -> type != ResourceType.AGRICULTURE).toList();
            Ledger ledger = ledgers.get(territory.id());
            if (nonFood.isEmpty()) {
                ledger.raw.add("Agriculture upkeep: none (no non-food raw producer)");
                continue;
            }
            int required = EconomyBalance.agricultureUpkeepPerCycle(territory.producerLevel());
            long available = simulated.resource(ResourceType.AGRICULTURE);
            long consumed = Math.min(available, required);
            double ratio = available < required && required != 0
                    ? (double) available / required : 1.0;
            simulated.addResource(ResourceType.AGRICULTURE, -consumed);
            ledger.amount(ResourceType.AGRICULTURE, -consumed);
            ledger.raw.add("Agriculture upkeep -" + consumed + "/" + required +
                    " /15m" + (consumed < required ? " (SHORTAGE: raw yield reduced)" : ""));
            for (ResourceType type : nonFood) {
                int potential = EconomyEngine.rawOutputPerCycle(territory, type);
                int actual = Math.max(0, (int) Math.floor(potential * ratio));
                simulated.addResource(type, actual);
                ledger.amount(type, actual);
                ledger.raw.add(type.id() + " +" + actual +
                        " /15m (" + territory.rawResourceYieldPercent(type) +
                        "% raw yield" + (actual < potential ?
                        ", limited from " + potential + " by Agriculture" : "") + ")");
            }
        }

        // Phase 3: Processors across all cities run in tier order. The
        // ProcessorPreview is shared with the production engine itself.
        for (int tier = 1; tier <= 3; tier++) {
            for (TerritoryData territory : owned) {
                Ledger ledger = ledgers.get(territory.id());
                for (int i = 0; i < territory.buildings().size(); i++) {
                    BuildingInstance building = territory.buildings().get(i);
                    BuildingType type = building.type();
                    if (!type.isProcessor() || type.processingTier() != tier) continue;
                    EconomyEngine.ProcessorPreview preview =
                            EconomyEngine.processorPreview(simulated, territory, building);
                    String name = "[" + i + "] " + type.displayName() +
                            " L" + building.level() + " (tier " + tier + ")";
                    if (preview.status() == EconomyEngine.ProcessorStatus.PAUSED_TARGET) {
                        ledger.processors.add(name + ": PAUSED AT TARGET " +
                                preview.currentOutput() + "/" + preview.targetStock() +
                                "; inputs 0, output 0");
                        continue;
                    }
                    if (preview.status() == EconomyEngine.ProcessorStatus.WAITING_INPUTS) {
                        ledger.processors.add(name + ": WAITING INPUTS; needs " +
                                inputs(type, 1) + "; stock target " + preview.targetStock() +
                                "; output 0");
                        continue;
                    }
                    int batches = preview.runnableBatches();
                    ledger.processors.add(name + ": consumes " + inputs(type,batches) +
                            " -> +" + preview.outputPerCycle() + " " + type.output().id() +
                            "/15m; target " + preview.currentOutput() + "/" + preview.targetStock() +
                            (EconomyEngine.logisticsBonusPercent(territory) > 0
                                    ? " (logistics +" + EconomyEngine.logisticsBonusPercent(territory) + "%)"
                                    : ""));
                    for (Map.Entry<ResourceType,Integer> input : type.inputs().entrySet())
                        ledger.amount(input.getKey(), -(long)input.getValue()*batches);
                    ledger.amount(type.output(), preview.outputPerCycle());
                    simulated.consume(type.inputs(), batches);
                    simulated.addResource(type.output(), preview.outputPerCycle());
                }
            }
        }

        // Phase 4: Territorial and commercial money credits. No passive
        // material upkeep is charged by Logistics, Weapons or Vehicle factories.
        for (TerritoryData territory : owned) {
            Ledger ledger = ledgers.get(territory.id());
            ledger.treasury += EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE;
            ledger.cash.add("Territory income +$" +
                    EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE + "/15m");
            for (int i = 0; i < territory.buildings().size(); i++) {
                BuildingInstance building = territory.buildings().get(i);
                if (building.type() == BuildingType.COMMERCIAL_DISTRICT) {
                    long money = EconomyEngine.commercialIncomePerCycle(building.level());
                    ledger.treasury += money;
                    ledger.cash.add("[" + i + "] Commercial District L" + building.level() +
                            " +$" + money + "/15m");
                } else if (building.type() == BuildingType.LOGISTICS_CENTER) {
                    ledger.utilities.add("[" + i + "] Logistics Center L" + building.level() +
                            ": +" + EconomyEngine.logisticsBonusPercent(territory) +
                            "% to this city's processors; no upkeep or cash");
                } else if (!building.type().isProcessor()) {
                    ledger.utilities.add("[" + i + "] " + building.type().displayName() +
                            " L" + building.level() + ": procurement unlock; " +
                            "no automated production/upkeep");
                }
            }
        }

        Ledger selectedLedger = ledgers.get(selected.id());
        List<String> lines = new ArrayList<>();
        lines.add("City " + selected.cityLevel() + " | Raw producer L" +
                selected.producerLevel() + " | nation: " + liveNation.name());
        lines.add("NEXT 15m snapshot, not a charge. Shared national stock simulated " +
                "in actual phase/tier order.");
        lines.add("Total cash +$" + selectedLedger.treasury + "/15m (+$" +
                selectedLedger.treasury*4 + "/hr)");
        lines.add("---- RAW PRODUCTION AND UPKEEP ----");
        lines.addAll(selectedLedger.raw);
        lines.add("---- PROCESSORS AND INPUTS ----");
        if (selectedLedger.processors.isEmpty())
            lines.add("No processor factories purchased.");
        else lines.addAll(selectedLedger.processors);
        lines.add("---- CASH INCOME ----");
        lines.addAll(selectedLedger.cash);
        lines.add("---- PASSIVE / PROCUREMENT BUILDINGS ----");
        if (selectedLedger.utilities.isEmpty())
            lines.add("No other purchased buildings.");
        else lines.addAll(selectedLedger.utilities);
        lines.add("---- NET CONTRIBUTION TO NATIONAL STOCKPILE /15m ----");
        int changes=0;
        for (ResourceType type : ResourceType.values()) {
            long net=selectedLedger.net.getOrDefault(type,0L);
            if(net==0) continue;
            changes++;
            lines.add(type.id() + ": " + signed(net) + " /15m (hourly x4 only " +
                    "if supply/targets stay steady)");
        }
        if(changes==0) lines.add("No net material change in selected territory.");
        lines.add("Nation live treasury: $" + liveNation.treasury() +
                " (no money spent by this audit)");
        lines.add("PREVIEW ONLY: other owned cities also use this stockpile. " +
                "Use /catan debug cycle only when deliberately testing a real cycle.");
        return new Report(selected.id().toLowerCase(Locale.ROOT),
                selected.name(), List.copyOf(lines));
    }

    private static String signed(long amount) {
        return amount >= 0 ? "+" + amount : Long.toString(amount);
    }

    private static String inputs(BuildingType type,int batches) {
        List<String> parts = new ArrayList<>();
        type.inputs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> parts.add((long)e.getValue()*batches + " " + e.getKey().id()));
        return parts.isEmpty() ? "none" : String.join(" + ",parts);
    }
}
