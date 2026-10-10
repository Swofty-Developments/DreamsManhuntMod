package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatrolSpawner.class)
public interface PatrolSpawnerAccessor {
	@Accessor("nextTick")
	int dreammanhunt$getNextTick();

	@Accessor("nextTick")
	void dreammanhunt$setNextTick(int ticks);
}
