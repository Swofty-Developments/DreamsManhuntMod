# Red team findings: on-screen tells across a savestate cut

Goal: footage cut from just before a save to just after a restore must look like uncut footage. This file lists every tell the red team pass found. Each entry gives the location in the 26.3 sources (paths are relative to `/mnt/work/mc-src-26.3/net/minecraft/`), its class and its status.

Classes:

- **(a)** Visible, and its phase leaks uptime, elapsed time or a hidden counter. This is the food-jolt kind.
- **(b)** Visible discontinuity at the cut: some state resets or jumps after a restore.
- **(c)** Cosmetic and random in vanilla anyway, so it has no forensic value.

## How the evidence was gathered

The evidence comes from `RedTeamGameTest`, run with `scripts/client-gametest.sh redteam`. Set `REDTEAM_SCENES=a,b` to run a subset.

Each scene goes through these steps:

1. Set up the scene with commands.
2. Save with `/savestate save`. Some scenes use the real save key instead.
3. Disturb for about 200 ticks: walk, turn, take damage, pick up items.
4. Restore with `/savestate load`. The key scenes use the real menu click instead.
5. Diff the frames at the same `Hud.tickCount` offsets after the capture and after the resume, at +6, +10, +16, +26 and +46 ticks. The diff uses a per-channel fuzz of 24.

The server keeps ticking throughout, so continued motion after resume is part of every comparison. Diff images (`*-diff.png`, red marks a differing pixel) sit next to the screenshots in `build/run/clientGameTest/screenshots/`. The run writes its summary to `build/run/clientGameTest/redteam-results.txt`.

## Findings, ranked

### 1. Block entity animation counters reset on restore (a + b) — fixed

A restore re-sends chunks, and the client rebuilds every block entity from the packet. Each one restarts its client-only animation state from zero. Several of these are free-running counters that started when the chunk loaded on this client, so their phase also shows how long the client has been nearby.

| Block entity | Location | What shows |
|---|---|---|
| Enchanting table | `world/level/block/entity/EnchantingTableBlockEntity.java:21-30,67,99` | Book bob `sin(time*0.1)`, page flutter. With no player nearby the idle book turns `tRot += 0.02`/tick, so its facing encodes ticks since load. |
| Conduit | `ConduitBlockEntity.java:41-44,75,86`, `client/renderer/blockentity/ConduitRenderer.java:97-120` | Cage bob `sin(tickCount*0.1)`, eye phase `tickCount/66 % 3`, cage rotation `activeRotation`. `isActive` is false until `gameTime % 40 == 0`, so the cage shows the inactive shell for up to 2 s. |
| Mob spawner | `world/level/BaseSpawner.java:52-53,85-90` | Display mob spin. |
| Trial spawner, vault | `trialspawner/TrialSpawner.java:255`, `vault/VaultClientData.java:7-20` | Display spin. |
| Powered dragon/piglin head | `SkullBlockEntity.java:53-61` | Jaw/ear animation counter. |
| Bell, chest/ender chest/shulker lids | `BellBlockEntity.java:31-36`, `ChestLidController.java:6-8`, `ShulkerBoxBlockEntity.java:46-49` | Swing and lid state. A chest someone holds open draws closed. |

Evidence (before the fix): `enchanting_table` max 3.1% differing (both books in other poses), `spawners` 1.0% (pig spin plus flames).

Fix: `client/state/extra/BlockEntityAnimations` captures every block entity in the loaded chunks, keyed by position and type. It copies every non-final primitive and enum field of the block entity, and nested `net.minecraft.world.level.*` state holders (`BaseSpawner`, `TrialSpawner` data, `VaultClientData`, `ChestLidController`). The values go back at Apply and again at Resume.

### 2. Beacon beam vanishes for up to 4 s after a restore (b) — fixed

Location: `world/level/block/entity/BeaconBlockEntity.java:72,168-183,297-298,321-334`.

`levels` is written to the update tag but never read back, and `getBeamSections()` returns nothing while `levels == 0`. A rebuilt beacon only recomputes `levels` when `gameTime % 80 == 0`, and it rescans the beam 10 blocks per tick. The beam is missing until both have happened.

Evidence: `beacon` (key + menu path) was 1.7-1.9% differing on every frame, the whole beam.

Fix: the walker above restores `levels` and `lastCheckY`, and rebuilds `beamSections`/`checkingBeamSections` from saved (colour, height) pairs.

### 3. Entity-id-seeded animation phase (a + b) — partly fixed, rest handed off

Entity ids come from the static `ServerLevel.ENTITY_COUNTER` (`server/level/ServerLevel.java:198`). It only ever grows over server uptime, and in singleplayer it survives world reloads within the same JVM. Every entity the restore re-creates gets a new, higher id. Anything drawn from the id therefore jumps at the cut and shows bits of a counter that tracks uptime.

