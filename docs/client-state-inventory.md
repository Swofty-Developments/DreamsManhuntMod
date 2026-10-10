# Client state inventory (Minecraft 26.3)

This is every piece of client-side state that affects what is on screen or what happens next, taken from the decompiled 26.3 sources. Each row says whether a server restore puts it back on its own (fresh respawn packet, new `LocalPlayer`, fresh chunks and entities) and how `net.swofty.dreammanhunt.client.state` captures and restores it. Paths are relative to `/mnt/work/mc-src-26.3/net/minecraft/`. "gap" marks state the mod does not restore yet.

## Notable findings

- Texture animations tick once per rendered frame that has at least one tick due (`Minecraft.runTick`, `client/Minecraft.java:1208-1212`), not once per client tick. This means their phase depends on frame rate.
- The FOV transition (`fovModifier`/`oldFovModifier`) and eye-height smoothing have moved into `client/Camera.java`. `GameRenderer` no longer has them.
- `ItemInHandRenderer` is gone. First-person hand heights and the visible items now live in `client/player/FirstPersonHandsAndItems.java`, which `LocalPlayer` owns.
- Walk bob, view bob and cape physics live in `client/entity/ClientAvatarState.java`, which `AbstractClientPlayer` owns.
- Chunk section fade-in runs on the wall clock (`RenderSection.uploadedTime`, `chunkSectionFadeInTime` option). Every re-meshed section fades in after a restore, and block entities stay hidden while visibility is below 0.3.
- `handleRespawn` copies `portalEffectIntensity`/`oPortalEffectIntensity` and the `ItemActivation` object (the totem animation) from the pre-restore player. A plain server restore therefore shows post-save values, not the saved ones.
- In the same dimension the `ClientLevel` is reused, so particles, block cracks, sky flash, rain levels and sounds all survive the respawn.
- The server only re-sends rain packets when the restored level is raining (`server/players/PlayerList.java:652-656`). A clear save restored into a rainy session stays rainy on the client.
- `LevelLoadingScreen` only closes through `Minecraft.tick` -> `gameMode.tick()` -> `Connection.tick()` -> `ClientPacketListener.tick()` -> `LevelLoadTracker.tickClientLoad()`. Suppressing the whole client tick would leave the loading screen up for ever.
- `Options.FieldAccess` and `Options.OptionAccess` are private interfaces. Mixin `@Invoker` can't name them, so settings go through `Options.save()`/`Options.load()`.
- The enchantment glint is pure wall clock (`Util.getMillis() * glintSpeed * 8`).
- Hud heart blink is tick based (`healthBlinkTime` vs `tickCount`), but the ghost "old health" hearts are held for 1000 ms of wall clock (`lastHealthTime`).
- Hunger and heart shake is seeded with `tickCount * 312871` every frame (`client/gui/Hud.java:785`). Restoring `Hud.tickCount` therefore makes the shake pattern exact, with no RNG state to save.
- 26.3 has no `LevelRenderer` tick counter. Clouds and weather columns derive from `gameTime`, which the server restores.

In-memory parts (particles, toasts, subtitles, the live chat message objects) survive only within one game session; after a restart the chat falls back to the serialised copy and the others are cleared.

## Mechanism summary

- **VisualClock** (`VisualClock`): a mod-owned wall clock, `Util.getNanos() + offset`.
  - It is frozen between RestoreBegin and RestoreResume.
  - At Resume it is set to the visual time captured at save, so every routed phase continues exactly from the save.
  - Routed call sites (`@WrapOperation` on `Util.getMillis()`): `TextureTransform.setupGlintTexturing`, `Hud.extractPlayerHealth`, `WorldBorderRenderer.prepareDynamicTransforms`, and `LerpingBossEvent` (`<init>`/`setProgress`/`getProgress`).
  - A global offset on `Util`'s time source was rejected. Stepping it backwards stalls `DeltaTracker` (negative residual), stalls the integrated server's `nextTickTimeNanos`, puts chunk fade timestamps in the future (invisible terrain), and breaks keepalive, deferred-packet expiry, `LevelLoadTracker` timeouts and the FPS and AFK throttles. Stepping it forwards runs catch-up tick bursts.
