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
| Weather transition | `Level.oRainLevel`, `rainLevel`, `oThunderLevel`, `thunderLevel` | All four are stored per dimension and written back after the level is rebuilt, which otherwise sets them from the weather flags. Each player is sent `RAIN_LEVEL_CHANGE` and `THUNDER_LEVEL_CHANGE` with the restored values after the level info, because the server only broadcasts them when they change. The vanilla packet sets the client's previous and current values to the same number. |
| Entity id counter | `ServerLevel.ENTITY_COUNTER` (static) | Stored and set back before the levels are rebuilt, so the first entity spawned after a restore gets the id it would have got in the uncut timeline. |
| Sound seed RNG | `Level.soundSeedGenerator` (a `ThreadSafeLegacyRandomSource`) | Seed stored per dimension and written back. Picks the variant and pitch seed of every sound the server plays. |
| Level empty timer | `ServerLevel.emptyTime` | Stored per dimension. Decides whether an empty level still ticks entities. |
| Overworld custom spawner timers | `PhantomSpawner.nextTick`, `PatrolSpawner.nextTick`, `CatSpawner.nextTick` | Stored. The spawners are rebuilt with the level, so without this their timers would start from zero. |
| Entity RNG | `Entity.random` for every loaded entity, players included | Seed stored per UUID. A mixin at the tail of `Entity.load` applies it when that entity is next loaded from the restored files. |
| Entity ids | `Entity.id` | Stored per UUID. Entities created from storage (`EntityType.create(ValueInput, …)`, which covers chunk loads, passengers, vehicles and ender pearls from player data) no longer take an id in the constructor. They get a placeholder, and the `Entity.load` hook then gives them their saved id, or a fresh id from the counter if they were not loaded at save time. A restore therefore consumes no ids, and a restored entity keeps the id that client visuals derive from (phantom wing phase, boat passenger facing, minecart jitter). Every new id skips ids that are live in any level, so players who joined after the save, and players who keep their current id (see below), never collide. Rebuilt `ServerPlayer`s are created under the same placeholder rule and then take the player's current id, because a respawn packet cannot change the client's own entity id. A player who relogged after the save therefore keeps the newer id. |
| Entity tick counter | `Entity.tickCount` | Stored per UUID and applied in the same `Entity.load` hook. Drives `tickCount % N` AI checks, ageing and animations. |
| Hurt invulnerability | `Entity.invulnerableTime` | Stored per UUID and applied in the same hook. |
| Attack cooldown | `LivingEntity.attackStrengthTicker` | Stored per UUID and applied in the same hook. Sets the damage of the next melee hit. |
| Idle timer | `LivingEntity.noActionTime` | Stored per UUID and applied in the same hook. Drives mob despawning. |
| Jump delay | `LivingEntity.noJumpDelay` | Stored per UUID and applied in the same hook. |
| Ambient sound timer | `Mob.ambientSoundTime` | Stored per UUID and applied in the same hook. Decides when a mob next makes its idle sound. |
| XP pickup delay | `Player.takeXpDelay` | Stored per UUID and applied in the same hook. |
| Item cooldowns | `Player.cooldowns` (`ItemCooldowns`: per-group start and end tick plus its own `tickCount`) | Stored per player. On restore the exact start, end and `tickCount` are put back on the server, so the server-side fraction is the same as at save time. Clients get one vanilla `ClientboundCooldownPacket` per group with the remaining ticks, so the client cooldown ends on the same tick as the server's. Because that packet only carries a duration, the client overlay starts from a full bar. The exact client fraction is up to the client-state snapshot. |
| Carried item and 2x2 crafting grid | `containerMenu.getCarried()`, `inventoryMenu` crafting slots | Stored per player. If the player's own inventory menu was the open menu at save time, the grid and the carried item are put back exactly and synced to the client with the inventory menu's full sync. The inventory screen itself is not reopened. |
| Carried item and input slots of any other open menu | `getCarried()` and every slot backed by a transient container: `SimpleContainer` (not the ender chest), `TransientCraftingContainer` or `MerchantContainer`. Output slots, which refuse their own item, and mount inventories are skipped. This covers the crafting table, anvil, smithing table, grindstone, stonecutter, loom, enchanting table, cartography table, beacon payment and villager trading. | Stored per player. The restored player starts with no container open, so these items go back into the inventory the way vanilla's close does (`placeItemBackInInventory`, dropped if the inventory is full). The menu itself is not reopened. |
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
| Players | Each online player gets a fresh `ServerPlayer` built through the login and respawn path. The player list, the connection's `player` field, advancements and stats are swapped to the new object. The entity id is kept, and ender pearls and vehicles are respawned from player data. Fabric's `ServerPlayerEvents.AFTER_RESPAWN` fires with `alive = true` for each player. `COPY_FROM` is not fired: Fabric's attachment API listens to it and copies every attachment from the old player, which would overwrite the attachments loaded from the snapshot. |