| Visual | Location | Status |
|---|---|---|
| Phantom wing phase `id*3 + age`, and the client flap sound | `world/entity/monster/Phantom.java:105-114`, `client/renderer/entity/PhantomRenderer.java:30` | **Fixed.** `PhantomMixin` and `PhantomRendererMixin` seed it from the UUID. |
| Witch nose wiggle speed `0.01*(id % 10)` | `client/renderer/entity/WitchRenderer.java:32`, `client/model/monster/witch/WitchModel.java:69` | **Fixed.** `WitchRendererMixin` seeds it from the UUID. |
| Animal passenger in a boat faces `yRot + (id%2==0 ? 90 : 270)` | `world/entity/vehicle/boat/AbstractBoat.java:699` | Handed off to **engine**. The code is shared by server and client, so the client cannot reseed it alone. Keeping entity ids stable across a restore (`setId(oldId)` on re-created entities) would close this row and the next two at the root. |
| Minecart ±0.002 z-fight jitter from an id hash | `client/renderer/entity/AbstractMinecartRenderer.java:40-43,110` | Handed off to engine (as above). Sub-pixel. |
| Compass with no target (Nether/End) adds `hash(id + ctx)/2^31` | `client/renderer/item/ItemModelResolver.java:31-35`, `client/renderer/item/properties/numeric/CompassAngleState.java:54` | (c). The needle chases a fresh random target every tick, so the constant offset cannot be recovered. |
| Arrows/stingers stuck in players `RandomSource(id)` | `client/renderer/entity/layers/StuckInBodyLayer.java:65` | Not a tell. `PlayerRestorer` keeps the player id (`setId(old.getId())`). |

### 4. Server rain and thunder level are not restored (b) — handed off to engine

Location: `world/level/Level.java:119-122` (`oRainLevel`/`rainLevel`/`oThunderLevel`/`thunderLevel` on the **server** level). The server only sends `RAIN_LEVEL_CHANGE`/`THUNDER_LEVEL_CHANGE` when its own value changes.

The client puts its captured level back, but the server's level is whatever the disturbance left it at. If the server is already at 1.0, it sends nothing more, and the client sits at the captured value while the uncut timeline keeps darkening. Otherwise the client jumps to the server's post-disturbance value.

Evidence: `rain` and `thunder` match for +6..+16 ticks, then differ by 43-52% of the frame at +26/+46 as the sky brightness diverges. This happens whenever a save lands inside a weather transition.

Fix (engine): capture and restore the four server `Level` fields per dimension.

### 5. Dropped item bob/spin phase re-rolled (b) — fixed

Location: `world/entity/item/ItemEntity.java:55` (`public final float bobOffs = random*2π`), `client/renderer/entity/ItemEntityRenderer.java:37-48`.

The generic entity field copy in `client.state` skips final fields, so every dropped item jumped to a new height and angle.

Evidence: `items_orbs_frames` 1.1-1.6% (all three items).

Fix: `ExtraState` restores `bobOffs` by UUID through a `@Mutable` accessor.

### 6. Compass, recovery compass and clock needles ease in from the pre-restore angle (b) — fixed

Location: `client/renderer/item/properties/numeric/NeedleDirectionHelper.java:38-62`, `CompassAngleState.java:28-36`, `Time.java:23-28`.

There is one wobbler per item model, shared by every compass or clock on screen. It survives restores and world changes. After a restore the needle swings from wherever the disturbance left it (factor 0.8/0.9, about 1-2 s).

Fix: `NeedleDirectionHelperMixin` tracks every standard wobbler, and `NeedleWobblers` restores `rotation`, `deltaRotation` and `lastUpdateTick`. Limitation: wobbler identity exists only in memory. A snapshot loaded after a client restart keeps the live wobbler state.

### 7. Ambient, particle and entity randomness (c in vanilla) — made exact

- `client/multiplayer/ClientLevel.java:571`: `animateTick` makes a fresh `createThreadLocalInstance()` RNG every tick. This drives drips, portal, end-portal, campfire and fire particles.
- `client/particle/Particle.java:36`: each particle seeds its own RNG from `nanoTime`.
- The client level RNG, `ParticleEngine.random`, the `SpellParticle.RANDOM` static and every entity's `random` (squid tentacle speed, chicken flaps, …) were never restored.

None of this can be recovered from footage, but it made every scene with particles or squids diverge within a second of the resume.

Evidence: `nether_portal` 0.9%, `end_portal_gateway` 0.5%, `campfire_fire` 11% (smoke), squid tentacles in `mobs_air_water`.

