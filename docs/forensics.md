# Tick forensics check

`ForensicGameTest` (`scripts/client-gametest.sh forensic`) checks independently that a savestate
restore leaves no trace that tick forensics can find. It works the way tick_studio does: read the
hunger-bar and heart jolts from the picture, brute-force `Hud.tickCount` from each jolt, then plot
video time against cracked uptime. A cut shows up as a break in that line.

Code: `src/gametest/java/net/swofty/dreammanhunt/test/ForensicGameTest.java` and
`src/gametest/java/net/swofty/dreammanhunt/test/forensic/`.

## What the HUD leaks (26.3)

`Hud.extractPlayerHealth` (`client/gui/Hud.java:764`) runs every frame in survival:

1. `random.setSeed(tickCount * 312871)`. The product is an `int` multiply, so it wraps before it is
   widened to `long`. `random` is a `LegacyRandomSource` (the Java LCG:
   `seed = (seed ^ 0x5DEECE66D) & (2^48-1)`, `next(bits) = seed >>> (48-bits)` after each step).
2. `extractArmor` draws without touching the RNG.
3. `extractHearts` walks the containers from the highest index down to 0. If
   `ceil(health) + ceil(absorption) <= 4`, each container does `yo += random.nextInt(2)`
   (0 or 1 pixel down).
4. If the player has no vehicle with hearts, `extractFood` draws the 10 food icons, rightmost first.
   If `saturation <= 0` and `tickCount % (food * 3 + 1) == 0`, each icon does
   `yo += random.nextInt(3) - 1` (-1, 0 or +1).
5. Air bubbles come after food and only touch the RNG when every bubble is empty, so they never
   change the heart or food values.

`nextInt(2)` is the power-of-two path (`(2 * next(31)) >> 31`, the top bit of the new seed).
`nextInt(3)` is the rejection loop (`sample % 3`, retried while `sample - mod + 2` overflows).
`JoltModel` implements exactly this. At start-up the test checks it against the game's own
`RandomSource.create(tick * 312871)` on 200,000 ticks (the first 100,000 plus 100,000 random
ints, negatives included).

With food 0 and saturation 0 the period is 1, so the food bar jolts on every tick. At 4 HP or less
all 10 hearts shake on every frame too. One frame then carries 10 heart bits plus 10 food trits,
about 25.9 bits.

## Method

### Recording

`FrameRecorder` hooks `START_CLIENT_TICK` and queues `Screenshot.takeScreenshot` on the main render
target. At that point the target still holds the last frame rendered after the previous tick, so
the result is one frame per client tick, a 20 fps "video" of the real game loop. This avoids
`context.takeScreenshot`, which renders its own frame and waits 1-2 ticks for the readback, so it
cannot sample every tick. For each frame the recorder stores:

- a PNG crop of the HUD band;
- a full screenshot every 10th frame (every 20th during validation);
- an independent recorder clock: its own count of `START_CLIENT_TICK` calls, which the mod never
  touches;
- the real `Hud.tickCount` at that moment. This is ground truth, used only to score the decoder
  and never by the cracker.

### Decoder (pixels only)

`HudDecoder` knows two things: the GUI layout and the vanilla sprites. The layout is
`xLeft = guiWidth/2 - 91`, `xRight = guiWidth/2 + 91` and `y = guiHeight - 39`. The
sprites are `hud/heart/*` and `hud/food_*`, loaded from the resource manager. The test runs at GUI
scale 1 on the 854x480 gametest window. At scale 2, the first-person arm covers the rightmost food
icon. For each of the 10
heart slots and 10 food slots, it tries every vertical shift (hearts -1..2, food -2..2) and every
plausible sprite stack:

- hearts: container, plain or blinking, with or without a ghost overlay and a full or half overlay;
- food: empty, half or full, plain or hunger.

