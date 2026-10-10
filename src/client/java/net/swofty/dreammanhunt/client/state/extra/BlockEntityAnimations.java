package net.swofty.dreammanhunt.client.state.extra;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class BlockEntityAnimations {
	private static final int MAX_DEPTH = 3;
	private static final String NESTED_PREFIX = "net.minecraft.world.level.";
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

	private BlockEntityAnimations() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		CompoundTag out = new CompoundTag();
		out.putString("dimension", level.dimension().identifier().toString());
		ListTag list = new ListTag();
		for (LevelChunk chunk : loadedChunks(minecraft)) {
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				CompoundTag state = new CompoundTag();
				walk(blockEntity, blockEntity.getClass(), state, 0, Collections.newSetFromMap(new IdentityHashMap<>()), true);
				if (state.isEmpty()) {
					continue;
				}
				Identifier type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
				state.putLong("@pos", blockEntity.getBlockPos().asLong());
				state.putString("@type", String.valueOf(type));
				list.add(state);
			}
		}
		out.put("list", list);
		return out;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		ClientLevel level = minecraft.level;
		if (tag.isEmpty() || !level.dimension().identifier().toString().equals(tag.getStringOr("dimension", ""))) {
			return;
		}
		for (Tag raw : tag.getListOrEmpty("list")) {
			if (!(raw instanceof CompoundTag state)) {
				continue;
			}
			BlockPos pos = BlockPos.of(state.getLongOr("@pos", 0L));
			BlockEntity blockEntity = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false) == null ? null : level.getBlockEntity(pos);
			if (blockEntity == null || !String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())).equals(state.getStringOr("@type", ""))) {
				continue;
			}
			walk(blockEntity, blockEntity.getClass(), state, 0, Collections.newSetFromMap(new IdentityHashMap<>()), false);
		}
	}

	private static List<LevelChunk> loadedChunks(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		ChunkPos center = minecraft.player.chunkPosition();
		int radius = minecraft.options.getEffectiveRenderDistance() + 2;
		List<LevelChunk> chunks = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(center.x() + dx, center.z() + dz, false);
				if (chunk != null) {
					chunks.add(chunk);
				}
			}
		}
		return chunks;
	}

	private static void walk(Object owner, Class<?> type, CompoundTag tag, int depth, Set<Object> seen, boolean capture) {
		if (!seen.add(owner)) {
			return;
		}
		for (Field field : fields(type, owner instanceof BlockEntity ? BlockEntity.class : Object.class)) {
			try {
				walkField(owner, field, tag, depth, seen, capture);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
	}

	private static void walkField(Object owner, Field field, CompoundTag tag, int depth, Set<Object> seen, boolean capture) throws ReflectiveOperationException {
		String key = field.getDeclaringClass().getSimpleName() + "." + field.getName();
		Class<?> type = field.getType();
		boolean isFinal = Modifier.isFinal(field.getModifiers());
		if (type.isPrimitive()) {
			if (isFinal) {
				return;
			}
			if (capture) {
				Primitives.put(tag, key, type, field.get(owner));
			} else if (tag.contains(key)) {
				Primitives.set(owner, field, tag, key, type);
			}
			return;
		}
		Object value = field.get(owner);
		if (type.isEnum()) {
			if (isFinal) {
				return;
			}
			if (capture) {
				if (value != null) {
					tag.putString(key, ((Enum<?>) value).name());
				}
			} else if (tag.contains(key)) {
				setEnum(owner, field, type, tag.getStringOr(key, ""));
			}
			return;
		}
		if (value instanceof List<?> list && field.getName().toLowerCase().contains("beam")) {
			beamSections(owner, field, list, tag, key, capture, isFinal);
			return;
		}
		if (value instanceof List<?> list && field.getName().equals("effectBlocks")) {
			blockPositions(list, tag, key, capture);
			return;
		}
		if (value == null || depth >= MAX_DEPTH || !nestable(value)) {
			return;
		}
		CompoundTag nested = capture ? new CompoundTag() : tag.getCompoundOrEmpty(key);
		if (!capture && nested.isEmpty()) {
			return;
		}
		walk(value, value.getClass(), nested, depth + 1, seen, capture);
		if (capture && !nested.isEmpty()) {
			tag.put(key, nested);
		}
	}

	private static boolean nestable(Object value) {
		Class<?> type = value.getClass();
		return type.getName().startsWith(NESTED_PREFIX)
			&& !type.isRecord()
			&& !type.isEnum()
			&& !(value instanceof Level)
			&& !(value instanceof BlockState)
			&& !(value instanceof BlockEntity)
			&& !(value instanceof Entity)
			&& !(value instanceof BlockPos);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setEnum(Object owner, Field field, Class<?> type, String name) throws IllegalAccessException {
		try {
			field.set(owner, Enum.valueOf((Class) type, name));
		} catch (IllegalArgumentException ignored) {
		}
	}

	@SuppressWarnings("unchecked")
	private static void beamSections(Object owner, Field field, List<?> list, CompoundTag tag, String key, boolean capture, boolean isFinal) throws IllegalAccessException {
		if (capture) {
			int[] values = new int[list.size() * 2];
			for (int i = 0; i < list.size(); i++) {
				if (!(list.get(i) instanceof BeaconBeamOwner.Section section)) {
					return;
				}
				values[i * 2] = section.getColor();
				values[i * 2 + 1] = section.getHeight();
			}
			tag.putIntArray(key, values);
			return;
		}
		int[] values = tag.getIntArray(key).orElse(null);
		if (values == null || isFinal) {
			return;
		}
		List<BeaconBeamOwner.Section> sections = new ArrayList<>();
		for (int i = 0; i + 1 < values.length; i += 2) {
			BeaconBeamOwner.Section section = new BeaconBeamOwner.Section(values[i]);
			for (int h = 1; h < values[i + 1]; h++) {
				section.increaseHeight();
			}
			sections.add(section);
		}
		field.set(owner, sections);
	}

	@SuppressWarnings("unchecked")
	private static void blockPositions(List<?> list, CompoundTag tag, String key, boolean capture) {
		if (capture) {
			long[] values = new long[list.size()];
			for (int i = 0; i < list.size(); i++) {
				if (!(list.get(i) instanceof BlockPos pos)) {
					return;
				}
				values[i] = pos.asLong();
			}
			tag.putLongArray(key, values);
			return;
		}
		long[] values = tag.getLongArray(key).orElse(null);
		if (values == null) {
			return;
		}
		List<BlockPos> target = (List<BlockPos>) list;
		target.clear();
		for (long value : values) {
			target.add(BlockPos.of(value));
		}
	}

	private static List<Field> fields(Class<?> type, Class<?> stop) {
		return FIELDS.computeIfAbsent(type, key -> {
			List<Field> fields = new ArrayList<>();
			for (Class<?> current = key; current != null && current != stop && current != Object.class; current = current.getSuperclass()) {
				for (Field field : current.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
						continue;
					}
					try {
						field.setAccessible(true);
						fields.add(field);
					} catch (RuntimeException ignored) {
					}
				}
			}
			return List.copyOf(fields);
		});
	}
}
