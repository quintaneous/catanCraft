# CatanCraft playtest preparation

Gameplay source `6ed6eaf31f946195f8b75fa869308c915537a6ed` from [PR #15](https://github.com/quintaneous/catanCraft/pull/15). The observations below come from that build, the pristine authored map, and the qualification records available on November 22, 2026. Connected-client gameplay remains pending.

| Roadmap work | What we learned | First action |
| --- | --- | --- |
| [Factory counters](shops/README.md) | The Weapons Factory has two crafting tables. The registered counter is on spruce planks. The Vehicle Factory has one crafting table. | At an owned, completed factory, right-click the registered counter with an empty main hand. Record the city, plot, response and whether the menu returns to the same factory. |
| [Server performance](performance/README.md) | A Town Hall 3→4 upgrade performed 47,087 synchronous block writes in each of 80 observed purchases. | Profile tick time and heap separately during an ordinary purchase and while idle on the same server. |
| [Agriculture](agriculture/README.md) | None of 40 modeled expansion routes ran short on Agriculture. Adding a fourth L5 satellite to one L5 starter can put food upkeep into deficit. | Compare the resource forecast before and after an expansion. Revisit pricing or new uses after observing what players actually do with the surplus. |
| [Monument territory](terrain/README.md) | The capture areas fall inside city territories, and the Refinery structure crosses a territory boundary. | Evaluate the neutral court as a separate ownership rule, then check access, rewards and territory UI. Keep roads, build sites and physical monuments in place. |
| [Interrupted tests](recovery/README.md) | The status planner checks each pair against the native logs and saved world state. | Use the planner after a workspace interruption to identify valid completed layouts and which attempt to rerun. |
| [Seed checkpoints](seed-checkpoint/README.md) | The optional utility can preserve a completed seed between the two native phases. | Trial capture and restore on a disposable seed before enabling it in routine qualification. |

## Reproducing the audits

Each directory contains its own reproduction instructions and records the inputs it used. Where a command starts with `receipts/parallel-roadmap`, that path refers to these tools in the qualification workspace. In a standalone checkout, invoke the corresponding script under `tools/qualification_audits` and pass the workspace or input paths documented for that audit. The qualification workspace contains the `resource-ui` checkout, pristine R7 inputs and original native receipts. The Agriculture replay also works with its bundled reference routes and the pinned gameplay checkout alone.

The draft is independent of the gameplay branch and contains developer tooling and measurements. The release handoff still requires all nine layout seed/restart pairs, authored wall coverage, retained native regressions and the matched fresh server ZIP.
