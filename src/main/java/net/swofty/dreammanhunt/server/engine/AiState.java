package net.swofty.dreammanhunt.server.engine;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class AiState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<String> ENTITY_DENY = Set.of(
			"id", "level", "vehicle", "position", "blockPosition", "chunkPosition", "bb", "uuid", "stringUUID",
			"levelCallback", "dimensions", "customData", "inBlockState", "removalReason", "useItem", "autoSpinAttackItemStack",
			"lastDamageSource", "locatorBarIcon"
	);
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
	private static final List<PendingRef> PENDING_REFS = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.BooleanSupplier> DEFERRED = new CopyOnWriteArrayList<>();

	private record PendingRef(Object owner, Field field, UUID target, ServerLevel level) {
	}

	private AiState() {
	}

	static CompoundTag capture(Entity entity) {
		CompoundTag tag = new CompoundTag();
		tag.put("self", fields(entity, ENTITY_DENY));
		tag.putByteArray("data", entityData(entity));
		if (entity instanceof Mob mob) {
			tag.put("navigation", fields(mob.getNavigation(), Set.of()));
			tag.put("move", fields(mob.getMoveControl(), Set.of()));
			tag.put("look", fields(mob.getLookControl(), Set.of()));
			tag.put("jump", fields(mob.getJumpControl(), Set.of()));
			tag.put("body", fields(get(mob, Mob.class, "bodyRotationControl"), Set.of()));
			tag.put("goals", goals(selector(mob, "goalSelector")));
			tag.put("targets", goals(selector(mob, "targetSelector")));
		}
		if (entity instanceof LivingEntity living && hasBrainState(living)) {
			tag.put("brain", BrainState.capture(living));
		}
		return tag;
	}

	static void apply(Entity entity, CompoundTag tag, ServerLevel level) {
		try {
			applyEntityData(entity, tag.getByteArray("data").orElse(new byte[0]));
			apply(entity, tag.getCompoundOrEmpty("self"), level, ENTITY_DENY);
			if (entity instanceof Mob mob) {
				apply(mob.getNavigation(), tag.getCompoundOrEmpty("navigation"), level, Set.of());
				apply(mob.getMoveControl(), tag.getCompoundOrEmpty("move"), level, Set.of());
				apply(mob.getLookControl(), tag.getCompoundOrEmpty("look"), level, Set.of());
				apply(mob.getJumpControl(), tag.getCompoundOrEmpty("jump"), level, Set.of());
				apply(get(mob, Mob.class, "bodyRotationControl"), tag.getCompoundOrEmpty("body"), level, Set.of());
				applyGoals(selector(mob, "goalSelector"), tag.getListOrEmpty("goals"), level);
				applyGoals(selector(mob, "targetSelector"), tag.getListOrEmpty("targets"), level);
				sharePath(mob, selector(mob, "goalSelector"));
				sharePath(mob, selector(mob, "targetSelector"));
			}
			if (entity instanceof LivingEntity living) {
				tag.getCompound("brain").ifPresent(brain -> BrainState.apply(living, brain, level));
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Could not restore AI state of {}", entity, e);
		}
	}

	static void resolveReferences() {
		List<PendingRef> resolved = new ArrayList<>();
		for (PendingRef ref : PENDING_REFS) {
			Entity target = ref.level.getEntity(ref.target);
			if (target == null) {
				continue;
			}
			try {
				ref.field.set(ref.owner, target);
				resolved.add(ref);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				resolved.add(ref);
			}
		}
		PENDING_REFS.removeAll(resolved);
		List<java.util.function.BooleanSupplier> done = new ArrayList<>();
		for (java.util.function.BooleanSupplier action : DEFERRED) {
			try {
				if (action.getAsBoolean()) {
					done.add(action);
				}
			} catch (RuntimeException e) {
				done.add(action);
			}
		}
		DEFERRED.removeAll(done);
	}

	static void defer(java.util.function.BooleanSupplier action) {
		if (!action.getAsBoolean()) {
			DEFERRED.add(action);
		}
	}

	static void deferRef(Object owner, Field field, UUID target, ServerLevel level) {
		PENDING_REFS.add(new PendingRef(owner, field, target, level));
	}

	static void clearReferences() {
		PENDING_REFS.clear();
		DEFERRED.clear();
	}

	private static boolean hasBrainState(LivingEntity living) {
		Object memories = get(living.getBrain(), net.minecraft.world.entity.ai.Brain.class, "memories");
		return memories instanceof Map<?, ?> map && !map.isEmpty();
	}

	private static void sharePath(Mob mob, GoalSelector selector) {
		Path navigationPath = mob.getNavigation().getPath();
		if (selector == null || navigationPath == null) {
			return;
		}
		CompoundTag navigationTag = encodePath(navigationPath);
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			for (Field field : fieldsOf(wrapped.getGoal().getClass())) {
				if (field.getType() != Path.class) {
					continue;
				}
				try {
					if (field.get(wrapped.getGoal()) instanceof Path path && path != navigationPath && encodePath(path).equals(navigationTag)) {
						field.set(wrapped.getGoal(), navigationPath);
					}
				} catch (IllegalAccessException ignored) {
				}
			}
		}
	}

	private static GoalSelector selector(Mob mob, String name) {
		return (GoalSelector) get(mob, Mob.class, name);
	}

	private static ListTag goals(GoalSelector selector) {
		ListTag list = new ListTag();
		if (selector == null) {
			return list;
		}
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("class", wrapped.getGoal().getClass().getName());
			entry.putBoolean("running", wrapped.isRunning());
			entry.put("fields", fields(wrapped.getGoal(), Set.of()));
			entry.put("final", finalPrimitives(wrapped.getGoal()));
			list.add(entry);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private static void applyGoals(GoalSelector selector, ListTag list, ServerLevel level) {
		if (selector == null || list.isEmpty()) {
			return;
		}
		List<WrappedGoal> goals = new ArrayList<>(selector.getAvailableGoals());
		if (goals.size() != list.size()) {
			return;
		}
		Map<Goal.Flag, WrappedGoal> lockedFlags = (Map<Goal.Flag, WrappedGoal>) get(selector, GoalSelector.class, "lockedFlags");
		for (int i = 0; i < goals.size(); i++) {
			WrappedGoal wrapped = goals.get(i);
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (!wrapped.getGoal().getClass().getName().equals(entry.getStringOr("class", ""))) {
				continue;
			}
			apply(wrapped.getGoal(), entry.getCompoundOrEmpty("fields"), level, Set.of());
			applyFinalPrimitives(wrapped.getGoal(), entry.getCompoundOrEmpty("final"));
			boolean running = entry.getBooleanOr("running", false);
			set(wrapped, WrappedGoal.class, "isRunning", running);
			if (running && lockedFlags != null) {
				for (Goal.Flag flag : wrapped.getGoal().getFlags()) {
					lockedFlags.put(flag, wrapped);
				}
			}
		}
	}

	private static CompoundTag finalPrimitives(Object owner) {
		CompoundTag tag = new CompoundTag();
		for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || !field.getType().isPrimitive()) {
					continue;
				}
				try {
					field.setAccessible(true);
					Tag encoded = encode(field.getType(), field.get(owner));
					if (encoded != null) {
						tag.put(key(field), encoded);
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
		return tag;
	}

	private static void applyFinalPrimitives(Object owner, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || !field.getType().isPrimitive()) {
					continue;
				}
				Tag encoded = tag.get(key(field));
				if (encoded == null) {
					continue;
				}
				try {
					field.setAccessible(true);
					Object value = decode(field.getType(), encoded);
					if (value != Undecodable.INSTANCE) {
						field.set(owner, value);
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
	}

	private static byte[] entityData(Entity entity) {
		List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
		if (values == null || values.isEmpty()) {
			return new byte[0];
		}
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.registryAccess());
		try {
			buffer.writeVarInt(values.size());
			for (SynchedEntityData.DataValue<?> value : values) {
				value.write(buffer);
			}
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			return bytes;
		} catch (RuntimeException e) {
			return new byte[0];
		} finally {
			buffer.release();
		}
	}

	private static void applyEntityData(Entity entity, byte[] bytes) {
		if (bytes.length == 0) {
			return;
		}
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), entity.registryAccess());
		try {
			int count = buffer.readVarInt();
			List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				int id = buffer.readUnsignedByte();
				values.add(SynchedEntityData.DataValue.read(buffer, id));
			}
			entity.getEntityData().assignValues(values);
		} catch (RuntimeException e) {
			LOGGER.warn("Could not restore entity data of {}", entity, e);
		} finally {
			buffer.release();
		}
	}

	private static CompoundTag fields(Object owner, Set<String> deny) {
		CompoundTag tag = new CompoundTag();
		if (owner == null) {
			return tag;
		}
		for (Field field : fieldsOf(owner.getClass())) {
			if (deny.contains(field.getName())) {
				continue;
			}
			try {
				Tag encoded = encode(field.getType(), field.get(owner));
				if (encoded != null) {
					tag.put(key(field), encoded);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
		return tag;
	}

	private static void apply(Object owner, CompoundTag tag, ServerLevel level, Set<String> deny) {
		if (owner == null || tag.isEmpty()) {
			return;
		}
		for (Field field : fieldsOf(owner.getClass())) {
			if (deny.contains(field.getName())) {
				continue;
			}
			Tag encoded = tag.get(key(field));
			if (encoded == null) {
				continue;
			}
			try {
				if (encoded instanceof CompoundTag seeded && seeded.contains("seed")) {
					if (field.get(owner) instanceof net.minecraft.world.level.levelgen.LegacyRandomSource random) {
						((net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().set(seeded.getLongOr("seed", 0L));
					}
					continue;
				}
				if (Entity.class.isAssignableFrom(field.getType())) {
					if (encoded instanceof CompoundTag ref && ref.contains("ref")) {
						UUID target = ref.read("ref", UUIDUtil.CODEC).orElse(null);
						Entity resolved = target == null ? null : level.getEntity(target);
						if (resolved != null && field.getType().isInstance(resolved)) {
							field.set(owner, resolved);
						} else if (target != null) {
							PENDING_REFS.add(new PendingRef(owner, field, target, level));
						}
					} else {
						field.set(owner, null);
					}
					continue;
				}
				Object value = decode(field.getType(), encoded);
				if (value != Undecodable.INSTANCE) {
					field.set(owner, value);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
	}

	enum Undecodable {
		INSTANCE
	}

	static Tag encode(Class<?> type, Object value) {
		CompoundTag tag = new CompoundTag();
		if (value == null) {
			if (type.isPrimitive()) {
				return null;
			}
			tag.putBoolean("null", true);
			return supported(type) ? tag : null;
		}
		switch (value) {
			case Integer i -> tag.putInt("v", i);
			case Long l -> tag.putLong("v", l);
			case Float f -> tag.putFloat("v", f);
			case Double d -> tag.putDouble("v", d);
			case Boolean b -> tag.putBoolean("v", b);
			case Short s -> tag.putShort("v", s);
			case Byte b -> tag.putByte("v", b);
			case String s -> tag.putString("v", s);
			case Enum<?> e -> tag.putString("v", e.name());
			case Vec3 v -> {
				tag.putDouble("x", v.x);
				tag.putDouble("y", v.y);
				tag.putDouble("z", v.z);
			}
			case BlockPos p -> tag.putLong("v", p.asLong());
			case Vec3i p -> tag.putIntArray("vi", new int[]{p.getX(), p.getY(), p.getZ()});
			case UUID u -> tag.store("v", UUIDUtil.CODEC, u);
			case Entity e -> tag.store("ref", UUIDUtil.CODEC, e.getUUID());
			case net.minecraft.world.entity.EntityReference<?> reference -> tag.store("eref", UUIDUtil.CODEC, reference.getUUID());
			case net.minecraft.world.level.levelgen.LegacyRandomSource random -> tag.putLong("seed", ((net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().get());
			case Path path -> tag.put("path", encodePath(path));
			default -> {
				return null;
			}
		}
		return tag;
	}

	private static boolean supported(Class<?> type) {
		return type == Integer.class || type == Long.class || type == Float.class || type == Double.class || type == Boolean.class
				|| type == String.class || type.isEnum() || type == Vec3.class || type == BlockPos.class || type == Vec3i.class || type == UUID.class
				|| Entity.class.isAssignableFrom(type) || type == Path.class || type == net.minecraft.world.entity.EntityReference.class;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static Object decode(Class<?> type, Tag encoded) {
		if (!(encoded instanceof CompoundTag tag)) {
			return Undecodable.INSTANCE;
		}
		if (tag.getBooleanOr("null", false)) {
			return null;
		}
		if (type == int.class || type == Integer.class) {
			return tag.getIntOr("v", 0);
		}
		if (type == long.class || type == Long.class) {
			return tag.getLongOr("v", 0L);
		}
		if (type == float.class || type == Float.class) {
			return tag.getFloatOr("v", 0.0F);
		}
		if (type == double.class || type == Double.class) {
			return tag.getDoubleOr("v", 0.0);
		}
		if (type == boolean.class || type == Boolean.class) {
			return tag.getBooleanOr("v", false);
		}
		if (type == short.class || type == Short.class) {
			return tag.getShortOr("v", (short) 0);
		}
		if (type == byte.class || type == Byte.class) {
			return tag.getByteOr("v", (byte) 0);
		}
		if (type == String.class) {
			return tag.getStringOr("v", "");
		}
		if (type.isEnum()) {
			try {
				return Enum.valueOf((Class) type, tag.getStringOr("v", ""));
			} catch (IllegalArgumentException e) {
				return Undecodable.INSTANCE;
			}
		}
		if (type == Vec3.class) {
			return new Vec3(tag.getDoubleOr("x", 0.0), tag.getDoubleOr("y", 0.0), tag.getDoubleOr("z", 0.0));
		}
		if (type == BlockPos.class) {
			return BlockPos.of(tag.getLongOr("v", 0L));
		}
		if (type == UUID.class) {
			return tag.read("v", UUIDUtil.CODEC).orElse(null);
		}
		if (type == Vec3i.class) {
			if (tag.contains("v")) {
				return BlockPos.of(tag.getLongOr("v", 0L));
			}
			int[] xyz = tag.getIntArray("vi").orElse(new int[3]);
			return xyz.length == 3 ? new Vec3i(xyz[0], xyz[1], xyz[2]) : Undecodable.INSTANCE;
		}
		if (type == Path.class) {
			return decodePath(tag.getCompoundOrEmpty("path"));
		}
		if (type == net.minecraft.world.entity.EntityReference.class && tag.contains("eref")) {
			return tag.read("eref", UUIDUtil.CODEC).map(net.minecraft.world.entity.EntityReference::of).orElse(null);
		}
		return Undecodable.INSTANCE;
	}

	static CompoundTag encodePath(Path path) {
		CompoundTag tag = new CompoundTag();
		ListTag nodes = new ListTag();
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			CompoundTag entry = new CompoundTag();
			entry.putInt("x", node.x);
			entry.putInt("y", node.y);
			entry.putInt("z", node.z);
			entry.putString("type", node.type.name());
			entry.putFloat("malus", node.costMalus);
			entry.putFloat("walked", node.walkedDistance);
			nodes.add(entry);
		}
		tag.put("nodes", nodes);
		tag.putLong("target", path.getTarget().asLong());
		tag.putBoolean("reached", path.canReach());
		tag.putInt("next", path.getNextNodeIndex());
		return tag;
	}

	static Path decodePath(CompoundTag tag) {
		List<Node> nodes = new ArrayList<>();
		for (Tag element : tag.getListOrEmpty("nodes")) {
			if (element instanceof CompoundTag entry) {
				Node node = new Node(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0));
				try {
					node.type = PathType.valueOf(entry.getStringOr("type", PathType.BLOCKED.name()));
				} catch (IllegalArgumentException ignored) {
				}
				node.costMalus = entry.getFloatOr("malus", 0.0F);
				node.walkedDistance = entry.getFloatOr("walked", 0.0F);
				nodes.add(node);
			}
		}
		Path path = new Path(nodes, BlockPos.of(tag.getLongOr("target", 0L)), tag.getBooleanOr("reached", false));
		path.setNextNodeIndex(tag.getIntOr("next", 0));
		return path;
	}

	private static String key(Field field) {
		return field.getDeclaringClass().getSimpleName() + "." + field.getName();
	}

	static List<Field> fieldsOf(Class<?> type) {
		return FIELDS.computeIfAbsent(type, t -> {
			List<Field> result = new ArrayList<>();
			for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					int modifiers = field.getModifiers();
					if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
						continue;
					}
					try {
						field.setAccessible(true);
						result.add(field);
					} catch (RuntimeException ignored) {
					}
				}
			}
			return result;
		});
	}

	private static Object get(Object owner, Class<?> declaring, String name) {
		try {
			Field field = declaring.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(owner);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static void set(Object owner, Class<?> declaring, String name, Object value) {
		try {
			Field field = declaring.getDeclaredField(name);
			field.setAccessible(true);
			field.set(owner, value);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}
}
