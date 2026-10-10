package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAccessor {
	@Accessor("lastItemInMainHand")
	ItemStack dm$getLastItemInMainHand();

	@Accessor("lastItemInMainHand")
	void dm$setLastItemInMainHand(ItemStack value);

	@Accessor("hurtDir")
	float dm$getHurtDir();

	@Accessor("hurtDir")
	void dm$setHurtDir(float value);

}