- **Freeze** (Begin -> Resume): `Minecraft.tick` is replaced by a minimal tick.
  - The minimal tick runs: `gameMode.tick` (connection, packet listener, load tracker), `gui.tick` with `Hud.tick` cancelled (screens still tick), `pendingConnection.tick` and `keyboardHandler.tick`.
  - Entities, block entities, the level, particles, the game renderer, music and sound ticks, keybinds and `ServerboundClientTickEndPacket` are all skipped.
  - The `DeltaTracker` is held frozen (partial tick 1.0), `TextureManager.tick` is cancelled, and mouse turning is blocked.
  - Queued key clicks are dropped at Resume.
- **Capture**: taken at a client tick boundary into NBT at `<game dir>/dreammanhunt/client_states/<id>.nbt`. If it is requested mid-tick it is deferred to the TAIL of `Minecraft.tick`; otherwise it runs immediately, between ticks.
- **Apply**: runs once the view is ready.
  - Ready means: no `LevelLoadingScreen` or overlay, chunks loaded in a circle of radius min(render distance, 8) - 1, compile queue empty, every visible section compiled and fully faded in, and all of that stable for 5 frames and 250 ms. The timeout is 15 s.
  - It applies everything except the timing counters, then calls `sendReady` once.
- **Options**: captured at save time as the `options.txt` lines minus `key_*` (via `Options.save()`).
  - They are restored at RestoreBegin, so the rebuilt view already uses them: the mod writes the captured lines plus the current `key_*` lines and calls `Options.load()`.
  - `cameraType`, `hideGui` and `smoothCamera` are not persisted in `options.txt`, so they are captured separately.
- **Resume**: re-applies the snapshot (packets may have arrived after Apply), then sets the timing counters:
  - `Hud.tickCount`
  - `Minecraft.clientTickCount`/`rightClickDelay`/`missTime`
  - texture animation `frame`/`subFrame` (marked dirty)
  - the `DeltaTracker` residual (with `lastMs` rebased)
  - `VisualClock.resumeAt(captured)`

  After that, the next client tick is the tick after capture.

## Hud (`client/gui/Hud.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `tickCount` :151 | Hunger and heart shake seed (`random.setSeed(tickCount*312871)` :785), food jolt cadence (`tickCount % (food*3+1)` :973), air bubble wobble :925, regen wave :796, chat fade base (`getGuiTicks` :1255), heart blink | no, never reset | set at Resume | HIGH |
| `random` :146 | heart and food jitter | n/a, reseeded from `tickCount` every frame | not needed | — |
| `lastHealth`, `displayHealth` :169-170 | ghost hearts and damage flash | no | Apply | HIGH |
| `lastHealthTime` :171 (wall clock, read at :769) | 1000 ms ghost-heart hold | no | VisualClock-routed; stored as visual ms, Apply | HIGH |
| `healthBlinkTime` :172 | heart blink (tick based vs `tickCount`) :768 | no | Apply | HIGH |
| `overlayMessageString`/`overlayMessageTime`/`animateOverlayMessageColor` :152-154 | action bar text and fade | no | Apply (Component via codec) | MEDIUM |
| `title`/`subtitle`/`titleTime`/`titleFadeInTime`/`titleStayTime`/`titleFadeOutTime` :163-168 | title overlay | no | Apply | MEDIUM |
| `toolHighlightTimer`/`lastToolHighlight` :156-157 | held-item name popup; re-triggers when the item arrives if not restored | no | Apply (ItemStack via codec) | MEDIUM |
| `vignetteBrightness` :155 | vignette darkness, eases 1%/tick | no | Apply | MEDIUM |
| `autosaveIndicatorValue`/`lastAutosaveIndicatorValue` :175-176 | autosave icon fade | no | Apply | LOW |
| `scopeScale` :179 | spyglass zoom-in | no | Apply | LOW |
| `lastBubblePopSoundPlayed` :173 | air-bubble pop sound | no | Apply | LOW |
| `isHidden` :150 | F1 hide-GUI | no | via `options.hideGui` | HIGH |
| `contextualInfoBar` :177 | XP, locator or jump bar choice; recomputed per frame | yes (derived) | none | LOW |

