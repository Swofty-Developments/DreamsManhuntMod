package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class ObjectCopier {
	private final @Nullable ClientLevel level;
	private final @Nullable Map<UUID, Entity> entities;
	private final Map<Object, Object> copies = new IdentityHashMap<>();
	private boolean unresolved;

	ObjectCopier(@Nullable ClientLevel level, @Nullable Map<UUID, Entity> entities) {
		this.level = level;
		this.entities = entities;
	}

	static Map<UUID, Entity> entitiesByUuid(ClientLevel level) {
		Map<UUID, Entity> map = new java.util.HashMap<>();
		for (Entity entity : level.entitiesForRendering()) {
			map.put(entity.getUUID(), entity);
		}
		return map;
	}

	<T> @Nullable T copyRoot(T source) {
		this.unresolved = false;
		T copy = this.copyObject(source);
		return this.unresolved ? null : copy;
	}

	@SuppressWarnings("unchecked")
	private <T> T copyObject(T source) {
		Object existing = this.copies.get(source);
		if (existing != null) {
			return (T) existing;
		}
		T copy = (T) Reflect.allocate(source.getClass());
		this.copies.put(source, copy);
		for (Field field : Reflect.instanceFields(source.getClass())) {
			try {
				field.set(copy, this.copyValue(field.get(source)));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return copy;
	}

	@SuppressWarnings("unchecked")
	private @Nullable Object copyValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof ClientLevel) {
			return this.level != null ? this.level : value;
		}
		if (value instanceof Entity entity) {
			if (this.entities == null) {
				return entity;
			}
			Entity current = this.entities.get(entity.getUUID());
			if (current == null) {
				this.unresolved = true;
			}
			return current;
		}
		if (value instanceof net.minecraft.client.gui.components.toasts.Toast) {
			return this.copyObject(value);
		}
		if (value instanceof ItemStack stack) {
			return stack.copy();
		}
		if (value instanceof RandomSource || value instanceof AtomicLong) {
			return this.copyRandom(value);
		}
		if (value instanceof ArrayList<?> list) {
			return new ArrayList<>(list);
		}
		if (value instanceof ArrayDeque<?> deque) {
			return new ArrayDeque<>(deque);
		}
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			Object array = Array.newInstance(value.getClass().getComponentType(), length);
			System.arraycopy(value, 0, array, 0, length);
			return array;
		}
		return value;
	}

	private Object copyRandom(Object value) {
		Object existing = this.copies.get(value);
		if (existing != null) {
			return existing;
		}
		if (value instanceof AtomicLong atomic) {
			AtomicLong copy = new AtomicLong(atomic.get());
			this.copies.put(value, copy);
			return copy;
		}
		Object copy = Reflect.allocate(value.getClass());
		this.copies.put(value, copy);
		for (Field field : Reflect.instanceFields(value.getClass())) {
			try {
				Object fieldValue = field.get(value);
				if (fieldValue instanceof AtomicLong || fieldValue instanceof RandomSource || isRandomHelper(fieldValue)) {
					fieldValue = this.copyRandom(fieldValue);
				}
				field.set(copy, fieldValue);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return copy;
	}

	private static boolean isRandomHelper(@Nullable Object value) {
		return value != null && value.getClass().getName().startsWith("net.minecraft.world.level.levelgen.");
	}

	static <T> List<T> copyAll(List<T> sources, ObjectCopier copier) {
		List<T> out = new ArrayList<>(sources.size());
		for (T source : sources) {
			T copy = copier.copyRoot(source);
			if (copy != null) {
				out.add(copy);
			}
		}
		return out;
	}
}
