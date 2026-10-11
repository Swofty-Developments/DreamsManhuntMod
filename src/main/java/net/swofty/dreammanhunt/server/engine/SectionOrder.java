package net.swofty.dreammanhunt.server.engine;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SectionOrder {
	private static volatile CompoundTag pending;
	private static boolean registered;

	private SectionOrder() {
	}

	static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			CompoundTag tag = pending;
			if (tag != null && server.tickRateManager().runsNormally()) {
				pending = null;
				apply(server, tag);
			}
		});
	}

	static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag sections = new CompoundTag();
			for (Map.Entry<Long, Object> entry : sections(level).entrySet()) {
				List<?> all = allInstances(entry.getValue());
				if (all == null || all.size() < 2) {
					continue;
				}
				ListTag order = new ListTag();
				for (Object element : all) {
					if (element instanceof Entity entity) {
						CompoundTag ref = new CompoundTag();
						ref.store("u", UUIDUtil.CODEC, entity.getUUID());
						order.add(ref);
					}
				}
				sections.put(Long.toString(entry.getKey()), order);
			}
			out.put(level.dimension().identifier().toString(), sections);
		}
		return out;
	}

	static void arm(CompoundTag tag) {
		pending = tag;
	}

	static void applyNow(MinecraftServer server) {
		CompoundTag tag = pending;
		if (tag != null) {
			apply(server, tag);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void apply(MinecraftServer server, CompoundTag tag) {
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag sections = tag.getCompoundOrEmpty(level.dimension().identifier().toString());
			Map<Long, Object> live = sections(level);
			for (String key : sections.keySet()) {
				Object section = live.get(Long.parseLong(key));
				if (section == null) {
					continue;
				}
				Map<UUID, Integer> rank = new HashMap<>();
				ListTag order = sections.getListOrEmpty(key);
				for (int i = 0; i < order.size(); i++) {
					Tag element = order.get(i);
					if (element instanceof CompoundTag ref) {
						int index = i;
						ref.read("u", UUIDUtil.CODEC).ifPresent(uuid -> rank.putIfAbsent(uuid, index));
					}
				}
				Object storage = get(section, "storage");
				if (storage == null) {
					continue;
				}
				sort((List) get(storage, "allInstances"), rank);
				Object byClass = get(storage, "byClass");
				if (byClass instanceof Map<?, ?> map) {
					for (Object list : map.values()) {
						sort((List) list, rank);
					}
				}
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void sort(List list, Map<UUID, Integer> rank) {
		if (list == null || list.size() < 2) {
			return;
		}
		List copy = new ArrayList(list);
		copy.sort((a, b) -> Integer.compare(rank(a, rank), rank(b, rank)));
		list.clear();
		list.addAll(copy);
	}

	private static int rank(Object entity, Map<UUID, Integer> rank) {
		return entity instanceof Entity e ? rank.getOrDefault(e.getUUID(), Integer.MAX_VALUE) : Integer.MAX_VALUE;
	}

	@SuppressWarnings("unchecked")
	private static Map<Long, Object> sections(ServerLevel level) {
		Object manager = get(level, "entityManager");
		Object storage = manager == null ? null : get(manager, "sectionStorage");
		Object sections = storage == null ? null : get(storage, "sections");
		return sections instanceof Map<?, ?> map ? (Map<Long, Object>) map : Map.of();
	}

	private static List<?> allInstances(Object section) {
		Object storage = get(section, "storage");
		Object all = storage == null ? null : get(storage, "allInstances");
		return all instanceof List<?> list ? list : null;
	}

	private static Object get(Object owner, String name) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(owner);
			} catch (NoSuchFieldException e) {
				continue;
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}
		return null;
	}
}
