package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

final class NeedleState {
	private NeedleState() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		CompoundTag needles = new CompoundTag();
		forEachNeedle(minecraft, (key, needle) -> needles.put(key, captureNeedle(needle)));
		tag.put("needles", needles);
		tag.put("clock", captureClock(minecraft));
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		CompoundTag needles = tag.getCompoundOrEmpty("needles");
		forEachNeedle(minecraft, (key, needle) -> {
			if (needles.contains(key)) {
				applyNeedle(needle, needles.getCompoundOrEmpty(key));
			}
		});
		applyClock(minecraft, tag.getCompoundOrEmpty("clock"));
	}

	private interface NeedleVisitor {
		void visit(String key, NeedleDirectionHelper needle);
	}

	private static Map<Identifier, ItemModel> cachedModels;
	private static List<Map.Entry<String, NeedleDirectionHelper>> cachedNeedles = List.of();

	private static void forEachNeedle(Minecraft minecraft, NeedleVisitor visitor) {
		Map<Identifier, ItemModel> models = Reflect.get(minecraft.getModelManager(), "bakedItemStackModels");
		if (models != cachedModels) {
			List<Map.Entry<String, NeedleDirectionHelper>> found = new ArrayList<>();
			collectNeedles(models, (key, needle) -> found.add(Map.entry(key, needle)));
			cachedNeedles = found;
			cachedModels = models;
			com.mojang.logging.LogUtils.getLogger().info("Tracking {} needle properties: {}", found.size(), found.stream().map(e -> e.getKey() + "=" + e.getValue().getClass().getSimpleName()).toList());
		}
		for (Map.Entry<String, NeedleDirectionHelper> entry : cachedNeedles) {
			visitor.visit(entry.getKey(), entry.getValue());
		}
	}

	private static void collectNeedles(Map<Identifier, ItemModel> models, NeedleVisitor visitor) {
		Map<String, ItemModel> sorted = new TreeMap<>();
		models.forEach((id, model) -> sorted.put(id.toString(), model));
		Map<Object, Boolean> visited = new IdentityHashMap<>();
		for (Map.Entry<String, ItemModel> entry : sorted.entrySet()) {
			int index = 0;
			Deque<Object> stack = new ArrayDeque<>();
			stack.push(entry.getValue());
			while (!stack.isEmpty()) {
				Object current = stack.pop();
				if (current == null || visited.put(current, Boolean.TRUE) != null) {
					continue;
				}
				if (current instanceof NeedleDirectionHelper needle) {
					visitor.visit(entry.getKey() + "#" + index++, needle);
					continue;
				}
				List<Object> children = new ArrayList<>();
				if (current.getClass().isArray()) {
					if (!current.getClass().getComponentType().isPrimitive()) {
						for (int i = 0; i < Array.getLength(current); i++) {
							children.add(Array.get(current, i));
						}
					}
				} else if (current instanceof Collection<?> collection) {
					children.addAll(collection);
				} else if (current instanceof Map<?, ?> map) {
					children.addAll(map.values());
					if (current instanceof it.unimi.dsi.fastutil.objects.Object2ObjectFunction<?, ?> function) {
						children.add(function.defaultReturnValue());
					}
				} else if (current.getClass().getName().startsWith("net.minecraft.client.renderer.item")) {
					for (Field field : Reflect.instanceFields(current.getClass())) {
						if (field.getType().isPrimitive()) {
							continue;
						}
						try {
							children.add(field.get(current));
						} catch (IllegalAccessException e) {
							throw new IllegalStateException(e);
						}
					}
				}
				for (int i = children.size() - 1; i >= 0; i--) {
					Object child = children.get(i);
					if (child != null && (child.getClass().isArray() || child instanceof Collection<?> || child instanceof Map<?, ?>
						|| child.getClass().getName().startsWith("net.minecraft.client.renderer.item"))) {
						stack.push(child);
					}
				}
			}
		}
	}

	private static CompoundTag captureNeedle(NeedleDirectionHelper needle) {
		CompoundTag tag = new CompoundTag();
		Object owner = needle;
		for (Field field : Reflect.instanceFields(owner.getClass())) {
			try {
				Object value = field.get(owner);
				if (value instanceof RandomSource random) {
					tag.put(field.getName(), PlayerMotionState.captureRandomState(random));
				} else if (value instanceof NeedleDirectionHelper.Wobbler wobbler) {
					tag.put(field.getName(), captureWobbler(wobbler));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return tag;
	}

	private static void applyNeedle(NeedleDirectionHelper needle, CompoundTag tag) {
		for (Field field : Reflect.instanceFields(needle.getClass())) {
			if (!tag.contains(field.getName())) {
				continue;
			}
			try {
				Object value = field.get(needle);
				if (value instanceof RandomSource random) {
					PlayerMotionState.applyRandomState(random, tag.getCompoundOrEmpty(field.getName()));
				} else if (value instanceof NeedleDirectionHelper.Wobbler wobbler) {
					applyWobbler(wobbler, tag.getCompoundOrEmpty(field.getName()));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static CompoundTag captureWobbler(NeedleDirectionHelper.Wobbler wobbler) {
		CompoundTag tag = new CompoundTag();
		for (Field field : Reflect.instanceFields(wobbler.getClass())) {
			if (Modifier.isFinal(field.getModifiers()) || !field.getType().isPrimitive()) {
				continue;
			}
			try {
				if (field.getType() == float.class) {
					tag.putFloat(field.getName(), field.getFloat(wobbler));
				} else if (field.getType() == long.class) {
					tag.putLong(field.getName(), field.getLong(wobbler));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return tag;
	}

	private static void applyWobbler(NeedleDirectionHelper.Wobbler wobbler, CompoundTag tag) {
		for (Field field : Reflect.instanceFields(wobbler.getClass())) {
			if (Modifier.isFinal(field.getModifiers()) || !tag.contains(field.getName())) {
				continue;
			}
			try {
				if (field.getType() == float.class) {
					field.setFloat(wobbler, tag.getFloatOr(field.getName(), field.getFloat(wobbler)));
				} else if (field.getType() == long.class) {
					field.setLong(wobbler, tag.getLongOr(field.getName(), field.getLong(wobbler)));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static CompoundTag captureClock(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("gameTime", minecraft.level.getGameTime());
		ClientClockManager clocks = minecraft.level.clockManager();
		tag.putLong("lastTickGameTime", Reflect.<Long>get(clocks, "lastTickGameTime"));
		CompoundTag instances = new CompoundTag();
		Map<Object, Object> map = Reflect.get(clocks, "clocks");
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			String key = clockKey(entry.getKey());
			if (key == null) {
				continue;
			}
			CompoundTag instance = new CompoundTag();
			instance.putLong("totalTicks", Reflect.<Long>get(entry.getValue(), "totalTicks"));
			instance.putFloat("partialTick", Reflect.<Float>get(entry.getValue(), "partialTick"));
			instance.putFloat("rate", Reflect.<Float>get(entry.getValue(), "rate"));
			instances.put(key, instance);
		}
		tag.put("instances", instances);
		return tag;
	}

	private static void applyClock(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		minecraft.level.setTimeFromServer(tag.getLongOr("gameTime", minecraft.level.getGameTime()));
		ClientClockManager clocks = minecraft.level.clockManager();
		Reflect.set(clocks, "lastTickGameTime", tag.getLongOr("lastTickGameTime", 0L));
		CompoundTag instances = tag.getCompoundOrEmpty("instances");
		Map<Object, Object> map = Reflect.get(clocks, "clocks");
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			String key = clockKey(entry.getKey());
			if (key == null || !instances.contains(key)) {
				continue;
			}
			CompoundTag instance = instances.getCompoundOrEmpty(key);
			Reflect.set(entry.getValue(), "totalTicks", instance.getLongOr("totalTicks", 0L));
			Reflect.set(entry.getValue(), "partialTick", instance.getFloatOr("partialTick", 0.0F));
			Reflect.set(entry.getValue(), "rate", instance.getFloatOr("rate", 1.0F));
		}
	}

	private static String clockKey(Object holder) {
		if (holder instanceof net.minecraft.core.Holder<?> h) {
			return h.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
		}
		return null;
	}
}
