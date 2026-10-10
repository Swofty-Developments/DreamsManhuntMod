package net.swofty.dreammanhunt.client.state;

import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.swofty.dreammanhunt.client.mixin.state.MultiPlayerGameModeAccessor;

final class MiningState {
	private MiningState() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		if (minecraft.gameMode == null || minecraft.level == null) {
			return tag;
		}
		DynamicOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		MultiPlayerGameModeAccessor mode = (MultiPlayerGameModeAccessor) minecraft.gameMode;
		tag.putBoolean("destroying", mode.dm$isDestroying());
		tag.store("pos", BlockPos.CODEC, mode.dm$getDestroyBlockPos());
		tag.store("item", ItemStack.OPTIONAL_CODEC, ops, mode.dm$getDestroyingItem().copy());
		tag.putFloat("progress", mode.dm$getDestroyProgress());
		tag.putFloat("ticks", mode.dm$getDestroyTicks());
		tag.putInt("delay", mode.dm$getDestroyDelay());
		Direction direction = mode.dm$getDestroyDirection();
		if (direction != null) {
			tag.store("direction", Direction.CODEC, direction);
		}
		tag.putInt("carried", mode.dm$getCarriedIndex());
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty() || minecraft.gameMode == null || minecraft.level == null) {
			return;
		}
		DynamicOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		MultiPlayerGameModeAccessor mode = (MultiPlayerGameModeAccessor) minecraft.gameMode;
		mode.dm$setDestroying(tag.getBooleanOr("destroying", false));
		mode.dm$setDestroyBlockPos(tag.read("pos", BlockPos.CODEC).orElse(new BlockPos(-1, -1, -1)));
		mode.dm$setDestroyingItem(tag.read("item", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY));
		mode.dm$setDestroyProgress(tag.getFloatOr("progress", 0.0F));
		mode.dm$setDestroyTicks(tag.getFloatOr("ticks", 0.0F));
		mode.dm$setDestroyDelay(tag.getIntOr("delay", 0));
		mode.dm$setDestroyDirection(tag.read("direction", Direction.CODEC).orElse(null));
		mode.dm$setCarriedIndex(tag.getIntOr("carried", mode.dm$getCarriedIndex()));
	}
}
