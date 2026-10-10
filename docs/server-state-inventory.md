# Server state inventory

What a savestate holds on the server side, and what it leaves out. Restores rebuild every
`ServerLevel` from the copied files, so anything vanilla writes to disk comes back through the
normal load path. This file covers the rest: state that vanilla keeps only in memory.

## How a savestate is stored

`<world>/dreammanhunt/savestates/<id>/`

| File | Contents |
|---|---|
| `world/` | Copy of the world folder after `saveEverything(flush, force)`. Includes `level.dat`, every dimension's `region/`, `entities/`, `poi/`, `data/` (raids, world border, dragon fight, chunk tickets), the global `data/` folder (game rules, weather, world clocks, scoreboard, custom boss bars, `/schedule` timers, random sequences, command storage, maps, stopwatches, wandering trader), and `players/` (data, stats, advancements). `session.lock` and `dreammanhunt/` are skipped. |
| `runtime.nbt` | The in-memory state listed below as captured. |
| `meta.json` | `SavestateInfo.CODEC`. |

## In-memory state that is captured

| State | Where it lives | How it is captured and restored |
|---|---|---|
| Server tick counter | `MinecraftServer.tickCount` | Stored and set back through an accessor. Drives `tickCount % N` schedules such as the autosave and time-sync cadence. |
| Tick rate manager | `ServerTickRateManager`: tick rate, frozen flag, frozen steps left, sprint ticks left, scheduled sprint ticks, previous frozen flag | Stored. Restored through accessors, then `updateStateToClients` and the per-player join sync send it to clients. |
| Level RNG | `Level.random` (a `LegacyRandomSource`) | The raw 48-bit seed is stored per dimension and written back into the `AtomicLong`. This RNG drives random ticks, mob spawning, weather rolls, lightning and most block behaviour. |
| Level LCG | `Level.randValue` | Stored per dimension. Picks the positions for random ticks and lightning. |
| Level empty timer | `ServerLevel.emptyTime` | Stored per dimension. Decides whether an empty level still ticks entities. |
| Overworld custom spawner timers | `PhantomSpawner.nextTick`, `PatrolSpawner.nextTick`, `CatSpawner.nextTick` | Stored. The spawners are rebuilt with the level, so without this their timers would start from zero. |
| Entity RNG | `Entity.random` for every loaded entity, players included | Seed stored per UUID. A mixin at the tail of `Entity.load` applies it when that entity is next loaded from the restored files. |
| Entity tick counter | `Entity.tickCount` | Stored per UUID and applied in the same `Entity.load` hook. Drives `tickCount % N` AI checks, ageing and animations. |
| Hurt invulnerability | `Entity.invulnerableTime` | Stored per UUID and applied in the same hook. |
| Attack cooldown | `LivingEntity.attackStrengthTicker` | Stored per UUID and applied in the same hook. Sets the damage of the next melee hit. |
| Idle timer | `LivingEntity.noActionTime` | Stored per UUID and applied in the same hook. Drives mob despawning. |
| XP pickup delay | `Player.takeXpDelay` | Stored per UUID and applied in the same hook. |
| Level data held in memory | `PrimaryLevelData`: game time, spawn point, default game type, difficulty, difficulty lock, allow commands | Stored. The live `WorldData` is updated in place, because it is a `final` field on the server and is never reloaded from `level.dat`. Game time also sets the deadlines of scheduled block and fluid ticks, which chunks store relative to it. |

## Persisted state that needs special handling on restore

Vanilla never reloads these from disk while the server is running, so the engine handles each one:

| State | Handling |
|---|---|
| Global saved data held in `final` server fields (`RandomSequences`, `WeatherData`, `ServerClockManager`, `CustomBossEvents`, `TimerQueue`) | The `SavedDataStorage` cache is cleared, each object is reloaded from the restored files, and the server field is swapped through a `@Mutable` accessor. The clock manager is re-`init`ed. |
| `Stopwatches`, `CommandStorage` | Reloaded and reassigned. Map data and the map index load lazily from the cleared cache. |
| Game rules | The live `GameRuleMap` stays registered in the storage. Values from the snapshot are applied with `GameRules.setAll(..., server)` so that rule callbacks fire. |
| Scoreboard | Objectives and teams are removed while players are still listed, so clients get the remove packets. The snapshot's `ScoreboardSaveData` is then loaded and each player gets the full scoreboard sync. |
| Boss bars | Every `ServerBossEvent` is tracked in a weak registry and detached from all players before teardown. Custom boss bars are re-added through `onPlayerConnect`. Raid, dragon and mob bars come back through their own tick and tracking logic. |
| Chunk tickets | `TicketStorage` comes back with the level data. `activateAllDeactivatedTickets` runs for each new level, as in `prepareLevels`, which restores forced chunk and portal tickets. Ender pearl tickets are not persisted. They are placed again when a player's pearls respawn from player data. |
| Players | Each online player gets a fresh `ServerPlayer` built through the login and respawn path. The player list, the connection's `player` field, advancements and stats are swapped to the new object. The entity id is kept, and ender pearls and vehicles are respawned from player data. |

## In-memory state that is not captured

| State | Why it is left out |
|---|---|
| `Level.threadSafeRandom`, `Level.soundSeedGenerator` | Only pick sound variants for clients and seeds for some off-thread work. Nothing on the server depends on them afterwards. |
| Pending block events (`ServerLevel.blockEvents`) | Filled and drained within a single tick, so the queue is empty between ticks, when `save` runs. |
| Navigation and pathfinding (`Mob.navigation` paths, `navigatingMobs`, `PathTypeCache`) | Rebuilt by the AI on the next tick. A mob may pick a slightly different path than it would have without the restore. |
| Goal selector state and Brain memories without a codec (for example `NEAREST_VISIBLE_PLAYER`, `LOOK_TARGET`, `PATH`) | Recomputed by sensors and goals within a few ticks. Memories with a codec are saved with the entity. |
| Look, move and jump controls, `yBodyRot` and `yHeadRot` lag | Re-derived from the saved rotation on load. |
| Sleeping status (`SleepStatus`) | Recomputed from the players every tick. |
| Natural spawner state (`lastSpawnState`, mob caps) | Recomputed every tick. |
| `VillageSiege` progress | Only matters during the midnight siege window. It restarts from idle. |
| Light engine queues, chunk generation and IO futures | Transient work. Lit and generated chunks are already saved. |
| `MinecraftServer.ticksUntilAutosave`, status ping sampling RNG, metrics | Do not affect gameplay. |
| Network ids of non-player entities | Reassigned on load. Clients are sent remove packets for the old ids and add packets for the new ones. Player ids are kept. |
| Open container menus and the cursor item | Every open menu is closed on restore. Items in the 2x2 crafting grid and on the cursor at save time are not in player data, so they are lost. |
| `ServerGamePacketListenerImpl` state (keep-alive, chat chain, teleport ids, chunk batch counters) | Tied to the live connection, which is kept. The usual respawn teleport handshake re-syncs the position. |
| Mod-specific in-memory state of other mods | Not known to the engine. Mods can listen to `ServerLevelEvents.UNLOAD` and `LOAD`, which fire for every level during a restore. |
