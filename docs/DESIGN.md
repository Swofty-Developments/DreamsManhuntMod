# DreamManhunt — design contract

Fabric mod for Minecraft **26.3** (Loader 0.19.5, Fabric API 0.162.0+26.3, Mojang names, Java 25).
Installed on the server **and** on every client (runner + hunters).

## What it does

- A keybind (**Save state**, default `K`, rebindable in Controls) saves a savestate of the whole game.
- A keybind (**Savestate menu**, default `J`) opens a screen listing every savestate (thumbnail, name,
  real time, in-game time, who made it). Clicking one continues the game from that savestate.
- A restore puts back *everything*, server and client, for every connected player:
  - Server: every block, entity, block entity, scheduled tick, POI, level data (time, weather, game
    rules, world border, dragon fight, boss bars, …), saved data (scoreboard, raids, maps, command
    storage, random sequences, …), every player (position, dimension, inventory, ender chest, health,
    food/saturation/exhaustion, XP, effects, spawn point, abilities, game mode, recipes,
    **advancements**, stats), tick rate manager state, RNG state where it influences what happens next.
  - Client (each player's own client): every piece of client state that affects what is on screen or
    what happens next, down to `Hud.tickCount` (it seeds the hunger-bar/heart shake RNG:
    `random.setSeed(tickCount * 312871)` in `net.minecraft.client.gui.Hud`), health blink timers,
    enchantment glint phase, texture animation frames, cloud position, fog/light transitions, view
    bobbing, hand/item animations, FOV transitions, client tick rate, and the game settings.
- **Acceptance bar:** footage cut from "just before the save" to "just after the restore" must be
  indistinguishable from uncut footage, including to tick-based forensics such as tick_studio, which
  brute-forces `Hud.tickCount` from hunger-bar jolt patterns to detect cuts and infer uptime. After a
  restore the client must continue from the *saved* tick counters, not from the current ones.

## Build, run, test

- Repo: `/mnt/work/IdeaProjects/DreamManhunt` (branch `main`). Agent worktrees live in
  `/mnt/work/IdeaProjects/DreamManhunt-wt/<name>`.
- Decompiled 26.3 sources (common + client, Mojang names): `/mnt/work/mc-src-26.3/` — grep these,
  don't run genSources.
- Fabric API jars and sources: `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.
- **Pin every CPU-heavy command** to the healthy cores: `taskset -c 2-7,10,11,14-31 ...`. The box has
  faulty cores; never let a build or test touch 0, 1, 8, 9, 12, 13.
- Compile: `taskset -c 2-7,10,11,14-31 ./gradlew --no-configuration-cache compileJava compileClientJava compileGametestJava`
- Client gametests (headless real client, Xvfb, Vulkan on NVIDIA):
  `scripts/client-gametest.sh <name>` where name ∈ `smoke | clientstate | engine | e2e | all`.
  Screenshots: `build/run/clientGameTest/screenshots/`, logs: `build/run/clientGameTest/logs/latest.log`.
  The client gametest API (`ClientGameTestContext`, `TestSingleplayerContext`,
  `TestDedicatedServerContext`, `TestInput`, `takeScreenshot`, `assertScreenshotEquals`,
  `runOnClient`, `computeOnClient`, `getServer().runOnServer/computeOnServer/runCommand`) runs a real
  integrated or dedicated server plus a real client and steps ticks deterministically.
- 26.3 naming notes: the in-game HUD class is `net.minecraft.client.gui.Hud` (it was `Gui`); the
  renderer has Vulkan and OpenGL backends behind `com.mojang.renderpearl`; `Util` gets its time source
  from `RenderSystem.initBackendSystem()`.

## Ownership (do not edit files you don't own)

| Area | Owner | Packages / files |
|---|---|---|
| Server snapshot engine | `engine` agent | `net.swofty.dreammanhunt.server.engine.*`, mixins in `dreammanhunt.server.mixins.json` (package `net.swofty.dreammanhunt.mixin.server`), `src/gametest/.../EngineGameTest.java`, `docs/server-state-inventory.md` |
| Client state capture/restore | `clientstate` agent | `net.swofty.dreammanhunt.client.state.*`, mixins in `dreammanhunt.client.mixins.json` (package `net.swofty.dreammanhunt.client.mixin.state`), `src/gametest/.../ClientStateGameTest.java`, `docs/client-state-inventory.md` |
| Networking, coordinator, keybinds, GUI, commands, config, thumbnails | `net` agent | `net.swofty.dreammanhunt.network.*`, `net.swofty.dreammanhunt.server.coordinator.*`, `net.swofty.dreammanhunt.client.ui.*`, `net.swofty.dreammanhunt.client.network.*`, mixins in `dreammanhunt.common.mixins.json` (package `net.swofty.dreammanhunt.mixin.common`) and `dreammanhunt.clientui.mixins.json` (package `net.swofty.dreammanhunt.client.mixin.ui`), `DreamManhunt.java`, `DreamManhuntClient.java`, lang file, `src/gametest/.../EndToEndGameTest.java` |
| Shared contract | integrator only | `api/SavestateInfo.java`, the public signatures of `SavestateEngine` and `ClientStateManager`, `build.gradle`, `fabric.mod.json`, `docs/DESIGN.md` |

You may add private helpers, new classes and resources inside your own packages freely. Prefer
`@Accessor`/`@Invoker` mixins over access wideners (no shared AW file). If you truly need a contract
change, make the smallest additive change and call it out in your final report.

## Contracts

### Server engine — `net.swofty.dreammanhunt.server.engine.SavestateEngine`

All methods are called on the server thread.

```java
static SavestateEngine get(MinecraftServer server);
SavestateInfo save(String id, String name, @Nullable ServerPlayer creator) throws SavestateException; // blocking snapshot of the current tick
List<SavestateInfo> list();                // oldest first
Optional<SavestateInfo> find(String idOrName);
void restore(String id) throws SavestateException; // see below
boolean delete(String id);
```

- Savestate ids are random UUID strings chosen by the caller. Storage: `<world dir>/dreammanhunt/savestates/<id>/`, with
  `meta.json` holding `SavestateInfo.CODEC`.
- `save` captures the state as of the end of the previous tick (it is called while handling a packet
  or command, before this tick's world ticking). It must not change game state, kick anyone, or
  leave the server in a different state than before.
- `restore` returns once the world and every online player have been put back and every packet the
  clients need to rebuild their view (respawn/level info, chunks as the chunk sender delivers them,
  entities, player info, inventories, health/food, XP, effects, abilities, advancements, recipes,
  tick state, time, weather, world border, boss bars, scoreboard) has been queued or will be sent by
  the normal tracking machinery. Nobody is disconnected. Clients must end up with no stale blocks or
  entities from the pre-restore world. A loading screen on clients during the restore is acceptable.
- Players online now but absent from the snapshot keep their current data and stay where they are.
- Players in the snapshot but offline now get their saved playerdata written back so they load the
  restored state when they join.
- Performance target: save < 2 s and restore < 5 s for a fresh manhunt-sized world
  (a couple of thousand chunks across overworld + nether).

### Client state — `net.swofty.dreammanhunt.client.state.ClientStateManager`

All methods are called on the client main thread.

```java
static ClientStateManager get();
void capturePending();                 // initiator pressed Save: capture right now into a pending slot
void onCapture(String stateId);        // server broadcast: bind a fresh pending capture (< 2 s old) to this id, else capture now; persist
boolean has(String stateId);
void delete(String stateId);
void onRestoreBegin(String stateId);   // server is about to restore: stop local simulation/input
void onRestoreApply(String stateId, Runnable sendReady); // all restore packets queued before this one have been handled;
                                       // wait until the view is fully rebuilt (level loaded, chunks around the player received,
                                       // visible sections compiled; timeout ~15 s), apply the snapshot (everything except the
                                       // timing counters), then call sendReady exactly once
void onRestoreResume(String stateId);  // server resumed: set the timing counters (Hud.tickCount etc.), resume simulation
boolean isRestoring();
```

- Client snapshots are stored per client at `<game dir>/dreammanhunt/client_states/<id>.nbt`.
- A client with no snapshot for the id still goes through Begin/Apply/Resume and calls `sendReady`.
- Settings restore covers `Options` values except key mappings.

### Protocol (owned by the `net` agent)

C2S: `SaveRequest(name)`, `ListRequest`, `RestoreRequest(id)`, `DeleteRequest(id)`, `RestoreReady(id)`.
S2C: `StateList(list<SavestateInfo>, canManage)`, `Capture(id)`, `RestoreBegin(id)`,
`RestoreApply(id)`, `RestoreResume(id)`, `Notice(text)`.

Save:
1. Initiating client sends `SaveRequest`.
2. Server (permission check, not busy): generates `id`, sends `Capture(id)` to every client with the mod,
   then `engine.save(id, name, player)` on the same tick. Every client, the initiator included, captures
   when `Capture(id)` arrives, so all snapshots line up with the server's tick. If the save fails, clients
   drop the id (`Discard(id)`).
3. Each client stores a thumbnail (downscaled screenshot) for the id.

Restore:
1. Server: mark busy, send `RestoreBegin(id)` to all, then `engine.restore(id)`.
2. Server: remember the restored tick-rate state, freeze level ticking with the tick rate manager and
   block player ticking (`ServerPlayer` tick/doTick) so nothing advances while clients rebuild.
3. Server: send `RestoreApply(id)` to all modded clients.
4. Each client: `onRestoreApply` → when ready, `RestoreReady(id)`.
5. Server: when every modded client is ready (or 20 s timeout), unblock players, put the restored
   tick-rate state back, send `RestoreResume(id)`, clear busy.
6. Each client: `onRestoreResume` → timing counters set, simulation resumes.

Vanilla clients are tolerated (they just get the server restore). A modded client on a server
without the mod shows a notice instead of doing anything.

Permissions: allowed if the player is the singleplayer owner or has gamemaster-level permission
(configurable in `config/dreammanhunt.json`, which can also allow everyone).

## Working rules

- Work only in your worktree and branch. Commit with Conventional Commits (lowercase imperative
  description, no bullets, **no Co-Authored-By or any Claude attribution**). Several small commits.
- Read the actual 26.3 sources before writing a mixin; every mixin target must exist in 26.3.
- Keep code comments sparse and useful; no boilerplate javadoc.
- Verify with the client gametests and look at the screenshots yourself.
