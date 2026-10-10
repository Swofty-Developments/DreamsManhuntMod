package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.player.ItemActivation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemActivation.class)
public interface ItemActivationAccessor {
	@Accessor("item")
	ItemStack dm$getItem();

	@Accessor("item")
	void dm$setItem(ItemStack value);

	@Accessor("ticks")
	int dm$getTicks();

	@Accessor("ticks")
	void dm$setTicks(int value);

	@Accessor("offX")
	float dm$getOffX();

	@Accessor("offX")
	void dm$setOffX(float value);

	@Accessor("offY")
	float dm$getOffY();

	@Accessor("offY")
	void dm$setOffY(float value);

}
