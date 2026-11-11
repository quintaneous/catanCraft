# 0.1.44 — TaCZ-only handheld weapon correction

Status: **validated development checkpoint, 220/220 native phases passed, 213 distinct scenarios, zero failures**. This is not owner installed-pack acceptance or an intermediate installation request.

The owner's correction continues delivered043 rather than restarting integration. [Contract](TACZ_ONLY_044_CONTRACT.md), [active catalog and exact provisional pools](TACZ_ONLY_044_CATALOG.md), and [native policy audit](TACZ_ONLY_044_NATIVE_POLICY.md) describe the changed behavior. Current shop stock is52 TaCZ guns and17 ammo types; three additional TaCZ guns remain reward-only. SBW vehicles and genuine mounted weapons remain enabled. SBW handheld guns and handheld explosive acquisition/use are disabled within the documented native boundaries. Existing records/items remain intact; disabled undelivered SBW claims stay held, without reroll/substitution/refund. TaCZ prices, identities and original revisions remain unchanged.

## Exact attempts

1. Source `8c945338df7764cdd01b44ef4222f885e905de0f`, tree `42cd3aa5d5cab13a77cf57005334d5af1c31c5f0`, [run42938314877](https://github.com/quintaneous/catanCraft/actions/runs/42938314877): compile failure in the new native test, `LootContext.Builder.create(Optional.empty())` did not match Minecraft1.20.1's nullable `ResourceLocation` API. All17 matrix builds failed; zero GameTests ran. [All17 failed job logs](evidence/tacz_only44_run1_compile_failed.zip),183,354bytes, SHA-256 `fb6394f99dd14971643e7611b7920d2b2f8c885fba08480c578a1926e837f288`, are retained. Corrected only the test call to `create(null)` and workflow trigger comment; no policy assertion removed.
2. Corrected source `0b1be0f76339a2fbcaaaebabda27edd7263f27a4`, tree `cab02ff62fe523f0234720496517f2393278ca00`, [run42938632615](https://github.com/quintaneous/catanCraft/actions/runs/42938632615): SUCCESS,220/220 phases /213 distinct scenarios /0 failures. The downloaded combined archive13819886742 (8,919,962bytes, SHA-256 `6db6a730de5aeb56a52836aec8b18626a3afa27e5cc16ba64134bb8b7d893764`) passed the source-matched verifier. This run proved native projectile creation and ammo-counter debit, but review identified that the vehicle fixture should also prove physical inventory shell debit. No production policy changed for this strengthening.
3. Final test-strengthening source `d698f1f75616370979e04c861cb4f8ec59e175d2`, tree `f855da8286f7b933d967b6b6f5a42b0e9600f0ec`, [run42939239598](https://github.com/quintaneous/catanCraft/actions/runs/42939239598): SUCCESS, **220/220 phases /213 distinct scenarios /0 failures**. The mounted LAV test now loads eight real native shells and requires seven after a real shot; verifier requires all seven policy markers and the physical debit.

## Final artifact and independent verification

Exact compiled source: `d698f1f75616370979e04c861cb4f8ec59e175d2` (tree `f855da8286f7b933d967b6b6f5a42b0e9600f0ec`). Later documentation commits do not change this source/JAR relationship.

- JAR: `catancraft-0.1.44-tacz-only.jar`, 3,593,848 bytes, SHA-256 `c2d0dda48f31f1a04a3d853d68470b80956920c7c4569da6bbbf73a2f5835ca1`.
- [Combined source/evidence artifact13819399809](https://github.com/quintaneous/catanCraft/actions/runs/42939239598/artifacts/13819399809): 8,920,641 bytes, SHA-256 `2e2a778374238324c6db510cdb720703223220b7cbfff2eb64241e16552bddf2`.
- [Final machine verification](evidence/tacz_only44_validation.json) and [earlier passing run2 verification](evidence/tacz_only44_run2_validation.json).

The downloaded artifact passed `tools/shop44/verify_evidence.py`: exact JAR/source and manifest hashes; all476 committed source files and289 manifest records; every phase log and expected count; unchanged native purchase fixtures/runtime manifests; independent-JVM TaCZ once-only claim and historical SBW held-claim proofs; original purchase/reward recovery and terrain policy evidence. All596 unaffected pre-existing compiled classes and construction assets match043. Only declared source owners, new classes/templates, release metadata and registered mixins differ.

The matrix retains207 earlier phases and adds six active-catalog/pool cases plus seven native handheld-policy cases. The native tests exercise all45 registered SBW gun items, five packet handlers, two release implementations, final loot/recipe filters, both-hand/creative/pickup controls and preserved inert legacy NBT. The genuine mounted LAV test fires a real projectile and consumes exactly one physical native shell (8→7); a held gun supplied with that vehicle and detached vehicle data both refuse. The verifier reads actual packaged class annotations to require both exact development and production recipe selectors, remap=false and require=1. This checks packaged selector bytes, not a separate authenticated-client launch.

Historical042/043 SBW low-level qualification fixtures remain for record compatibility only. They do not authorize current sales, claims or handheld firing. New production equipment pools contain only TaCZ. Old SBW claim reservation bytes, original winner, credited source and native item metadata survive restart with zero delivered items, extra charge, extra credit or recovery saves. No historical outcome is rerolled.


## Limits and owner test

No live world or installed mods were accessed or changed. Accepted City A North Reinforced / East, South and West Basic, base readiness and033/034 recovery remain preserved. No reset, manual import, multiplayer, repeated accepted purchases or Blender work. Keep the [one combined owner acceptance plan](COMBINED_SHOP_ACCEPTANCE_PLAN.md); no intermediate installation request. Published-runtime validation does not certify the actual installed/add-on pack or connected client. Prices/odds remain provisional. SEC-01 remains **UNRESOLVED / HIGH PRIORITY** until scoped installed-pack acceptance. Vehicle bays and playable siege/gate integration retain their existing separate contracts and defender-win requirement.
