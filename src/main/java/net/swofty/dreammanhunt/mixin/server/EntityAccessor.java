package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("random")
	RandomSource dreammanhunt$getRandom();

	@Accessor("invulnerableTime")
	int dreammanhunt$getInvulnerableTime();

	@Accessor("invulnerableTime")
	void dreammanhunt$setInvulnerableTime(int ticks);
}