Fix: `ClientLevelAnimateMixin` and `ParticleRandomMixin` draw seeds from two mod-owned RNGs, and `RandomStates` captures and restores those, the level, particle engine and spell RNGs, and every entity RNG by UUID.

### 8. The local player is not put back exactly when moving at the save (b) — handed off to client-state

Location: `client.state.ClientSnapshot.applyPlayer`. It restores `xo/yo/zo`, rotation and `deltaMovement` only when the server position is within 1e-3 of the captured client position. The server's view of a moving player lags the client by a tick or so. When the player is walking, falling, swimming or pushed by water at the save, the restored camera starts from the server's position with zero velocity.

Evidence: `fluids` (player pushed by flowing water) differs by 5.4-6.6% on every frame, and the whole view is offset by a fraction of a block.

### 9. Hearts blink once after a menu restore (b) — handed off to client-state

Evidence: `beacon` (key + menu path), frame +16 only: the hearts draw with the white blink outline, which is absent in the uncut timeline. `Hud.extractPlayerHealth` (`client/gui/Hud.java:766-776`) starts a blink when health changes while `damageCooldownTime > 0`. A health or damage-cooldown update reaches the client after Resume and re-arms `healthBlinkTime`.

### 10. Scrolling widget labels use absolute uptime (a, screens only) — handed off to net/ui

- `client/gui/ActiveTextCollector.java:77`: labels too wide for their widget scroll with phase `sin(π/2·cos(2π·ms/1000/period))`, period ≥ 3 s. The phase reveals `Util.getMillis()` (uptime) mod the period.
- `client/gui/screens/LoadingDotsText.java:8`: `(ms/300) % 4`.

These only show on screens (pause/options buttons, the savestate menu), not in-world. Fix: route both through `VisualClock`.

### 11. Theoretical RNG-state leaks (c in practice) — closed at the root (see "Randomness at the root")

- `world/entity/LightningBolt.java:46,122`: the bolt shape is fully determined by `seed = random.nextLong()` drawn from a fresh `RandomSource.create()`, which is `seedUniquifier ^ nanoTime` (`world/level/levelgen/RandomSupport.java:14,41`).
- `client/gui/Font.java:34,91`: one session-wide RNG for `§k` glyphs.

In principle both expose nanoTime (uptime since boot) bits, but recovering them from rendered pixels is impractical. Since the root fix, both come from restorable seed streams instead of `nanoTime`, so neither leaks uptime and both repeat exactly after a restore.

### 12. Wall-clock date (by design) — not fixed

- `client/renderer/item/properties/select/LocalTime.java:57-85`: the `local_time` item property, used by the vanilla chest item model for the Christmas texture.
- `client/renderer/blockentity/ChestRenderer.java:51-55`: `SpecialDates.isExtendedChristmas()`.

Both show the real date, and only around 24-26 December.

### Checked and clean

Clouds, rain/snow columns, banners, beacon/gateway beam scroll, end-portal shader `GameTime` (`client/renderer/GlobalSettingsUniform.java:34`, the only jar shader that reads it is `core/rendertype_end_portal.fsh`), stars (fixed seed 10842), moon phase and sunset all derive from server `gameTime`/clock and matched exactly (`clouds`, `night_sky`, `moon_phase` 0.00000). Chunk section fade-in is relative to upload and is waited out by `ViewReadiness`.

## Randomness at the root

Everything random in the game is pseudo-random. Instead of chasing generators one by one, the mod puts back the state that fresh randomness is drawn from, on both sides.

### Every source of fresh randomness in vanilla 26.3

