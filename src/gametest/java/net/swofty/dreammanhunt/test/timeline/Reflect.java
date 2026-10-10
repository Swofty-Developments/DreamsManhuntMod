package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Reflect {
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

	private Reflect() {
	}

	public static List<Field> fields(Class<?> type) {
		return FIELDS.computeIfAbsent(type, t -> {
			List<Field> result = new ArrayList<>();
			for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
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

	public static Object get(Object owner, String name) {
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

	public static void dumpFields(Object owner, String prefix, Map<String, String> out, int depth) {
		if (owner == null) {
			out.put(prefix, "null");
			return;
		}
		for (Field field : fields(owner.getClass())) {
			Object value;
			try {
				value = field.get(owner);
			} catch (IllegalAccessException e) {
				continue;
			}
			if (skip(value)) {
				continue;
			}
			out.put(prefix + "." + field.getName(), describe(value, depth));
		}
	}

	private static boolean skip(Object value) {
		return value instanceof Level || value instanceof MinecraftServer || value instanceof Brain<?>
				|| (value != null && value.getClass().getName().contains("$$Lambda"));
	}

	public static String describe(Object value, int depth) {
		return switch (value) {
			case null -> "null";
			case AtomicLong a -> Long.toString(a.get());
			case AtomicInteger a -> Integer.toString(a.get());
			case Float f -> Float.toString(f);
			case Double d -> Double.toString(d);
			case Number n -> n.toString();
			case Boolean b -> b.toString();
			case CharSequence s -> s.toString();
			case Character c -> c.toString();
			case Enum<?> e -> e.name();
			case Entity entity -> "E:" + entity.getUUID();
			case Vec3 v -> v.x + "," + v.y + "," + v.z;
			case BlockPos p -> p.toShortString();
			case Vec3i p -> p.toShortString();
			case AABB box -> box.toString();
			case Identifier id -> id.toString();
			case ResourceKey<?> key -> key.identifier().toString();
			case Holder<?> holder -> holder.unwrapKey().map(k -> k.identifier().toString()).orElse("holder");
			case Optional<?> optional -> optional.map(inner -> "Opt(" + describe(inner, depth) + ")").orElse("empty");
			case RandomSource random -> "rng:" + describeRandom(random);
			case Path path -> describePath(path);
			case Node node -> node.x + "," + node.y + "," + node.z;
			case Collection<?> collection -> describeCollection(collection, depth);
			case Map<?, ?> map -> map.size() <= 16 && depth > 0 ? describeMap(map, depth) : "map[" + map.size() + "]";
			case Object[] array -> "array[" + array.length + "]";
			case int[] array -> java.util.Arrays.toString(array);
			case long[] array -> java.util.Arrays.toString(array);
			case float[] array -> java.util.Arrays.toString(array);
			case double[] array -> java.util.Arrays.toString(array);
			case boolean[] array -> java.util.Arrays.toString(array);
			default -> depth > 0 && !(value instanceof Level) ? nested(value, depth - 1) : value.getClass().getSimpleName();
		};
	}

	private static String describeRandom(RandomSource random) {
		Object seed = get(random, "seed");
		return seed == null ? random.getClass().getSimpleName() : describe(seed, 0);
	}

	public static String describePath(Path path) {
		StringBuilder builder = new StringBuilder("path[next=").append(path.getNextNodeIndex()).append(" of ").append(path.getNodeCount());
		builder.append(" target=").append(path.getTarget().toShortString()).append(" reached=").append(path.canReach()).append(" nodes=");
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			builder.append(node.x).append(',').append(node.y).append(',').append(node.z).append(';');
		}
		return builder.append(']').toString();
	}

	private static String describeCollection(Collection<?> collection, int depth) {
		if (depth <= 0 || collection.size() > 16) {
			return "size=" + collection.size();
		}
		List<String> parts = new ArrayList<>();
		for (Object element : collection) {
			parts.add(describe(element, depth - 1));
		}
		return parts.toString();
	}

	private static String describeMap(Map<?, ?> map, int depth) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			parts.add(describe(entry.getKey(), 0) + "=" + describe(entry.getValue(), depth - 1));
		}
		parts.sort(String::compareTo);
		return parts.toString();
	}

	private static String nested(Object value, int depth) {
		if (value.getClass().isRecord() || value.getClass().getName().startsWith("java.")) {
			return value.toString();
		}
		Map<String, String> inner = new java.util.LinkedHashMap<>();
		dumpFields(value, "", inner, depth);
		return value.getClass().getSimpleName() + inner;
	}
}
