# Red team findings: on-screen tells across a savestate cut

Goal: footage cut from just before a save to just after a restore must be indistinguishable from uncut footage. This file lists every tell found by the red team pass, where it lives in the 26.3 sources (paths relative to `/mnt/work/mc-src-26.3/net/minecraft/`), its class, and its status.

Classes:

- **(a)** visible, and its phase leaks uptime, elapsed time or a hidden counter (the food-jolt kind).
- **(b)** visible discontinuity at the cut (state resets or jumps after a restore).
- **(c)** cosmetic and random in vanilla anyway, so no forensic value.

Evidence comes from `RedTeamGameTest` (`scripts/client-gametest.sh redteam`): each scene is set up with commands, saved with `/savestate save` (or the save key), disturbed for ~200 ticks (walk, turn, take damage, gain items), restored with `/savestate load` (or the menu click), and the frames at the same `Hud.tickCount` offsets (+6, +10, +16, +26, +46 after capture) are diffed with a per-channel fuzz of 24. Diff images (`*-diff.png`, red = differing pixel) sit next to the screenshots. Every scene runs unfrozen, so continued motion after resume is part of the comparison.

## Findings

(in progress)
