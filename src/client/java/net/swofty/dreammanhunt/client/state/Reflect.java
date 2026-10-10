package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;

final class Reflect {
	private static final Map<Class<?>, List<Field>> INSTANCE_FIELDS = new ConcurrentHashMap<>();
	private static final Map<String, Field> NAMED = new ConcurrentHashMap<>();
	private static final Unsafe UNSAFE;

	static {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (Unsafe) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private Reflect() {
	}

	static List<Field> instanceFields(Class<?> type) {
		return INSTANCE_FIELDS.computeIfAbsent(type, key -> {
			List<Field> fields = new ArrayList<>();
			for (Class<?> current = key; current != null && current != Object.class; current = current.getSuperclass()) {
				for (Field field : current.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						fields.add(field);
					}
				}
			}
			return List.copyOf(fields);
		});
	}

	static Field field(Class<?> owner, String name) {
		return NAMED.computeIfAbsent(owner.getName() + "#" + name, key -> {
			for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
				try {
					Field field = current.getDeclaredField(name);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
			throw new IllegalArgumentException("No field " + name + " in " + owner.getName());
		});
	}

	@SuppressWarnings("unchecked")
	static <T> T get(Object target, String name) {
		try {
			return (T) field(target.getClass(), name).get(target);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	static void set(Object target, String name, Object value) {
		try {
			field(target.getClass(), name).set(target, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	static <T> T allocate(Class<T> type) {
		try {
			return (T) UNSAFE.allocateInstance(type);
		} catch (InstantiationException e) {
			throw new IllegalStateException("Cannot allocate " + type.getName(), e);
		}
	}
}
