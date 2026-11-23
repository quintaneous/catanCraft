# CatanCraft performance audit for roadmap #14

On gameplay commit `6ed6eaf31f946195f8b75fa869308c915537a6ed`, a Town Hall 3→4 purchase performed **47,087 block writes** in every observed city. I would profile this purchase first when investigating a pause during construction. For a report of lag immediately after joining, capture startup and the first few minutes separately.

## What the completed tests measured

Five clean worlds ran accelerated paid progression across cities A–P and then restarted. Layouts 2–6 had complete seed and restart process records, native logs and saved world reports. Counts below come from those ten logs on the same commit.

| Recorded event | Samples | Median | Range |
| --- | ---: | ---: | ---: |
| Minecraft startup scheduling backlog | 10 warnings across 10 starts | 3,300 ms | 2,049–4,493 ms |
| Spawn preparation on fresh start | 5 processes | 3,222 ms | 2,931–3,883 ms |
| Spawn preparation on restart | 5 processes | 3,468 ms | 2,987–3,989 ms |
| All-dimensions save completion messages during the full progression run | 5 runs | 514 | 514–514 |
| All-dimensions save completion messages during readback after restart | 5 runs | 1 | 1–1 |
| Committed block writes per Town Hall 3→4 purchase | 80 purchases | 47,087 | 47,087–47,087 |
| Committed block writes per Town Hall 4→5 purchase | 80 purchases | 30,276 | 30,276–30,276 |

Each launch logged one backlog warning 2–5 seconds after the spawn-preparation message. Minecraft reports accumulated scheduling delay at that instant. These logs provide no timed attribution to an individual tick, GC event or mod. Spawn preparation measures one part of startup. Full-save counts use one message per all-dimensions completion and exclude the three per-dimension messages. They include harness and shutdown activity and supply no disk-flush durations.

The Town Hall command invokes the prestige upgrade directly. That path journals the before-image, loops over changed blocks, checks the full envelope, then saves the transaction and records the commit. This makes placement and persistence separable profiling targets. The accelerated harness recorded 544 paid/founding progression actions per world, with no connected player. Its view and simulation distances were both 3. A player's real purchase frequency, tick times and memory use still need dedicated-server measurements. [Upgrade call](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/java/com/quin/catancraft/command/CatanCommands.java#L1439) · [Placement and commit](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/java/com/quin/catancraft/world/TownHallPrestigeUpgradeService.java#L285) · [GameTest configuration](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/tools/authored53/prepare.py#L94)

## Next measurements on an isolated dedicated server

| Situation | Measurements to retain | Decision it supports |
| --- | --- | --- |
| Fresh start, same-run restart, then stationary idle | Timestamp server launch, ready, login and first play action. Record server tick durations and startup backlog with clean and already-loaded chunks identified. | Decide whether the pause is concentrated at spawn or persists during ordinary play. |
| Town Hall 3→4, then a wall quote and purchase as separate events | Capture server tick p50, p95, p99, maximum and counts over 50/100 ms. Correlate individual peaks with server-thread stacks and file writes during block updates, journal compression, chunk loading and saves. Keep event times alongside a same-city idle control. | Identify the dominant code path before changing placement or durable save ordering. Recheck exact charge and restart recovery on any fix. |
| Travel out and back on a repeatable route, then idle | At comparable intervals record server process RSS, host free RAM/paging, JVM heap occupancy after natural GC, GC pauses, and loaded chunks or retained tickets when available. Keep the client process separate. Repeat the route and recovery interval. | Distinguish short allocation peaks, cached or native memory, and sustained growth after chunks should unload. A minimum RAM claim requires an actual heap-size comparison under the same workload. |

The existing profiling guide specifies how to collect the server tick series and Java Flight Recorder data. Leave expensive developer profiling out of Quin's ordinary first session. If Quin notices a pause, the city, action and approximate time are enough to start reproducing it. [Dedicated profiling guide](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/docs/SERVER_PERFORMANCE_INVESTIGATION.md)

## Reproduce

From the qualification workspace, run `python3 receipts/parallel-roadmap/performance/analyze.py receipts/combined 2 3 4 5 6`. Compare its JSON with `result.json`. The analyzer validates each completed phase's commit, log SHA-256, process status, saved result, world ID, and expected checkpoint count. Supply additional layouts only after their two phase records are complete. The script reads existing evidence and requires Python only.