## Failed restores

A restore first flushes the live game (global saved data, `level.dat`, player stats and advancements, and every level's dirty chunks while it closes) and keeps the live runtime state and each player's live data in memory. It then moves the live world folder aside to `<world>/dreammanhunt/restore-backup-<id>-<time>/` with a rename on the same filesystem, and copies the snapshot in. If any later step throws, the engine detaches whatever players were already attached, closes any half-built levels, moves the backup back, and rebuilds the levels and players from the live state, so the world ends up as it was just before the restore. `restore` then throws a `SavestateException` ending in "the world was rolled back". If the rollback fails too, the backup folder is left in place for manual recovery. After a successful restore the backup is deleted on the IO pool.

Saving the dirty chunks of the old levels is what makes the rollback lossless. At manhunt scale it is the largest part of a restore (about 0.7 s of 0.9 s with 2048 forced chunks across the overworld and nether all dirty), which is still well under the 5 s budget, so it is kept.

## In-memory state that is not captured

| State | Why it is left out |
|---|---|
| Pending block events (`ServerLevel.blockEvents`) | Filled and drained within a single tick, so the queue is empty between ticks, when `save` runs. |
| Navigation and pathfinding (`Mob.navigation` paths, `navigatingMobs`, `PathTypeCache`) | Rebuilt by the AI on the next tick. A mob may pick a slightly different path than it would have without the restore. |
| Goal selector state and Brain memories without a codec (for example `NEAREST_VISIBLE_PLAYER`, `LOOK_TARGET`, `PATH`) | Recomputed by sensors and goals within a few ticks. Memories with a codec are saved with the entity. |
| Look, move and jump controls, `yBodyRot` and `yHeadRot` lag | Re-derived from the saved rotation on load. |
| Sleeping status (`SleepStatus`) | Recomputed from the players every tick. |
| Natural spawner state (`lastSpawnState`, mob caps) | Recomputed every tick. |
| `VillageSiege` progress | Only matters during the midnight siege window. It restarts from idle. |
| `LivingEntity.lastHurtByMob` and its timestamp, `lastHurtByPlayer` | The attacker reference is not saved by vanilla, so the timestamps alone would point at nothing. Revenge targeting and player-kill XP credit start from scratch. |
| Light engine queues, chunk generation and IO futures | Transient work. Lit and generated chunks are already saved. |
| `MinecraftServer.ticksUntilAutosave`, status ping sampling RNG, metrics | Do not affect gameplay. |
| Open menus and screens | Every open menu is closed on restore, and its transient items are returned as described above. Reopening the same menu would mean rebuilding each menu type's provider (block position, villager offers, beacon data), so it is not done. |
| `ServerGamePacketListenerImpl` state (keep-alive, chat chain, teleport ids, chunk batch counters) | Tied to the live connection, which is kept. The usual respawn teleport handshake re-syncs the position. |
| Mod-specific in-memory state of other mods | Not known to the engine. Mods can listen to `ServerLevelEvents.UNLOAD` and `LOAD`, which fire for every level during a restore. |
