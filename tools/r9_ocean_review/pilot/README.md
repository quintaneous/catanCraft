# Pine Reach to Brinewatch

This Java 1.20.1 Creative save joins a forest road, the existing B1 bridge approach and river, an authored north estuary, and the western lighthouse. The review ZIP contains the world, road and sea map, woodland comparison, directions and saved-world verification. See [the proposed gathering and delivery playtest](Fieldwork_and_Delivery.md) for the next gameplay work.

## Quin’s in-game review

Quin liked the detail in the Pine Reach forest and pointed out how quickly it gives way to sparse ground elsewhere. Use that section as the visual reference when reviewing all 16 territories. In woodland, carry the clustered trees, understory and uneven edges farther from the main road. Give quarries, farmland and open riverbanks comparable attention using their own terrain and materials. Review the transition from each detailed patch into the surrounding land from the road and from above.

Quin found the B1 bank, launched a boat, understood the route with the minimap, and found the lighthouse easy to use. Keep the existing approach and landing as references for future routes. The visit did not assess freight handling, a pickup transfer, or navigation without a minimap.

## Terrain and city identity

Treat terrain as permanent during normal play and sieges. Future siege damage may affect registered building blocks only. Being inside a city or a siege boundary is insufficient. Adjacent soil, rock and riverbeds remain outside the damage mask. Read-only ground around wall foundations may naturally switch between grass and ordinary dirt.

Quin clarified that resource identity should be strongest in a few landmarks and the overall city atmosphere. A coal territory should feel like a mining town throughout the settlement. Factory redesigns are optional; the earlier factory skyline studies are exploratory rather than a requirement to reskin every factory.

Develop each city as a coherent settlement through housing, streets, lighting, landscaping, working yards and a few prominent landmarks. For coal territories, explore miners’ terraces, weathered brick and timber, coal-loading yards and a pithead landmark. These are proposals for review, not approved footprints or new gameplay systems. Carry quieter material and streetscape details between landmarks so the identity remains visible while walking through the city. Use an equivalent settlement-level approach for other resource territories.

Make progression visible in the settlement’s increasing scale, finish and civic presence while retaining its resource identity. Keep the familiar building functions, accessible entrances, shop counters, roads, resource access, placement anchors and defense clearances intact. Prepare a city-scale review before committing landmark locations or changes to the construction system; ask Quin about unresolved design choices. Keep this work separate from the current gameplay qualification candidate.

## Rebuild from the qualification workspace

Use the exact pinned R7 input tree at `inputs/CatanCraft_Clean_R7_Input` and the gameplay checkout at `resource-ui` on `6ed6eaf31f946195f8b75fa869308c915537a6ed`. Place this pilot directory at `receipts/coast-pilot`, the review assets from its parent directory at `receipts/ocean-v2`, and the parent's `mainland` directory at `receipts/mainland-coast-plan`. Python 3.11+, NumPy and nbtlib 2.0.4 are required for world assembly. The source-input folder includes the original NBT checker. Rendering additionally uses Pillow and the review renderer dependencies described in the parent directory.

```sh
python receipts/coast-pilot/world.py --out receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch --report receipts/coast-pilot/replay-build.json
python receipts/coast-pilot/verify.py receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch
python receipts/coast-pilot/check_marks.py resource-ui inputs/CatanCraft_Clean_R7_Input receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch
python receipts/coast-pilot/mainland_verify.py
```

The builder requires a new destination. Original region files and level metadata are hash-checked. Each altered original block is listed with its before and after state in the compressed placement file. Published receipts include the coast joins, two dock approaches, rotated lighthouse block comparison, boat channel, boarding-bank walk, lighting markers, and changes to each original region. Vehicle and freight trials remain open.
