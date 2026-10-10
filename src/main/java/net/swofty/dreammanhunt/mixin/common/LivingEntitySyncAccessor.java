package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntitySyncAccessor {
	@Accessor("fallFlyTicks")
	int dreammanhunt$sync$getFallFlyTicks();

	@Accessor("fallFlyTicks")
	void dreammanhunt$sync$setFallFlyTicks(int ticks);

	@Accessor("autoSpinAttackTicks")
	int dreammanhunt$sync$getAutoSpinAttackTicks();

	@Accessor("autoSpinAttackTicks")
	void dreammanhunt$sync$setAutoSpinAttackTicks(int ticks);

	@Accessor("autoSpinAttackDmg")
	float dreammanhunt$sync$getAutoSpinAttackDmg();

	@Accessor("autoSpinAttackDmg")
	void dreammanhunt$sync$setAutoSpinAttackDmg(float damage);

	@Accessor("autoSpinAttackItemStack")
	@Nullable ItemStack dreammanhunt$sync$getAutoSpinAttackItemStack();

	@Accessor("autoSpinAttackItemStack")
	void dreammanhunt$sync$setAutoSpinAttackItemStack(@Nullable ItemStack stack);

	@Invoker("setLivingEntityFlag")
	void dreammanhunt$sync$setLivingEntityFlag(int flag, boolean value);
}
