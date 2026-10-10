# DreamManhunt

[<img src="https://discordapp.com/assets/e4923594e694a21542a489471ecffa50.svg" alt="Discord" height="55" />](https://discord.swofty.net)

A Fabric mod for Minecraft 26.3 that savestates the entire game: the server, the world and every connected client. Press a key to save, open the menu, and click a savestate to continue from it. Footage cut from a save to its restore matches uncut footage frame for frame, down to the hunger-bar jolts.

> **Note**: Install the mod on the server and on every player's client, runner and hunters alike.

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

### Pseudorandom generators

- Replaces the game's clock-seeded generators with a mod-owned seed stream that is saved and restored
- Level, entity, sound-seed and random-sequence generators on the server
- Client level, particle engine, spell particle and per-entity generators on the client
- Ambient particles: drips, portal swirl, end portal, campfire smoke, fire
- Each particle's own generator
- Music picker and sound variant generators

### Player view

- View bobbing, walk animation, hand swing, hand heights and equip progress
- Item use progress and attack strength
- FOV transition, eye height and smooth camera
- Sprint, portal and nausea effects, water vision
- Darkness, blindness and night vision fades
- Rain fog fade and torch flicker
- Totem animation and elytra wings
- Block breaking cracks

### World visuals

- Every live particle, including emitters and block-break particles
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
- Per-entity tick count, hurt invulnerability, attack cooldown, jump delay, ambient sound timer and XP pickup delay

### Players

- Position, dimension and rotation
- Inventory, ender chest, selected slot, cursor item and 2x2 crafting grid
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

The singleplayer host and players with the `dreammanhunt:manage` permission can save, load and delete. Everyone else gets a read-only menu.

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
scripts/client-gametest.sh [smoke|clientstate|engine|e2e|audio|redteam|forensic|all]
```

## Documentation

- [Design and protocol](docs/DESIGN.md)
- [Client state inventory](docs/client-state-inventory.md)
- [Server state inventory](docs/server-state-inventory.md)

## Credits

Thanks to the Fabric team and all contributors.

## License

See the repository for license details.
