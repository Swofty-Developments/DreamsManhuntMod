package net.swofty.dreammanhunt.client.state;

import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.component.SwingAnimation;
import net.swofty.dreammanhunt.client.mixin.state.CameraAccessor;
import net.swofty.dreammanhunt.client.mixin.state.ChatComponentAccessor;
import net.swofty.dreammanhunt.client.mixin.state.ClientAvatarStateAccessor;
import net.swofty.dreammanhunt.client.mixin.state.ClientLevelAccessor;
import net.swofty.dreammanhunt.client.mixin.state.DeltaTrackerTimerAccessor;
import net.swofty.dreammanhunt.client.mixin.state.FirstPersonHandsAccessor;
import net.swofty.dreammanhunt.client.mixin.state.GameRendererAccessor;
import net.swofty.dreammanhunt.client.mixin.state.HudAccessor;
import net.swofty.dreammanhunt.client.mixin.state.ItemActivationAccessor;
import net.swofty.dreammanhunt.client.mixin.state.LegacyRandomSourceAccessor;
import net.swofty.dreammanhunt.client.mixin.state.LevelAccessor;
import net.swofty.dreammanhunt.client.mixin.state.LightmapExtractorAccessor;
import net.swofty.dreammanhunt.client.mixin.state.LivingEntityAccessor;
import net.swofty.dreammanhunt.client.mixin.state.LocalPlayerAccessor;
import net.swofty.dreammanhunt.client.mixin.state.MinecraftAccessor;
import net.swofty.dreammanhunt.client.mixin.state.PlayerAccessor;
import net.swofty.dreammanhunt.client.mixin.state.SpriteAnimationStateAccessor;
import net.swofty.dreammanhunt.client.mixin.state.SwingStateAccessor;
import net.swofty.dreammanhunt.client.mixin.state.TextureAtlasAccessor;
import net.swofty.dreammanhunt.client.mixin.state.TextureManagerAccessor;
import net.swofty.dreammanhunt.client.mixin.state.WalkAnimationStateAccessor;

public final class ClientSnapshot {
	public static final int FORMAT = 1;
	private static boolean resuming;
	private static final double SAME_POSITION_EPSILON = 1.0E-3;

	private ClientSnapshot() {
	}

	public static CompoundTag capture(Minecraft minecraft) {
		DynamicOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag root = new CompoundTag();
		root.putInt("format", FORMAT);
		root.putLong("capturedAtEpochMs", System.currentTimeMillis());
		root.put("timing", captureTiming(minecraft));
		root.put("hud", captureHud(minecraft, ops));
		root.put("textures", captureTextures(minecraft));
		root.put("renderer", captureRenderer(minecraft));
		root.put("player", capturePlayer(minecraft.player, ops));
		root.put("level", captureLevel(minecraft.level));
		root.put("entities", captureEntities(minecraft));
		root.put("chat", captureChat(minecraft, ops));
		root.put("options", OptionsState.capture(minecraft));
		root.put("cooldowns", CooldownState.capture(minecraft.player));
		root.put("fades", FadeState.capture(minecraft));
		root.put("destroyProgress", captureDestroyProgress(minecraft.level));
		root.put("mining", MiningState.capture(minecraft));
		root.put("screen", ScreenState.capture(minecraft));
		root.put("needles", NeedleState.capture(minecraft));
		root.put("audio", net.swofty.dreammanhunt.client.state.audio.AudioState.capture(minecraft));
		net.swofty.dreammanhunt.client.state.extra.ExtraState.capture(minecraft, root);
		LiveStates.Live live = LiveStates.create(root);
		live.chat = new ArrayList<>(((ChatComponentAccessor) minecraft.gui.hud.getChat()).dm$getAllMessages());
		live.particles = ParticleState.capture(minecraft);
		live.overlays = OverlayState.capture(minecraft);
		live.entityTracking = EntityTrackingState.capture(minecraft);
		return root;
	}

	public static void applyEarly(Minecraft minecraft, CompoundTag root) {
		OptionsState.apply(minecraft, root.getCompoundOrEmpty("options"));
	}

	public static void apply(Minecraft minecraft, CompoundTag root) {
		DynamicOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		LiveStates.Live live = LiveStates.get(root);
		if (live != null && live.particles != null) {
			ParticleState.apply(minecraft, live.particles);
		} else {
			minecraft.particleEngine.clearParticles();
		}
		if (live != null && live.overlays != null) {
			OverlayState.apply(minecraft, live.overlays);
		} else {
			minecraft.gui.toastManager().clear();
		}
		applyDestroyProgress(minecraft.level, root.getListOrEmpty("destroyProgress"));
		MiningState.apply(minecraft, root.getCompoundOrEmpty("mining"));
		CooldownState.apply(minecraft.player, root.getCompoundOrEmpty("cooldowns"));
		FadeState.apply(minecraft, root.getCompoundOrEmpty("fades"));
		applyHud(minecraft, root.getCompoundOrEmpty("hud"), ops);
		applyRenderer(minecraft, root.getCompoundOrEmpty("renderer"));
		applyPlayer(minecraft.player, root.getCompoundOrEmpty("player"), ops);
		applyLevel(minecraft.level, root.getCompoundOrEmpty("level"));
		if (!resuming) {
			if (live != null && live.entityTracking != null) {
				EntityTrackingState.apply(minecraft, live.entityTracking);
			}
			applyEntities(minecraft, root.getListOrEmpty("entities"));
		} else if (live != null && live.entityTracking != null) {
			EntityTrackingState.applyLateArrivals(minecraft, live.entityTracking);
		}
		applyChat(minecraft, root, ops);
		net.swofty.dreammanhunt.client.state.extra.ExtraState.apply(minecraft, root);
		net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
		camera.attributeProbe().reset();
		camera.attributeProbe().tick(minecraft.level, camera.position());
		minecraft.level.environmentAttributes().invalidateTickCache();
		net.swofty.dreammanhunt.client.state.audio.AudioState.apply(minecraft, root.getCompoundOrEmpty("audio"));
		ScreenState.apply(minecraft, root.getCompoundOrEmpty("screen"));
		NeedleState.apply(minecraft, root.getCompoundOrEmpty("needles"));
	}

