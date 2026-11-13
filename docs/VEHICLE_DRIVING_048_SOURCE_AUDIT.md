# ECO-03 — native driving source audit, 048

Read-only source inspection for driving/steering investigation after validated 047. This is not a native execution result or bay activation approval. No runtime code, provider data, authored geometry, prices or live state was changed by this audit.

## Exact input

The published SBW 0.8.9.2 source archive was read from the repository-only pinned runtime input. Its SHA-256 is `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`, matching `tools/sec01_035/prepare_runtime.py`. Source members were read directly from that verified ZIP rather than trusting earlier extracted copies.

| Member | SHA-256 |
|---|---|
| `entity/vehicle/base/VehicleEntity.kt` | `11b33524a9da7dacd02e3d5d448c5869de0c357e47cc3d6699e2a9352c252392` |
| `entity/vehicle/utils/VehicleEngineUtils.kt` | `840bf33ca775cd5c5c9fa622c0f3dd0e825cecf6e956c736f9939ee5e6e93aa9` |
| `network/message/send/VehicleMovementMessage.kt` | `3ce5247c7151ed33896756ce8f087d228205063e854011d66e2bb63e5a76ac55` |
| `data/vehicle/subdata/EngineInfo.kt` | `c2647ea7471ad0d35dc27c030f2ce2a5931f283f8cf4d5fb1f96bc0a98ba881a` |

Kotlin paths above have ZIP prefix `com/atsuishio/superbwarfare/`. The effective vehicle data must still match the existing pinned pickup `b53ee2f4cb22708e68decf7af5a26ddb8587d88303f8400c47b32c0b5c22841a` and LAV `1773f7ca88cb557dbb0ee3232bfae510f0e75c6ea928f5fabc2f5329f25337ae` JSON hashes through the runtime resource manager.

## Native control contract

`VehicleMovementMessage(short keys)` routes its server handler to the sender's mounted vehicle only when that sender is the vehicle's first passenger. Its alternative linked-monitor/drone path is outside this fixture. Use an empty-handed driver, actual native boarding, a real server-side packet context and the provider handler, then observe ordinary server entity ticks. This tests the server handler and physics, not authenticated client transmission or client prediction.

`processInput` maps left=1, right=2, forward=4, back=8, up=16, down=32, decoy=64, fire=128 and sprint=256. For the wheel engine, up applies braking. Use only reviewed movement bits and explicitly send neutral input when releasing controls. Direct input-property writes omit the packet's driver authorization check.

Both vehicles use the Wheel engine, not Track, and have native maximum energy 5,000,000. The fixture may seed a declared finite amount through native energy storage before beginning, then must observe actual depletion without replenishment. Missing first passenger clears steering/throttle and sets power to zero. Insufficient energy clears steering/throttle and damps power. Wheel operation does not require the aircraft/helicopter engine-start flags.

| Parameter | Pickup | LAV-150 |
|---|---:|---:|
| Energy cost rate | 32 | 64 |
| Forward acceleration increment | 0.024 | 0.03 |
| Reverse acceleration decrement | 0.012 | 0.02 |
| Forward speed coefficient | 1.48 | 1 |
| Reverse speed coefficient | 1.1 | 0.6 |
| Steering speed | 0.075 | 0.07 |
| Buoyancy | 0 | 0.052 |

These are provider coefficients, not measured speeds or turning radii. Wheel yaw changes depend on actual horizontal velocity and the evolving rudder/steering state. Rotating a stationary vehicle in place, as in the earlier geometric investigator, cannot establish a drivable turn.

The first native `travel()` call initializes engine information; subsequent calls execute the engine. Energy cost uses the incoming power value and integer truncation, so an initial low-power tick may legitimately consume zero. Assert measurable consumption over a driven interval instead of inventing a per-tick minimum. Neutral input coasts by damping power; dry non-drift braking applies additional damping and is not an instantaneous stop.

## Required evidence and false-pass controls

Natural vehicle ticks call travel, gravity and `move(SELF)` and also execute block collision/terrain compatibility callbacks. A driving fixture must add the actual entity to world storage, maintain genuine block/entity chunk readiness, retain the real driver, and observe finite positions, velocity, heading, native OBBs, energy and elapsed ticks. Initial pose setup is legitimate; subsequent forced positions, yaw, velocity, on-ground state, disabled gravity, or direct engine-only stepping would invalidate a native trajectory claim.

Use the exact unchanged factory-over-starter composition, explicit staged bounds and preserved posts/lanterns. Check source integrity and physical clearance during/after the trajectory; do not remove obstacles or enlarge the source to make a route succeed. Record attempted controls and actual outcomes, including route departures or collision failures. Forward movement, steering response and safe completion of a particular authored route are distinct assertions. A single successful steering arc is not proof of all factory turns, steering radii, braking distances or safe public deployment.

Retain no-driver/unauthorized-passenger and insufficient-energy observations as appropriate for the implemented fixture. Failed or held geometry must remain visible in evidence. No source-only finding here clears authenticated-client acceptance, production facility authority, counter/common-command wiring or SEC-01 installed-pack acceptance.