## Wall-clock-driven visuals

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `TextureTransform.setupGlintTexturing` (`client/renderer/rendertype/TextureTransform.java:32`) | enchantment glint scroll for item, entity and armor glint (`millis % 110000`, `% 30000`) | no | VisualClock `@WrapOperation`; phase continues from the save | HIGH |
| `Hud.extractPlayerHealth` `Util.getMillis` :769 | ghost-heart hold | no | VisualClock `@WrapOperation` | HIGH |
| `WorldBorderRenderer.prepareDynamicTransforms` :185 | world border stripe scroll (`% 3000`) | no | VisualClock `@WrapOperation` | MEDIUM |
| `LerpingBossEvent.setTime` :14, :29/:38/:42 | boss bar progress lerp (100 ms) | partly (bars re-sent) | VisualClock `@WrapOperation` | LOW |
| `SectionRenderDispatcher.RenderSection.uploadedTime` :210, `getVisibility` :217 | chunk section fade-in (`chunkSectionFadeInTime`); block entities hidden below 0.3 visibility (`LevelExtractor:288`) | no; re-meshed sections fade in | readiness waits until every visible section has visibility 1.0 before Apply | HIGH |
| Transient blocks (`ClientPacketListener:1156`, `LevelExtractor:235-247`, `LevelRenderer:1029`) | client-predicted block overlays | no | short-lived; expire on their own (gap: not explicitly cleared) | LOW |
| `ToastManager.ToastInstance.animationStartTime`/`becameFullyVisibleAt` (`client/gui/components/toasts/ToastManager.java:195-196,237`) | toast slide and display time | no | restored: `Util.getMillis()` in `ToastInstance.update` routed through the visual clock; instances deep-copied (in memory) | MEDIUM |
| `NowPlayingToast` static `musicNoteColorTick` :52 (`System.currentTimeMillis`) | music note colour cycle | no | gap | LOW |
| `SubtitleOverlay` subtitles :25/:27, times :138/:145 | subtitle fade | no | restored: `Subtitle` time calls routed through the visual clock; list deep-copied (in memory) | LOW |
| `SpectatorGui.lastSelectionTime` :23 | spectator hotbar fade | no | restored: visual clock plus saved value | LOW |
| `ActiveTextCollector` :77, `LoadingDotsText` :8 | scrolling long labels, loading dots | no | restored: routed through the visual clock | LOW |
| `EditBox.focusedTime` (`MultiLineEditBox`, `AbstractSignEditScreen`, `IMEPreeditOverlay` not yet) | text cursor blink | no | restored for `EditBox`: visual clock plus the saved `focusedTime` of the chat input | LOW |
| `ChatListener.previousMessageTime` :47 | chat delay queue | no | gap | LOW |
| `MouseHandler.lastHandleMovementTime` :55 (`Blaze3D.getTime`), `smoothTurnX/Y` :50-51, `accumulatedDX/DY` :52-53 | cinematic camera smoothing, player turning | no | turning is blocked during the freeze; smoothing state is a gap (only matters with `smoothCamera`) | LOW |
| `LocalTime` item property (`client/renderer/item/properties/select/LocalTime.java:73`) | real-time clock text | n/a | real local time by design | LOW |
| `LoadingOverlay`, `TitleScreen`, `SplashRenderer`, `Panorama`, `LevelLoadingScreen:91` | menu and loading fades | n/a | not in restored footage | LOW |
| `FramerateLimiter`, `FramerateLimitTracker` :41/:69 | frame pacing | n/a | none | LOW |
| `LevelLoadTracker` :43/:53 (`LEVEL_LOAD_CLOSE_DELAY_MS`) | loading screen close delay | n/a | readiness waits for the screen to close | MEDIUM |

