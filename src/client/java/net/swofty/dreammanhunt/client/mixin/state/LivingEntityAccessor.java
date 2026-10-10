package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	int dm$getAttackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void dm$setAttackStrengthTicker(int value);

	@Accessor("itemSwapTicker")
	int dm$getItemSwapTicker();

	@Accessor("itemSwapTicker")
	void dm$setItemSwapTicker(int value);

	@Accessor("useItemRemaining")
	int dm$getUseItemRemaining();

	@Accessor("useItemRemaining")
	void dm$setUseItemRemaining(int value);

	@Accessor("useItem")
	ItemStack dm$getUseItem();

	@Accessor("useItem")
	void dm$setUseItem(ItemStack value);

	@Accessor("fallFlyTicks")
	int dm$getFallFlyTicks();

	@Accessor("fallFlyTicks")
	void dm$setFallFlyTicks(int value);

	@Accessor("swimAmount")
	float dm$getSwimAmount();

	@Accessor("swimAmount")
	void dm$setSwimAmount(float value);

	@Accessor("swimAmountO")
	float dm$getSwimAmountO();

	@Accessor("swimAmountO")
	void dm$setSwimAmountO(float value);

	@Accessor("swingState")
	LivingEntity.SwingState dm$getSwingState();
}
