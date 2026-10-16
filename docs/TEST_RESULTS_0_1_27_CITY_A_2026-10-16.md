# CatanCraft v0.1.27 — City A application result and occupancy feedback

Date: 2026-10-16.
Status: **Owner application PASS; occupancy usability defect OPEN.**

## Evidence and scope

The owner supplied two in-game screenshots after the v0.1.27 City A checklist and wrote: "I dont think upgrades shouldnt be able to happen if theres items or people in the area anyway, but when I got all the entities out it did work".

The first screenshot shows R5 public-operation refusals for `entity.minecraft.item`, then `entity.minecraft.iron_golem`, then `entity.minecraft.item`. The second explicitly shows:

> R5 public spaces completed: 1661 block changes; zero charge; purchases and levels unchanged.

Record the public-space application and reported successful completion as PASS on the owner's existing developed City A. The screenshot contains a command result, not an independent balance/NBT audit. This report does not explicitly confirm repeat-as-no-op, a normal restart, every new feature's visual quality, or all-city acceptance; do not mark those individually passed from this evidence. Earlier accepted Town Hall, producer L3 retrofit/L4-L5 upgrades and Commercial/Logistics passes remain valid. Do not ask the owner to reset, reapply, repurchase, or roll back a successful application to test this feedback.

Released gameplay source remains `f74b4002c07c9f451c7947c164444a59b4f2b3eb`. Branch head rechecked before this documentation write: `be06bedb765332661ae0618c53c4e4298280a713`. Installed baseline remains the already-delivered v0.1.27 JAR. No new build or CI run is claimed by this record.

## Verified cause

Read `StreetscapeTransaction.run` and `StreetscapeR5.plan` at that source. The preflight currently iterates every entity returned for each complete public-feature AABB and refuses immediately, irrespective of whether the entity intersects a changed collision shape or loses support. The bounds include read-context space, not only physical hazards. `ProducerTransaction` similarly rejects all entities in producer envelopes.

For a fresh public-space apply, this occupancy check is before creation of the durable journal and before upgrade block writes. The refusals shown are consistent with that preflight protection; they do not themselves demonstrate an interrupted transaction. They also do not prove whether either blocker would really collide with a change: the screenshots do not locate them precisely.

## Next code requirement — not implemented by this record

Replace broad keep-everyone-out behavior with a tested obstruction policy shared where practical by R5 public work and producer migration. Being inside a read/plot envelope is not, by itself, a reason to refuse.

- Allow players, mobs, vehicles and dropped items in unaffected safe space. Permit proven nonhazardous material-only changes with unchanged collision/support geometry rather than demanding an empty plot.
- Still protect an entity whose occupied space would be filled, whose supporting surface would be removed/lowered, or whose attachment/access would be invalidated. Account for actual entity bounds and the planned intermediate/final shapes. Checking only whether an entity's center is in a write cell is insufficient; so is blindly comparing air/non-air.
- Do not kill, delete, duplicate, silently collect, despawn or broadly teleport entities as a workaround. Preserve item identity and complete stack NBT. Automated relocation would be separate transaction work requiring safe destination and durable recovery; do not sneak it into a collision-check change.
- A real obstruction should name its location/type and provide a local actionable reason. A known fresh refusal should clearly state no upgrade write/payment occurred; pending/uncertain journals retain conservative recovery guidance.
- Apply safety to forward work, resumed remaining writes and reverse rollback. Keep loaded-chunk/entity checks, authorization, exact source validation, sparse writes, historical journals, actual inventory NBT and economy receipts intact.

Required new tests: harmless nearby item, living entity and vehicle-sized bounds allowed and preserved; unchanged supported floor/material swap; actual new-solid intersection and removed/changed support refused; wide bounding boxes crossing the edge; attachment/unsafe-shape conservative refusal; entity-free normal path; known fresh refusal creates no journal or payment; pending resume/reverse rollback obstruction; both supported orientations. Keep retained regression assertions and add or precisely revise only the old deliberately broad occupancy expectations, with explicit rationale.

Bundle this behavior correction with the next relevant tested release where practical, rather than requesting another JAR swap solely to record feedback. Producer Exterior R2 is still awaiting immutable-revision/same-tier integration, not installed by v0.1.27. R5 house work and R4-A/R6 defenses remain separate. The crowbar exploit remains unresolved/high priority. Preserve developed R7 City A; no live world/mod/config was accessed or changed. R8-B stays at its unapproved three-area review gate; orientation pavilion remains deferred.

This is a documentation-only acceptance/defect record. **No occupancy behavior has changed in the installed mod.**
