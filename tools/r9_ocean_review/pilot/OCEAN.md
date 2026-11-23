# North estuary to Brinewatch

From the north river mouth, follow the green-and-white pilot spars into the estuary. On the outbound trip keep green to starboard (right). A pair of taller yellow-and-white lights marks each major turn. Follow the northern leg west, then the western leg south, then turn west to Brinewatch. Approach the east landing from the water centered at (−1383, −431). The deck is two blocks west, with its walking surface at Y77. For the north landing, peel off at (−1362, −430), sail north to Z −464, west to X −1419 and south to Z −454.

The supplied exterior covers this review route. It does not encircle the map. The north and west strips provide a 4,388-chunk L-shaped area that joins the original R7 mainland without changing its outermost authored row. The mixed beaches, gravel spits, rough headlands and two groups of sea stacks mark the transition to deeper water. Brinewatch has a shallow undersea shelf that meets the bottom of the schematic. All position pairs are X, Z.

| Channel | Reserved cross-section | Shallowest measured depth |
| --- | ---: | ---: |
| Entrance throat | 8 blocks | 4 water blocks |
| Widened estuary | 24 blocks | 7 |
| North circuit to northwest turn | 32 blocks | 11 |
| West circuit to station turn | 32 blocks | 11 |
| Westward approach to the station reservation | 32 blocks | 10 |

The source-water level is Y75, with its surface plane at Y76. The review route's long straight marks are 84 blocks apart. Each of the 32 fixed spars uses a small copper float, white band and sea lantern. Six form three pairs of taller lights at turns. Moorings are ordinary blocks, without entities or commands.

The sounding tool reads the actual landmark schematic, reproduces its rotated non-air overlay and checks every column on both routes against the procedural terrain and markers. Both berth approaches support a three-block-wide boat envelope with three wet blocks below the surface and two air blocks above. The outer landings have a solid deck below two clear air blocks. The deck is one block beyond the edge of the tested boat envelope. Exact dismount behavior and turn visibility at the owner's client settings require a boat trial.

The seam survey contains the 1,248 north and 800 west original columns along which new terrain meets the frozen mainland. Replaying the survey independently checks the pinned R7 source region bytes before deriving these profiles. Every neighboring new column matches the old bed height, top material and wet status. This covers the offshore join. The separate inland artwork and transfer checklist have their own protected-area checks.

Run from the qualification workspace:

    python3 -B receipts/coast-pilot/survey_seam.py resource-ui inputs/CatanCraft_Clean_R7_Input > /tmp/r9-seam.json
    cmp /tmp/r9-seam.json receipts/coast-pilot/ocean-seam.json
    python3 -B receipts/coast-pilot/sound_ocean.py receipts/ocean-v2/lighthouse > /tmp/r9-sounding.json
    cmp /tmp/r9-sounding.json receipts/coast-pilot/ocean.json

The chunk writer constructs these columns in a disposable world. Its independent region readback checks the bytes actually delivered. The channel proof above is measured from the source geometry and is separate from the packaged save's verification.

The integrated Anvil copy subsequently passed a separate, read-only inspection of every pilot spar, including its waterlogged chain and anchor. Its 7,248 primary boat-footprint columns and 312 alternate-route columns all contained three source-water blocks and two air blocks. The receipt is ocean-persisted.json. Recheck the unpacked world with:

    python3 -B receipts/coast-pilot/check_marks.py resource-ui inputs/CatanCraft_Clean_R7_Input receipts/coast-pilot/review/CatanCraft_Pine_Reach_Brinewatch