## Minecraft and DeltaTracker timing, client tick rate

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `Minecraft.clientTickCount` :392 | client tick counter (debug, telemetry) | no | set at Resume | MEDIUM |
| `Minecraft.rightClickDelay` :362 | use-key repeat delay | no | set at Resume | MEDIUM |
| `Minecraft.missTime` :366 | attack-miss cooldown | no | set at Resume | LOW |
| `DeltaTracker.Timer.deltaTickResidual`/`pausedDeltaTickResidual` (`client/DeltaTracker.java:40,42`) | partial tick: entity and camera interpolation phase of the first frames | no | captured; set at Resume with `lastMs`/`lastUiMs` rebased to now, so there is no catch-up burst | MEDIUM |
| `DeltaTracker.Timer.frozen`/`paused` :47-48 | partial tick held at 1.0 | recomputed per frame | forced frozen during restore | LOW |
| `Minecraft.pause`, `frames`, `lastTime`, `fps`, `lastNanoTime` | pause flag, FPS counter | recomputed | none | LOW |
| Client `TickRateManager` (`ClientLevel.tickRateManager` :163; `world/TickRateManager.java` fields `tickrate`, `isFrozen`, `frozenTicksToRun`, `runGameElements`) | client tick rate, frozen and step state | yes (ticking state and step packets) | server; not captured | MEDIUM |
| Tick order (`Minecraft.tick` :1874-1974) | n/a | n/a | capture at TAIL; freeze replaces it with the minimal tick | — |

## Game time, clocks, clouds, weather

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `ClientLevelData.gameTime` (`ClientLevel.java:1209`) | clouds (`CloudRenderer:158`), weather columns (`WeatherEffectRenderer:76-93`), GameTime shader uniform (end portal and gateway, `GlobalSettingsUniform:34`), block-break expiry | yes (set-time / clock sync) | server | HIGH |
| `ClientClockManager.lastTickGameTime`, `ClientClockInstance.totalTicks`/`partialTick`/`rate` (`client/ClientClockManager.java`) | day time, sky, world clocks | yes (`sendLevelInfo` full clock sync) | server | HIGH |
| `LevelRenderer` ticks / cloud counter | — | n/a: 26.3 has none | none | — |
| `SkyRenderer`, `CloudRenderer`, `WeatherEffectRenderer` buffers | GPU caches | deterministic | none | — |

## ClientLevel and Level (`client/multiplayer/ClientLevel.java`, `world/level/Level.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `rainLevel`/`oRainLevel`/`thunderLevel`/`oThunderLevel` (Level :119-122) | rain, thunder, sky darkening, rain fog | only if the restored world is raining | Apply sets all four | HIGH |
| `skyFlashTime` :169 | lightning sky flash | no (level reused) | Apply via `setSkyFlashTime` | MEDIUM |
| `rainSoundTime` :170 | rain sound cadence | no | gap | LOW |
| `destroyingBlocks`/`destructionProgress` :187-188 | block crack overlays | no in the same dimension | restored: saved entries (id, pos, progress, tick) re-inserted into both maps | MEDIUM |
| `endFlashState` :164 (`client/renderer/EndFlashState.java`) | End sky flash | derived from clock time | server via clock | LOW |
| `explosionTracker` :184, `lightUpdateQueue` | transient | n/a | none | LOW |
| `blockStatePredictionHandler` :182 | pending block predictions | overwritten by chunks | none | LOW |
| Level `random` (Level :123); `animateTick` uses `RandomSource.createThreadLocalInstance()` | ambient particles and drips | no; non-deterministic in vanilla | gap (would need a `@Redirect`) | LOW |
| `tickingEntities` order :158 | entity tick order | insertion order of add-entity packets | none | LOW |
| `serverSimulationDistance` :181 | simulation distance | yes | server | LOW |

