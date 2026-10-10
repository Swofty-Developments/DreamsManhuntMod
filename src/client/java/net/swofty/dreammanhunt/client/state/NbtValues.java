package net.swofty.dreammanhunt.client.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class NbtValues {
	private NbtValues() {
	}

	static <T> void put(CompoundTag tag, String key, Codec<T> codec, DynamicOps<Tag> ops, @Nullable T value) {
		if (value != null) {
			codec.encodeStart(ops, value).ifSuccess(encoded -> tag.put(key, encoded));
		}
	}

	static <T> @Nullable T get(CompoundTag tag, String key, Codec<T> codec, DynamicOps<Tag> ops) {
		Tag raw = tag.get(key);
		if (raw == null) {
			return null;
		}
		return codec.parse(ops, raw).result().orElse(null);
	}

	static void putItem(CompoundTag tag, String key, DynamicOps<Tag> ops, @Nullable ItemStack stack) {
		put(tag, key, ItemStack.OPTIONAL_CODEC, ops, stack == null ? ItemStack.EMPTY : stack);
	}

	static ItemStack getItem(CompoundTag tag, String key, DynamicOps<Tag> ops) {
		ItemStack stack = get(tag, key, ItemStack.OPTIONAL_CODEC, ops);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	static void putComponent(CompoundTag tag, String key, DynamicOps<Tag> ops, @Nullable Component component) {
		put(tag, key, ComponentSerialization.CODEC, ops, component);
	}

	static @Nullable Component getComponent(CompoundTag tag, String key, DynamicOps<Tag> ops) {
		return get(tag, key, ComponentSerialization.CODEC, ops);
	}
}
