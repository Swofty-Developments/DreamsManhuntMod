package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BrainState {
	private static final int MAX_DEPTH = 6;
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

	private BrainState() {
	}

	static CompoundTag capture(LivingEntity entity) {
		Brain<?> brain = entity.getBrain();
		CompoundTag tag = new CompoundTag();
		tag.put("memories", captureMemories(brain));
		CompoundTag sensors = new CompoundTag();
		for (Map.Entry<?, ?> entry : sensors(brain).entrySet()) {
			sensors.put(sensorKey(entry.getKey()), walk(entry.getValue()));
		}
		tag.put("sensors", sensors);
		tag.put("behaviors", walkBehaviors(brain, null));
		tag.put("active", activities((Set<?>) get(brain, "activeActivities")));
		tag.put("core", activities((Set<?>) get(brain, "coreActivities")));
		Object defaultActivity = get(brain, "defaultActivity");
		if (defaultActivity instanceof Activity activity) {
			tag.putString("default", activityKey(activity));
		}
		Object lastScheduleUpdate = get(brain, "lastScheduleUpdate");
		if (lastScheduleUpdate instanceof Long last) {
			tag.putLong("last_schedule_update", last);
		}
		return tag;
	}

	static void apply(LivingEntity entity, CompoundTag tag, ServerLevel level) {
		Brain<?> brain = entity.getBrain();
		applyMemories(entity, brain, tag.getCompoundOrEmpty("memories"), level);
		CompoundTag sensors = tag.getCompoundOrEmpty("sensors");
		for (Map.Entry<?, ?> entry : sensors(brain).entrySet()) {
			sensors.getCompound(sensorKey(entry.getKey())).ifPresent(state -> apply(entry.getValue(), state, level));
		}
		walkBehaviors(brain, new Applier(tag.getCompoundOrEmpty("behaviors"), level));
		applyActivities(brain, "activeActivities", tag.getListOrEmpty("active"));
		applyActivities(brain, "coreActivities", tag.getListOrEmpty("core"));
		tag.getString("default").map(BrainState::activity).ifPresent(activity -> set(brain, "defaultActivity", activity));
		tag.getLong("last_schedule_update").ifPresent(last -> set(brain, "lastScheduleUpdate", last));
	}

	private record Applier(CompoundTag state, ServerLevel level) {
	}

	private static CompoundTag walkBehaviors(Brain<?> brain, Applier applier) {
		CompoundTag out = new CompoundTag();
		Object byPriority = get(brain, "availableBehaviorsByPriority");
		if (!(byPriority instanceof Map<?, ?> priorities)) {
			return out;
		}
		for (Map.Entry<?, ?> priority : priorities.entrySet()) {
			if (!(priority.getValue() instanceof Map<?, ?> activities)) {
				continue;
			}
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> activity : activities.entrySet()) {
				if (activity.getKey() instanceof Activity key) {
					sorted.put(activityKey(key), activity.getValue());
				}
			}
			for (Map.Entry<String, Object> activity : sorted.entrySet()) {
				if (!(activity.getValue() instanceof Collection<?> behaviors)) {
					continue;
				}
				int index = 0;
				for (Object behavior : behaviors) {
					String key = priority.getKey() + "/" + activity.getKey() + "/" + index++ + "/" + behavior.getClass().getName();
					if (applier == null) {
						out.put(key, walk(behavior));
					} else {
						applier.state.getCompound(key).ifPresent(state -> apply(behavior, state, applier.level));
					}
				}
			}
		}
		return out;
	}

	static CompoundTag walk(Object root) {
		CompoundTag out = new CompoundTag();
		walk(root, out, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
		return out;
	}

	private static void walk(Object node, CompoundTag out, int depth, Set<Object> seen) {
		if (node == null || depth > MAX_DEPTH || !seen.add(node)) {
			return;
		}
		if (node instanceof ShufflingList<?> shuffling) {
			List<?> entries = (List<?>) get(shuffling, "entries");
			if (entries != null) {
				ListTag order = new ListTag();
				ListTag children = new ListTag();
				for (Object entry : entries) {
					CompoundTag signature = new CompoundTag();
					signature.putString("v", signature(entry));
					order.add(signature);
					CompoundTag child = new CompoundTag();
					walk(entry, child, depth + 1, seen);
					children.add(child);
				}
				out.put("@order", order);
				out.put("@entries", children);
			}
			return;
		}
		if (node instanceof Collection<?> collection) {
			ListTag children = new ListTag();
			for (Object element : collection) {
				CompoundTag child = new CompoundTag();
				if (descendable(element)) {
					walk(element, child, depth + 1, seen);
				}
				children.add(child);
			}
			out.put("@elements", children);
			return;
		}
		for (Field field : fields(node.getClass())) {
			Object value;
			try {
				value = field.get(node);
			} catch (IllegalAccessException e) {
				continue;
			}
			String key = field.getDeclaringClass().getSimpleName() + "." + field.getName();
			if (!Modifier.isFinal(field.getModifiers())) {
				Tag encoded = AiState.encode(field.getType(), value);
				if (encoded != null) {
					out.put(key, encoded);
					continue;
				}
			}
			if (descendable(value)) {
				CompoundTag child = new CompoundTag();
				walk(value, child, depth + 1, seen);
				if (!child.isEmpty()) {
					out.put(key, child);
				}
			}
		}
	}

	static void apply(Object root, CompoundTag state, ServerLevel level) {
		apply(root, state, level, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void apply(Object node, CompoundTag state, ServerLevel level, int depth, Set<Object> seen) {
		if (node == null || depth > MAX_DEPTH || !seen.add(node)) {
			return;
		}
		if (node instanceof ShufflingList<?> shuffling) {
			List entries = (List) get(shuffling, "entries");
			if (entries == null) {
				return;
			}
			ListTag order = state.getListOrEmpty("@order");
			if (order.size() == entries.size()) {
				List remaining = new ArrayList(entries);
				List reordered = new ArrayList();
				for (Tag element : order) {
					String wanted = element instanceof CompoundTag compound ? compound.getStringOr("v", "") : "";
					for (int i = 0; i < remaining.size(); i++) {
						if (signature(remaining.get(i)).equals(wanted)) {
							reordered.add(remaining.remove(i));
							break;
						}
					}
				}
				reordered.addAll(remaining);
				entries.clear();
				entries.addAll(reordered);
			}
			ListTag children = state.getListOrEmpty("@entries");
			for (int i = 0; i < Math.min(children.size(), entries.size()); i++) {
				apply(entries.get(i), children.getCompoundOrEmpty(i), level, depth + 1, seen);
			}
			return;
		}
		if (node instanceof Collection<?> collection) {
			ListTag children = state.getListOrEmpty("@elements");
			int i = 0;
			for (Object element : collection) {
				if (i < children.size() && descendable(element)) {
					apply(element, children.getCompoundOrEmpty(i), level, depth + 1, seen);
				}
				i++;
			}
			return;
		}
		for (Field field : fields(node.getClass())) {
			String key = field.getDeclaringClass().getSimpleName() + "." + field.getName();
			Tag encoded = state.get(key);
			if (encoded == null) {
				continue;
			}
			try {
				if (!Modifier.isFinal(field.getModifiers()) && encoded instanceof CompoundTag compound && !compound.contains("@order") && !compound.contains("@elements") && simple(compound)) {
					if (Entity.class.isAssignableFrom(field.getType())) {
						UUID target = compound.read("ref", UUIDUtil.CODEC).orElse(null);
						Entity resolved = target == null ? null : level.getEntity(target);
						if (target == null) {
							field.set(node, null);
						} else if (resolved != null && field.getType().isInstance(resolved)) {
							field.set(node, resolved);
						} else {
							AiState.deferRef(node, field, target, level);
						}
						continue;
					}
					Object value = AiState.decode(field.getType(), compound);
					if (value != AiState.Undecodable.INSTANCE) {
						field.set(node, value);
						continue;
					}
				}
				Object value = field.get(node);
				if (descendable(value) && encoded instanceof CompoundTag child) {
					apply(value, child, level, depth + 1, seen);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
	}

	private static boolean simple(CompoundTag compound) {
		return compound.contains("v") || compound.contains("null") || compound.contains("ref") || compound.contains("path")
				|| compound.contains("vi") || (compound.contains("x") && compound.contains("y") && compound.contains("z") && compound.size() == 3);
	}

	private static boolean descendable(Object value) {
		if (value == null || value instanceof Entity || value instanceof Brain<?> || value instanceof ServerLevel || value instanceof Class<?>) {
			return false;
		}
		if (value instanceof BehaviorControl<?> || value instanceof Sensor<?> || value instanceof ShufflingList<?>) {
			return true;
		}
		String name = value.getClass().getName();
		if (name.contains("$$Lambda")) {
			return true;
		}
		if (name.startsWith("org.apache.commons.lang3.mutable.")) {
			return true;
		}
		if (value instanceof ShufflingList.WeightedEntry<?>) {
			return true;
		}
		if (value instanceof Collection<?> collection) {
			for (Object element : collection) {
				return descendable(element);
			}
			return false;
		}
		return name.startsWith("net.minecraft.world.entity.ai.behavior.") || name.startsWith("net.minecraft.world.entity.ai.sensing.");
	}

	private static String signature(Object entry) {
		Object data = get(entry, "data");
		Object weight = get(entry, "weight");
		return (data == null ? "null" : data.getClass().getName()) + "#" + weight;
	}

	private static CompoundTag captureMemories(Brain<?> brain) {
		CompoundTag out = new CompoundTag();
		Object memories = get(brain, "memories");
		if (!(memories instanceof Map<?, ?> map)) {
			return out;
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof MemoryModuleType<?> type) || type.canSerialize() || !(entry.getValue() instanceof MemorySlot<?> slot)) {
				continue;
			}
			Identifier id = BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(type);
			if (id == null) {
				continue;
			}
			CompoundTag memory = new CompoundTag();
			memory.putLong("ttl", slot.timeToLive());
			Object value = slot.value();
			if (value == null) {
				memory.putBoolean("empty", true);
			} else {
				CompoundTag encoded = encodeValue(value);
				if (encoded == null) {
					continue;
				}
				memory.put("value", encoded);
			}
			out.put(id.toString(), memory);
		}
		return out;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void applyMemories(LivingEntity entity, Brain<?> brain, CompoundTag memories, ServerLevel level) {
		Object raw = get(brain, "memories");
		if (!(raw instanceof Map<?, ?> map)) {
			return;
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof MemoryModuleType<?> type) || type.canSerialize() || !(entry.getValue() instanceof MemorySlot slot)) {
				continue;
			}
			Identifier id = BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(type);
			CompoundTag memory = id == null ? null : memories.getCompound(id.toString()).orElse(null);
			if (memory == null) {
				continue;
			}
			long ttl = memory.getLongOr("ttl", Long.MAX_VALUE);
			if (memory.getBooleanOr("empty", false)) {
				slot.clear();
				set(slot, "timeToLive", ttl);
				continue;
			}
			CompoundTag value = memory.getCompoundOrEmpty("value");
			AiState.defer(() -> {
				Object decoded = decodeValue(value, entity, level);
				if (decoded == Unresolved.INSTANCE) {
					return false;
				}
				if (decoded != null) {
					slot.set(decoded, ttl);
				}
				return true;
			});
		}
	}

	private enum Unresolved {
		INSTANCE
	}

	private static CompoundTag encodeValue(Object value) {
		CompoundTag tag = new CompoundTag();
		switch (value) {
			case Entity entity -> tag.store("entity", UUIDUtil.CODEC, entity.getUUID());
			case NearestVisibleLivingEntities visible -> {
				Object list = get(visible, "nearbyEntities");
				tag.put("visible", entities(list instanceof List<?> l ? l : List.of()));
			}
			case WalkTarget walk -> {
				CompoundTag tracker = encodeTracker(walk.getTarget());
				if (tracker == null) {
					return null;
				}
				tag.put("walk", tracker);
				tag.putFloat("speed", walk.getSpeedModifier());
				tag.putInt("close", walk.getCloseEnoughDist());
			}
			case PositionTracker tracker -> {
				CompoundTag encoded = encodeTracker(tracker);
				if (encoded == null) {
					return null;
				}
				tag.put("tracker", encoded);
			}
			case Path path -> tag.put("path", AiState.encodePath(path));
			case List<?> list when list.stream().allMatch(e -> e instanceof Entity) -> tag.put("entities", entities(list));
			default -> {
				Tag simple = AiState.encode(value.getClass(), value);
				if (!(simple instanceof CompoundTag compound)) {
					if (value instanceof net.minecraft.util.Unit) {
						tag.putBoolean("unit", true);
						return tag;
					}
					return null;
				}
				tag.put("simple", compound);
				tag.putString("type", value.getClass().getName());
			}
		}
		return tag;
	}

	private static Object decodeValue(CompoundTag tag, LivingEntity body, ServerLevel level) {
		if (tag.contains("entity")) {
			Entity entity = tag.read("entity", UUIDUtil.CODEC).map(level::getEntity).orElse(null);
			return entity == null ? Unresolved.INSTANCE : entity;
		}
		if (tag.contains("visible")) {
			List<LivingEntity> entities = resolveAll(tag.getListOrEmpty("visible"), level);
			return entities == null ? Unresolved.INSTANCE : new NearestVisibleLivingEntities(level, body, entities);
		}
		if (tag.contains("entities")) {
			List<LivingEntity> entities = resolveAll(tag.getListOrEmpty("entities"), level);
			return entities == null ? Unresolved.INSTANCE : entities;
		}
		if (tag.contains("walk")) {
			PositionTracker tracker = decodeTracker(tag.getCompoundOrEmpty("walk"), level);
			return tracker == null ? Unresolved.INSTANCE : new WalkTarget(tracker, tag.getFloatOr("speed", 1.0F), tag.getIntOr("close", 0));
		}
		if (tag.contains("tracker")) {
			PositionTracker tracker = decodeTracker(tag.getCompoundOrEmpty("tracker"), level);
			return tracker == null ? Unresolved.INSTANCE : tracker;
		}
		if (tag.contains("path")) {
			return AiState.decodePath(tag.getCompoundOrEmpty("path"));
		}
		if (tag.getBooleanOr("unit", false)) {
			return net.minecraft.util.Unit.INSTANCE;
		}
		if (tag.contains("simple")) {
			try {
				Class<?> type = Class.forName(tag.getStringOr("type", ""));
				Object value = AiState.decode(type, tag.getCompoundOrEmpty("simple"));
				return value == AiState.Undecodable.INSTANCE ? null : value;
			} catch (ClassNotFoundException e) {
				return null;
			}
		}
		return null;
	}

	private static CompoundTag encodeTracker(PositionTracker tracker) {
		CompoundTag tag = new CompoundTag();
		if (tracker instanceof EntityTracker entityTracker) {
			Object entity = get(entityTracker, "entity");
			if (!(entity instanceof Entity target)) {
				return null;
			}
			tag.store("entity", UUIDUtil.CODEC, target.getUUID());
			tag.putBoolean("eye", Boolean.TRUE.equals(get(entityTracker, "trackEyeHeight")));
			tag.putBoolean("target_eye", Boolean.TRUE.equals(get(entityTracker, "targetEyeHeight")));
			return tag;
		}
		if (tracker instanceof BlockPosTracker blockPosTracker) {
			tag.putLong("block", blockPosTracker.currentBlockPosition().asLong());
			Vec3 center = blockPosTracker.currentPosition();
			tag.putDouble("x", center.x);
			tag.putDouble("y", center.y);
			tag.putDouble("z", center.z);
			return tag;
		}
		return null;
	}

	private static PositionTracker decodeTracker(CompoundTag tag, ServerLevel level) {
		if (tag.contains("entity")) {
			Entity entity = tag.read("entity", UUIDUtil.CODEC).map(level::getEntity).orElse(null);
			return entity == null ? null : new EntityTracker(entity, tag.getBooleanOr("eye", false), tag.getBooleanOr("target_eye", false));
		}
		BlockPosTracker tracker = new BlockPosTracker(BlockPos.of(tag.getLongOr("block", 0L)));
		Vec3 center = new Vec3(tag.getDoubleOr("x", 0.0), tag.getDoubleOr("y", 0.0), tag.getDoubleOr("z", 0.0));
		if (!center.equals(tracker.currentPosition())) {
			return new BlockPosTracker(center);
		}
		return tracker;
	}

	private static ListTag entities(List<?> list) {
		ListTag out = new ListTag();
		for (Object element : list) {
			if (element instanceof Entity entity) {
				CompoundTag ref = new CompoundTag();
				ref.store("uuid", UUIDUtil.CODEC, entity.getUUID());
				out.add(ref);
			}
		}
		return out;
	}

	private static List<LivingEntity> resolveAll(ListTag refs, ServerLevel level) {
		List<LivingEntity> result = new ArrayList<>();
		for (Tag element : refs) {
			UUID uuid = element instanceof CompoundTag ref ? ref.read("uuid", UUIDUtil.CODEC).orElse(null) : null;
			Entity entity = uuid == null ? null : level.getEntity(uuid);
			if (!(entity instanceof LivingEntity living)) {
				return null;
			}
			result.add(living);
		}
		return result;
	}

	private static Map<?, ?> sensors(Brain<?> brain) {
		Object sensors = get(brain, "sensors");
		return sensors instanceof Map<?, ?> map ? map : Map.of();
	}

	private static String sensorKey(Object type) {
		Identifier id = type instanceof net.minecraft.world.entity.ai.sensing.SensorType<?> sensorType ? BuiltInRegistries.SENSOR_TYPE.getKey(sensorType) : null;
		return id == null ? String.valueOf(type) : id.toString();
	}

	private static String activityKey(Activity activity) {
		Identifier id = BuiltInRegistries.ACTIVITY.getKey(activity);
		return id == null ? activity.toString() : id.toString();
	}

	private static Activity activity(String key) {
		Identifier id = Identifier.tryParse(key);
		return id == null ? null : BuiltInRegistries.ACTIVITY.getValue(id);
	}

	private static ListTag activities(Set<?> activities) {
		List<String> names = new ArrayList<>();
		if (activities != null) {
			for (Object activity : activities) {
				if (activity instanceof Activity a) {
					names.add(activityKey(a));
				}
			}
		}
		Collections.sort(names);
		ListTag out = new ListTag();
		for (String name : names) {
			CompoundTag entry = new CompoundTag();
			entry.putString("v", name);
			out.add(entry);
		}
		return out;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void applyActivities(Brain<?> brain, String field, ListTag names) {
		Object raw = get(brain, field);
		if (!(raw instanceof Set set)) {
			return;
		}
		set.clear();
		for (Tag element : names) {
			if (element instanceof CompoundTag entry) {
				Activity activity = activity(entry.getStringOr("v", ""));
				if (activity != null) {
					set.add(activity);
				}
			}
		}
	}

	private static List<Field> fields(Class<?> type) {
		return FIELDS.computeIfAbsent(type, t -> {
			List<Field> result = new ArrayList<>();
			for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) {
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

	private static Object get(Object owner, String name) {
		for (Field field : fields(owner.getClass())) {
			if (field.getName().equals(name)) {
				try {
					return field.get(owner);
				} catch (IllegalAccessException e) {
					return null;
				}
			}
		}
		return null;
	}

	private static void set(Object owner, String name, Object value) {
		for (Field field : fields(owner.getClass())) {
			if (field.getName().equals(name)) {
				try {
					field.set(owner, value);
				} catch (IllegalAccessException | RuntimeException ignored) {
				}
				return;
			}
		}
	}
}