## Texture animation (`client/renderer/texture/`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `SpriteContents.AnimationState.frame`/`subFrame`/`isDirty` (`SpriteContents.java:333-339`) | water, lava, fire, portal, sea lantern and other animated frames, plus the interpolation progress | no | per atlas (`TextureManager.byPath` -> `TextureAtlas.animatedTexturesStates` :46, keyed by atlas id and index, list size checked); set at Resume and marked dirty | MEDIUM |
| `TextureManager.tick` :101 (called once per frame from `Minecraft.runTick` :1210) | animation advance | n/a | cancelled during the freeze | MEDIUM |

## Local player: `LocalPlayer` (`client/player/LocalPlayer.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `yBob`/`xBob`/`yBobO`/`xBobO` :153-156 | first-person hand sway | no (new player starts at 0, so the hand swings hard) | Apply | HIGH |
| `portalEffectIntensity`/`oPortalEffectIntensity` :159-160 | portal overlay (`Hud:300`) and warp | partly: copied from the pre-restore player | Apply overwrites | HIGH |
| `spinningEffectTime`/`spinningEffectSpeed` :161-162 | nausea and portal swirl phase | no | Apply | MEDIUM |
| `waterVisionTime` :171 | underwater fog distance (ramps over 600 ticks) | no | Apply | HIGH |
| `startedUsingItem`/`usingItemHand` :165-166 | `isUsingItem` (bow draw, eating, shield) | partly (data sync restarts it at full) | Apply: `startUsingItem(hand)` then `useItemRemaining` | HIGH |
| `wasSprinting` :143 and sprint shared flag | sprint FOV, sprint state | partly (only with KEEP_ENTITY_DATA, from the old player) | Apply `setSprinting` and `wasSprinting` | HIGH |
| `sprintTriggerTime` :149 | double-tap sprint window | no | Apply | LOW |
| `jumpRidingTicks`/`jumpRidingScale` :157-158 | horse jump charge bar | no | gap | LOW |
| `autoJumpTime` :169, `handsBusy` :167, `crouching` :142 | auto-jump, hand lock, crouch | recomputed per tick | none | LOW |
| `wasFallFlying` :170 | elytra sound trigger | n/a | none (re-triggering the sound is wanted) | LOW |
| `xLast`/`yLast`/`zLast`/`yRotLast`/`xRotLast`/`lastOnGround`/`positionReminder` :135-144 | movement packet deltas | n/a | restored with every other client primitive; `positionReminder` is forced to 20 at Resume so the next tick reports the position (one position packet per server tick, an extra packet gets the player kicked) | MEDIUM |
| `experienceDisplayStartTick` :152 | XP bar visibility (vs `tickCount`) | no | Apply | LOW |
| `ambientSoundHandlers` :132 (`BiomeAmbientSoundsHandler.loopSounds`/`moodiness`, `UnderwaterAmbientSoundHandler`, `BubbleColumnAmbientSoundHandler`) | biome loop, cave mood, underwater ambience | no; the old loop keeps playing and a second copy starts in the same dimension | gap (audible double loop; stopping sounds at restore mitigates it) | MEDIUM |
| Position, rotation, `xo/yo/zo`, `xOld..`, `yRotO`/`xRotO`, `deltaMovement`, collision and fluid flags, pose, shared flags, `stuckSpeedMultiplier`, input (`keyPresses`, `moveVector`, `lastSentInput`), entity and level `RandomSource` state | camera position, first-frame interpolation and the next ticks of movement | partly (teleport to the server's copy, which lags the client by a tick and has no velocity) | restored from the client's own capture (`PlayerMotionState`: every non-final primitive of `LocalPlayer` and its superclasses except `Entity.id`), sprint re-applied through `setSprinting` so the speed modifier comes back | HIGH |
| Health, absorption, food, saturation, experience (client copy) | hearts, hunger bar, XP bar, heart blink trigger | yes, but possibly after Resume, and a changed value re-arms the blink | set from the capture at Apply so late server updates with the same values are no-ops | HIGH |
| Open screen (`Gui.screen`): inventory and recipe book visibility, creative tab and scroll, chat input text/cursor/selection/blink, pause menu, mouse position | what is on screen, hover highlight | no; the engine closes menus | reopened at Apply; container screens fall back to the player inventory (server-side reopen needed); death and other screens not reopened | HIGH |
| `itemActivation` :163 (`client/player/ItemActivation.java`: `item`, `ticks`, `offX`, `offY`) | totem pop animation | partly: the same object carries over from the pre-restore player | Apply copies all four | HIGH |
| `firstPersonHandsAndItems` :164 (see below) | hands | no | Apply | HIGH |

## FirstPersonHandsAndItems (`client/player/FirstPersonHandsAndItems.java`, replaces ItemInHandRenderer)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `mainHandItem`/`offHandItem` :20-21 | visible first-person items | no (start EMPTY, then the equip pop) | Apply (ItemStack codec) | HIGH |
| `mainHandHeight`/`oMainHandHeight`/`offHandHeight`/`oOffHandHeight` :22-25 | hand lowering and raising | no | Apply | HIGH |

## AbstractClientPlayer / ClientAvatarState (`client/entity/ClientAvatarState.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `walkDist`/`walkDistO` :8-9 | view-bob phase (`Camera:156`), third-person walk | no | Apply | HIGH |
| `bob`/`bobO` :16-17 | view-bob amplitude | no | Apply | HIGH |
| `deltaMovementOnPreviousTick` :7 | model lean | no | restored (local player; remote players via the generic entity copy) | LOW |
| `xCloak`/`yCloak`/`zCloak` and their O values :10-15 | cape physics | no; snaps when more than 10 blocks off | restored when the player is back at the saved position | LOW |

## Player (`world/entity/player/Player.java`) and LivingEntity (`world/entity/LivingEntity.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `attackStrengthTicker`/`itemSwapTicker` (LE :240-241) | attack indicator, hand height target | no; also reset when `lastItemInMainHand` differs | Apply both and set `lastItemInMainHand` to the current main hand | HIGH |
| `lastItemInMainHand` (Player :169) | attack-strength reset trigger | no | Apply | HIGH |
| `cooldowns` (Player :170, `ItemCooldowns` map plus `tickCount`) | hotbar cooldown overlay, use prediction | no; new instance, never re-sent | restored: map entries (start, end) and `tickCount`; `ClientboundCooldownPacket` ignored between Begin and Resume and the values re-applied at Resume | HIGH |
| `hurtDir` (Player :174), `hurtTime`/`hurtDuration` (LE :236-237) | hurt camera tilt (`GameRenderer.bobHurt`), red tint | no | Apply | HIGH |
| `swingState` (LE :232; `currentSwing`, `ticks`, `oldAnimation`, `animation`) | hand swing in first and third person | no | Apply (SwingAnimation codec) | HIGH |
| `useItem`/`useItemRemaining` (LE :267-268) | draw, eat and spyglass progress | partly (restarts full) | Apply | HIGH |
| `walkAnimation` (LE :242: `speedOld`, `speed`, `position`, `positionScale`) | limb swing | no | Apply | MEDIUM |
| `yBodyRot`/`yBodyRotO`/`yHeadRot`/`yHeadRotO` (LE :243-246) | body and head yaw | yes for re-added entities, no for the new LocalPlayer | Apply | MEDIUM |
| `fallFlyTicks` (LE :269) | elytra pitch ramp | no | Apply | MEDIUM |
| `swimAmount`/`swimAmountO` (LE :279-280) | swim and crawl pose blend | no | Apply | MEDIUM |
| `elytraAnimationState` (LE :247) | wing spread | no | restored (local player and every other living entity) | LOW |
| `deathTime` (LE :238), `damageCooldownTime` (LE :239) | death roll, Hud heart blink trigger | no | restored | LOW |
| `activeEffects` blend (`MobEffectInstance.BlendState` :368-405) | darkness, blindness and night vision fade | yes, but snaps (blend=false) | restored: `factor`/`factorPreviousFrame` per effect id on the local player | MEDIUM |
| `jumpTriggerTime`, `takeXpDelay` (Player :156-157) | creative fly double-tap, XP pickup | no | gap | LOW |

## Entity (`world/entity/Entity.java`), all entities

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `tickCount` :256 | renderer `ageInTicks` (idle animations, item spin), AnimationState timing, XP bar check | no (new entities start at 0) | Apply per UUID (local player included) | MEDIUM |
| `random` :255 | client particles | no; random in vanilla | none | LOW |
| `wasTouchingWater`/`wasEyeInWater`/`firstTick` :259-262 | splash suppression, fluid state | recomputed | none | LOW |
| `portalProcess`/`portalCooldown` :286-287 | portal overlay rise | partly | gap | LOW |
| `moveDist`/`flyDist`/`nextStep` :247-250 | footstep cadence | no | gap | LOW |

## Other entities' client animation state

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `tickCount`, `walkAnimation`, body and head yaw, `hurtTime`, `swingState` | limb swing, rotations, hurt tint, swings | partly | Apply per UUID | MEDIUM |
| `AnimationState.startTick` (`world/entity/AnimationState.java`): Warden, Camel, Sniffer, Breeze, Armadillo, Frog, Axolotl, Rabbit, Bat, Creaking, CopperGolem | keyframe animations | no; event-started ones are lost, data-driven ones restart | restored: every `AnimationState` field's `startTick` together with `tickCount`, by UUID | MEDIUM |
| Creeper `swell`/`oldSwell`, Guardian `clientSideAttackTime`, Shulker `currentPeekAmount`, Fox `crouchAmount`, Panda `sitAmount`/`rollAmount`, Bee `rollAmount` | lerps toward synced targets | no; restart from 0 | restored by the generic per-entity copy (every non-final primitive declared below `LivingEntity`/`Entity`) | MEDIUM |
| IronGolem, Ravager, Hoglin, Zoglin, Creaking attack ticks; Sheep `eatAnimationTick`; Wolf `shakeAnim` | event animations | no | restored by the generic per-entity copy | LOW |
| Squid tentacles, Chicken and Parrot flap, ItemEntity `age`/`bobOffs`, ExperienceOrb `age`, EndCrystal `time`, FallingBlockEntity `time`, boat paddles, arrow `shakeTime` | free-running client animations | no | restored by the generic per-entity copy (primitives and `float[]`) | LOW |
| EnderDragon `flightHistory`, `flapTime`/`oFlapTime` | dragon body segments and wings | no | restored: `flightHistory` samples and head, flap fields via the generic copy | MEDIUM |

## Camera (`client/Camera.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `fovModifier`/`oldFovModifier` :74-75 | FOV transition (sprint, fly, bow, speed) | no; survives respawn with post-save values | Apply | HIGH |
| `eyeHeight`/`eyeHeightOld` :62-63 | camera Y smoothing (sneak, swim) | no | Apply | HIGH |
| `attributeProbe` :80 (`world/attribute/EnvironmentAttributeProbe.java`) | one-tick lerp of sky, fog and light colours | no | gap (re-seeds on the next tick) | LOW |
| `fov`, `hudFov`, matrices, frustum | per-frame derived | recomputed | none | — |

## GameRenderer, Lightmap, fog

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `GameRenderer.bossOverlayWorldDarkening`/`O` :114-115 | boss-bar world darkening | no | Apply | MEDIUM |
| `LightmapRenderStateExtractor.blockLightFlicker` :24 | torch and block light flicker | no | Apply | LOW |
| `LightmapRenderStateExtractor.randomSource` :23 (LegacyRandomSource `seed` :14) | flicker random walk | no | Apply (seed restored) | LOW |
| `LightmapRenderStateExtractor.needsUpdate` :20 | lightmap rebuild | no | set to true at Apply | LOW |
| `AtmosphericFogEnvironment.rainFogMultiplier` (`client/renderer/fog/environment/AtmosphericFogEnvironment.java:25`) | rain fog distance, eased per frame | no | restored (found through `FogRenderer.FOG_ENVIRONMENTS`) | MEDIUM |
| Water, lava, powder snow, blindness and darkness fog | read player and effect state | stateless | via player fields | — |
| `spectatedEntityPostEffect` :130 | spectator shaders | yes | none | LOW |

## Particles, sounds, music

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `ParticleEngine.particles`/`trackingEmitters`/`particlesToAdd` :29-31 | live particles | only cleared on a dimension change | restored: every particle, pending particle and tracking emitter deep-copied at capture (in memory, reflective field copy, own `RandomSource` state) and copied back with the level and entity references rebound; particles whose entity is gone are dropped | HIGH |
| `SoundEngine` playing instances and ticking sounds :65-77 | sounds in flight | stopped only on a dimension change | gap: positions not restored | MEDIUM |
| `MusicManager.currentMusic`/`nextSongDelay` :22-25 | background music | no | gap | LOW |

## Chat, boss bars, subtitles, toasts, tab list

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| `ChatComponent.allMessages` :66 (`GuiMessage.addedTime` in Hud ticks), `trimmedMessages` :67 | visible chat lines and fade | no | Apply restores the message list (content plus `addedTime`) and rescales | MEDIUM |
| `ChatComponent.chatScrollbarPos`/`newMessageSinceScroll` :68-69 | scroll position | no | Apply | LOW |
| `ChatComponent.messageDeletionQueue` :72 | pending deletions | no | gap | LOW |
| `BossHealthOverlay.events` :54 | boss bars | partly; stale bars stay unless removed | progress lerp on VisualClock; set re-sent by server | MEDIUM |
| `SubtitleOverlay.subtitles` :25 | subtitles | no | restored (in memory) | LOW |
| `ToastManager.visibleToasts`/`queued` :27-29 | toasts | no | restored with `occupiedSlots` (in memory); the now-playing music toast is left to the audio owner | MEDIUM |
| `PlayerTabOverlay.visible`/`healthStates` :63-64 | tab list and its heart blink | no | gap | LOW |

## Options (`client/Options.java`)

| Item | Drives | Server restore? | Capture/restore | Pri |
|---|---|---|---|---|
| Every `OptionInstance` in `processDumpedOptions` :1518 and `processOptions` :1561, plus plain fields, `soundCategory_*` and `modelPart_*` | FOV, gamma, view bobbing, render distance, clouds, particles, glint speed and strength, sound volumes, chat, and so on | no | `options.txt` lines minus `key_*`, restored at Begin through `Options.load()` with the current `key_*` lines kept; callbacks fire only for changed values | HIGH |
| `key_*` | key mappings | n/a | deliberately excluded | — |
| `resourcePacks`, `incompatibleResourcePacks`, `lang` | packs and language | no | stored, but gap: no `reloadResourcePacks` / `setSelected` | LOW |
| `cameraType` :784 (F5), `hideGui` (F1), `smoothCamera` :786 | perspective, HUD visibility, cinematic camera | not in `options.txt` | captured separately, Apply | HIGH |
| Side-effecting options (`fullscreen`, `guiScale`, `graphicsPreset`, `mipmapLevels`, `soundDevice`, `enableVsync`, `highContrast`) | window, GUI, resources | n/a | applied by `load()` only when changed; a `mipmapLevels` change needs a reload (gap) | LOW |