	public static void resume(Minecraft minecraft, CompoundTag root) {
		resuming = true;
		try {
			apply(minecraft, root);
		} finally {
			resuming = false;
		}
		PlayerMotionState.syncServer(minecraft.player);
		applyTextures(minecraft, root.getCompoundOrEmpty("textures"));
		for (KeyMapping key : minecraft.options.keyMappings) {
			while (key.consumeClick()) {
			}
		}
		applyTiming(minecraft, root.getCompoundOrEmpty("timing"));
	}

	public static void applyBarrierTiming(Minecraft minecraft, CompoundTag root) {
		LiveStates.Live live = LiveStates.get(root);
		if (live != null && live.overlays != null) {
			OverlayState.apply(minecraft, live.overlays);
		} else {
			minecraft.gui.toastManager().clear();
		}
		applyTextures(minecraft, root.getCompoundOrEmpty("textures"));
		CompoundTag timing = root.getCompoundOrEmpty("timing");
		applyTiming(minecraft, timing);
		if (timing.contains("visualNanos")) {
			VisualClock.freezeAt(timing.getLongOr("visualNanos", VisualClock.nanos()));
		}
	}

	public static CompoundTag counters(Minecraft minecraft) {
		DynamicOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag root = new CompoundTag();
		root.put("timing", captureTiming(minecraft));
		root.put("hud", captureHud(minecraft, ops));
		root.put("textures", captureTextures(minecraft));
		root.put("renderer", captureRenderer(minecraft));
		root.put("player", capturePlayer(minecraft.player, ops));
		root.put("level", captureLevel(minecraft.level));
		root.put("entities", captureEntities(minecraft));
		root.put("cooldowns", CooldownState.capture(minecraft.player));
		root.put("fades", FadeState.capture(minecraft));
		root.put("destroyProgress", captureDestroyProgress(minecraft.level));
		root.putInt("particles", ParticleState.count(minecraft));
		root.putInt("toasts", OverlayState.visibleToastCount(minecraft));
		return root;
	}

	private static CompoundTag captureTiming(Minecraft minecraft) {
		MinecraftAccessor mc = (MinecraftAccessor) minecraft;
		DeltaTrackerTimerAccessor timer = (DeltaTrackerTimerAccessor) mc.dm$getDeltaTracker();
		CompoundTag tag = new CompoundTag();
		tag.putInt("hudTickCount", ((HudAccessor) minecraft.gui.hud).dm$getTickCount());
		tag.putLong("clientTickCount", mc.dm$getClientTickCount());
		tag.putInt("rightClickDelay", mc.dm$getRightClickDelay());
		tag.putInt("missTime", minecraft.missTime);
		tag.putFloat("deltaTickResidual", timer.dm$getDeltaTickResidual());
		tag.putFloat("pausedDeltaTickResidual", timer.dm$getPausedDeltaTickResidual());
		tag.putLong("visualNanos", VisualClock.nanos());
		return tag;
	}

	private static void applyTiming(Minecraft minecraft, CompoundTag tag) {
		MinecraftAccessor mc = (MinecraftAccessor) minecraft;
		HudAccessor hud = (HudAccessor) minecraft.gui.hud;
		hud.dm$setTickCount(tag.getIntOr("hudTickCount", hud.dm$getTickCount()));
		mc.dm$setClientTickCount(tag.getLongOr("clientTickCount", mc.dm$getClientTickCount()));
		mc.dm$setRightClickDelay(tag.getIntOr("rightClickDelay", 0));
		minecraft.missTime = tag.getIntOr("missTime", 0);
		DeltaTrackerTimerAccessor timer = (DeltaTrackerTimerAccessor) mc.dm$getDeltaTracker();
		long now = net.minecraft.util.Util.getMillis();
		timer.dm$setDeltaTickResidual(tag.getFloatOr("deltaTickResidual", 0.0F));
		timer.dm$setPausedDeltaTickResidual(tag.getFloatOr("pausedDeltaTickResidual", 0.0F));
		timer.dm$setLastMs(now);
		timer.dm$setLastUiMs(now);
		if (tag.contains("visualNanos")) {
			VisualClock.resumeAt(tag.getLongOr("visualNanos", VisualClock.nanos()));
		} else {
			VisualClock.unfreeze();
		}
	}

