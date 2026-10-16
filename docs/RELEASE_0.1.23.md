# CatanCraft 0.1.23 — Town Hall upgrades tolerate normal door use

## User-reported blocker
The v0.1.22 screenshot expected a dark-oak lower door at City A (-750,84,-779), but found AIR. This was not simply an open door. The message explicitly confirms no world writes and no purchase charge. Do not ask the owner to rebuild TH3, restore doors, clear journals or restart with another world. Use the SAME developed R7 test save.

## Fix
A read-only paired-door policy validates complete old and new schematic door pairs. At exact old Town Hall door positions, air for one or both halves is accepted when any surviving counterpart retains its expected block ID, facing, hinge and half. Missing non-door blocks, replaced doors/containers, fluids, unknown geometry and unrelated edits still refuse before writes. A source door does not have to be closed: OPEN and POWERED may differ, as may normal gate/trapdoor OPEN/POWERED. Waterlogging is not ignored.

Both halves of each destination door initialize from the new building's consistent authored pair. This prevents retaining one open old half next to a closed newly created half. It does not require closing or repairing ANY old door before purchase. Compatible surviving single-block gates/trapdoors can retain their current open/powered state.

The upgrade journals actual before states (including air, never fictitious closed doors), serialized BlockState NBT, and any changed empty decorative lectern's NBT. The main management lectern and its book remain untouched. Journal compressed-NBT read-back is verified before the first write. The final audit covers ALL 229,125 cells, including deliberately retained interaction states, and refuses unexpected new item entities before charge.

No schematic bytes, world folders, national resources, purchase prices, fixed plots, civic installers or prior journals are changed by installing this JAR. The existing R7 disposable-world opt-in and owner/source-token guards remain. This patch does NOT enable arbitrary repair of missing walls or deleting player storage. TH1/2/3 legacy placement code is unchanged; missing known doors from that path no longer require manual repair before a prestige upgrade.

## Validation boundary
Eight Forge GameTests are provided: open/closed/powered doors in both orientations; one/both missing halves; foreign blocks and wrong door geometry; missing walls/malformed pairs; trapdoor/gate/manager normal interaction; consistent target halves even with an open surviving source half; actual no-drop placement; legacy transient shape-loss reproduction. See the final commit's CI evidence for their results. Real upgraded TH4/TH5 client visuals, restart, inventories, the full modpack and exactly-once economy still require the user's continuation test.

No generic crash/restart recovery tool for prior interrupted TH transactions is added. Existing journals remain immutable to new purchases; stop on an interrupted transaction. Tested civic install/restart results remain accepted. Resource-producer artwork is not integrated; terrain R8/R9 and Superb Warfare crowbar issue #6 remain separate/unresolved. No live-world deployment approval.
