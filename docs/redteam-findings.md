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

### 3. Entity-id-seeded animation phase (a + b) — fixed (client UUID seeding plus engine id restore)

Entity ids come from the static `ServerLevel.ENTITY_COUNTER` (`server/level/ServerLevel.java:198`). It only ever grows over server uptime, and in singleplayer it survives world reloads within the same JVM. Before main `bca5376`, every entity the restore re-created got a new, higher id. Anything drawn from the id therefore jumped at the cut and showed bits of a counter that tracks uptime. The engine now restores ids and the counter, which closes the discontinuity. The phantom and witch visuals are additionally seeded from the UUID, because even an exact id still carries bits of the number of entities created since the server started.

| Visual | Location | Status |
|---|---|---|
| Phantom wing phase `id*3 + age`, and the client flap sound | `world/entity/monster/Phantom.java:105-114`, `client/renderer/entity/PhantomRenderer.java:30` | **Fixed.** `PhantomMixin` and `PhantomRendererMixin` seed it from the UUID. |
| Witch nose wiggle speed `0.01*(id % 10)` | `client/renderer/entity/WitchRenderer.java:32`, `client/model/monster/witch/WitchModel.java:69` | **Fixed.** `WitchRendererMixin` seeds it from the UUID. |
| Animal passenger in a boat faces `yRot + (id%2==0 ? 90 : 270)` | `world/entity/vehicle/boat/AbstractBoat.java:699` | **Fixed by engine** (main `bca5376`). Saved entity ids and `ENTITY_COUNTER` are restored, so re-created entities keep their ids. |
| Minecart ±0.002 z-fight jitter from an id hash | `client/renderer/entity/AbstractMinecartRenderer.java:40-43,110` | **Fixed by engine** (as above). Sub-pixel. |
| Compass with no target (Nether/End) adds `hash(id + ctx)/2^31` | `client/renderer/item/ItemModelResolver.java:31-35`, `client/renderer/item/properties/numeric/CompassAngleState.java:54` | (c). The needle chases a fresh random target every tick, so the constant offset cannot be recovered. |
| Arrows/stingers stuck in players `RandomSource(id)` | `client/renderer/entity/layers/StuckInBodyLayer.java:65` | Not a tell. `PlayerRestorer` keeps the player id (`setId(old.getId())`). |

### 4. Server rain and thunder level are not restored (b) — fixed by engine (main `bca5376`)

Location: `world/level/Level.java:119-122` (`oRainLevel`/`rainLevel`/`oThunderLevel`/`thunderLevel` on the **server** level). The server only sends `RAIN_LEVEL_CHANGE`/`THUNDER_LEVEL_CHANGE` when its own value changes.

The client puts its captured level back, but the server's level is whatever the disturbance left it at. If the server is already at 1.0, it sends nothing more, and the client sits at the captured value while the uncut timeline keeps darkening. Otherwise the client jumps to the server's post-disturbance value.

Evidence: `rain` and `thunder` match for +6..+16 ticks, then differ by 43-52% of the frame at +26/+46 as the sky brightness diverges. This happens whenever a save lands inside a weather transition.

Fix (engine, landed in main): the four server `Level` fields are stored per dimension and re-sent to each player after the restore (`docs/server-state-inventory.md`, "Weather transition").

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

Fix: closed at the root, see "Randomness at the root". `ClientLevelAnimateMixin` routes `animateTick` through the client seed stream, particle RNGs come from that stream, and `RandomStates` also restores the level, particle engine, spell particle and per-entity RNG states by UUID.

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

### 13. Spawner display mob fails to load on the client since main `bca5376` (b) — handed off to engine

Every frame, `BaseSpawner.getOrCreateDisplayEntity` (`world/level/BaseSpawner.java:259`) on the **Render thread** goes through the engine's `EntityType.create` id-deferral wrapper and `Entity.load` hook. `EntityRuntimeRestorer.apply` (`server/engine/EntityRuntimeRestorer.java:35`) then calls `getId()` before an id is assigned and throws `IllegalStateException: Tried to access entity ID before ID assignment`.

The spawner cage renders empty, and the log fills with thousands of stack traces. In the matrix this does not show as a diff only because it is equally broken in both timelines. The fix belongs to the engine: skip the runtime hook on client levels, or for entities that have no id yet.

### 14. Restoring into the Nether kills the player in lava (b) — handed off to engine

Scenes `nether` and `nether_compass_clock` build a sealed glass box at Nether y 68-80 before the save. In the uncut timeline the player stands inside it. After the restore, the player "tried to swim in lava" between +10 and +16 ticks and gets the death screen (64-75% of the frame differs). The log shows `Player0 standing on air - force-sending blocks below`. The Nether blocks or the player position around the restore need checking.

