package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.npc.CatSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CatSpawner.class)
public interface CatSpawnerAccessor {
	@Accessor("nextTick")
	int dreammanhunt$getNextTick();

	@Accessor("nextTick")
	void dreammanhunt$setNextTick(int ticks);
}