It scores the fraction of the stack's opaque pixels that match the screenshot within 12 per
channel. A pixel matches if it equals either the sprite colour or its sRGB-decoded value. The 26.3
GL path writes GUI sprites to the main target linearised: the empty heart's `#6e6e6e` interior
reads back as `#282828`, while the sky keeps its normal colours. A slot decodes only if its best score is at least 0.85 and beats every other shift by at
least 0.15. The sprite stacks also give health and food levels, which select the predictor
conditions (hearts shake when health is 4 or less, food period `food*3+1`). A frame counts as a
jolt frame when at least one food icon is displaced. Saturation can't be seen, so a jolt is what
tells the analyst it is 0.

### Cracking

`TickCracker` brute-forces every `t` in `[0, 2^31)` (all non-negative `int` values, about 3.4
years of client uptime; no prior on the range) in parallel. It keeps every `t` whose predicted
hearts and food match the decoded frame exactly. Over that range one frame is not unique (about 36
false candidates are expected), so, as tick_studio does, the line is found across frames. Every
candidate votes for the offset `t - videoFrame`. Any offset with at least 5 votes is a line. Each
frame's cracked uptime is its candidate on the best-supported line through it. A break is any pair
of consecutive video frames whose cracked uptimes don't differ by exactly the frames elapsed.

### Scenario (singleplayer, real save/restore path)

The test sets easy difficulty (no starvation damage at 4 HP), survival, GUI scale 1, hidden chat,
and the camera looking straight up so the HUD sits on open sky. It then sets 4 HP, food 0 and
saturation 0.

1. **Validation**: records 120 frames and checks the decoder against `Hud.tickCount`.
2. **Restore run**: records 50 frames, then presses the real Save key (`SavestateKeys.SAVE` via
   `TestInput`) and waits for the savestate, thumbnail and client snapshot. It then mutates the
   world: gold block, diamonds in slot 0, 14 HP, food 17 with saturation 3, a 12-block teleport
   and `damage @p 2`. After 40 ticks it opens the menu with the real Menu key, clicks the entry's
   thumbnail, waits for the resume, and records 60 more frames.
3. **Control run**: the same as the restore run, except that the tick restore is skipped. At the
   first tick after resume the recorder sets `Hud.tickCount` back to the live value it had before
   the restore froze it. Everything else is still restored.

The edited video is what an editor would cut. It runs from the start of the recording to the
frame rendered right after the save key was handled, which shows the captured state. Then it
continues from the first frame rendered after the first post-resume tick. The frames in between
(the save hitch, the mutation, the menu and the restore) are dropped, and the two parts are
joined one frame apart.

## Results

Numbers are from a full run on 2026-10-11, rebased on main `d7174a2`. Tick values differ from
run to run; the pass/fail pattern does not.

| Check | Result |
|---|---|
| Predictor vs `LegacyRandomSource` | 200,000 / 200,000 seeds identical |
| Decoder on 120 frames with known `Hud.tickCount` | decoded pattern equals the prediction on 120/120 (min template score 1.000, min margin to the next shift 0.389) |
| Brute force on the same frames, `[0, 2^31)` per frame | true tick among the candidates on 120/120; mean 36.9 candidates per frame; unique by itself within `[0, 2^20)` on 118/120 |
| Line fit on the validation frames | one line through all 120 frames; offset 147 = the real `Hud.tickCount` at frame 0 |
| **Restore** (real keys and menu) | save at recorder tick 278, resume at 328. Edited video: 52 + 59 = 111 frames, mean 37.4 candidates per frame. **One line** (uptime = frame + 277) through all 111 frames, **no break**. Across the cut the cracked uptime goes 328 -> 329 in one frame. The cracked tick equals the real `Hud.tickCount` on every frame. |
| **Control** (tick restore skipped) | save at 448, resume at 500, same 52 + 59 frames. **Two lines**: offset 398 over frames 0..51, offset 442 over frames 52..110. **One break, exactly at the cut** (frame 52): 449 -> 494, +45 ticks in one frame, 44 more than the expected +1. |

