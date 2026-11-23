# Preserve a verified seed for a restart replay

The five completed seeds for layouts 2–6 took 671–699 seconds each on gameplay commit `6ed6eaf31f946195f8b75fa869308c915537a6ed`. A checkpoint taken once at the boundary can avoid replaying those progression commands after an interrupted restart check. Actual time saved also depends on copy and verification time. A completed layout directory was about 259 MiB on this worker.

**Status.** The prototype passed 11 synthetic Linux tests, including an independent restored copy and rejection of a held world lock. Every available completed native seed already had a verify attempt. A native recovery rehearsal remains pending. The active qualification runner does not invoke the helper.

## Developer use

Run from the checkout workspace on 64-bit x86 Linux, using Python 3.11 or newer. Keep the neighboring `recovery/resume_plan.py` module. The helper uses the project's clean R7 NBT reader and the existing native seed, restart proof and ledger checks. The single writer must exclusively own scheduling across the operation. Call capture synchronously after a clean seed process exits and before launching Gradle for verification. A Minecraft world lock only covers an opened world. It cannot detect a launcher in the pre-start period.

Choose a new checkpoint path in a separate existing directory with room for an independent full copy. For example, the following commands assume that the current directory is the workspace and that the developer has selected the layout and source for the current run.

```bash
helper=receipts/parallel-roadmap/seed-checkpoint/checkpoint.py
campaign_source=$(cat receipts/combined/source.txt)
layout=7
checkpoint_dir="$PWD/receipts/developer-seed-copies"
mkdir -p "$checkpoint_dir"
snapshot="$checkpoint_dir/$campaign_source-layout-$layout"
python3 "$helper" capture "$PWD" "$layout" "$campaign_source" "$snapshot"
```

Retain the printed SHA-256 separately with the run handoff. Continue the normal native restart check. If verification is interrupted, first establish that both the writer and the server have exited. Preserve the entire attempted world and its verify receipts separately. Leave the original seed log, seed exit record and staging receipt at their recorded paths and keep the original disposable build path vacant. Restore uses this exact path because the authored world identity binds it.

```bash
python3 "$helper" restore "$PWD" "$layout" "$campaign_source" "$snapshot" --manifest-sha256 "$recorded_manifest_digest"
```

On return, the exclusive writer can invoke the ordinary native verify command and collect a new process receipt. Restoration on its own earns no gameplay or layout pass. Review and retain the original attempt. Capture/restore publish only to a new location using an atomic no-replace rename on the same filesystem. Preserve the snapshot, digest and original seed/staging receipts if the workspace itself may be discarded.

## Reproduce the synthetic checks

```bash
(cd receipts/parallel-roadmap/seed-checkpoint && PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v)
```

Fixtures contain a tiny sealed restart record, miniature saved nation ledger, sample purchase history, all required checkpoint identities, and simulated native receipts. Tests take place entirely in a private temporary tree. The checks cover restoration integrity, active Linux locks, partial verify artifacts, incomplete seed execution, source identity, extra or changed files, independent file copies, symlinks, hard links, receipt drift and existing destinations.
