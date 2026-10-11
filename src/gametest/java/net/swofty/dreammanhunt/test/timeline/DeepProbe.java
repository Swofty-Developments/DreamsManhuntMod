package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
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

public final class DeepProbe {
	private static final int MAX_DEPTH = Integer.getInteger("dreammanhunt.timeline.deepDepth", 7);
	private static final int MAX_ELEMENTS = 48;

	private DeepProbe() {
	}

	public static void install(int maxTick) {
		Probes.addServer((server, area, out) -> capture(server, area, out, maxTick));
	}

	private static void capture(MinecraftServer server, TimelineScenario.Area area, Fingerprint out, int maxTick) {
		if (TimelineRecorder.currentTick() > maxTick) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!area.contains(entity) || entity instanceof net.minecraft.world.entity.player.Player) {
					continue;
				}
				Map<String, String> leaves = new TreeMap<>();
				walk(entity, "", leaves, 0, Collections.newSetFromMap(new IdentityHashMap<>()), entity);
				Map<String, String> fields = out.subject("deep:" + Probes.entityKey(entity));
				fields.putAll(leaves);
			}
		}
	}

	private static final String[] NOISE = {
			".inBlockState", ".finalMovementsThisTick", ".lineOfSightTest", ".attributesToSync", ".attributesToUpdate", ".cachedValue", ".dirty",
			"fabric_", ".elytraAnimationState", ".interpolation", ".lastKnown", ".bb", ".blockPosition", ".chunkPosition", ".feetBlockState", ".nodeEvaluator.", ".pathFinder.", ".openSet", ".cameFrom"
	};

	private static void walk(Object node, String path, Map<String, String> out, int depth, Set<Object> seen, Entity root) {
		for (String noise : NOISE) {
			if (path.contains(noise)) {
				return;
			}
		}
		if (path.contains(".nodes[") && (path.endsWith(".closed") || path.endsWith(".f") || path.endsWith(".g") || path.endsWith(".h") || path.endsWith(".heapIdx"))) {
			return;
		}
		if (node instanceof net.minecraft.world.level.block.state.StateHolder<?, ?> || node instanceof net.minecraft.world.phys.shapes.VoxelShape) {
			out.put(path, String.valueOf(node));
			return;
		}
		if (node == null) {
			out.put(path, "null");
			return;
		}
		String leaf = depth == 0 ? null : leaf(node, root);
		if (leaf != null) {
			out.put(path, leaf);
			return;
		}
		if (depth >= MAX_DEPTH) {
			out.put(path, "<" + node.getClass().getSimpleName() + ">");
			return;
		}
		if (!seen.add(node)) {
			return;
		}
		if (node.getClass().isArray()) {
			int length = Array.getLength(node);
			out.put(path + "#len", Integer.toString(length));
			for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
				walk(Array.get(node, i), path + "[" + i + "]", out, depth + 1, seen, root);
			}
			return;
		}
		if (node instanceof Map<?, ?> map) {
			out.put(path + "#size", Integer.toString(map.size()));
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				sorted.put(keyString(entry.getKey(), root), entry.getValue());
			}
			int i = 0;
			for (Map.Entry<String, Object> entry : sorted.entrySet()) {
				if (i++ >= MAX_ELEMENTS) {
					break;
				}
				walk(entry.getValue(), path + "{" + entry.getKey() + "}", out, depth + 1, seen, root);
			}
			return;
		}
		if (node instanceof Collection<?> collection) {
			out.put(path + "#size", Integer.toString(collection.size()));
			boolean ordered = node instanceof List<?> || node.getClass().getName().contains("Linked") || node.getClass().getName().contains("Tree");
			List<Object> elements = new ArrayList<>(collection);
			if (!ordered) {
				elements.sort((a, b) -> keyString(a, root).compareTo(keyString(b, root)));
			}
			for (int i = 0; i < Math.min(elements.size(), MAX_ELEMENTS); i++) {
				Object element = elements.get(i);
				String index = ordered ? Integer.toString(i) : keyString(element, root);
				walk(element, path + "[" + index + "]", out, depth + 1, seen, root);
			}
			return;
		}
		String name = node.getClass().getName();
		if (!(name.startsWith("net.minecraft.") || name.contains("$$Lambda") || name.startsWith("org.apache.commons.lang3.mutable.")
				|| name.startsWith("it.unimi.dsi.fastutil.") || name.startsWith("com.google.common.collect."))) {
			out.put(path, "<" + node.getClass().getSimpleName() + ">");
			return;
		}
		for (Field field : Reflect.fields(node.getClass())) {
			if (Modifier.isTransient(field.getModifiers())) {
				continue;
			}
			Object value;
			try {
				value = field.get(node);
			} catch (IllegalAccessException e) {
				continue;
			}
			walk(value, path + "." + field.getName(), out, depth + 1, seen, root);
		}
	}

	private static String leaf(Object value, Entity root) {
		return switch (value) {
			case Number n -> n instanceof Float f ? Float.toString(f) : n instanceof Double d ? Double.toString(d) : n.toString();
			case Boolean b -> b.toString();
			case CharSequence s -> s.toString();
			case Character c -> c.toString();
			case Enum<?> e -> e.name();
			case Entity entity -> entity == root ? "<self>" : "E:" + entity.getUUID();
			case Vec3 v -> v.x + "," + v.y + "," + v.z;
			case BlockPos p -> p.toShortString();
			case Vec3i p -> p.toShortString();
			case AABB box -> box.toString();
			case Identifier id -> id.toString();
			case ResourceKey<?> key -> key.identifier().toString();
			case Holder<?> holder -> holder.unwrapKey().map(k -> k.identifier().toString()).orElse("<holder>");
			case RandomSource random -> "rng:" + Reflect.describe(Reflect.get(random, "seed"), 0);
			case ItemStack stack -> stack.toString();
			case Level level -> "<level " + level.dimension().identifier() + ">";
			case MinecraftServer server -> "<server>";
			case SynchedEntityData data -> "<data>";
			case Class<?> type -> type.getSimpleName();
			default -> null;
		};
	}

	private static String keyString(Object key, Entity root) {
		if (key == null) {
			return "null";
		}
		String leaf = leaf(key, root);
		if (leaf != null) {
			return leaf;
		}
		String text = String.valueOf(key);
		if (text.contains("@")) {
			return key.getClass().getSimpleName();
		}
		return text;
	}
}
