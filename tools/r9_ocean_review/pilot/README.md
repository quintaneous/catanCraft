# Pine Reach to Brinewatch

This Java 1.20.1 Creative save joins a forest road, the existing B1 bridge approach and river, an authored north estuary, and the western lighthouse. The review ZIP contains the world, road and sea map, woodland comparison, directions and saved-world verification. See [the proposed gathering and delivery playtest](Fieldwork_and_Delivery.md) for the next gameplay work.

## Quin’s in-game review

Quin liked the detail in the Pine Reach forest and pointed out how quickly it gives way to sparse ground elsewhere. Use that section as the visual reference when reviewing all 16 territories. In woodland, carry the clustered trees, understory and uneven edges farther from the main road. Give quarries, farmland and open riverbanks comparable attention using their own terrain and materials. Review the transition from each detailed patch into the surrounding land from the road and from above.

Quin found the B1 bank, launched a boat, understood the route with the minimap, and found the lighthouse easy to use. Keep the existing approach and landing as references for future routes. The visit did not assess freight handling, a pickup transfer, or navigation without a minimap.

## Rebuild from the qualification workspace

Use the exact pinned R7 input tree at `inputs/CatanCraft_Clean_R7_Input` and the gameplay checkout at `resource-ui` on `6ed6eaf31f946195f8b75fa869308c915537a6ed`. Place this pilot directory at `receipts/coast-pilot`, the review assets from its parent directory at `receipts/ocean-v2`, and the parent's `mainland` directory at `receipts/mainland-coast-plan`. Python 3.11+, NumPy and nbtlib 2.0.4 are required for world assembly. The source-input folder includes the original NBT checker. Rendering additionally uses Pillow and the review renderer dependencies described in the parent directory.

```sh
python receipts/coast-pilot/world.py --out receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch --report receipts/coast-pilot/replay-build.json
python receipts/coast-pilot/verify.py receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch
python receipts/coast-pilot/check_marks.py resource-ui inputs/CatanCraft_Clean_R7_Input receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch
python receipts/coast-pilot/mainland_verify.py
```

The builder requires a new destination. Original region files and level metadata are hash-checked. Each altered original block is listed with its before and after state in the compressed placement file. Published receipts include the coast joins, two dock approaches, rotated lighthouse block comparison, boat channel, boarding-bank walk, lighting markers, and changes to each original region. Vehicle and freight trials remain open.
