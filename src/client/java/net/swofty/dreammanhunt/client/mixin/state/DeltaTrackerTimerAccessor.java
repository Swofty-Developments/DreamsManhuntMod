package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
	@Accessor("deltaTickResidual")
	float dm$getDeltaTickResidual();

	@Accessor("deltaTickResidual")
	void dm$setDeltaTickResidual(float value);

	@Accessor("pausedDeltaTickResidual")
	float dm$getPausedDeltaTickResidual();

	@Accessor("pausedDeltaTickResidual")
	void dm$setPausedDeltaTickResidual(float value);

	@Accessor("lastMs")
	void dm$setLastMs(long value);

	@Accessor("lastUiMs")
	void dm$setLastUiMs(long value);
}
