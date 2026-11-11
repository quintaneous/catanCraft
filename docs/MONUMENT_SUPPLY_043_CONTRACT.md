# 0.1.43 monument supply rewards and terrain configuration contract

Continues validated 0.1.42 source `9b3eb646a494348632e4b5c6a692c57f9a9f2f16` and documentation head `de492b863cfa5aeeafb98743f92a5b504cdccf82` on `chatgpt/combined-shop-acceptance`. MON-01 / ECO-03 implementation; SEC-01 remains **UNRESOLVED / HIGH PRIORITY**. Build/native validation is pending for this source commit; this document is a contract, not a pass report.

## Player behavior

New monument activations freeze one hidden weighted outcome from the monument's supply pool. Capturing it credits national money/materials exactly once through the existing 041 reward journal. Equipment remains an earned claim for the winning nation. The current nation leader opens **Monument supply rewards** at the existing authorized City A starter/factory weapon counter, reviews a specific gun or ammunition entitlement, and confirms its free delivery. Equipment prizes do not require a purchased factory tier. Gun-and-ammunition bundles have separate claim entries, each with its own receipt.

The first durable reservation binds a claim to that leader's original player identity. A replacement leader cannot collect an already reserved claim. Recovery uses the original actor and original counter/facility contract. Full inventory, unavailable provider, changed native configuration, unsupported historic entitlement, conflicting purchase/reward recovery or ambiguous persistence blocks delivery without substituting an item, rerolling a reward or charging money. Items already proven delivered remain completed after consumption; retry cannot grant another copy.

Claims save and read back the actual native player inventory and matching receipt before completion. They do not debit or rewrite nation accounting. An ambiguous delivery without a matching persisted player receipt is held for investigation, not automatically redelivered. No automatic reassignment, free fallback item or unapproved vehicle spawn exists.

## Pools and compatibility

[Full provisional pools and exact odds](MONUMENT_SUPPLY_043_POOLS.md) cover all 76 ordinary guns, 21 ammunition identities and three additional qualified drop-only guns: SCAR-H, SCAR Mk20 and Golden Desert Eagle. Ordinary shop stock stays at 97 offers; these three rare guns are excluded from purchases. All guns are unloaded; associated ammunition is a separate claim. Native .338 stacks contain 30; other qualified ammunition bundles contain 32.

Industrial/Refinery pools assign 75% to national supplies, 20% to ammunition, 4.9% to ordinary gun bundles and 0.1% to their rare gun bundle. Military assigns 40%, 30%, 29.5% and 0.5%. These are **provisional playtest odds and prices**, with no combat/economy calibration claimed. Other DROP/HOLD/conditional guns and all vehicles remain outside active reward pools. Vehicles await reviewed fixed deployment bays.

Existing OPEN/CAPTURED/SETTLED 041 outcomes and their winners/credits remain frozen. Already-active pre-journal monuments retain the old compatibility payout when their activation is first journaled. Only new activations use 043 supply pools. Existing 040/042 shop receipts, agreed prices, native item identity and recovery remain supported. Authentic controlled 042 TaCZ/SBW frozen-intent fixtures are retained with provenance for direct original-supply rechecks; these contain no owner-world data.

## Terrain configuration

[Configuration audit](TERRAIN_CONFIG_043_AUDIT.md) describes the public Forge lifecycle overlay. Ten native terrain-damage getters (three TaCZ, seven SBW) remain false without periodic autosave writes to watched TOML. Authored file values may remain true; the effective getters consumed by native gameplay are false. Unrelated settings retain their normal setters/save/reload behavior. Unsupported native config contracts fail visibly and procurement holds while protection is unavailable. Actual load, setters, external FileWatcher reload and byte-exact file restoration require the native 043 suite; a source review alone does not resolve the observed 042 parser race.

## Validation and owner boundary

The planned exact-source workflow contains 207 phase executions / 200 distinct scenarios, retaining all 178 validated 042 phases. New coverage includes eight pool/compatibility cases, ten claim failure/authority cases, four real native claim cases, two real native claim restart scenarios across separate JVMs (four phases), and three combined-provider config lifecycle cases. Build output, original logs, manifests, native journals/playerdata, terrain proofs and exact source/JAR hashes must be independently verified before a checkpoint is marked validated. Failures are retained with the corrective source.

This remains part of the combined playable release. No intermediate owner installation or repeated accepted purchase checklist is requested. Connected-client interaction, actual installed/add-on matching, balancing, fixed vehicle bays, player-facing siege integration and installed-pack crowbar acceptance remain separate. Preserve accepted City A North Reinforced and East/South/West Basic, purchase recovery and readiness. No live world, installed mod, reset, import, multiplayer test or Blender task is authorized by this checkpoint.
