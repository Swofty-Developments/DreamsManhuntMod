package net.swofty.dreammanhunt.server.sync;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;

public final class TickOrder {
	private TickOrder() {
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag tag = new CompoundTag();
			ListTag ticks = new ListTag();
			for (LevelChunk chunk : tickingChunks(level)) {
				if (chunk.getBlockTicks() instanceof LevelChunkTicks<Block> blockTicks) {
					blockTicks.getAll().forEach(tick -> ticks.add(entry("b" + BuiltInRegistries.BLOCK.getKey(tick.type()), tick)));
				}
				if (chunk.getFluidTicks() instanceof LevelChunkTicks<Fluid> fluidTicks) {
					fluidTicks.getAll().forEach(tick -> ticks.add(entry("f" + BuiltInRegistries.FLUID.getKey(tick.type()), tick)));
				}
			}
			tag.put("ticks", ticks);
			try {
				tag.putLong("subTickCount", subTickCount().getLong(level));
			} catch (ReflectiveOperationException ignored) {
			}
			out.put(level.dimension().identifier().toString(), tag);
		}
		return out;
	}

	public static void apply(MinecraftServer server, CompoundTag tag) {
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag saved = tag.getCompoundOrEmpty(level.dimension().identifier().toString());
			if (saved.isEmpty()) {
				continue;
			}
			Map<String, Long> order = new HashMap<>();
			for (Tag raw : saved.getListOrEmpty("ticks")) {
				if (raw instanceof CompoundTag entry) {
					order.put(entry.getStringOr("k", ""), entry.getLongOr("s", 0L));
				}
			}
			for (LevelChunk chunk : tickingChunks(level)) {
				if (chunk.getBlockTicks() instanceof LevelChunkTicks<Block> blockTicks) {
					reorder(blockTicks, order, tick -> "b" + BuiltInRegistries.BLOCK.getKey(tick.type()));
				}
				if (chunk.getFluidTicks() instanceof LevelChunkTicks<Fluid> fluidTicks) {
					reorder(fluidTicks, order, tick -> "f" + BuiltInRegistries.FLUID.getKey(tick.type()));
				}
			}
			if (saved.contains("subTickCount")) {
				try {
					subTickCount().setLong(level, saved.getLongOr("subTickCount", 0L));
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}
	}

	private static <T> void reorder(LevelChunkTicks<T> ticks, Map<String, Long> order, java.util.function.Function<ScheduledTick<T>, String> type) {
		List<ScheduledTick<T>> current = new ArrayList<>(ticks.getAll().toList());
		List<ScheduledTick<T>> replaced = new ArrayList<>();
		boolean changed = false;
		for (ScheduledTick<T> tick : current) {
			Long saved = order.get(key(type.apply(tick), tick));
			if (saved != null && saved != tick.subTickOrder()) {
				replaced.add(new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick(), tick.priority(), saved));
				changed = true;
			} else {
				replaced.add(tick);
			}
		}
		if (!changed) {
			return;
		}
		ticks.removeIf(tick -> true);
		replaced.forEach(ticks::schedule);
	}

	private static CompoundTag entry(String type, ScheduledTick<?> tick) {
		CompoundTag entry = new CompoundTag();
		entry.putString("k", key(type, tick));
		entry.putLong("s", tick.subTickOrder());
		return entry;
	}

	private static String key(String type, ScheduledTick<?> tick) {
		BlockPos pos = tick.pos();
		return type + "@" + pos.asLong() + "#" + tick.triggerTick() + "/" + tick.priority().getValue();
	}

	private static List<LevelChunk> tickingChunks(ServerLevel level) {
		List<LevelChunk> chunks = new ArrayList<>();
		level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunks::add);
		return chunks;
	}

	private static Field subTickCount() throws NoSuchFieldException {
		Field field = Level.class.getDeclaredField("subTickCount");
		field.setAccessible(true);
		return field;
	}
}
