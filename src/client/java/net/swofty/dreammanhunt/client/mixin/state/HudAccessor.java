package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("tickCount")
	int dm$getTickCount();

	@Accessor("tickCount")
	void dm$setTickCount(int value);

	@Accessor("overlayMessageString")
	Component dm$getOverlayMessageString();

	@Accessor("overlayMessageString")
	void dm$setOverlayMessageString(Component value);

	@Accessor("overlayMessageTime")
	int dm$getOverlayMessageTime();

	@Accessor("overlayMessageTime")
	void dm$setOverlayMessageTime(int value);

	@Accessor("animateOverlayMessageColor")
	boolean dm$getAnimateOverlayMessageColor();

	@Accessor("animateOverlayMessageColor")
	void dm$setAnimateOverlayMessageColor(boolean value);

	@Accessor("toolHighlightTimer")
	int dm$getToolHighlightTimer();

	@Accessor("toolHighlightTimer")
	void dm$setToolHighlightTimer(int value);

	@Accessor("lastToolHighlight")
	ItemStack dm$getLastToolHighlight();

	@Accessor("lastToolHighlight")
	void dm$setLastToolHighlight(ItemStack value);

	@Accessor("titleTime")
	int dm$getTitleTime();

	@Accessor("titleTime")
	void dm$setTitleTime(int value);

	@Accessor("title")
	Component dm$getTitle();

	@Accessor("title")
	void dm$setTitle(Component value);

	@Accessor("subtitle")
	Component dm$getSubtitle();

	@Accessor("subtitle")
	void dm$setSubtitle(Component value);

	@Accessor("titleFadeInTime")
	int dm$getTitleFadeInTime();

	@Accessor("titleFadeInTime")
	void dm$setTitleFadeInTime(int value);

	@Accessor("titleStayTime")
	int dm$getTitleStayTime();

	@Accessor("titleStayTime")
	void dm$setTitleStayTime(int value);

	@Accessor("titleFadeOutTime")
	int dm$getTitleFadeOutTime();

	@Accessor("titleFadeOutTime")
	void dm$setTitleFadeOutTime(int value);

	@Accessor("lastHealth")
	int dm$getLastHealth();

	@Accessor("lastHealth")
	void dm$setLastHealth(int value);

	@Accessor("displayHealth")
	int dm$getDisplayHealth();

	@Accessor("displayHealth")
	void dm$setDisplayHealth(int value);

	@Accessor("lastHealthTime")
	long dm$getLastHealthTime();

	@Accessor("lastHealthTime")
	void dm$setLastHealthTime(long value);

	@Accessor("healthBlinkTime")
	long dm$getHealthBlinkTime();

	@Accessor("healthBlinkTime")
	void dm$setHealthBlinkTime(long value);

	@Accessor("lastBubblePopSoundPlayed")
	int dm$getLastBubblePopSoundPlayed();

	@Accessor("lastBubblePopSoundPlayed")
	void dm$setLastBubblePopSoundPlayed(int value);

	@Accessor("autosaveIndicatorValue")
	float dm$getAutosaveIndicatorValue();

	@Accessor("autosaveIndicatorValue")
	void dm$setAutosaveIndicatorValue(float value);

	@Accessor("lastAutosaveIndicatorValue")
	float dm$getLastAutosaveIndicatorValue();

	@Accessor("lastAutosaveIndicatorValue")
	void dm$setLastAutosaveIndicatorValue(float value);

	@Accessor("scopeScale")
	float dm$getScopeScale();

	@Accessor("scopeScale")
	void dm$setScopeScale(float value);

	@Accessor("isHidden")
	boolean dm$getIsHidden();

	@Accessor("isHidden")
	void dm$setIsHidden(boolean value);
}