### 15. Open screens are not restored (b) — handed off to client-state

When the save happens with the inventory open (`inventory_glint`), the resumed game has the screen closed (97-98% differs). The death screen (`elytra`, where the setup fall killed the player) comes back with its 20-tick button delay restarted (`client/gui/screens/DeathScreen.java`, `delayTicker`), so its buttons render disabled.

### 16. Active item use is dropped by the restore (b) — handed off to engine and client-state

`spyglass` saves while the spyglass is in use, with the use key still held. After the restore the scope overlay is gone and the item is lowered (60% differs). The client re-applies `startUsingItem`, but the restored server player is not using the item and stops the use. The use key is held through the restore and is never re-pressed, so vanilla never restarts it. The same applies to bows, eating, shields and tridents.

### 17. Server and client tick phase shifted by one tick after some restores (b) — fixed (coordinator, `e47dc0c`)

Location: `server/coordinator/SavestateCoordinator.afterTick` (main `42ef0aa`).

Once the barrier also waited for chunk delivery, it could complete from the end-of-tick hook (`tickChildren` TAIL). There it sent `RestoreResume` one level tick earlier, relative to the server, than both the ready-packet path (head of the tick, right after `processQueuedPackets`) and the save. When that happened, the resumed client ran a tick ahead of the server.

Every server-driven value then arrived a tick late. Client game time (sky, clouds, rain column scroll), rain levels and rain splash counts all differed. The debug trace showed `clientGameTime 374` (uncut) vs `373` (restored) with every RNG state identical.

This caused the flaky `snow`/`rain` residual: whichever scene's restore had to wait for chunks showed it, and it was 8.6% in the final run. Fix: the completion check moved from `afterTick` to a new `beforeTick` at the head of `tickServer`.

### 18. Block entity ticker order changes after a restore (b) — fixed (`10ab7ad`)

Location: `world/level/Level.java:110-112` (`blockEntityTickers`, `pendingBlockEntityTickers`).

A restore re-adds block entities in chunk-packet (client) or chunk-load (server) order. Tickers that share the level RNG then draw from it in a different order. Campfire smoke (`CampfireBlockEntity.particleTick` uses `level.random`), spawners and vaults all diverge, while the RNG states sampled between ticks stay identical.

This caused the `campfire_fire` regression after the main merge (6.3-9.8%). Fix: `randomroot.TickerOrder` captures the ticker order by position and re-sorts the lists, on the client at Resume and on the server at the first unfrozen tick.

### 19. §k glyphs depended on frame count and render path (b) — fixed (`2f5cd8f`)

Fabric's screenshot renders through `GameRenderer.extract/render` with a fixed `DeltaTracker`, not through `Minecraft.renderFrame`, so the per-frame reseed was skipped there. Real play has the same problem whenever frame counts per tick differ.

`GlyphSeedMixin` now reseeds `Font.random` at the head of `GameRenderer.extract` from `mix(session salt ^ mix(clientTickCount, partial tick bits))`. Glyphs still change every frame, as in vanilla, but they are a pure function of game time (tick + partial tick), like everything else on screen. `lightning_obfuscated` went from 5.7% to 0.000%.

### 20. Spawner scene diverged through spawned pigs (b) — closed by ticker order and the engine round 4

`spawners` spawns pigs (`RequiredPlayerRange 16`). Before the ticker-order fix their positions and AI differed (21% at +26 ticks). After the ticker-order fix and main `5d0c035` (spawner display mobs), it is 0.4%: sub-pixel flame and pig edges.

### 21. Server beacon runtime state is not restored (b) — handed off to engine

Location: `world/level/block/entity/BeaconBlockEntity.java:72-73,121-183,321-334`.

On the server, `levels`, `lastCheckY` and the beam scan lists are not read back from NBT. The rebuilt beacon re-scans from scratch and recomputes `levels` on the next `gameTime % 80 == 0`. Activation (`BEACON_ACTIVATE` sound, the `CONSTRUCT_BEACON` advancement with its chat line and toast) therefore fires at a different tick than in the uncut timeline.

Evidence: `beacon` (key + menu path) is 3.1% at +10 ticks, from the "Bring Home the Beacon" chat line appearing earlier in the restored timeline. The toast carries over into the next scene's frame (`conduit` 1.2%, toast only). The client side is restored (finding 2). The server needs the same: capture `levels`, `lastCheckY`, `beamSections` and `checkingBeamSections` per beacon and put them back after the level rebuild.

### 22. Static client generators created off the client thread (b) — fixed (`c98461c`)

