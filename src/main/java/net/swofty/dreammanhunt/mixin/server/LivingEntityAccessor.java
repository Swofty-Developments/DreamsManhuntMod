package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	int dreammanhunt$getAttackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void dreammanhunt$setAttackStrengthTicker(int ticks);

	@Accessor("noActionTime")
	int dreammanhunt$getNoActionTime();

	@Accessor("noActionTime")
	void dreammanhunt$setNoActionTime(int ticks);
}
