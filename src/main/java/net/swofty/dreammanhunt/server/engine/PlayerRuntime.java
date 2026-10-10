package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.swofty.dreammanhunt.mixin.server.CooldownInstanceAccessor;
import net.swofty.dreammanhunt.mixin.server.ItemCooldownsAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PlayerRuntime {
	private PlayerRuntime() {
	}

	static CompoundTag capture(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.store("uuid", UUIDUtil.CODEC, player.getUUID());
		ItemCooldownsAccessor cooldowns = (ItemCooldownsAccessor) player.getCooldowns();
		tag.putInt("cooldown_tick", cooldowns.dreammanhunt$getTickCount());
		ListTag entries = new ListTag();
		for (Map.Entry<Identifier, Object> entry : cooldowns.dreammanhunt$getCooldowns().entrySet()) {
			CooldownInstanceAccessor instance = (CooldownInstanceAccessor) entry.getValue();
			CompoundTag cooldown = new CompoundTag();
			cooldown.putString("group", entry.getKey().toString());
			cooldown.putInt("start", instance.dreammanhunt$getStartTime());
			cooldown.putInt("end", instance.dreammanhunt$getEndTime());
			entries.add(cooldown);
		}
		tag.put("cooldowns", entries);

		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		AbstractContainerMenu menu = player.containerMenu;
		tag.putBoolean("inventory_menu_open", menu == player.inventoryMenu);
		tag.store("carried", ItemStack.OPTIONAL_CODEC, ops, menu.getCarried().copy());
		tag.put("inventory_grid", items(player.inventoryMenu.getInputGridSlots(), ops));
		if (menu != player.inventoryMenu && menu instanceof AbstractCraftingMenu crafting) {
			tag.put("menu_grid", items(crafting.getInputGridSlots(), ops));
		}
		return tag;
	}

	static void applyCooldowns(ServerPlayer player, CompoundTag tag) {
		ItemCooldowns cooldowns = player.getCooldowns();
		ItemCooldownsAccessor accessor = (ItemCooldownsAccessor) cooldowns;
		int now = tag.getIntOr("cooldown_tick", accessor.dreammanhunt$getTickCount());
		List<CompoundTag> entries = new ArrayList<>();
		for (Tag element : tag.getListOrEmpty("cooldowns")) {
			if (element instanceof CompoundTag entry) {
				entries.add(entry);
			}
		}
		for (CompoundTag entry : entries) {
			int start = entry.getIntOr("start", now);
			int end = entry.getIntOr("end", now);
			Identifier group = Identifier.tryParse(entry.getStringOr("group", ""));
			if (group != null && end > start) {
				accessor.dreammanhunt$setTickCount(start);
				cooldowns.addCooldown(group, end - start);
			}
		}
		accessor.dreammanhunt$setTickCount(now);
		for (CompoundTag entry : entries) {
			int end = entry.getIntOr("end", now);
			Identifier group = Identifier.tryParse(entry.getStringOr("group", ""));
			if (group != null && end > now) {
				player.connection.send(new ClientboundCooldownPacket(group, end - now));
			}
		}
	}

	static void applyMenu(ServerPlayer player, CompoundTag tag) {
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<Slot> grid = player.inventoryMenu.getInputGridSlots();
		for (Tag element : tag.getListOrEmpty("inventory_grid")) {
			if (element instanceof CompoundTag entry) {
				int index = entry.getIntOr("index", -1);
				ItemStack stack = entry.read("item", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
				if (index >= 0 && index < grid.size()) {
					grid.get(index).set(stack);
				}
			}
		}
		ItemStack carried = tag.read("carried", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
		if (tag.getBooleanOr("inventory_menu_open", true)) {
			player.inventoryMenu.setCarried(carried);
		} else {
			returnToPlayer(player, carried);
		}
		for (Tag element : tag.getListOrEmpty("menu_grid")) {
			if (element instanceof CompoundTag entry) {
				returnToPlayer(player, entry.read("item", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY));
			}
		}
	}

	private static void returnToPlayer(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static ListTag items(List<Slot> slots, RegistryOps<Tag> ops) {
		ListTag list = new ListTag();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (!stack.isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("index", i);
				entry.store("item", ItemStack.OPTIONAL_CODEC, ops, stack.copy());
				list.add(entry);
			}
		}
		return list;
	}
}
