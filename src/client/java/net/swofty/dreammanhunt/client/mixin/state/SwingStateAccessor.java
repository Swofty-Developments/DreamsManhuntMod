package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.SwingState.class)
public interface SwingStateAccessor {
	@Accessor("currentSwing")
	LivingEntity.SwingDescription dm$getCurrentSwing();

	@Accessor("currentSwing")
	void dm$setCurrentSwing(LivingEntity.SwingDescription value);

	@Accessor("ticks")
	int dm$getTicks();

	@Accessor("ticks")
	void dm$setTicks(int value);

	@Accessor("oldAnimation")
	float dm$getOldAnimation();

	@Accessor("oldAnimation")
	void dm$setOldAnimation(float value);

	@Accessor("animation")
	float dm$getAnimation();

	@Accessor("animation")
	void dm$setAnimation(float value);

}
