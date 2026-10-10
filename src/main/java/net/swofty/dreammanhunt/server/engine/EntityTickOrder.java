package net.swofty.dreammanhunt.server.engine;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class EntityTickOrder {
	private EntityTickOrder() {
	}

	static ListTag capture(MinecraftServer server) {
		ListTag levels = new ListTag();
		for (ServerLevel level : server.getAllLevels()) {
			Int2ObjectMap<Entity> active = active(level);
			if (active == null) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			tag.putString("dimension", level.dimension().identifier().toString());
			ListTag order = new ListTag();
			for (Entity entity : active.values()) {
				CompoundTag entry = new CompoundTag();
				entry.store("uuid", UUIDUtil.CODEC, entity.getUUID());
				order.add(entry);
			}
			tag.put("order", order);
			levels.add(tag);
		}
		return levels;
	}

	static void apply(MinecraftServer server, ListTag levels) {
		for (Tag element : levels) {
			if (!(element instanceof CompoundTag tag)) {
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.dimension().identifier().toString().equals(tag.getStringOr("dimension", ""))) {
					continue;
				}
				Int2ObjectMap<Entity> active = active(level);
				if (active == null) {
					continue;
				}
				Map<UUID, Entity> byUuid = new HashMap<>();
				for (Entity entity : active.values()) {
					byUuid.put(entity.getUUID(), entity);
				}
				Int2ObjectMap<Entity> reordered = new Int2ObjectLinkedOpenHashMap<>();
				for (Tag entry : tag.getListOrEmpty("order")) {
					if (entry instanceof CompoundTag compound) {
						compound.read("uuid", UUIDUtil.CODEC).map(byUuid::remove).ifPresent(entity -> reordered.put(entity.getId(), entity));
					}
				}
				for (Entity entity : active.values()) {
					if (byUuid.containsKey(entity.getUUID())) {
						reordered.put(entity.getId(), entity);
					}
				}
				setActive(level, reordered);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Int2ObjectMap<Entity> active(ServerLevel level) {
		try {
			Object tickList = field(ServerLevel.class, "entityTickList").get(level);
			return (Int2ObjectMap<Entity>) field(tickList.getClass(), "active").get(tickList);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static void setActive(ServerLevel level, Int2ObjectMap<Entity> map) {
		try {
			Object tickList = field(ServerLevel.class, "entityTickList").get(level);
			field(tickList.getClass(), "active").set(tickList, map);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
