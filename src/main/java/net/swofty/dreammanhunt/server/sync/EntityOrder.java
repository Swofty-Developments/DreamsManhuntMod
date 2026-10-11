package net.swofty.dreammanhunt.server.sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.swofty.dreammanhunt.mixin.sync.EntityTickListAccessor;
import net.swofty.dreammanhunt.mixin.sync.ServerLevelAccessor;

public final class EntityOrder {
	private EntityOrder() {
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			ListTag order = new ListTag();
			((ServerLevelAccessor) level).dm$getEntityTickList().forEach(entity -> order.add(StringTag.valueOf(entity.getUUID().toString())));
			out.put(level.dimension().identifier().toString(), order);
		}
		return out;
	}

	public static void apply(MinecraftServer server, CompoundTag tag) {
		for (ServerLevel level : server.getAllLevels()) {
			ListTag order = tag.getListOrEmpty(level.dimension().identifier().toString());
			if (order.isEmpty()) {
				continue;
			}
			Map<UUID, Integer> rank = new HashMap<>();
			for (int i = 0; i < order.size(); i++) {
				Tag raw = order.get(i);
				if (raw instanceof StringTag(String value)) {
					try {
						rank.putIfAbsent(UUID.fromString(value), i);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			EntityTickListAccessor list = (EntityTickListAccessor) ((ServerLevelAccessor) level).dm$getEntityTickList();
			if (list.dm$getIterated() != null) {
				continue;
			}
			Int2ObjectMap<Entity> active = list.dm$getActive();
			List<Entity> entities = new ArrayList<>(active.values());
			List<Entity> sorted = new ArrayList<>(entities);
			sorted.sort((a, b) -> Integer.compare(rank.getOrDefault(a.getUUID(), Integer.MAX_VALUE), rank.getOrDefault(b.getUUID(), Integer.MAX_VALUE)));
			active.clear();
			for (Entity entity : sorted) {
				active.put(entity.getId(), entity);
			}
		}
	}
}
