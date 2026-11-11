# SEC-01: native terrain configuration policy, 0.1.43

Implementation checkpoint, 2026-11-11. Native execution is pending; this document
does not claim a passing watcher run, clean startup, owner acceptance, or release
qualification. Preserve the failed 0.1.42 observation and all new failed runs.

The 0.1.42 combined run retained at
`build/stock42-evidence/run1/expanded/native_combined/stock42-evidence/native_combined.log`
reported a FileWatcher `ConfigLoadingException`, caused by a NightConfig
`ParsingException` for duplicate `[ammo]`, after the old guard called
`BooleanValue.set(false)`. The old periodic setter has been removed.

## Exact mechanism and sources

The target is Minecraft 1.20.1 / Forge 47.4.10 / NightConfig 3.6.4, with the
published TaCZ 1.2.0 and SBW 0.8.9.2 configs. The relevant Forge source was checked
at commit `132704e5f23dbee28d776738eb1c0c42fefc0bf6` (the 47.4.10 changes), not
the moving `1.20.x` branch. NightConfig was checked at v3.6.4 commit
`16ceeae4d2438b922dc6692f086991f7c6e026a8`.

- [ForgeConfigSpec](https://github.com/MinecraftForge/MinecraftForge/blob/132704e5f23dbee28d776738eb1c0c42fefc0bf6/src/main/java/net/minecraftforge/common/ForgeConfigSpec.java):
  `ConfigValue.get()` reads `childConfig.getOrElse(path, defaultSupplier)`;
  `set()` mutates that config before assigning its cache; public `setConfig()`
  replaces the backing view and clears all caches through `afterReload()`.
- [ConfigFileTypeHandler](https://github.com/MinecraftForge/MinecraftForge/blob/132704e5f23dbee28d776738eb1c0c42fefc0bf6/fmlcore/src/main/java/net/minecraftforge/fml/config/ConfigFileTypeHandler.java):
  Forge constructs a synchronous autosaving file config; its watcher loads the
  original file-backed config, corrects it if needed, resets spec caches, then
  fires `Reloading` on the provider's mod bus.
- [ModConfigEvent](https://github.com/MinecraftForge/MinecraftForge/blob/132704e5f23dbee28d776738eb1c0c42fefc0bf6/src/fmlcommon/java/net/minecraftforge/fml/event/config/ModConfigEvent.java):
  loading configs are queryable; reload events may arrive off the game thread.
- [NightConfig AutosaveCommentedFileConfig](https://github.com/TheElectronWill/night-config/blob/16ceeae4d2438b922dc6692f086991f7c6e026a8/core/src/main/java/com/electronwill/nightconfig/core/file/AutosaveCommentedFileConfig.java)
  mutates before saving, whereas the lock used by
  [WriteSyncFileConfig](https://github.com/TheElectronWill/night-config/blob/16ceeae4d2438b922dc6692f086991f7c6e026a8/core/src/main/java/com/electronwill/nightconfig/core/file/WriteSyncFileConfig.java)
  protects the inner file read/write. Synchronizing on the outer config or Forge
  spec does not share that private monitor and cannot fix this race.

No private reflection, guessed monitor, watcher removal, native-class patch,
native-JAR modification, or suppression of parser errors is used.

## Effective policy

`CombatTerrainSafety.register()` subscribes at HIGHEST priority to each present
provider's own mod bus before normal COMMON/SERVER configuration loading.
Standard FML exposes `getEventBus()`. Pinned KotlinForForge 4.11.0 exposes the
public final JVM accessor `getEventBus$kfflang()`; the published nested
`kfflang-4.11.0.jar` class method has access flags `0x0011`. This version-specific
accessor is accepted only for the exact known container class. Unknown loaders
fail visibly instead of silently leaving protection unenforced. There is no
static linkage to an optional native mod or KFF class.

At Loading/Reloading, every expected public native field must be exactly a
`ForgeConfigSpec.BooleanValue`, belong by identity to the current spec, and have
a distinct path. TaCZ's independent `tacz-pre.toml` COMMON preload event is
explicitly excluded; it contains no gameplay terrain flags. The target contracts
are TaCZ COMMON and SBW SERVER. Both the raw config and its policy view must pass
the spec's existing validation before `setConfig()` is called, so installation
does not run correction or autosave.

The policy view supplies false for all ten paths:

| Provider | Native public fields |
| --- | --- |
| TaCZ AmmoConfig | DESTROY_GLASS, IGNITE_BLOCK, EXPLOSIVE_AMMO_DESTROYS_BLOCK |
| SBW VehicleConfig | COLLISION_DESTROY_SOFT_BLOCKS, COLLISION_DESTROY_NORMAL_BLOCKS, COLLISION_DESTROY_HARD_BLOCKS, COLLISION_DESTROY_BLOCKS_BEASTLY |
| SBW ProjectileConfig | PROJECTILE_DESTROY_BLOCKS |
| SBW ExplosionConfig | EXPLOSION_DESTROY, EXTRA_EXPLOSION_EFFECT |

The original `ModConfig.getConfigData()` remains the original watched config;
authored TOML is not rewritten by enforcement. It may contain `true` while native
gameplay getters return `false`. This is a server policy, not a claim that disk
values have changed. Provider fingerprints must use effective ConfigValue getters
rather than infer gameplay state from raw TOML.

On a normal watcher reload, the spec retains its policy view throughout the raw
parse and cache reset; protected reads never consult the watched map. The
Reloading listener also reinstalls the view for supported lifecycle paths that
replace the raw config, including synchronized in-memory server config loading.
All ten guards are checked again after installation. Unloading drops the binding.

`BooleanValue.set(true)` throws before Forge can assign a true cached value;
`set(false)` is an in-memory no-op through the delegate. Unrelated path setters
still delegate to the original config. File-backed views retain the
`CommentedFileConfig` interface and delegate file operations, preserving
`ForgeConfigSpec.save()` for unrelated settings. Nested reads, `valueMap()` and
`entrySet()` expose the same effective guarded values, so validation and
NightConfig copies cannot accidentally read raw true values through those views.
Bulk views are read-only; protected branches cannot be replaced or removed.
Unexpected protected section types fail visibly.

The periodic `enforce()` now checks loaded bindings and native cached/uncached
getters without mutating the watched map. Missing or incomplete policy throws
visibly. Native procurement calls `requireProtected(provider)` and translates a
failure to its ordinary refused-supply result. A failed lifecycle installation
cannot leave a valid native procurement qualification.

This removes the proven guard-induced autosave contention. It does not claim to
repair every possible third-party config writer or malformed external TOML.
Normal unrelated writes and file watching retain upstream Forge semantics.

## Native validation contract

`TerrainConfig43Tests` has three tests in namespace `catancraft_terrain43`.
They require both verified published runtimes, an empty marked seed-zero
GameTest world under `build/controlled-catancraft_terrain43-run/world`, and
file-backed native configs inside that fixture. CI must create this fixture
fresh, with native config files absent before launch, to cover their creation and
initial loading. No owner-world or installed-mod directory is an input.

1. Verify ten actual native getters after initial loading and 32 full cache
   resets/rechecks. Assert authored file bytes are unchanged.
2. Refuse all ten guarded true setters without poisoning caches; accept false
   setters without file writes. Change one unrelated real setting per provider,
   wait for actual FileWatcher Reloading callbacks, verify normal persistence,
   restore original bytes, and observe the restoration reload.
3. Atomically edit each real TOML so all ten authored guards become true and an
   unrelated setting changes. Observe actual watcher reload callbacks, raw true
   values and refreshed unrelated getters, while all native protection getters
   remain false. Recheck cache resets, preserve edited bytes, then restore exact
   originals and observe another reload.

Only successful cases write
`world/data/catancraft_terrain43_{loaded,setters,watcher}.json`, recording actual
paths/values, original and restored file hashes, and observed reload counts. A
timed-out or failed reload remains failed evidence. Parent validation must scan
the full startup/runtime log and reject `ConfigLoadingException`,
`ParsingException`, or FileWatcher exceptions; a three-test count alone cannot
establish a clean watcher run. Published input JAR manifests must be reverified
unchanged after the run. Existing 0.1.42 failure evidence remains retained.

At this checkpoint only static source/API review and `git diff --check` have
completed. Builds and writable native worlds are owned by the parent runner.
