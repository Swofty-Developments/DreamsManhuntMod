package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("luck")
	int dreammanhunt$getLuck();

	@Accessor("lureSpeed")
	int dreammanhunt$getLureSpeed();

	@Invoker("setHookedEntity")
	void dreammanhunt$setHookedIn(@Nullable Entity entity);
}