The decoder made no errors and no frame needed a fallback. Every frame of both edited videos
decoded with full confidence and showed a food jolt plus the low-HP heart shake.

The plots are in `build/run/clientGameTest/screenshots/forensic/`:

- `restore-plot.png`: one straight line through the dashed cut.
- `control-plot.png`: two parallel segments with a step at the cut.
- `*-series.csv`: per frame, the video frame, recorder tick, cracked uptime, candidate count and
  true tick.
- `restore/`, `control/` and `validate/`: the HUD crops and full frames.

The test also cracks the frames the editor removed that still show the saved HUD:

- The frames rendered after the save but before the mutation reaches the client crack to the
  following ticks (329 in the restore run; 450 and 451 in the control). The game just kept running.
- The frame rendered between applying the resume and the first post-resume tick cracks to the saved
  tick (328 in the restore run, 449 in the control). So the resume makes the client show the saved
  tick for exactly one frame before it advances. If an editor kept that frame, the video would
  repeat a tick once. That looks the same as an ordinary frame rendered between two ticks, which
  happens in every recording above 20 fps.

The control shows how big a cut is when the tick counter is not restored. The jump is the number
of HUD ticks between the save and the moment the restore froze the HUD: here 44 ticks of mutation,
waiting and menu, of 52 recorder ticks in total, because the menu pauses the game. tick_studio
would show that as a 2.2 s step. Restoring `Hud.tickCount` removes it entirely.

## What this proves and what it does not

It proves:

- Footage cut from the last frame before the save to the first tick after the restore gives the
  same continuous uptime line as uncut footage. This holds for the hunger-bar and low-HP heart
  shake, cracked from pixels alone over the full non-negative `int` range, through the real Save
  key, menu click and restore barrier in singleplayer.
- The method is sensitive enough to see the cut when the mod does not restore `Hud.tickCount`.
  Lines must match their offset exactly, so a one-tick error at the cut (a skipped or doubled tick)
  would also show as two lines and a break. That follows from the check; no run tests it directly.

It does not prove:

- Anything about forensics that do not use `Hud.tickCount`: texture animation phase, enchantment
  glint, clouds and sky, entity animations, sounds, chunk fade-in, F3 values or frame pacing.
  `ClientStateGameTest`, `AudioGameTest` and the inventories cover those separately.
- Robustness to real video. The decoder reads lossless frames from the framebuffer at GUI scale 1
  and a known layout. Compression, scaling and the higher GUI scales that tick_studio handles are
  not exercised. The jolts are one GUI pixel, so they are easy here and harder in a compressed
  1080p stream.
- Other setups: a dedicated server, several clients, or a save made while food is above 0. There,
  jolts only happen every `food*3+1` ticks, so fewer frames carry information. The prediction,
  decoding and cracking are the same, but this test does not run those cases.
- What frames rendered inside the restore barrier show. The recorder samples at
  `START_CLIENT_TICK`. That event does not fire while the mod's freeze cancels `Minecraft.tick`, so
  barrier frames are not sampled. An earlier run against older main did sample two. They showed the
  rebuilt world with the saved HUD (2 hearts, empty food) and the advancement toasts from the
  mutation still on screen. The timing counters are only set at Resume
  (`docs/client-state-inventory.md`), so those frames carry the frozen jolt pattern of the live
  `Hud.tickCount`, not the saved one. An editor who cut into the barrier instead of after the
  resume would leave a detectable step. Setting `Hud.tickCount` at Apply, while `Hud.tick` stays
  frozen, would turn every barrier frame into a repeat of the saved tick.

## Running it

`scripts/client-gametest.sh forensic` takes about 10 minutes. Most of that is brute force:
`2^31` candidates for each of 342 frames, about 1.5 s per frame on the client JVM's common
fork-join pool. A test failure prints the decoded and predicted patterns of the first frames that
disagree. The `[forensic]` lines in `build/run/clientGameTest/logs/latest.log` hold the numbers
above.