	private static CompoundTag captureHud(Minecraft minecraft, DynamicOps<Tag> ops) {
		HudAccessor hud = (HudAccessor) minecraft.gui.hud;
		CompoundTag tag = new CompoundTag();
		NbtValues.putComponent(tag, "overlayMessage", ops, hud.dm$getOverlayMessageString());
		tag.putInt("overlayMessageTime", hud.dm$getOverlayMessageTime());
		tag.putBoolean("animateOverlayMessageColor", hud.dm$getAnimateOverlayMessageColor());
		tag.putFloat("vignetteBrightness", minecraft.gui.hud.vignetteBrightness);
		tag.putInt("toolHighlightTimer", hud.dm$getToolHighlightTimer());
		NbtValues.putItem(tag, "lastToolHighlight", ops, hud.dm$getLastToolHighlight());
		tag.putInt("titleTime", hud.dm$getTitleTime());
		NbtValues.putComponent(tag, "title", ops, hud.dm$getTitle());
		NbtValues.putComponent(tag, "subtitle", ops, hud.dm$getSubtitle());
		tag.putInt("titleFadeInTime", hud.dm$getTitleFadeInTime());
		tag.putInt("titleStayTime", hud.dm$getTitleStayTime());
		tag.putInt("titleFadeOutTime", hud.dm$getTitleFadeOutTime());
		tag.putInt("lastHealth", hud.dm$getLastHealth());
		tag.putInt("displayHealth", hud.dm$getDisplayHealth());
		tag.putLong("lastHealthTime", hud.dm$getLastHealthTime());
		tag.putLong("healthBlinkTime", hud.dm$getHealthBlinkTime());
		tag.putInt("lastBubblePopSoundPlayed", hud.dm$getLastBubblePopSoundPlayed());
		tag.putFloat("autosaveIndicatorValue", hud.dm$getAutosaveIndicatorValue());
		tag.putFloat("lastAutosaveIndicatorValue", hud.dm$getLastAutosaveIndicatorValue());
		tag.putFloat("scopeScale", hud.dm$getScopeScale());
		tag.putBoolean("hidden", hud.dm$getIsHidden());
		return tag;
	}

	private static void applyHud(Minecraft minecraft, CompoundTag tag, DynamicOps<Tag> ops) {
		if (tag.isEmpty()) {
			return;
		}
		HudAccessor hud = (HudAccessor) minecraft.gui.hud;
		hud.dm$setOverlayMessageString(NbtValues.getComponent(tag, "overlayMessage", ops));
		hud.dm$setOverlayMessageTime(tag.getIntOr("overlayMessageTime", 0));
		hud.dm$setAnimateOverlayMessageColor(tag.getBooleanOr("animateOverlayMessageColor", false));
		minecraft.gui.hud.vignetteBrightness = tag.getFloatOr("vignetteBrightness", 1.0F);
		hud.dm$setToolHighlightTimer(tag.getIntOr("toolHighlightTimer", 0));
		hud.dm$setLastToolHighlight(NbtValues.getItem(tag, "lastToolHighlight", ops));
		hud.dm$setTitleTime(tag.getIntOr("titleTime", 0));
		hud.dm$setTitle(NbtValues.getComponent(tag, "title", ops));
		hud.dm$setSubtitle(NbtValues.getComponent(tag, "subtitle", ops));
		hud.dm$setTitleFadeInTime(tag.getIntOr("titleFadeInTime", 10));
		hud.dm$setTitleStayTime(tag.getIntOr("titleStayTime", 70));
		hud.dm$setTitleFadeOutTime(tag.getIntOr("titleFadeOutTime", 20));
		hud.dm$setLastHealth(tag.getIntOr("lastHealth", 0));
		hud.dm$setDisplayHealth(tag.getIntOr("displayHealth", 0));
		hud.dm$setLastHealthTime(tag.getLongOr("lastHealthTime", 0L));
		hud.dm$setHealthBlinkTime(tag.getLongOr("healthBlinkTime", 0L));
		hud.dm$setLastBubblePopSoundPlayed(tag.getIntOr("lastBubblePopSoundPlayed", 0));
		hud.dm$setAutosaveIndicatorValue(tag.getFloatOr("autosaveIndicatorValue", 0.0F));
		hud.dm$setLastAutosaveIndicatorValue(tag.getFloatOr("lastAutosaveIndicatorValue", 0.0F));
		hud.dm$setScopeScale(tag.getFloatOr("scopeScale", 0.0F));
		hud.dm$setIsHidden(tag.getBooleanOr("hidden", false));
	}

	private static CompoundTag captureTextures(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		for (Map.Entry<Identifier, AbstractTexture> entry : ((TextureManagerAccessor) minecraft.getTextureManager()).dm$getByPath().entrySet()) {
			if (!(entry.getValue() instanceof TextureAtlas atlas)) {
				continue;
			}
			List<SpriteContents.AnimationState> states = ((TextureAtlasAccessor) atlas).dm$getAnimatedTexturesStates();
			int[] frames = new int[states.size() * 2];
			for (int i = 0; i < states.size(); i++) {
				SpriteAnimationStateAccessor state = (SpriteAnimationStateAccessor) (Object) states.get(i);
				frames[i * 2] = state.dm$getFrame();
				frames[i * 2 + 1] = state.dm$getSubFrame();
			}
			tag.putIntArray(entry.getKey().toString(), frames);
		}
		return tag;
	}

