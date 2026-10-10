package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PhantomSpawner.class)
public interface PhantomSpawnerAccessor {
	@Accessor("nextTick")
	int dreammanhunt$getNextTick();

	@Accessor("nextTick")
	void dreammanhunt$setNextTick(int ticks);
}
