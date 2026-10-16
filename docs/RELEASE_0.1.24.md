# CatanCraft 0.1.24 - Town Hall derived-state compatibility

Date: 2026-10-14
Base: e78108a6ebc0914ce515b4632a306d91e0c46498 (0.1.23)

## Report
The continued City A upgrade refused a deepslate tile stair at (-762,106,-781).
Material, facing=south, half=bottom and waterlogged=false matched. Only
shape=straight in the source differed from shape=outer_left in the world.
The screenshot states no world writes or resources charged.
The TH3 authored local cell (25,27,9) is that straight south-facing stair; its
neighbor at (25,27,10) faces east. The added native tests pin this exact
neighborhood and verify the engine produces outer_left.

## Change
TownHallInteractionPolicy now ignores a narrow typed set of derived properties:
stair corner SHAPE; fence/pane/iron-bar cardinal connections; wall side heights
and post; gate IN_WALL; existing leaf DISTANCE; lightning-rod POWERED; grassy
block SNOWY appearance. Existing door/open/powered/missing-original-pair handling
and management-lectern exceptions remain.
This is NOT a block-ID-only comparison and does NOT ignore every property named
shape. Material, facing, half, axis, waterlogging and all other properties remain
strict. Foreign storage or added blocks still fail their own full-envelope cell
check. Missing walls and other non-door blocks remain protected refusals.
No forced paste or resource bypass has been added.

## Preservation
The transaction service is unchanged: actual unnormalized before-state snapshots,
read-back-verified journals, management book/NBT preservation, full destination
verification, ownership, saved placement tokens, occupied-volume and cost gates
remain in force. Existing journals are not renamed, deleted or reinterpreted.
All original gameplay schematic bytes are unchanged. TH4/TH5 copies under
tests/fixtures are CI-only inputs, not replacement gameplay assets in the JAR.

## Regression scope
Eight prior door tests are retained. Eight new tests exercise all five stair
shapes over six materials, both halves and four facings; protected-property
refusals; connection states and water refusal; narrow environmental states;
native stair and fence updates; complete TH3/TH4/TH5 envelopes in both supported
city orientations, compared immediately after native shape recalculation and
again after delayed ticks. Each complete envelope contains 229,125 cells.
The workflow must pass and its logs must contain all six full-fixture records
before this candidate is delivered. Compilation alone is insufficient.
These are physical/block-state tests, NOT full purchase/economy, management UI,
restart or TaCZ/Superb Warfare player acceptance tests. That acceptance remains
pending on the owner's installed modpack.

## Install and continue
Back up and stop the same R7 TEST world and close Minecraft. Replace only the
CatanCraft JAR on server and client with 0.1.24; leave one CatanCraft JAR per mods
folder. Keep the world, existing buildings, external schematics, nation data,
authorization marker and journals. Do not manually reshape stairs, close/repair
doors, reset city levels or import replacement schematics.
Stand on the road clear of the Town Hall volume and retry once:

```text
/nation upgrade city a
```

Verify TH4 and one charge, management book/UI, then normal restart persistence.
After that passes, repeat the ordinary purchase for TH5. A new incomplete-journal
message means stop and preserve diagnostics, not delete/retry. This is still an
explicitly gated backed-up disposable R7 development-world release, not live
world deployment approval. R8/R9, resource visual integration and the crowbar
issue remain separate.