	private static void applyTextures(Minecraft minecraft, CompoundTag tag) {
		for (Map.Entry<Identifier, AbstractTexture> entry : ((TextureManagerAccessor) minecraft.getTextureManager()).dm$getByPath().entrySet()) {
			if (!(entry.getValue() instanceof TextureAtlas atlas)) {
				continue;
			}
			int[] frames = tag.getIntArray(entry.getKey().toString()).orElse(null);
			List<SpriteContents.AnimationState> states = ((TextureAtlasAccessor) atlas).dm$getAnimatedTexturesStates();
			if (frames == null || frames.length != states.size() * 2) {
				continue;
			}
			for (int i = 0; i < states.size(); i++) {
				SpriteAnimationStateAccessor state = (SpriteAnimationStateAccessor) (Object) states.get(i);
				state.dm$setFrame(frames[i * 2]);
				state.dm$setSubFrame(frames[i * 2 + 1]);
				state.dm$setDirty(true);
			}
			try {
				java.lang.reflect.Method upload = TextureAtlas.class.getDeclaredMethod("uploadAnimationFrames");
				upload.setAccessible(true);
				upload.invoke(atlas);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static CompoundTag captureRenderer(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		CameraAccessor camera = (CameraAccessor) minecraft.gameRenderer.mainCamera();
		tag.putFloat("eyeHeight", camera.dm$getEyeHeight());
		tag.putFloat("eyeHeightOld", camera.dm$getEyeHeightOld());
		tag.putFloat("fovModifier", camera.dm$getFovModifier());
		tag.putFloat("oldFovModifier", camera.dm$getOldFovModifier());
		GameRendererAccessor renderer = (GameRendererAccessor) minecraft.gameRenderer;
		tag.putFloat("bossDarkening", renderer.dm$getBossOverlayWorldDarkening());
		tag.putFloat("bossDarkeningO", renderer.dm$getBossOverlayWorldDarkeningO());
		LightmapExtractorAccessor lightmap = (LightmapExtractorAccessor) renderer.dm$getLightmapExtractor();
		tag.putFloat("blockLightFlicker", lightmap.dm$getBlockLightFlicker());
		if (lightmap.dm$getRandomSource() instanceof LegacyRandomSourceAccessor random) {
			tag.putLong("flickerSeed", random.dm$getSeed().get());
		}
		tag.putString("cameraType", minecraft.options.getCameraType().name());
		tag.putBoolean("smoothCamera", minecraft.options.smoothCamera);
		return tag;
	}

	private static void applyRenderer(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		CameraAccessor camera = (CameraAccessor) minecraft.gameRenderer.mainCamera();
		camera.dm$setEyeHeight(tag.getFloatOr("eyeHeight", camera.dm$getEyeHeight()));
		camera.dm$setEyeHeightOld(tag.getFloatOr("eyeHeightOld", camera.dm$getEyeHeightOld()));
		camera.dm$setFovModifier(tag.getFloatOr("fovModifier", 1.0F));
		camera.dm$setOldFovModifier(tag.getFloatOr("oldFovModifier", 1.0F));
		GameRendererAccessor renderer = (GameRendererAccessor) minecraft.gameRenderer;
		renderer.dm$setBossOverlayWorldDarkening(tag.getFloatOr("bossDarkening", 0.0F));
		renderer.dm$setBossOverlayWorldDarkeningO(tag.getFloatOr("bossDarkeningO", 0.0F));
		LightmapExtractorAccessor lightmap = (LightmapExtractorAccessor) renderer.dm$getLightmapExtractor();
		lightmap.dm$setBlockLightFlicker(tag.getFloatOr("blockLightFlicker", 0.0F));
		lightmap.dm$setNeedsUpdate(true);
		if (tag.contains("flickerSeed") && lightmap.dm$getRandomSource() instanceof LegacyRandomSourceAccessor random) {
			random.dm$getSeed().set(tag.getLongOr("flickerSeed", 0L));
		}
		try {
			net.minecraft.client.CameraType type = net.minecraft.client.CameraType.valueOf(tag.getStringOr("cameraType", "FIRST_PERSON"));
			if (minecraft.options.getCameraType() != type) {
				minecraft.options.setCameraType(type);
				minecraft.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? minecraft.getCameraEntity() : null);
			}
		} catch (IllegalArgumentException ignored) {
		}
		minecraft.options.smoothCamera = tag.getBooleanOr("smoothCamera", false);
	}

	private static CompoundTag capturePlayer(LocalPlayer player, DynamicOps<Tag> ops) {
		CompoundTag tag = captureLiving(player);
		LocalPlayerAccessor local = (LocalPlayerAccessor) player;
		PlayerAccessor base = (PlayerAccessor) player;
		tag.putDouble("x", player.getX());
		tag.putDouble("y", player.getY());
		tag.putDouble("z", player.getZ());
		tag.putDouble("xo", player.xo);
		tag.putDouble("yo", player.yo);
		tag.putDouble("zo", player.zo);
		tag.putDouble("xOld", player.xOld);
		tag.putDouble("yOld", player.yOld);
		tag.putDouble("zOld", player.zOld);
		tag.putFloat("yRot", player.getYRot());
		tag.putFloat("xRot", player.getXRot());
		tag.putFloat("yRotO", player.yRotO);
		tag.putFloat("xRotO", player.xRotO);
		tag.putDouble("dx", player.getDeltaMovement().x);
		tag.putDouble("dy", player.getDeltaMovement().y);
		tag.putDouble("dz", player.getDeltaMovement().z);
		tag.putBoolean("sprinting", player.isSprinting());
		tag.putBoolean("wasSprinting", local.dm$getWasSprinting());
		tag.putInt("sprintTriggerTime", local.dm$getSprintTriggerTime());
		tag.putInt("jumpRidingTicks", local.dm$getJumpRidingTicks());
		tag.putFloat("jumpRidingScale", local.dm$getJumpRidingScale());
		tag.putFloat("portalEffectIntensity", player.portalEffectIntensity);
		tag.putFloat("oPortalEffectIntensity", player.oPortalEffectIntensity);
		tag.putFloat("spinningEffectTime", local.dm$getSpinningEffectTime());
		tag.putFloat("spinningEffectSpeed", local.dm$getSpinningEffectSpeed());
		tag.putInt("waterVisionTime", local.dm$getWaterVisionTime());
		tag.putInt("autoJumpTime", local.dm$getAutoJumpTime());
		tag.putBoolean("wasFallFlying", local.dm$getWasFallFlying());
		tag.putInt("experienceDisplayStartTick", player.experienceDisplayStartTick);
		tag.putFloat("yBob", player.yBob);
		tag.putFloat("xBob", player.xBob);
		tag.putFloat("yBobO", player.yBobO);
		tag.putFloat("xBobO", player.xBobO);
		tag.putFloat("hurtDir", base.dm$getHurtDir());
		tag.putBoolean("usingItem", local.dm$getStartedUsingItem());
		if (local.dm$getUsingItemHand() != null) {
			tag.putString("usingItemHand", local.dm$getUsingItemHand().name());
		}
		NbtValues.putItem(tag, "lastItemInMainHand", ops, base.dm$getLastItemInMainHand());
		ClientAvatarStateAccessor avatar = (ClientAvatarStateAccessor) player.avatarState();
		tag.putFloat("walkDist", avatar.dm$getWalkDist());
		tag.putFloat("walkDistO", avatar.dm$getWalkDistO());
		tag.putFloat("bob", avatar.dm$getBob());
		tag.putFloat("bobO", avatar.dm$getBobO());
		tag.putDouble("xCloak", avatar.dm$getXCloak());
		tag.putDouble("yCloak", avatar.dm$getYCloak());
		tag.putDouble("zCloak", avatar.dm$getZCloak());
		tag.putDouble("xCloakO", avatar.dm$getXCloakO());
		tag.putDouble("yCloakO", avatar.dm$getYCloakO());
		tag.putDouble("zCloakO", avatar.dm$getZCloakO());
		tag.putDouble("prevDx", avatar.dm$getDeltaMovementOnPreviousTick().x);
		tag.putDouble("prevDy", avatar.dm$getDeltaMovementOnPreviousTick().y);
		tag.putDouble("prevDz", avatar.dm$getDeltaMovementOnPreviousTick().z);
		FirstPersonHandsAccessor hands = (FirstPersonHandsAccessor) local.dm$getHands();
		NbtValues.putItem(tag, "mainHandItem", ops, hands.dm$getMainHandItem());
		NbtValues.putItem(tag, "offHandItem", ops, hands.dm$getOffHandItem());
		tag.putFloat("mainHandHeight", hands.dm$getMainHandHeight());
		tag.putFloat("oMainHandHeight", hands.dm$getOMainHandHeight());
		tag.putFloat("offHandHeight", hands.dm$getOffHandHeight());
		tag.putFloat("oOffHandHeight", hands.dm$getOOffHandHeight());
		ItemActivationAccessor activation = (ItemActivationAccessor) player.itemActivation();
		if (activation.dm$getItem() != null) {
			NbtValues.putItem(tag, "activationItem", ops, activation.dm$getItem());
		}
		tag.putInt("activationTicks", activation.dm$getTicks());
		tag.putFloat("activationOffX", activation.dm$getOffX());
		tag.putFloat("activationOffY", activation.dm$getOffY());
		tag.put("elytra", EntityAnimations.captureElytra(player));
		tag.put("motion", PlayerMotionState.capture(player));
		return tag;
	}

	private static void applyPlayer(LocalPlayer player, CompoundTag tag, DynamicOps<Tag> ops) {
		if (tag.isEmpty()) {
			return;
		}
		LocalPlayerAccessor local = (LocalPlayerAccessor) player;
		PlayerAccessor base = (PlayerAccessor) player;
		double x = tag.getDoubleOr("x", player.getX());
		double y = tag.getDoubleOr("y", player.getY());
		double z = tag.getDoubleOr("z", player.getZ());
		CompoundTag motion = tag.getCompoundOrEmpty("motion");
		PlayerMotionState.apply(player, motion);
		boolean samePlace = motion.isEmpty()
			? Math.abs(player.getX() - x) < SAME_POSITION_EPSILON && Math.abs(player.getY() - y) < SAME_POSITION_EPSILON && Math.abs(player.getZ() - z) < SAME_POSITION_EPSILON
			: player.level().dimension().identifier().toString().equals(motion.getStringOr("dimension", ""));
		if (samePlace && motion.isEmpty()) {
			player.setPosRaw(x, y, z);
			player.xo = tag.getDoubleOr("xo", x);
			player.yo = tag.getDoubleOr("yo", y);
			player.zo = tag.getDoubleOr("zo", z);
			player.xOld = tag.getDoubleOr("xOld", x);
			player.yOld = tag.getDoubleOr("yOld", y);
			player.zOld = tag.getDoubleOr("zOld", z);
			player.setYRot(tag.getFloatOr("yRot", player.getYRot()));
			player.setXRot(tag.getFloatOr("xRot", player.getXRot()));
			player.yRotO = tag.getFloatOr("yRotO", player.getYRot());
			player.xRotO = tag.getFloatOr("xRotO", player.getXRot());
			player.setDeltaMovement(tag.getDoubleOr("dx", 0.0), tag.getDoubleOr("dy", 0.0), tag.getDoubleOr("dz", 0.0));
		}
		applyUse(player, local, tag);
		applyLiving(player, tag);
		boolean sprinting = tag.getBooleanOr("sprinting", false);
		if (player.isSprinting() != sprinting) {
			player.setSprinting(sprinting);
		}
		local.dm$setWasSprinting(tag.getBooleanOr("wasSprinting", sprinting));
		local.dm$setSprintTriggerTime(tag.getIntOr("sprintTriggerTime", 0));
		local.dm$setJumpRidingTicks(tag.getIntOr("jumpRidingTicks", 0));
		local.dm$setJumpRidingScale(tag.getFloatOr("jumpRidingScale", 0.0F));
		player.portalEffectIntensity = tag.getFloatOr("portalEffectIntensity", 0.0F);
		player.oPortalEffectIntensity = tag.getFloatOr("oPortalEffectIntensity", 0.0F);
		local.dm$setSpinningEffectTime(tag.getFloatOr("spinningEffectTime", 0.0F));
		local.dm$setSpinningEffectSpeed(tag.getFloatOr("spinningEffectSpeed", 0.0F));
		local.dm$setWaterVisionTime(tag.getIntOr("waterVisionTime", 0));
		local.dm$setAutoJumpTime(tag.getIntOr("autoJumpTime", 0));
		local.dm$setWasFallFlying(tag.getBooleanOr("wasFallFlying", false));
		player.experienceDisplayStartTick = tag.getIntOr("experienceDisplayStartTick", Integer.MIN_VALUE);
		player.yBob = tag.getFloatOr("yBob", player.getYRot());
		player.xBob = tag.getFloatOr("xBob", player.getXRot());
		player.yBobO = tag.getFloatOr("yBobO", player.yBob);
		player.xBobO = tag.getFloatOr("xBobO", player.xBob);
		base.dm$setHurtDir(tag.getFloatOr("hurtDir", 0.0F));
		base.dm$setLastItemInMainHand(player.getMainHandItem().copy());
		ClientAvatarStateAccessor avatar = (ClientAvatarStateAccessor) player.avatarState();
		avatar.dm$setWalkDist(tag.getFloatOr("walkDist", 0.0F));
		avatar.dm$setWalkDistO(tag.getFloatOr("walkDistO", 0.0F));
		avatar.dm$setBob(tag.getFloatOr("bob", 0.0F));
		avatar.dm$setBobO(tag.getFloatOr("bobO", 0.0F));
		if (samePlace) {
			avatar.dm$setXCloak(tag.getDoubleOr("xCloak", x));
			avatar.dm$setYCloak(tag.getDoubleOr("yCloak", y));
			avatar.dm$setZCloak(tag.getDoubleOr("zCloak", z));
			avatar.dm$setXCloakO(tag.getDoubleOr("xCloakO", x));
			avatar.dm$setYCloakO(tag.getDoubleOr("yCloakO", y));
			avatar.dm$setZCloakO(tag.getDoubleOr("zCloakO", z));
		}
		avatar.dm$setDeltaMovementOnPreviousTick(new net.minecraft.world.phys.Vec3(tag.getDoubleOr("prevDx", 0.0), tag.getDoubleOr("prevDy", 0.0), tag.getDoubleOr("prevDz", 0.0)));
		FirstPersonHandsAccessor hands = (FirstPersonHandsAccessor) local.dm$getHands();
		hands.dm$setMainHandItem(NbtValues.getItem(tag, "mainHandItem", ops));
		hands.dm$setOffHandItem(NbtValues.getItem(tag, "offHandItem", ops));
		hands.dm$setMainHandHeight(tag.getFloatOr("mainHandHeight", 0.0F));
		hands.dm$setOMainHandHeight(tag.getFloatOr("oMainHandHeight", 0.0F));
		hands.dm$setOffHandHeight(tag.getFloatOr("offHandHeight", 0.0F));
		hands.dm$setOOffHandHeight(tag.getFloatOr("oOffHandHeight", 0.0F));
		ItemActivationAccessor activation = (ItemActivationAccessor) player.itemActivation();
		activation.dm$setItem(tag.contains("activationItem") ? NbtValues.getItem(tag, "activationItem", ops) : null);
		activation.dm$setTicks(tag.getIntOr("activationTicks", 0));
		activation.dm$setOffX(tag.getFloatOr("activationOffX", 0.0F));
		activation.dm$setOffY(tag.getFloatOr("activationOffY", 0.0F));
		EntityAnimations.applyElytra(player, tag.getCompoundOrEmpty("elytra"));
	}

	private static void applyUse(LocalPlayer player, LocalPlayerAccessor local, CompoundTag tag) {
		boolean using = tag.getBooleanOr("usingItem", false);
		if (using && tag.contains("usingItemHand")) {
			InteractionHand hand = InteractionHand.valueOf(tag.getStringOr("usingItemHand", InteractionHand.MAIN_HAND.name()));
			if (player.isUsingItem() && player.getUsedItemHand() != hand) {
				player.stopUsingItem();
			}
			if (!player.isUsingItem()) {
				player.startUsingItem(hand);
			}
		} else if (!using && player.isUsingItem()) {
			player.stopUsingItem();
		}
	}

	private static CompoundTag captureLiving(LivingEntity entity) {
		CompoundTag tag = new CompoundTag();
		LivingEntityAccessor living = (LivingEntityAccessor) entity;
		tag.putInt("tickCount", entity.tickCount);
		tag.putFloat("yBodyRot", entity.yBodyRot);
		tag.putFloat("yBodyRotO", entity.yBodyRotO);
		tag.putFloat("yHeadRot", entity.yHeadRot);
		tag.putFloat("yHeadRotO", entity.yHeadRotO);
		tag.putInt("hurtTime", entity.hurtTime);
		tag.putInt("hurtDuration", entity.hurtDuration);
		tag.putInt("deathTime", entity.deathTime);
		tag.putInt("damageCooldownTime", entity.damageCooldownTime);
		tag.putInt("attackStrengthTicker", living.dm$getAttackStrengthTicker());
		tag.putInt("itemSwapTicker", living.dm$getItemSwapTicker());
		tag.putInt("useItemRemaining", living.dm$getUseItemRemaining());
		tag.putInt("fallFlyTicks", living.dm$getFallFlyTicks());
		tag.putFloat("swimAmount", living.dm$getSwimAmount());
		tag.putFloat("swimAmountO", living.dm$getSwimAmountO());
		WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) entity.walkAnimation;
		tag.putFloat("walkSpeedOld", walk.dm$getSpeedOld());
		tag.putFloat("walkSpeed", walk.dm$getSpeed());
		tag.putFloat("walkPosition", walk.dm$getPosition());
		tag.putFloat("walkPositionScale", walk.dm$getPositionScale());
		SwingStateAccessor swing = (SwingStateAccessor) living.dm$getSwingState();
		tag.putInt("swingTicks", swing.dm$getTicks());
		tag.putFloat("swingOldAnimation", swing.dm$getOldAnimation());
		tag.putFloat("swingAnimation", swing.dm$getAnimation());
		LivingEntity.SwingDescription current = swing.dm$getCurrentSwing();
		if (current != null) {
			CompoundTag swingTag = new CompoundTag();
			swingTag.putString("hand", current.hand().name());
			swingTag.putInt("duration", current.durationTicks());
			NbtValues.put(swingTag, "animation", SwingAnimation.CODEC, NbtOps.INSTANCE, current.animation());
			tag.put("swing", swingTag);
		}
		return tag;
	}

	private static void applyLiving(LivingEntity entity, CompoundTag tag) {
		LivingEntityAccessor living = (LivingEntityAccessor) entity;
		entity.tickCount = tag.getIntOr("tickCount", entity.tickCount);
		entity.yBodyRot = tag.getFloatOr("yBodyRot", entity.yBodyRot);
		entity.yBodyRotO = tag.getFloatOr("yBodyRotO", entity.yBodyRotO);
		entity.yHeadRot = tag.getFloatOr("yHeadRot", entity.yHeadRot);
		entity.yHeadRotO = tag.getFloatOr("yHeadRotO", entity.yHeadRotO);
		entity.hurtTime = tag.getIntOr("hurtTime", 0);
		entity.hurtDuration = tag.getIntOr("hurtDuration", entity.hurtDuration);
		entity.deathTime = tag.getIntOr("deathTime", 0);
		entity.damageCooldownTime = tag.getIntOr("damageCooldownTime", 0);
		living.dm$setAttackStrengthTicker(tag.getIntOr("attackStrengthTicker", living.dm$getAttackStrengthTicker()));
		living.dm$setItemSwapTicker(tag.getIntOr("itemSwapTicker", living.dm$getItemSwapTicker()));
		if (entity.isUsingItem()) {
			living.dm$setUseItemRemaining(tag.getIntOr("useItemRemaining", living.dm$getUseItemRemaining()));
		}
		living.dm$setFallFlyTicks(tag.getIntOr("fallFlyTicks", 0));
		living.dm$setSwimAmount(tag.getFloatOr("swimAmount", 0.0F));
		living.dm$setSwimAmountO(tag.getFloatOr("swimAmountO", 0.0F));
		WalkAnimationState walkState = entity.walkAnimation;
		WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) walkState;
		walk.dm$setSpeedOld(tag.getFloatOr("walkSpeedOld", 0.0F));
		walk.dm$setSpeed(tag.getFloatOr("walkSpeed", 0.0F));
		walk.dm$setPosition(tag.getFloatOr("walkPosition", 0.0F));
		walk.dm$setPositionScale(tag.getFloatOr("walkPositionScale", 1.0F));
		SwingStateAccessor swing = (SwingStateAccessor) living.dm$getSwingState();
		swing.dm$setTicks(tag.getIntOr("swingTicks", 0));
		swing.dm$setOldAnimation(tag.getFloatOr("swingOldAnimation", 0.0F));
		swing.dm$setAnimation(tag.getFloatOr("swingAnimation", 0.0F));
		CompoundTag swingTag = tag.getCompoundOrEmpty("swing");
		SwingAnimation animation = swingTag.isEmpty() ? null : NbtValues.get(swingTag, "animation", SwingAnimation.CODEC, NbtOps.INSTANCE);
		if (animation != null) {
			InteractionHand hand = InteractionHand.valueOf(swingTag.getStringOr("hand", InteractionHand.MAIN_HAND.name()));
			swing.dm$setCurrentSwing(new LivingEntity.SwingDescription(hand, animation, swingTag.getIntOr("duration", 6)));
		} else {
			swing.dm$setCurrentSwing(null);
		}
	}

