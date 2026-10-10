package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
	@Accessor("speedOld")
	float dm$getSpeedOld();

	@Accessor("speedOld")
	void dm$setSpeedOld(float value);

	@Accessor("speed")
	float dm$getSpeed();

	@Accessor("speed")
	void dm$setSpeed(float value);

	@Accessor("position")
	float dm$getPosition();

	@Accessor("position")
	void dm$setPosition(float value);

	@Accessor("positionScale")
	float dm$getPositionScale();

	@Accessor("positionScale")
	void dm$setPositionScale(float value);

}