`EnchantingTableBlockEntity.RANDOM` (page flips) and `SpellParticle.RANDOM` are class-initialised by whichever thread touches the class first, often the integrated server thread. The client generator registry therefore never saw them. Both are now restored explicitly. `enchanting_table` went from 0.76% to 0.000%.

### 23. Compass and clock random spin in the Nether still differs (b) — open, handed off to client-state

`nether_compass_clock` is 1.3-1.8% and `recovery_compass` (no death location, so it spins) is 0.4-1.5%. Only the needle pixels differ. The spin comes from `CompassAngleState.random` / `Time.randomSource` through the shared no-target wobbler (`client/renderer/item/properties/numeric/CompassAngleState.java:49-55`, `Time.java:22-36`). Those RNGs are created while models bake on worker threads, so the registry misses them.

Restoring their seeds together with the wobblers (commit `0c2b76f`, reverted in `b3bf57a`) did not change the result and made `recovery_compass` worse. The update must therefore also run at a different moment relative to `level.getGameTime()` in the two timelines. Likely causes: frames rendered during the barrier with a server-set game time, or the restore's two applies. A per-frame trace of `noTargetWobbler.lastUpdateTick` against `gameTime` in both timelines should show it.

### 24. Nether scene setup (test bug) — fixed (`bd1c8cc`)

The box was filled before the Nether chunks were loaded ("That position is not loaded"), so both timelines died in lava. The scene now force-loads, waits for the chunks and checks the box exists. On main `5d0c035` (engine round 4), `nether` is 0.000% at every frame, with player position, camera, dimension and health identical per frame (20.0 in both). No lower-health or early-death difference remains.

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
- **Client hooks.** The stream is captured last in `RandomStates` (part of `ExtraState`), so nothing the capture itself touches can draw from it afterwards. At Apply and Resume it is scheduled, then applied at the start of the first client tick after Resume (`MinecraftThreadMixin`). Anything the restore path consumes (audio replay, particle copies, re-created entities) is discarded. A reflective read once forced `SpellParticle.<clinit>` during the capture and shifted every later particle by one draw. The draw trace (`REDTEAM_SEEDDEBUG=1`, which lists the call site of every stream draw between the save/resume and +6 ticks) found it.
- **§k glyphs.** `Font.random` is consumed once per obfuscated glyph per rendered *frame*, so its state depends on how many frames the game rendered. That count varies with frame pacing, even in vanilla. The mod reseeds it at the start of every frame from a per-tick seed drawn from the client stream, mixed with the frame's partial tick. Obfuscated glyphs still change every frame as in vanilla, but they are now a pure function of (tick, partial tick), like everything else on screen.

This closes finding 11 at the root: the bolt shape comes from the UUID that the restored server stream reproduces, and the §k glyph RNG is restored. Scene `lightning_obfuscated` is the proof: two bolts are summoned 1 tick after the save in both timelines, with an obfuscated title and subtitle on screen.

## Scene matrix

"Before fixes" is run 2 (main at `55cf7f0` plus the test only). Scenes from `underwater` onwards were not reached in that run because the original Nether setup killed the player. "After fixes" is the final run on this branch merged with main `d7174a2` (block entity, item bob, wobbler, uuid seeding and root randomness fixes). Values are the maximum fraction of differing pixels over the five frames (fuzz 24). The full log is `build/redteam-final.log`, and the per-frame values are in the `[redteam]` lines.

| Scene | Path | Before fixes (max differing) | After fixes (max differing) |
|---|---|---|---|
| `baseline` | command | 0.000% (run 1) | 0.000% |
| `nether_portal` | command | 0.854% | 0.194% |
| `end_portal_gateway` | command | 0.515% | 0.000% |
| `beacon` | key + menu | 1.924% | 0.091% |
| `conduit` | command | 0.043% | 0.011% |
| `enchanting_table` | command | 3.089% | 0.764% |
| `spawners` | command | 1.016% | 0.016% |
| `campfire_fire` | command | 11.386% | 6.342% |
| `fluids` | command | 6.635% | 6.723% |
| `items_orbs_frames` | key + menu | 1.617% | 0.000% |
| `xp_orbs` | command | 0.197% | 0.019% |
| `armor_stands` | command | 0.567% | 0.000% |
| `mobs_land` | key + menu | 0.600% | 0.169% |
| `mobs_air_water` | command | 0.679% | 0.368% |
| `third_person_self` | command | 0.237% | 0.018% |
| `night_sky` | command | 0.000% | 0.000% |
| `moon_phase` | command | 0.000% | 0.000% |
| `sunset` | command | 0.025% | 0.025% |
| `clouds` | command | 0.000% | 0.000% |
| `rain` | command | 51.541% | 0.000% |
| `snow` | command | 0.700% | 8.626% |
| `thunder` | command | 48.951% | 0.000% |
| `underwater` | command | not run | 0.639% |
| `nether` | command | not run | 64.198% |
| `end_sky` | command | not run | 0.031% |
| `blindness` | command | not run | 0.000% |
| `darkness` | command | not run | 0.000% |
| `nausea` | command | not run | 0.000% |
| `night_vision` | command | not run | 0.000% |
| `inventory_glint` | command | not run | 98.377% |
| `f3_debug` | command | not run | 0.047% |
| `f1_hidden` | command | not run | 0.000% |
| `low_health_shake` | key + menu | not run | 0.382% |
| `elytra` | command | not run | 9.418% |
| `spyglass` | command | not run | 59.973% |
| `map_item` | command | not run | 0.000% |
| `compass` | command | not run | 0.438% |
| `recovery_compass` | command | not run | 0.166% |
| `clock` | command | not run | 0.000% |
| `lodestone_compass` | command | not run | 0.791% |
| `nether_compass_clock` | command | not run | 74.647% |
| `walking` | command | not run | 7.762% |
| `id_phase_mobs` | command | not run | 0.169% |
| `heads_bell_shulker` | command | not run | 0.000% |
| `lightning_obfuscated` | command | not run | 5.739% |
| `hotbar_pop` | command | not run | 5.591% |