	private static CompoundTag captureLevel(ClientLevel level) {
		CompoundTag tag = new CompoundTag();
		LevelAccessor weather = (LevelAccessor) level;
		ClientLevelAccessor client = (ClientLevelAccessor) level;
		tag.putString("dimension", level.dimension().identifier().toString());
		tag.putFloat("rainLevel", weather.dm$getRainLevel());
		tag.putFloat("oRainLevel", weather.dm$getORainLevel());
		tag.putFloat("thunderLevel", weather.dm$getThunderLevel());
		tag.putFloat("oThunderLevel", weather.dm$getOThunderLevel());
		tag.putInt("skyFlashTime", client.dm$getSkyFlashTime());
		tag.putInt("rainSoundTime", client.dm$getRainSoundTime());
		tag.putLong("gameTime", level.getGameTime());
		return tag;
	}

	private static void applyLevel(ClientLevel level, CompoundTag tag) {
		if (tag.isEmpty() || !level.dimension().identifier().toString().equals(tag.getStringOr("dimension", ""))) {
			return;
		}
		LevelAccessor weather = (LevelAccessor) level;
		ClientLevelAccessor client = (ClientLevelAccessor) level;
		weather.dm$setRainLevel(tag.getFloatOr("rainLevel", 0.0F));
		weather.dm$setORainLevel(tag.getFloatOr("oRainLevel", 0.0F));
		weather.dm$setThunderLevel(tag.getFloatOr("thunderLevel", 0.0F));
		weather.dm$setOThunderLevel(tag.getFloatOr("oThunderLevel", 0.0F));
		client.dm$setSkyFlashTime(tag.getIntOr("skyFlashTime", 0));
		client.dm$setRainSoundTime(tag.getIntOr("rainSoundTime", 0));
	}

