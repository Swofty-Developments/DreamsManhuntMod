package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.swofty.dreammanhunt.mixin.server.CooldownInstanceAccessor;
import net.swofty.dreammanhunt.mixin.server.ItemCooldownsAccessor;
import net.swofty.dreammanhunt.mixin.server.ServerPlayerGameModeAccessor;

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
		tag.put("mining", captureMining(player));

		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		AbstractContainerMenu menu = player.containerMenu;
		tag.putBoolean("inventory_menu_open", menu == player.inventoryMenu);
		tag.store("carried", ItemStack.OPTIONAL_CODEC, ops, menu.getCarried().copy());
		tag.put("inventory_grid", items(player.inventoryMenu.getInputGridSlots(), ops));
		if (menu != player.inventoryMenu) {
			tag.put("menu_transient", transientItems(menu, ops));
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

	private static CompoundTag captureMining(ServerPlayer player) {
		ServerPlayerGameModeAccessor mode = (ServerPlayerGameModeAccessor) player.gameMode;
		CompoundTag tag = new CompoundTag();
		tag.putInt("game_ticks", mode.dreammanhunt$getGameTicks());
		tag.putBoolean("destroying", mode.dreammanhunt$isDestroyingBlock());
		tag.store("destroy_pos", BlockPos.CODEC, mode.dreammanhunt$getDestroyPos());
		tag.store("destroy_direction", Direction.CODEC, mode.dreammanhunt$getDestroyDirection());
		tag.putInt("destroy_start", mode.dreammanhunt$getDestroyProgressStart());
		tag.putBoolean("delayed", mode.dreammanhunt$hasDelayedDestroy());
		tag.store("delayed_pos", BlockPos.CODEC, mode.dreammanhunt$getDelayedDestroyPos());
		tag.putInt("delayed_start", mode.dreammanhunt$getDelayedTickStart());
		tag.putInt("last_sent_state", mode.dreammanhunt$getLastSentState());
		return tag;
	}

	static void applyMining(ServerPlayer player, CompoundTag runtime) {
		CompoundTag tag = runtime.getCompoundOrEmpty("mining");
		if (tag.isEmpty()) {
			return;
		}
		ServerPlayerGameModeAccessor mode = (ServerPlayerGameModeAccessor) player.gameMode;
		mode.dreammanhunt$setGameTicks(tag.getIntOr("game_ticks", 0));
		mode.dreammanhunt$setDestroyingBlock(tag.getBooleanOr("destroying", false));
		mode.dreammanhunt$setDestroyPos(tag.read("destroy_pos", BlockPos.CODEC).orElse(BlockPos.ZERO));
		mode.dreammanhunt$setDestroyDirection(tag.read("destroy_direction", Direction.CODEC).orElse(Direction.DOWN));
		mode.dreammanhunt$setDestroyProgressStart(tag.getIntOr("destroy_start", 0));
		mode.dreammanhunt$setDelayedDestroy(tag.getBooleanOr("delayed", false));
		mode.dreammanhunt$setDelayedDestroyPos(tag.read("delayed_pos", BlockPos.CODEC).orElse(BlockPos.ZERO));
		mode.dreammanhunt$setDelayedTickStart(tag.getIntOr("delayed_start", 0));
		int lastSent = tag.getIntOr("last_sent_state", -1);
		mode.dreammanhunt$setLastSentState(lastSent);
		if (mode.dreammanhunt$isDestroyingBlock() && lastSent >= 0) {
			player.level().destroyBlockProgress(player.getId(), mode.dreammanhunt$getDestroyPos(), lastSent);
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
		for (Tag element : tag.getListOrEmpty("menu_transient")) {
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

	private static ListTag transientItems(AbstractContainerMenu menu, RegistryOps<Tag> ops) {
		ListTag list = new ListTag();
		if (menu instanceof AbstractMountInventoryMenu) {
			return list;
		}
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !isTransient(slot.container) || !slot.mayPlace(stack)) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.store("item", ItemStack.OPTIONAL_CODEC, ops, stack.copy());
			list.add(entry);
		}
		return list;
	}

	private static boolean isTransient(Container container) {
		if (container instanceof PlayerEnderChestContainer || container instanceof ResultContainer) {
			return false;
		}
		return container instanceof SimpleContainer || container instanceof TransientCraftingContainer || container instanceof MerchantContainer;
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