| Source | Call sites | Thread | Visible or simulated effect | Routed through the seed stream? |
|---|---|---|---|---|
| `RandomSupport.generateUniqueSeed()` (`world/level/levelgen/RandomSupport.java:40`, `SEED_UNIQUIFIER * … ^ System.nanoTime()`) | Behind `RandomSource.create()` (34 call sites; every `Entity.random` at `world/entity/Entity.java:255`, `Level.random` at `world/level/Level.java:123`, `Particle.random` at `client/particle/Particle.java:36`, `ParticleEngine`, `Font.random` at `client/gui/Font.java:34`, `EnchantingTableBlockEntity.RANDOM`, compass/clock wobbler RNGs, lightmap flicker, …) and `RandomSource.createThreadSafe()`. Direct worldgen uses: `NoiseBasedChunkGenerator.java:236,468`, `ChunkGenerator.java:346`, `OceanMonumentStructure.java:59`. | Client main, server main, worldgen workers | Entity UUIDs (`Entity.java:289`, `Mth.createInsecureUUID(random)`), lightning bolt shape (`LightningBolt.java:46,122`), particle motion, mob AI rolls on new entities, §k glyphs, page flips | Yes, on the client and server main threads. Other threads keep vanilla behaviour. |
| `RandomSource.createThreadLocalInstance()` (`util/RandomSource.java:27`, `ThreadLocalRandom.current().nextLong()`) | `ClientLevel.java:571` (animateTick ambient particles), `Level.java:117` (`randValue`, lightning/snow/ice chunk positions), `AngerManagement.java:35` (warden), `SpreadPlayersCommand.java:117`, `PlayerSpawnFinder.java:47`. Re-seeded straight away, so harmless: `WeatherEffectRenderer.java:76`, `LevelRenderer.java:1007`. Menus only: `LogoRenderer.java:23`, `TeleportToTeamMenuCategory.java:95`. Off-thread: `QueryThreadGs4.java:284`, `NativeLibrariesBootstrap.java:114`, `EntityZombieVillagerTypeFix.java:21`. | Mixed | Ambient particles, weather tick positions, warden anger delay, spawn placement | Yes, for the five gameplay call sites (call-site wraps). The rest are re-seeded straight away, menu-only or off-thread. |
| `ThreadLocalRandom` | Only inside `RandomSource.createThreadLocalInstance()` | — | — | Covered above |
| `new java.util.Random()` without a seed | None in 26.3 | — | — | — |
| `Math.random()` | `client/gui/render/TextureSetup.java:45`, `com/mojang/renderpearl/api/pipeline/RenderPipeline.java:71` (static sort-key seeds, fixed per session); `ServerConnectionListener.java:225` (lag-simulation jitter) | Class init / netty | Draw-order tie-break key, constant for the whole session, so not a cut tell | No (constant per session) |
| `UUID.randomUUID()` | `CustomBossEvents.java:23` (load), `ServerConnectionListener.java:200` (memory connection), chat/name/skin reports | Server main / client | Not visible | No |
| `SecureRandom` | `server/jsonrpc/security/SecurityConfig.java:13` | — | Not visible | No |
| Seeds from `nanoTime`/`currentTimeMillis` | Only via `generateUniqueSeed` | — | — | Covered above |

### What the mod does

The logic lives in `net.swofty.dreammanhunt.randomroot.SeedStreams`. Its mixins are in `dreammanhunt.randomroot.mixins.json` (environment `*`); the client-only hooks are in `dreammanhunt.clientextra.mixins.json`.

- **Two seed streams.** There is one for the client main thread and one for the server main thread. Each is a `LegacyRandomSource` seeded from `SecureRandom` at startup, so new worlds still get random seeds. On those threads, `generateUniqueSeed()` (`RandomSupportMixin`) and the gameplay `createThreadLocalInstance()` call sites (`ThreadLocalRandomCallsMixin`, `SpreadPlayersCommandMixin`, `ClientLevelAnimateMixin`) draw from the stream. Calls from any other thread (worldgen, netty, IO, sound) stay vanilla.
- **Every generator created on a main thread is registered** in a weak identity set (`RandomSourceRegistryMixin` on all four `RandomSource` implementations). A capture records the stream seed and the internal state of every live generator. A restore puts both back. Long-lived generators (`Font.random`, `EnchantingTableBlockEntity.RANDOM`, compass/clock wobbler RNGs, `ParticleEngine.random`, `SpellParticle.RANDOM`, the level RNG, …) therefore continue exactly where they were. The generator states are kept in memory (64 snapshots per side); the stream seed is persisted.
- **Client entities built from add-entity packets** (`AddEntitySeedMixin`) take their constructor seeds from `hash(uuid) ^ session salt` instead of the stream. Their randomness (lightning bolt shape, squid tentacles, …) then does not depend on when the packet happens to arrive. The salt is captured and restored with the stream.
- **Entities loaded from disk or a snapshot** (`EntityType.create` with `EntitySpawnReason.LOAD`, `EntityTypeMixin`) do not draw from the server stream. Chunk loading after a restore therefore cannot shift the stream.
- **Server hooks.** The stream is captured in `RuntimeState.capture` and re-applied after the restore's level rebuild, through one line each in the engine. It is applied once more at the start of the first unfrozen server tick (`MinecraftServerMixin`), which discards whatever the rebuild, player re-attachment and the barrier consumed.
- **Client hooks.** The stream is captured and applied through `RandomStates`, which is part of `ExtraState`, so it is restored again at Resume.

This closes finding 11 at the root: the bolt shape comes from the UUID that the restored server stream reproduces, and the §k glyph RNG is restored. Scene `lightning_obfuscated` is the proof: two bolts are summoned 1 tick after the save in both timelines, with an obfuscated title and subtitle on screen.

## Scene matrix

(filled in from the latest run)
