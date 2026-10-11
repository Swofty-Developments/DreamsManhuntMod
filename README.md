# DreamManhunt

[<img src="https://discordapp.com/assets/e4923594e694a21542a489471ecffa50.svg" alt="Discord" height="55" />](https://discord.swofty.net)

[![Watch the video](https://img.youtube.com/vi/ImLSm3my6eY/maxresdefault.jpg)](https://www.youtube.com/watch?v=ImLSm3my6eY)

A Fabric mod for Minecraft 26.3 that savestates the entire game, create your very own Dream Manhunt!

> **Note**: Install the mod on the server and on every player's client.

## Features

### Savestates

- Save with one key, restore by clicking a savestate in the menu
- Menu shows a thumbnail, name, time saved, in-game day and creator for every savestate
- Singleplayer and dedicated servers
- Every connected client restores at once, and the server waits for all of them before resuming
- Nobody is disconnected during a restore
- A failed restore rolls back to the live world
- `/savestate` commands for players, console and RCON

### Ticks and frames

- `Hud.tickCount`, the client tick counter and the partial tick
- Client tick rate and frozen state
- Server tick count and tick rate manager
- Every animated texture's frame and sub-frame: water, lava, fire, portals, sea lanterns, prismarine and the rest
- Cloud position, sky flash, rain and thunder levels
- A restorable visual clock for everything driven by real time: enchantment glints, ghost hearts, world border, boss bar easing, toasts, subtitles, the spectator hotbar fade

### HUD

- Hunger-bar jolts
- Low-health heart shake
- Regeneration heart wave
- Heart blink, ghost hearts and the damage flash
- Air bubble wobble
- Action bar text, titles and subtitles, with their fade timing
- Chat lines, with their fade-out timing and scroll position
- Held-item name popup
- Item cooldown overlays, including ender pearls
- Boss bars
- Toasts in flight, including advancement and recipe toasts
- Vignette, spyglass zoom and autosave indicator
- F1 hidden HUD and F5 perspective
- Inventory, recipe book, creative tab, chat box and pause screens open at the save, reopened with their state

### Pseudorandom generators

- Replaces the game's clock-seeded generators with a mod-owned seed stream that is saved and restored
- Level, entity, sound-seed and random-sequence generators on the server
- Client level, particle engine, spell particle and per-entity generators on the client
- Ambient particles: drips, portal swirl, end portal, campfire smoke, fire
- Each particle's own generator
- Music picker and sound variant generators
- Lightning bolt shapes and `§k` obfuscated text
- The order block entities draw from shared generators

### Player view

- View bobbing, walk animation, hand swing, hand heights and equip progress
- Item use progress and attack strength
- Velocity and movement state when walking, sprinting, falling or swimming at the save
- Held keys and mouse buttons resynced from physical input on resume
- FOV transition, eye height and smooth camera
- Sprint, portal and nausea effects, water vision
- Darkness, blindness and night vision fades
- Rain fog fade and torch flicker
- Totem animation and elytra wings
- Block breaking cracks

### World visuals

- Every live particle, including emitters and block-break particles
- Server entity trackers and client interpolation, so every entity is drawn where it would be without the cut
- Mob animations: every animation timer, creeper swell, ender dragon flight and wings, and each mob's own animation counters
- Phantom wing and witch nose phases
- Dropped item bob and spin, XP orbs
- Block entity animations: enchanting table book, conduit, spawner and trial spawner mobs, vaults, mob heads, bells, chest and shulker box lids
- Beacon beams
- Compass, recovery compass and clock needles

### Audio

- Music: current track, playback position and next-song delay
- Jukebox records at their playback position
- Every playing sound at its sample offset
- Looping sounds bound to their entities: minecarts, bees, guardians, sniffers, elytra
- Biome ambience, cave mood and underwater ambience

### Server and world

- Every block, block entity, scheduled tick, point of interest and entity in every dimension
- Time, weather, game rules, world border, dragon fight, boss bars and the wandering trader
- Scoreboards, teams and objectives
- Raids, maps, command storage and random sequences
- Phantom, patrol and cat spawner timers
- Rain and thunder transition levels in every dimension
- Mob AI: goals, pathfinding, brain memories and behaviours, combat trackers, and the order entities tick in
- Pistons mid-move, scheduled ticks in their original order, random tick and spawning order
- Every saved chunk and entity ticking again before play resumes
- Entity ids and the entity id counter
- Per-entity tick count, hurt invulnerability, damage window, attack cooldown, jump delay, ambient sound timer and XP pickup delay

### Players

- Position, dimension and rotation
- Velocity, fall distance, fire ticks and air
- Inventory, ender chest, selected slot, cursor item and 2x2 crafting grid
- Items in open menus: crafting table, anvil, smithing table, grindstone, stonecutter, loom, enchanting table, cartography table, beacon and trading
- Items in use: spyglasses, bows, crossbows, food, shields
- Block mining progress on server and client
- Open containers, portal timers, fishing bobbers, chat session and spectator target
- Health, food, saturation and exhaustion
- XP, effects, spawn point, abilities and game mode
- Advancements with criteria progress, recipes and statistics
- Item cooldowns

### Settings

- Every option in `options.txt` except key bindings

## Controls

| Key | Action |
|---|---|
| `K` | Save state |
| `J` | Savestate menu |

Both are under **Controls → DreamManhunt**.

### Cut marker

During a restore, every restoring client draws a solid `#00FF00` bar over the left 6% of the screen, on top of everything. It is on every frozen frame and gone on the first frame of resumed play.

With `saveMarker` on, a `#FF0000` bar of the same shape starts on the first frame after a save is captured and stays for 10 ticks. The capture lands when the server takes the savestate, a tick or more after the save key press.

To cut, remove everything from the first red frame through the last green frame. The first frame after the green bar matches the first red frame without the bar.

Neither bar appears in thumbnails.

## Commands

| Command | Description |
|---|---|
| `/savestate save [name]` | Save a savestate |
| `/savestate load <name\|id>` | Continue from a savestate |
| `/savestate list` | List savestates |
| `/savestate delete <name\|id>` | Delete a savestate |

## Configuration

`config/dreammanhunt.json` on the server:

```json
{
  "allowEveryone": false,
  "permissionLevel": "gamemasters",
  "readyTimeoutSeconds": 20
}
```

The singleplayer host and players with the `dreammanhunt:manage` permission can save, load and delete.

`config/dreammanhunt-client.json` on each client:

```json
{
  "cutMarker": true,
  "saveMarker": false
}
```

| Option | Default | Effect |
|---|---|---|
| `cutMarker` | `true` | Green bar while a restore is frozen |
| `saveMarker` | `false` | Red bar for 10 ticks after a save capture |

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API
- Java 25

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Client gametests run headless:

```bash
scripts/client-gametest.sh [smoke|clientstate|engine|e2e|audio|redteam|forensic|mining|playersync|worldsync|trackingsync|timeline|all]
```

A dedicated server with five headless clients (runner, two hunters, a late joiner and a client without the mod's networking):

```bash
scripts/multiclient-test.sh
```

Results are in `build/multiclient/run-*/harness.log`.

## Documentation

- [Design and protocol](docs/DESIGN.md)
- [Client state inventory](docs/client-state-inventory.md)
- [Server state inventory](docs/server-state-inventory.md)
- [Red team findings](docs/redteam-findings.md)
- [Tick forensics](docs/forensics.md)

## Credits

Thanks to the Fabric team and all contributors.

## License

Use DreamManhunt in a YouTube video and the description must mention **Swofty**. See [LICENSE](LICENSE).
