package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
	@Accessor("wasSprinting")
	boolean dm$getWasSprinting();

	@Accessor("wasSprinting")
	void dm$setWasSprinting(boolean value);

	@Accessor("sprintTriggerTime")
	int dm$getSprintTriggerTime();

	@Accessor("sprintTriggerTime")
	void dm$setSprintTriggerTime(int value);

	@Accessor("jumpRidingTicks")
	int dm$getJumpRidingTicks();

	@Accessor("jumpRidingTicks")
	void dm$setJumpRidingTicks(int value);

	@Accessor("jumpRidingScale")
	float dm$getJumpRidingScale();

	@Accessor("jumpRidingScale")
	void dm$setJumpRidingScale(float value);

	@Accessor("spinningEffectTime")
	float dm$getSpinningEffectTime();

	@Accessor("spinningEffectTime")
	void dm$setSpinningEffectTime(float value);

	@Accessor("spinningEffectSpeed")
	float dm$getSpinningEffectSpeed();

	@Accessor("spinningEffectSpeed")
	void dm$setSpinningEffectSpeed(float value);

	@Accessor("waterVisionTime")
	int dm$getWaterVisionTime();

	@Accessor("waterVisionTime")
	void dm$setWaterVisionTime(int value);

	@Accessor("autoJumpTime")
	int dm$getAutoJumpTime();

	@Accessor("autoJumpTime")
	void dm$setAutoJumpTime(int value);

	@Accessor("startedUsingItem")
	boolean dm$getStartedUsingItem();

	@Accessor("startedUsingItem")
	void dm$setStartedUsingItem(boolean value);

	@Accessor("usingItemHand")
	InteractionHand dm$getUsingItemHand();

	@Accessor("usingItemHand")
	void dm$setUsingItemHand(InteractionHand value);

	@Accessor("wasFallFlying")
	boolean dm$getWasFallFlying();

	@Accessor("wasFallFlying")
	void dm$setWasFallFlying(boolean value);

	@Accessor("firstPersonHandsAndItems")
	FirstPersonHandsAndItems dm$getHands();
}
