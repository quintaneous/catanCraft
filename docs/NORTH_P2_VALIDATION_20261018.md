# North P2 implementation and validation — 2026-10-18

Runtime checkpoint: `7aafc34` on existing `codex/combined-integration`.
No reset, merge, push, deployment, live-world or installed-mod changes.
Prior producer/house/entity integration and archives are retained.

## Input and changes

P3 brief SHA-256:
`d21b860963e702d73e2df9aeccf5f99b693f2bcf2f0685eea52849c92e243f10`.
Original nested P2 archive SHA-256:
`039ff60335cf3d80f537adb689f11a833e3a15b7a2fcf54aeb6490bb2ae0b996`.
All 180 member hashes verified; runtime exporter reproduction matches
`685f117ad9ad12c95964b2393e94517f9647b2217028a12aee1fac5136a5a71b`.

- DEF-03: immutable P2 exporter/resource/loader and four full-size native tests;
  exact 5,013 fresh changes and 4,539 upgrade changes, protected roads/supports,
  north-only NW/NE ownership, 17 legacy socket conflict checks and narrow
  canonical extension permission. East/South/West are not inferred or enabled.
- DEF-02: partial incident-corner contracts, no loss of already recorded corner
  ownership, exact-byte reservation metadata cache and per-verification-pass
  loaded-chunk lookup. All declared states/NBT are still read before each write.
- ECO-03: existing procurement/factory/interaction and actual SBW vehicle-size
  requirements audit. No counter/bay coordinates or storefront implementation.
- Instructions, tracked status, native runner counts and prospective checklist
  updated. No dependency, gameplay price or version changes.

Detailed changed paths are the 18 files in `git show --stat 7aafc34`.

## Commands and preserved evidence

```bash
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
gradle build --offline --no-daemon --max-workers=4
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_*demo.py' -v
gradle runGameTestServer -PgameTestNamespace=catancraft_north_p2 --offline --no-daemon --max-workers=4
```

Full retained regression commands, exact commit/worktree and tool versions:
`/workspace/integration-evidence/north-p2-7aafc34/run.sh` and adjacent files.
Each suite stages `tests/fixtures/thall4.schem` and `thall5.schem` under its own
run directory's `config/catancraft/schematics/`; existing worlds are moved to
`build/test-world-history/`, never overwritten. The SBW namespace additionally
uses `--init-script tools/validate_sbw.gradle`, with independently pinned official
0.8.9.2 artifacts, not the owner's installed mods.

Focused P2 evidence is in `/workspace/integration-evidence/north-p2-focused/`:

| Log | Executed | Passed | Failed | Interpretation |
| --- | ---: | ---: | ---: | --- |
| `native.log` | 4 | 0 | 4 | Fixture book overlapped a protected road read; corrected fixture location. |
| `native-retry.log` | 4 | 2 | 2 | Test hardcoded initial treasury incorrectly; assertions now use actual starting ledger. |
| `catancraft_north_p2-optimized.log` | 4 | 3 | 1 | Delayed ground-state drift. |
| `native-drift-diagnostic.log` | 4 | 3 | 1 | Exact diagnosis: grass→dirt below wall; book NBT equal. |
| `catancraft_reservation_verifier-optimized.log` | 3 | 3 | 0 | Exact bytes, corruption, new pending files, phase/world changes, eviction/symlinks. |

The diagnostic P2 run is the checkpoint's same fixture/runtime logic before the
explicit production refusal and documentation were added. It is not relabeled as
a clean checkpoint run. The known failure was not repeatedly rerun to obtain a
pass. The normal `tools/validate_combined.sh` still includes that namespace and
assertion; a complete current run is expected to remain red until the code blocker
is fixed. Native-only fixture prices are 100/200 money and 3/6 stone, not approved
production prices.

## Readiness

**NOT RELEASE READY.** P2 production is explicitly disabled for the natural
support blocker, even if final prices are later supplied. See
[DEF03_NATURAL_SUPPORT_BLOCKER.md](DEF03_NATURAL_SUPPORT_BLOCKER.md) for the
versioned-policy proposal and required native coverage. Large synchronous
operations also produced substantial server lag; latency remains unaccepted.

Subsequent owner authorization supplies DEF-04 provisional prices; see the latest
INTEGRATION_STATUS entry for its separate implementation/evidence. INT-02 actual clean authored-world /
bootstrap payload remains missing. P3 other-side artwork/seams remain pending.
SEC-01 crowbar/retrieval and SEC-02 production replay remain OPEN/HIGH PRIORITY.
No owner in-game/modpack or weapon acceptance is claimed. No playable release
package is produced from a failing north test or substituted GameTest world.
