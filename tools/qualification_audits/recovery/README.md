# Recovering the nine-layout qualification

The read-only planner checks each layout on the requested combined source and prints the evidence behind its next action. The output is a diagnostic for the single queue owner. Running the planner never starts Gradle, opens a Minecraft world for writing, resets a run, or marks a gameplay build ready.

## Observed result

On November 22, 2026, the original records for layouts 2–6 each had matching process and game evidence covering 80 progression checkpoints and 16 restart checks. The replacement layout 7 seed log advanced during observation. Layouts 0, 1 and 8 had no recorded attempts. The prior layout 7 seed completed all 80 progression checks, followed by a restart attempt that stopped at city G, seven of sixteen. The old restart recorder never recorded a successful exit. Those seven checks cannot be combined with results from another world. See `observed.json` for the snapshot.

## Decisions

| Result | What the queue owner can do |
| --- | --- |
| `retain_pair` | Keep both native process receipts, their original logs, and both world reports. The planner checks the source, stage binding, command, namespace, modpack initialization script, native summaries, log hashes, 80 + 16 checkpoints, facility coverage, world identity and equality of paid-history inventories. Run the project’s full 18-report verifier before announcing the all-layout milestone. |
| `verify_seed` | After confirming single-writer ownership, launch only the original seed’s restart phase in its own run directory with a new recorder destination. This requires a successful seed process, all seed checkpoints, untouched immutable inputs, a valid sealed restart proof, matching saved ledger and matching hashes for every recorded paid history. Neither a verify report nor a verify recorder directory may exist. Physical state is still tested by the native restart run. |
| `wait` | An observed native log changed. Leave the existing writer alone. |
| `confirm_writer_stopped` | Inspect the owning execution session and server activity. A stale log, a PID lookup in another sandbox, a `RUNNING` receipt or the end of the observation interval cannot establish that the writer has stopped. |
| `preserve_then_restage` | After exclusive access is established, retain the previous attempt and use a new pristine authored world for this layout. A separately retained and validated checkpoint taken after the successful seed exit and before any verify attempt could instead supply the original seed for a new verify. This planner does not select or restore snapshots. |
| `fresh_stage` | Stage the new layout from the approved original when the shared queue is available. The planner also checks for orphaned run directories with the campaign’s layout naming convention. |

The default scan waits one second between log observations. The operator can supply `--writers-stopped` only after independently confirming the queue and server are stopped. Observed movement overrides that input. All recommendations to start anything remain subject to the single-writer requirement. The tool produces no locks or start commands. Older archived attempts and live-player worlds are outside the planner’s inputs.

## Why interrupted restarts need care

The seed phase in `Progression53Tests.java` saves and rereads the native nation ledger, writes the sealed restart proof, then emits the seed PASS. The actual seed recorder must also finish. During restart the native test rereads the ledger, books and prior journal hashes, loads every city, and overwrites `verify.json` as each checkpoint completes. A stopped verify process therefore leaves a touched world and can leave a credible-looking partial report. Preserve the interrupted evidence before any new attempt.

The planner reads the same CPR1 proof format as `ProducerJournal.java`. Before reopening a finished seed it checks the recorded compressed payload length and SHA-256, its NBT schema, layout and gameplay commit, all sixteen management-book entries, the saved nation ledger and the paid-history files. A genuine restart pass remains a native process result.

## Reproduce

Run from the project workspace with the supplied clean input interpreter present.

```sh
python3 receipts/parallel-roadmap/recovery/resume_plan.py
python3 -B receipts/parallel-roadmap/recovery/test_resume_plan.py -v
```

The 11 tests build private temporary fixtures from completed layout 6 evidence, with the temporary path substituted into fixture records. They cover a clean seed, completed pair, interrupted verify with and without a world report, partial seed, corrupt restart seal, modified ledger, modified journal, foreign source, changed native log, mismatched restart history, changing log and a new layout. These fixture tests exercise recovery decisions. They are separate from native gameplay counts.
