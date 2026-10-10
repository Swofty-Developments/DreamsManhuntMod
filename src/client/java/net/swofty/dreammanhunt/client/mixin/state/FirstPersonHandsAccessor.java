package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FirstPersonHandsAndItems.class)
public interface FirstPersonHandsAccessor {
	@Accessor("mainHandItem")
	ItemStack dm$getMainHandItem();

	@Accessor("mainHandItem")
	void dm$setMainHandItem(ItemStack value);

	@Accessor("offHandItem")
	ItemStack dm$getOffHandItem();

	@Accessor("offHandItem")
	void dm$setOffHandItem(ItemStack value);

	@Accessor("mainHandHeight")
	float dm$getMainHandHeight();

	@Accessor("mainHandHeight")
	void dm$setMainHandHeight(float value);

	@Accessor("oMainHandHeight")
	float dm$getOMainHandHeight();

	@Accessor("oMainHandHeight")
	void dm$setOMainHandHeight(float value);

	@Accessor("offHandHeight")
	float dm$getOffHandHeight();

	@Accessor("offHandHeight")
	void dm$setOffHandHeight(float value);

	@Accessor("oOffHandHeight")
	float dm$getOOffHandHeight();

	@Accessor("oOffHandHeight")
	void dm$setOOffHandHeight(float value);

}
