# R9 ocean and offshore monument review

This prototype uses the clean Season 1 R7 geometry at gameplay baseline `6ed6eaf31f946195f8b75fa869308c915537a6ed`. The archive contains a standalone vanilla Java 1.20.1 spectator save, two coastal study tiles, two monuments and an atlas. The review save uses its own local coordinates and daylight rules. It carries no player histories or active objectives.

## Review decisions

1. Compare the sheltered landing with the rock headland and estuary from a boat-height viewpoint. The matched images are real block renders of a study volume. The original panels show the cut face where that volume ends.
2. Inspect the entrance, landing height and interior routes of both monuments. The west lighthouse and eastern freighter are proposed placements. The remaining north and south sites are open reservations.
3. After the R8 mainland direction is settled, stage one estuary-to-site voyage on a new full-height copy of the accepted baseline. Measure continuous water depth and headroom, test boats, verify lighting and biome seams, and restart the copy before extending around the perimeter.
4. Before enabling the new world border on any gameplay build, compare protected roads, bridges, all 16 cities' plots, walls, producer and Town Hall sites, and existing monument anchors. Rerun opening shops, expansion, construction, rewards, ownership and restart on the finished packaged map. Existing saved seasons require separate migration tests.

## Reproduce

The four schematic generators and geometry checks are in their respective directories. The atlas and coast comparisons read the pristine R7 input; the technical notes list the qualification workspace paths. The gallery generator accepts the root containing the four asset subdirectories, the pristine Java 1.20.1 level.dat as a version/dimension-schema reference, and a new output directory. It uses nbtlib 2.0.4 and NumPy. Its independent readback script uses NumPy and the reference input's nbt_check.py.

    python3 gallery/build_gallery.py . /path/to/clean-source/level.dat /new/gallery > /tmp/placements.json
    python3 gallery/verify_gallery.py /new/gallery . /tmp/placements.json /path/to/CatanCraft_Clean_R7_Input

The gallery deliberately completes Brinewatch's underwater negative space with source water and supports solid foundation columns beneath both offshore sites. The gallery report counts these changes separately. Geometry readback and ZIP checks are complete. Connected-client visual review, in-game sailing, the full ocean build and gameplay integration remain open.

Revision 2 extends the Brinewake hull from 61 to 76 blocks and the beam from 19 to 23. Both original landing routes still lead to the objective. The lighthouse and coastal tile schematic hashes match revision 1. The mainland brief covers all 16 territories; the first proposed joint traversal runs from Pine Reach to the northern estuary and the west lighthouse. The owner archive is `CatanCraft_R9_Ocean_Design_Review_v2.zip`, SHA-256 `ea17af597fcffdbbdefeff6d243b994f37997827c8ee99b547b7a20ba87fd4a1`. Retrieve the original wreck from revision 1 (commit `c4ce06a0a5280133261e114bffe210a8143339bf`) to regenerate the comparison.

## Connected mainland and offshore pilot

The [Pine Reach to Brinewatch pilot](pilot/README.md) joins the forest, bridge, river and offshore station in a single Creative world. Its [proposed gathering and delivery playtest](pilot/Fieldwork_and_Delivery.md) covers wood nodes, agricultural freight, dispatch boards, transfers and wayfinding.
