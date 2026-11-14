# All-city weapon counters — development checkpoint

ECO-03 / INT-01 / INT-03, 2026-11-14 UTC. Source:
`55d1b5afb259e9f60858af4a414695675e92bc50` on
`chatgpt/combined-shop-acceptance`. This is implementation and scoped validation,
**not the combined server-wide owner release**. Vehicle facilities, wall providers
and complete fresh-server qualification remain outstanding. Do not install a new
JAR, move to A, reset a world or repeat accepted purchases for this checkpoint.

## Implemented behavior

Weapon and management counters now resolve the actual clicked site across all
16 configured cities and three ordinary industry slots. Resolution requires one
unambiguous site in the current dimension. The selected geometry must match the
hash-pinned reviewed map; factory coordinates come from the production placement
transform, including the southern plot-4 flip. Recognition of a reviewed counter
does not authorize spending and still catches missing/replaced counter blocks.

Each operation uses the resolved city's ownership, Town Hall, placement records,
paid-construction recovery and siege/restoration history. Existing connected/alive
Survival-or-Adventure actor, nation-leader, reach/sightline, loaded-block, completed
source/tier, factory uniqueness and native sparse-context checks remain. Ordinary
members retain management-dashboard routing at every management lectern. The
interaction handler has no earlier A-only gate; global held-item protection rules
still apply. Actual connected-client click-to-menu behavior remains unverified.

City A keeps its exact 16-field schema-1 facility compound and
`city_a_weapon_counter40_v1` revision. Other cities use
`all_city_weapon_counter51_v1` and one map-fingerprint field. Historical prices,
items, receipt formats, already rolled rewards and allowed factory-upgrade recovery
are unchanged. Strict current-quote level checks remain distinct from paid recovery.
Old starter source-token changes retain their existing refusal policy.

Pending purchases remain actor/world-wide and continue blocking another purchase
at a different site. Recovery now explicitly requires the same world, city,
building, plot, counter and anchor before full historical revalidation. Menus show
the original city/building for unfinished orders and the actor's reserved reward
claims. This is a location hint, not reallocation or migration. TaCZ-only stock,
reward pools/prices and SBW handheld restrictions remain unchanged.

## Executed scope and evidence

The new namespace has nine native test functions. Their internal cases are
coverage dimensions, **not extra distinct GameTests**:

| Coverage | Cases inside the nine tests |
| --- | ---: |
| Management / factory-site resolution | 16 / 48 |
| Counter/context positions versus actual placement plans | 240 |
| Independently reconstructed legacy A identity compounds | 16 |
| Distinct city/site identities | 64 |
| Factory exact-quote versus historical-recovery match policies | 48 |
| Starter TH1–TH5 inspections, including both prestige revisions | 112 |
| Factory native-context inspections, all slots and levels | 240 |
| Ownership/source/plot/block/entity-context refusals | 384 |
| Recognized sites still refusing FakePlayer shopping | 64 |

Construction inspection tests use native blocks with detached ledgers in a
separate marked flat world. They verify read-only block/entity/ledger behavior
and restore their fixture blocks. This is not a complete authored-terrain survey,
funded or unfunded campaign, native connected-player purchase, authenticated
client acceptance or owner visual acceptance.

**112/112 native phase executions passed, zero failures**, in eleven sequential
server processes on the exact source above, using Java 17.0.21+9, Gradle 8.8,
Minecraft 1.20.1 and Forge 47.4.10. Every namespace began in a new labeled run;
only each restart pair shared its own seed/verify world.

| Native namespace | Passed / executed |
| --- | ---: |
| `catancraft_shop51` | 9 / 9 |
| `catancraft_shop40` | 35 / 35 |
| `catancraft_rewards41` | 32 / 32 |
| `catancraft_claims43` | 10 / 10 |
| `catancraft_supply43` | 8 / 8 |
| `catancraft_active44` | 6 / 6 |
| `catancraft_stock42` | 8 / 8 |
| `catancraft_shop40_restart`, seed + verify | 2 / 2 |
| `catancraft_rewards41_restart`, seed + verify | 2 / 2 |

The retained restart suites verify native saved transaction/claim recovery across
separate server processes. They use synthetic facility bindings and do not prove
positive authenticated all-city menu interaction. Earlier full-pack, Python and
other historical suite results were not rerun or added to this count.

Four additional preparation checks passed by refusing an unsafe label, a label
without controlled mode, a symlinked path and an unknown save. These expected
nonzero exits are separate from GameTests. Existing sentinel bytes remained
unchanged; the unknown save gained no marker/settings and no server was launched.

The full offline build completed successfully in 19 seconds. Gradle `test
NO-SOURCE` is not a test pass. The development artifact still has the prior
`catancraft-0.1.50-vehicle-service.jar` filename, **is not the delivered 050 JAR**
and is not offered for installation. Its 3,970,480 bytes hash to
`00228dd09af4b8b103cf2cfc3d6e57ef1dfdfd533bdf0ce4c3a8b45a5b670a67`.

[Machine evidence](evidence/shop51_validation.json) records commands, source,
counts, log hashes and limitations. [Raw logs and runner snapshot](evidence/shop51_validation_evidence.zip)
include an internally verified file inventory; archive SHA-256:
`5c1ae87ff7140c7eb9e21dc182acd85d68d80ac620ef9a68b5d870c80320378e`.
All `src/main/resources` bytes match delivered 050. The original 050 owner ZIP
is unchanged, and both 033 source/documentation commits remain ancestors.

## Fresh native regression runs

`build.gradle` accepts optional `-PcontrolledRunLabel=NAME` only with
`-PcontrolledGameTests=true`. Names are validated; a label chooses
`build/validation-NAME/build/controlled-NAMESPACE-run`, preserving the exact
basename/parent guards in retained fixtures. Existing default paths are unchanged.
Choose a new label for another fresh attempt; seed/verify restart phases deliberately
share their own namespace directory within one label. No old run is moved/reset.

Preparation refuses symlink paths, changed markers/settings/schematics, unknown
existing saves, and a runtime init script overriding the selected labeled path.
Some older combined-mod init scripts still override paths and must be adapted
before using labels; refusal is intentional. No live world or installed mods are
read/copied. This supplies real isolated regression directories, while the
[authored progression ladder](FRESH_PROGRESSION_BASELINE.md) still needs native
campaign adapters and a full matching owner-server package.

## Remaining release gates

- Complete the vehicle and wall retrofit under the same all-city requirement.
- Qualify the reusable authored server's normal initialization/settings and full
  current mod/content/runtime package; run actual interleaved progression and
  existing-paid-save compatibility coverage.
- Validate connected-client menus and local purchases/claims in the combined
  solo checklist, beginning in the owner's City D. These tests do not establish
  installed add-on behavior or balanced prices/rarities.

Preserve accepted A North Reinforced / East–South–West Basic, mixed connections,
readiness and 034 acceptance. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY**.
No Blender task, multiplayer test, reset, manual import or owner retest was added.
