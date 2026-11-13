# Vehicle Fuel 49 arithmetic

`bash tools/vehicle49/test-policy.sh` compiles the pure quote policy with Java 17
and checks prices, clipping, invalid inputs, overflow and 10,000 sequential
partial refill operations. These standalone tests do not qualify native
delivery, charging, entity persistence, restart recovery or a player counter.

Provisional tariffs are 32 national Fuel for a pickup's full native tank and
80 for a LAV's full native tank. A quarter is therefore 8 and 20 Fuel, matching
the Fuel already included in those purchase prices. These are tuning defaults,
not a measured range or refinery-balance result. Tariffs are immutable versioned
values; a changed tariff requires a new catalog and explicit meter migration.

National stock uses whole Fuel. Each confirmed refill debits the ceiling of
its unpaid rational cost and retains the unused fraction as prepaid credit.
The meter numerator has native capacity as denominator, so no floating-point
rounding occurs. For example, a one-unit top-up may debit 1 Fuel, but its unused
credit pays for subsequent top-ups. UI must disclose that carry-forward credit;
it must not describe the whole debit as fuel consumed by that one top-up.
Splitting a refill cannot increase lifetime charges. Fractional credit must
persist per nation/vehicle alongside payment receipts; it is not transferable
between nations/vehicles, discarded on full tanks, or recreated on retry.

The quote is not authorization. Before execution the integration must recheck
the exact vehicle, nation, capacity, tariff, current energy, meter, factory area,
stock and operation receipt. A quote with zero debit may still deliver energy
when paid credit covers it. A full tank produces a no-op preserving credit.
Native mutation must not race movement or another charging source. Both debit
and native energy mutation require durable recovery and duplicate prevention.

New-catalog deliveries start at capacity/4, rounded down to a native integer.
At the pinned 5,000,000 capacity this is exactly 25%. Existing historical
deliveries and recovery records must not receive retroactive free energy.
