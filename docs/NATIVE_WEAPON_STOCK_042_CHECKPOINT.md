# ECO-03 — native stock042 validation checkpoint

The first source `111532c48e8bb519a32232b5b0d0d26900bd87bd` built, but [run 42912799018](https://github.com/quintaneous/catanCraft/actions/runs/42912799018) failed: **176 executed / 172 passed / 4 failed**; the two TaCZ restart phases did not run after the provider suite failed. No validated042 pass is claimed yet. The previous validated source is 041 `880acefc0ea6c43de100afbedec47fdc54e4930f`.

The failures exposed an oversized TaCZ .338 offer (32 requested, native limit30; also failed combined-provider coverage), the legacy four-row catalog display regression, and unstable SBW fingerprints across JVMs. Corrections explicitly price/display .338 as30 rounds, restore the four legacy discovery rows, and canonicalize semantically unordered native SBW perk membership/Set values. The new restart fixture also now expects the existing recovery core's one verified ledger save on first completion; no extra charge/item/player save and no completed-retry saves are permitted. The production recovery algorithm is unchanged.

All first-run files remain in artifact13808232809, SHA256 `3997b73125ead0ba45e8868fddda5686d77b6ae08f890297e6cc05e5e0e2c546`. Its ZIP CRC, all154 manifest entries and all434 source files were independently verified. [Machine record](evidence/native_weapon_stock42_run1.json).

The combined log also retained a pretest Forge/NightConfig duplicate `[ammo]` watcher exception immediately after existing terrain-protection configuration writes. It is not erased or described as a clean startup. Native shop qualification reads effective configuration; startup config write coordination is a separate unresolved observation to investigate before combined owner delivery.

Read [contract and limits](NATIVE_WEAPON_STOCK_042_CONTRACT.md) and [provisional prices](WEAPON_STOCK_042_PRICES.md). Corrected-source validation follows. Preserve one combined owner release/checklist; no intermediate installation or actual-pack acceptance is claimed. SEC-01 remains UNRESOLVED / HIGH PRIORITY.