All small residuals above 0 (≤0.8%) that were inspected are single sub-pixel edges or one changed particle. The rest were not inspected one by one in the time box.

### Follow-up runs

"Follow-up full run" is this branch with findings 17-19 and 22 fixed, before merging main `5d0c035`. The Nether scenes in it still used the broken setup. "Targeted run" is on main `5d0c035` with the fixed Nether setup. The logs are `build/redteam-final2.log` and the `[redteam]` lines.

| Scene | Previous report | Follow-up full run | Targeted run on main `5d0c035` |
|---|---|---|---|
| `baseline` | 0.000% | 0.000% | — |
| `nether_portal` | 0.194% | 0.000% | — |
| `end_portal_gateway` | 0.000% | 0.000% | — |
| `beacon` | 0.091% | 3.118% | 0.292% |
| `conduit` | 0.011% | 1.184% | — |
| `enchanting_table` | 0.764% | 0.000% | — |
| `spawners` | 0.016% | 2.664% | 0.413% |
| `campfire_fire` | 6.342% | 0.000% | — |
| `fluids` | 6.723% | 7.083% | — |
| `items_orbs_frames` | 0.000% | 0.169% | — |
| `xp_orbs` | 0.019% | 0.019% | — |
| `armor_stands` | 0.000% | 0.000% | — |
| `mobs_land` | 0.169% | 0.194% | — |
| `mobs_air_water` | 0.368% | 0.341% | — |
| `third_person_self` | 0.018% | 0.018% | — |
| `night_sky` | 0.000% | 0.000% | — |
| `moon_phase` | 0.000% | 0.000% | — |
| `sunset` | 0.025% | 0.025% | — |
| `clouds` | 0.000% | 0.000% | — |
| `rain` | 0.000% | 0.000% | — |
| `snow` | 8.626% | 0.000% | — |
| `thunder` | 0.000% | 0.000% | — |
| `underwater` | 0.639% | 0.477% | — |
| `nether` | 64.198% | 45.699% | 0.000% |
| `end_sky` | 0.031% | 0.268% | — |
| `blindness` | 0.000% | 0.000% | — |
| `darkness` | 0.000% | 0.000% | — |
| `nausea` | 0.000% | 0.000% | — |
| `night_vision` | 0.000% | 0.000% | — |
| `inventory_glint` | 98.377% | 98.416% | — |
| `f3_debug` | 0.047% | 0.038% | — |
| `f1_hidden` | 0.000% | 0.000% | — |
| `low_health_shake` | 0.382% | 0.382% | — |
| `elytra` | 9.418% | 21.867% | — |
| `spyglass` | 59.973% | 59.973% | — |
| `map_item` | 0.000% | 0.000% | — |
| `compass` | 0.438% | 0.438% | 0.438% |
| `recovery_compass` | 0.166% | 0.408% | 1.556% |
| `clock` | 0.000% | 0.000% | 0.000% |
| `lodestone_compass` | 0.791% | 0.774% | 0.699% |
| `nether_compass_clock` | 74.647% | 60.820% | 1.780% |
| `walking` | 7.762% | 7.762% | — |
| `id_phase_mobs` | 0.169% | 0.000% | — |
| `heads_bell_shulker` | 0.000% | 0.000% | — |
| `lightning_obfuscated` | 5.739% | 0.000% | — |
| `hotbar_pop` | 5.591% | 0.000% | — |
