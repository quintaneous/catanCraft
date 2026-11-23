# Brinewatch Pilot Station

A striped signal tower overlooks a keeper's cottage and a covered chart table. Two landing stages serve boats approaching from different sides of the rocky island. The cottage includes windows, two dormers, a chimney, a workbench, a loom, a sitting bench and a built-in berth. Inside the tower, a backed ladder links the ground floor, two intermediate landings, and the lantern gallery.

Axonometric view generated from the schematic blocks — see `Brinewatch_Pilot_Station.png` in the review archive or regenerate locally.

## Review placement

`Brinewatch_Pilot_Station.schem` is a Java 1.20.1 Sponge v2 schematic (data version 3465). Its positive local X/Y/Z dimensions are **55 × 50 × 47** and its stored offset is **[0,0,0]**. The suggested test location lies in the proposed western offshore reservation. All coordinates below are block coordinates.

| Feature | Schematic coordinates X,Y,Z | Suggested west-reservation coordinates X,Y,Z |
| --- | --- | --- |
| Registration anchor at the uppermost water block | 27,8,23 | −1410,75,−430 |
| Main landing, deck block | 2,9,24 | −1385,76,−431 |
| Alternate landing, deck block | 36,9,45 | −1419,76,−452 |
| Chart table, possible future objective | 26,14,27 | −1409,81,−434 |
| Tower ladder base, player's feet | 20,14,17 | −1403,81,−424 |
| Viewing gallery, player's feet | 19,40,21 | −1402,107,−428 |

Rotate 180 degrees about the registration anchor, including facing, stair shapes, fence connections and both halves of each door. The mapping is `X = −1410 − (x − 27)`, `Y = 67 + y`, `Z = −430 − (z − 23)`. The primary landing then faces east and the alternate faces north. The local footprint including air maps to X −1437…−1383 and Z −453…−407. The roof finial reaches Y116. Choose a separate, unclaimed prototype plot and keep both boat approaches open.

The highest water block is local Y8, with a flat display water surface at local Y9. In the proposed ocean this gives highest water block Y75 and a surface plane at Y76. Deck blocks at Y76 have their walking surface at Y77. Use a non-air overlay in open water. Leave water in empty cells through Y75 and extend the dryrock foundation to the surveyed seabed wherever required. The asset has no enclosed underwater spaces. The PNG uses an illustrative sea plane and flat block materials to show the actual saved geometry.

## What to check in Minecraft

Sail in from the east, disembark on the lower landing and follow the two-wide stairs to the station. Open the tower door and climb to the enclosed lantern and gallery. Walk back through the keeper's quarters and chart pavilion, then board a boat at the northern landing. Check whether the two entrances are legible at boat height, whether ladders and docks work with a boat pressed against them, and whether the gallery feels safe on the corners. Night visibility, first-person scale, real boat collision, live lighting and operation on a deployed server need an in-game check. The schematic supplies no registered capture objective, timed loot, commands, entities or inventories.

## Reproduce the artifact

Run these commands from this asset directory using Python 3 and Pillow. The optional `--registry` argument to `validate.py` points to the project's `python-tests/CatanCraft_City_A_Sector_Walls_P3/source` registry directory. Registry-backed results are recorded in `verification.json`.

```sh
python3 build.py
python3 render.py
python3 validate.py
```

The decoder read all 129,250 positions, including air. The asset includes 15,430 occupied blocks, 67 palette states including air, four paired doors and 31 backed ladder blocks. Both landing stages reach five destinations using cardinal, two-block-headroom routes, opened doors and the modeled stair and ladder transitions. The tested Minecraft file is identified by its SHA-256 in the verification record.
