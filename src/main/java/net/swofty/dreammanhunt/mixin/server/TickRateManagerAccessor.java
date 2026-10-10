package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickRateManager.class)
public interface TickRateManagerAccessor {
	@Accessor("frozenTicksToRun")
	void dreammanhunt$setFrozenTicksToRun(int ticks);

	@Accessor("isFrozen")
	void dreammanhunt$setIsFrozen(boolean frozen);

	@Accessor("runGameElements")
	void dreammanhunt$setRunGameElements(boolean run);
}
