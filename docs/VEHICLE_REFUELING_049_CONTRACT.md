# ECO-02 / ECO-03 — national Fuel refueling

Owner approved factory refueling using national Fuel, percentage gauges, Add 25% and Fill actions, vehicle-specific costs, and 25% included in new purchases. No batteries or native energy arithmetic should appear in the player flow.

## Provisional economic contract

| Vehicle | Full tank | Empty to 25% | New purchase Fuel already charged |
|---|---:|---:|---:|
| Pickup | 32 Fuel | 8 Fuel | 8 Fuel |
| LAV-150 | 80 Fuel | 20 Fuel | 20 Fuel |

These are versioned initial calibration values, not range-balanced prices. The new delivery catalog must initialize a newly created vehicle at one quarter of its verified native capacity before its first durable entity save. Historical purchase catalogs and recovery records stay unchanged; old vehicles receive no retroactive grant. The policy helper alone does not change delivery behavior.

Refills are proportional to actual energy added. National resources are integers: the first fractional charge rounds upward and retains the unused fraction as prepaid credit. Persist that credit with the debit, scoped to the exact nation and vehicle. Repeated small top-offs therefore do not repeatedly round up. The meter binds capacity, tariff version, vehicle offer and full-tank price; a mismatch holds for explicit migration rather than erasing credit. Credit is not redeemable as cash or transferable to another vehicle/nation.

## Player flow and authority required before activation

An authenticated nation leader interacts with the recognized, completed Vehicle Factory counter. The server resolves the nation's stationary, unoccupied vehicle in the surveyed service area. If several qualify, explicitly select one; never guess. Display the vehicle, current percentage, proposed percentage, whole Fuel debit and any credit used/retained. Confirm rechecks the same vehicle, nation, factory source/revision, location, ownership, capacity, energy, meter, available Fuel and quote expiry. A changed observation requires a new quote before any debit.

Only one operation may reserve a vehicle at a time. A pending operation must prevent driving, native charging and other energy mutations until safely completed or held for recovery. Do not implement that lock by disabling unrelated protection or teleporting a vehicle. These checks require an actual native adapter, not optimistic booleans supplied by a client.

## Persistence contract

Refueling crosses the national ledger and the native entity save. The protocol records durable attempted boundaries before either mutation. The Fuel debit, prepaid meter transition and exact immutable operation receipt must share one authoritative ledger save. Energy addition and its exact receipt must share one native vehicle save. Read back both. A resumed ambiguous attempt is held, never blindly repeated or refunded.

An exact durable energy receipt proves that the addition occurred even if fuel was subsequently consumed. Recovery must never restore a prior target energy value. Completed operations still require exact receipts; absence/conflict does not become success merely because a journal says complete. Original quotes and tariffs survive recovery unchanged.

## Implementation boundary

This checkpoint implements the pure quote/accounting policy and adapter-independent transaction protocol, with standalone Java tests. It does **not** register a factory counter, mutate native energy, grant starting fuel, persist a production meter, or provide a playable refueling feature. Native ledger/entity persistence, service-area authority, reservation enforcement, menu wiring and new delivery-catalog integration remain required. Full driving-route qualification is still held from 048. No new JAR release is claimed by these policy tests.

Preserve accepted City A and 033/034 purchase recovery, TaCZ-only handhelds and one combined owner build/checklist. No live-world/mod changes or intermediate install. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack acceptance.