	private static ListTag captureEntities(Minecraft minecraft) {
		ListTag list = new ListTag();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity == minecraft.player) {
				continue;
			}
			CompoundTag tag = entity instanceof LivingEntity living ? captureLiving(living) : new CompoundTag();
			tag.putString("uuid", entity.getUUID().toString());
			tag.putString("class", entity.getClass().getName());
			tag.put("anim", EntityAnimations.capture(entity));
			tag.putInt("tickCount", entity.tickCount);
			list.add(tag);
		}
		return list;
	}

	private static void applyEntities(Minecraft minecraft, ListTag list) {
		if (list.isEmpty()) {
			return;
		}
		Map<UUID, CompoundTag> byUuid = new HashMap<>();
		for (Tag raw : list) {
			if (raw instanceof CompoundTag tag) {
				try {
					byUuid.put(UUID.fromString(tag.getStringOr("uuid", "")), tag);
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			CompoundTag tag = byUuid.get(entity.getUUID());
			if (tag == null || entity == minecraft.player) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				applyLiving(living, tag);
			} else {
				entity.tickCount = tag.getIntOr("tickCount", entity.tickCount);
			}
			if (entity.getClass().getName().equals(tag.getStringOr("class", ""))) {
				EntityAnimations.apply(entity, tag.getCompoundOrEmpty("anim"));
			}
		}
	}

	private static ListTag captureDestroyProgress(ClientLevel level) {
		ListTag list = new ListTag();
		for (BlockDestructionProgress progress : ((ClientLevelAccessor) level).dm$getDestroyingBlocks().values()) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("id", progress.getId());
			tag.putLong("pos", progress.getPos().asLong());
			tag.putInt("progress", progress.getProgress());
			tag.putLong("tick", progress.getUpdatedRenderTick());
			list.add(tag);
		}
		return list;
	}

	private static void applyDestroyProgress(ClientLevel level, ListTag list) {
		ClientLevelAccessor accessor = (ClientLevelAccessor) level;
		accessor.dm$getDestroyingBlocks().clear();
		accessor.dm$getDestructionProgress().clear();
		for (Tag raw : list) {
			if (raw instanceof CompoundTag tag) {
				BlockDestructionProgress progress = new BlockDestructionProgress(tag.getIntOr("id", 0), BlockPos.of(tag.getLongOr("pos", 0L)));
				progress.setProgress(tag.getIntOr("progress", 0));
				progress.updateTick(tag.getLongOr("tick", 0L));
				accessor.dm$getDestroyingBlocks().put(progress.getId(), progress);
				accessor.dm$getDestructionProgress().computeIfAbsent(progress.getPos().asLong(), key -> new java.util.TreeSet<>()).add(progress);
			}
		}
	}

	private static CompoundTag captureChat(Minecraft minecraft, DynamicOps<Tag> ops) {
		ChatComponentAccessor chat = (ChatComponentAccessor) minecraft.gui.hud.getChat();
		CompoundTag tag = new CompoundTag();
		tag.putInt("scroll", chat.dm$getChatScrollbarPos());
		tag.putBoolean("newMessageSinceScroll", chat.dm$getNewMessageSinceScroll());
		ListTag messages = new ListTag();
		for (GuiMessage message : chat.dm$getAllMessages()) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("t", message.addedTime());
			entry.putString("s", message.source().name());
			NbtValues.putComponent(entry, "c", ops, message.content());
			messages.add(entry);
		}
		tag.put("messages", messages);
		return tag;
	}

	private static void applyChat(Minecraft minecraft, CompoundTag root, DynamicOps<Tag> ops) {
		CompoundTag tag = root.getCompoundOrEmpty("chat");
		if (tag.isEmpty()) {
			return;
		}
		LiveStates.Live live = LiveStates.get(root);
		List<GuiMessage> restored = live != null ? live.chat : null;
		if (restored == null) {
			restored = new ArrayList<>();
			for (Tag raw : tag.getListOrEmpty("messages")) {
				if (raw instanceof CompoundTag entry) {
					Component content = NbtValues.getComponent(entry, "c", ops);
					if (content == null) {
						continue;
					}
					GuiMessageSource source;
					try {
						source = GuiMessageSource.valueOf(entry.getStringOr("s", GuiMessageSource.values()[0].name()));
					} catch (IllegalArgumentException e) {
						source = GuiMessageSource.values()[0];
					}
					restored.add(new GuiMessage(entry.getIntOr("t", 0), content, null, source, null));
				}
			}
		}
		ChatComponentAccessor chat = (ChatComponentAccessor) minecraft.gui.hud.getChat();
		List<GuiMessage> messages = chat.dm$getAllMessages();
		messages.clear();
		messages.addAll(restored);
		minecraft.gui.hud.getChat().rescaleChat();
		chat.dm$setChatScrollbarPos(tag.getIntOr("scroll", 0));
		chat.dm$setNewMessageSinceScroll(tag.getBooleanOr("newMessageSinceScroll", false));
	}
}
