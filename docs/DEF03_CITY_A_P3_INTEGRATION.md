# DEF-03: approved City A P3 technical integration

Latest execution and remaining gates: [2026-10-19 validation](DEF03_VALIDATION_20261019.md).
The chronology below preserves earlier checkpoint results.

Owner appearance/footprint approval is recorded in issue #10 comment
[6256056792](https://github.com/quintaneous/catanCraft/issues/10#issuecomment-6256056792)
and confirmed directly in this task. All four sides are approved for isolated
City A testing. This supersedes the package's earlier pending-review wording;
its provenance and technical constraints remain intact. No repeated approval is
needed for the unchanged candidate.

Original P3 archive: 11,392,639 bytes, SHA-256
`96278c6ebfd1aef8454ebff9e51c0c56a48a273349d87928bf84e73ced039095`.
Extracted separately under `/workspace/catancraft-p3-inputs-20261018/` after
checking paths, symlinks, duplicate entries, size limits and ZIP CRC. No source
snapshot was copied over the existing checkout. Remotes were rechecked:
`codex/deliver-0.1.28` remains `3250ede71bff2b289cda450b0bb636e890cf6514`;
`v0.1-foundation` remains `d09f3544babeb2602261ddc2aeb90c34b8ff966e`.
Existing local commits through `699b4d0` were retained. Immutable asset checkpoint
`0c6dff4` is saved, followed by grouped candidate `d56c036` and fixed-cover v2
checkpoint `e5f60d1`. Focused results at e5f60d1 are natural support 6/6 and
full-city grouped transactions 3/3; consolidated regression is running. No
playable release is claimed.

Independent input audit passed 215 P3 member hashes, ten source provenance
files, nested P2 ZIP CRC and 180 member hashes. Both frozen North schematics
match their original bytes. The inspected supplied validators ran in a separate
audit copy, preserving the original input directory:

```
python3 source/validate_p3.py
python3 source/audit_delivery.py
```

Both exited zero: six exports, twelve supplied upgrade modes, twenty-four seam
coupons, three supplied assemblies, sixteen corner transitions and six
byte-reproducible exports. Rewritten contract outputs matched the originals.
These are independently executed offline checks, not native purchase acceptance.
Logs and `AUDIT_RESULT.json` are under
`/workspace/catancraft-p3-audit-20261018/`.

The immutable adapter resource includes all eight side/tier assets, original
source guards, exclusive ownership unions, twelve supplied P3 upgrade modes,
four derived North modes, corner overlays and source pins. North open modes are
derived by applying only the designated portal overlays to the frozen basic and
reinforced arrays, then taking their exact difference. They change 4,539 cells
closed, 4,533 with either portal open and 4,527 with both open; all 5,576 ownership
union cells remain guarded, including the air in open terminals. The three
original entrance pier caps remain untouched.

Current reservation reconciliation checks nineteen registered protected building
envelopes, the protected city core, roads and their two-cell margin, and the full
rear resource corridor X[-770..-730]. Legacy socket overlaps remain explicit
conflicts requiring retained ownership/history checks; this work does not adopt
or convert legacy defenses. No new footprint-wide clearing is permitted.

A concrete compatibility issue is accounted for in the new assembly composer:
North's historical read context includes 52 East-owned and 52 West-owned cells
at Z=-833/-832. Valid neighbor purchases replace their baseline air. A new
composed contract must use the proven neighboring revision/connection state at
these cells. Existing sealed North histories must not be silently rewritten or
made permissive. Geometry composition alone does not prove durable lineage.

The coordinated backend is now implemented in `SectorCityAP3Group.java`, with
service/transaction binding, exclusive seam ownership, retained-history lineage,
original payment recovery and terminal verification. One side operation includes
its newly required seam edits; it is not a loop of module or corner purchases.
A seam has twelve changed cells with two exclusive owners, coordinated by North
at NW/NE or South at SW/SE. No second corner price, neighbor ownership transfer,
fabricated sector level or rewritten historical journal is introduced. Upgrades
retain the open mode; pending operations reserve the city. The new full-city
native suite covers mixed-level purchases/upgrades and historical duplicate
handling, interrupted final-seam recovery/rollback, and late conflicts/missing
retained ancestry. Round5 completed **3 executed / 3 passed / 0 failed** at e5f60d1. The suite
performed all eight basic purchases/reinforced upgrades and opened every corner,
including North's upgrade with both portals already open. Combined charges were
exactly $22,000, Wood60, Stone120, Concrete200 and Steel60. It checked all saved
levels after SavedData reload, historical status/duplicate behavior, interrupted
East placement at its final foreign-owned seam followed by reload/resume/current
rollback preserving paid North, refusal of obsolete North rollback, and refusal
of a South quote when the retained North grandparent history was missing.
SavedData reload does not establish a full packaged Minecraft process restart.
Production resolution still routes through gated `Group.resolve`; purchases
remain disabled because runtime performance and package gates remain open.

The bounded natural-support policy records an explicit covered-original-grass
whitelist and durable exposure/prior-paid proof. It preserves strict behavior
for older journals and unrelated terrain. Round2 retained evidence and completed
round3/round5 reruns are distinguished below:

| Native suite | Actual result | Next step |
| --- | --- | --- |
| City A P3 geometry | 5 executed / 5 passed / 0 failed | Retain as geometry evidence, not full purchase acceptance. |
| Reservation verifier | 4 executed / 4 passed / 0 failed | Retain scoped evidence. |
| Sector service | 14 executed / 14 passed / 0 failed | Recheck changes affecting shared service behavior in combined validation. |
| Sector contracts | 18 executed / 18 passed / 0 failed | Retain scoped evidence. |
| Grouped support proof | 2 executed / 2 passed / 0 failed | Retain scoped evidence. |
| Natural support, round5 at e5f60d1 | 6 executed / 6 passed / 0 failed | Includes fixed-cover v2; earlier failures remain preserved. |
| Connection primitive, round3 | 4 executed / 4 passed / 0 failed | Prior fixture failure remains preserved. |
| Full-size North, round3 | 4 executed / 4 passed / 0 failed | Safety assertions passed; synchronous performance remains gated. |
| Full-city grouped backend, round5 at e5f60d1 | 3 executed / 3 passed / 0 failed | Same-source evidence is reused in consolidated validation; not repeated or relabeled as another run. |

The earlier full Python suite passed **60/60**, including eight P3 checks, and
compilation passed. Current consolidated-e5 validation is executing **169 other
native tests, 60 Python tests and the build**, reusing the three same-source
full-city passes for a **172-native target**. Those are planned counts; no
completed broad result is claimed yet.

Logs under `/workspace/integration-evidence/p3-native/` retain round2 failures,
`NORTH_STOPPED.txt`, round3 passes, round4's 3 executed / 1 passed / 2 failed, and
round5 closure. Round4 exposed natural grass decay beneath existing immutable
paving. `covered-grass-to-dirt-v2` journals and verifies that exact fixed cover;
NONE/v1 history semantics remain unchanged. Native fixed-cover and full-city
reruns now pass without permitting arbitrary source terrain changes.

The earlier 93-second measurement covered a synchronous multioperation sequence,
not one purchase. More focused complete quote/purchase timings are approximately
**23–35 seconds**, which remains a responsiveness gate despite passing safety
assertions. No failed assertion was disabled. Final-source regression, latency,
actual authored-world packaging and a full packaged process restart remain
separate from approved artwork and focused transaction tests.

Use the one prospective [fresh-world solo checklist](CITY_A_FRESH_SOLO_CHECKLIST.md)
when the matching real package and runtime gates are ready; it is not an instruction
to install this candidate into developed City A.

DEF-04 PROVISIONAL PLAYTEST PRICING remains Basic $1,500 + Wood 15 + Stone 30;
Reinforced adds $4,000 + Concrete 50 + Steel 15. Prices are whole-side stockpile
charges and exclude equipment, ammunition and functional gates. SEC-01 crowbar
protection and SEC-02 production replay remain unresolved as previously recorded.

INT-02 also remains concretely blocked on the clean authored world transfer:
eighteen available unique ZIP indexes, including nested archives, contain no
`level.dat` or `.mca` world data. P3 supplies bounded authoring context, not a
playable world. Existing native test worlds are test evidence, not a replacement
for that missing input. Do not copy the owner's developed City A or invent a
matching authored-world release. Retain the existing package assembler and
checkpoint code/tests independently while that input is missing.
