package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyBlockPos")
	BlockPos dm$getDestroyBlockPos();

	@Accessor("destroyBlockPos")
	void dm$setDestroyBlockPos(BlockPos pos);

	@Accessor("destroyingItem")
	ItemStack dm$getDestroyingItem();

	@Accessor("destroyingItem")
	void dm$setDestroyingItem(ItemStack stack);

	@Accessor("destroyProgress")
	float dm$getDestroyProgress();

	@Accessor("destroyProgress")
	void dm$setDestroyProgress(float progress);

	@Accessor("destroyTicks")
	float dm$getDestroyTicks();

	@Accessor("destroyTicks")
	void dm$setDestroyTicks(float ticks);

	@Accessor("destroyDelay")
	int dm$getDestroyDelay();

	@Accessor("destroyDelay")
	void dm$setDestroyDelay(int delay);

	@Accessor("destroyDirection")
	@Nullable Direction dm$getDestroyDirection();

	@Accessor("destroyDirection")
	void dm$setDestroyDirection(@Nullable Direction direction);

	@Accessor("isDestroying")
	boolean dm$isDestroying();

	@Accessor("isDestroying")
	void dm$setDestroying(boolean destroying);

	@Accessor("carriedIndex")
	int dm$getCarriedIndex();

	@Accessor("carriedIndex")
	void dm$setCarriedIndex(int index);
}
