package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerOpenersCounter.class)
public interface ContainerOpenersCounterAccessor {
	@Accessor("openCount")
	int dreammanhunt$getOpenCount();

	@Accessor("openCount")
	void dreammanhunt$setOpenCount(int count);
}
