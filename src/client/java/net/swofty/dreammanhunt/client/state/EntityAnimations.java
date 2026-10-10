package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.ElytraAnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;

final class EntityAnimations {
	private static final Set<Class<?>> NESTED = Set.of(ClientAvatarState.class, ElytraAnimationState.class);

	private EntityAnimations() {
	}

	static CompoundTag capture(Entity entity) {
		CompoundTag tag = new CompoundTag();
		Class<?> stop = entity instanceof LivingEntity ? LivingEntity.class : Entity.class;
		try {
			for (Class<?> type = entity.getClass(); type != stop && type != null; type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					captureField(entity, field, tag);
				}
			}
			if (entity instanceof LivingEntity living) {
				tag.put("elytra", capturePrimitives(living.elytraAnimationState));
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return tag;
	}

	static void apply(Entity entity, CompoundTag tag) {
		Class<?> stop = entity instanceof LivingEntity ? LivingEntity.class : Entity.class;
		try {
			for (Class<?> type = entity.getClass(); type != stop && type != null; type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					applyField(entity, field, tag);
				}
			}
			if (entity instanceof LivingEntity living && tag.contains("elytra")) {
				applyPrimitives(living.elytraAnimationState, tag.getCompoundOrEmpty("elytra"));
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String key(Field field) {
		return field.getDeclaringClass().getSimpleName() + "." + field.getName();
	}

	private static void captureField(Object owner, Field field, CompoundTag tag) throws ReflectiveOperationException {
		int modifiers = field.getModifiers();
		if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
			return;
		}
		Class<?> type = field.getType();
		String key = key(field);
		if (type.isPrimitive()) {
			if (Modifier.isFinal(modifiers)) {
				return;
			}
			field.setAccessible(true);
			putPrimitive(tag, key, type, field.get(owner));
			return;
		}
		field.setAccessible(true);
		Object value = field.get(owner);
		if (value instanceof AnimationState state) {
			tag.putInt(key, Reflect.<Integer>get(state, "startTick"));
		} else if (value instanceof DragonFlightHistory history) {
			CompoundTag historyTag = new CompoundTag();
			historyTag.putInt("head", Reflect.<Integer>get(history, "head"));
			DragonFlightHistory.Sample[] samples = Reflect.get(history, "samples");
			long[] ys = new long[samples.length];
			int[] rots = new int[samples.length];
			for (int i = 0; i < samples.length; i++) {
				ys[i] = Double.doubleToRawLongBits(samples[i].y());
				rots[i] = Float.floatToRawIntBits(samples[i].yRot());
			}
			historyTag.putLongArray("y", ys);
			historyTag.putIntArray("yRot", rots);
			tag.put(key, historyTag);
		} else if (value instanceof float[] floats) {
			int[] bits = new int[floats.length];
			for (int i = 0; i < floats.length; i++) {
				bits[i] = Float.floatToRawIntBits(floats[i]);
			}
			tag.putIntArray(key, bits);
		} else if (value != null && NESTED.contains(value.getClass())) {
			tag.put(key, capturePrimitives(value));
		}
	}

	private static void applyField(Object owner, Field field, CompoundTag tag) throws ReflectiveOperationException {
		int modifiers = field.getModifiers();
		String key = key(field);
		if (Modifier.isStatic(modifiers) || field.isSynthetic() || !tag.contains(key)) {
			return;
		}
		Class<?> type = field.getType();
		field.setAccessible(true);
		if (type.isPrimitive()) {
			if (!Modifier.isFinal(modifiers)) {
				setPrimitive(owner, field, tag, key, type);
			}
			return;
		}
		Object value = field.get(owner);
		if (value instanceof AnimationState state) {
			Reflect.set(state, "startTick", tag.getIntOr(key, Integer.MIN_VALUE));
		} else if (value instanceof DragonFlightHistory history) {
			CompoundTag historyTag = tag.getCompoundOrEmpty(key);
			long[] ys = historyTag.getLongArray("y").orElse(new long[0]);
			int[] rots = historyTag.getIntArray("yRot").orElse(new int[0]);
			DragonFlightHistory.Sample[] samples = Reflect.get(history, "samples");
			if (ys.length == samples.length && rots.length == samples.length) {
				for (int i = 0; i < samples.length; i++) {
					samples[i] = new DragonFlightHistory.Sample(Double.longBitsToDouble(ys[i]), Float.intBitsToFloat(rots[i]));
				}
				Reflect.set(history, "head", historyTag.getIntOr("head", -1));
			}
		} else if (value instanceof float[] floats) {
			int[] bits = tag.getIntArray(key).orElse(new int[0]);
			for (int i = 0; i < Math.min(bits.length, floats.length); i++) {
				floats[i] = Float.intBitsToFloat(bits[i]);
			}
		} else if (value != null && NESTED.contains(value.getClass())) {
			applyPrimitives(value, tag.getCompoundOrEmpty(key));
		}
	}

	private static CompoundTag capturePrimitives(Object owner) throws ReflectiveOperationException {
		CompoundTag tag = new CompoundTag();
		for (Field field : Reflect.instanceFields(owner.getClass())) {
			if (field.getType().isPrimitive() && !Modifier.isFinal(field.getModifiers())) {
				putPrimitive(tag, key(field), field.getType(), field.get(owner));
			} else if (field.getType() == net.minecraft.world.phys.Vec3.class) {
				net.minecraft.world.phys.Vec3 vec = (net.minecraft.world.phys.Vec3) field.get(owner);
				if (vec != null) {
					ListTag list = new ListTag();
					list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.x));
					list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.y));
					list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.z));
					tag.put(key(field), list);
				}
			}
		}
		return tag;
	}

	private static void applyPrimitives(Object owner, CompoundTag tag) throws ReflectiveOperationException {
		for (Field field : Reflect.instanceFields(owner.getClass())) {
			String key = key(field);
			if (!tag.contains(key)) {
				continue;
			}
			if (field.getType().isPrimitive() && !Modifier.isFinal(field.getModifiers())) {
				setPrimitive(owner, field, tag, key, field.getType());
			} else if (field.getType() == net.minecraft.world.phys.Vec3.class && !Modifier.isFinal(field.getModifiers())) {
				ListTag list = tag.getListOrEmpty(key);
				if (list.size() == 3) {
					field.set(owner, new net.minecraft.world.phys.Vec3(list.getDoubleOr(0, 0.0), list.getDoubleOr(1, 0.0), list.getDoubleOr(2, 0.0)));
				}
			}
		}
	}

	private static void putPrimitive(CompoundTag tag, String key, Class<?> type, Object value) {
		if (type == int.class) {
			tag.putInt(key, (Integer) value);
		} else if (type == float.class) {
			tag.putFloat(key, (Float) value);
		} else if (type == double.class) {
			tag.putDouble(key, (Double) value);
		} else if (type == long.class) {
			tag.putLong(key, (Long) value);
		} else if (type == boolean.class) {
			tag.putBoolean(key, (Boolean) value);
		} else if (type == short.class) {
			tag.putShort(key, (Short) value);
		} else if (type == byte.class) {
			tag.putByte(key, (Byte) value);
		}
	}

	private static void setPrimitive(Object owner, Field field, CompoundTag tag, String key, Class<?> type) throws IllegalAccessException {
		Tag raw = tag.get(key);
		if (raw == null) {
			return;
		}
		if (type == int.class) {
			field.setInt(owner, tag.getIntOr(key, field.getInt(owner)));
		} else if (type == float.class) {
			field.setFloat(owner, tag.getFloatOr(key, field.getFloat(owner)));
		} else if (type == double.class) {
			field.setDouble(owner, tag.getDoubleOr(key, field.getDouble(owner)));
		} else if (type == long.class) {
			field.setLong(owner, tag.getLongOr(key, field.getLong(owner)));
		} else if (type == boolean.class) {
			field.setBoolean(owner, tag.getBooleanOr(key, field.getBoolean(owner)));
		} else if (type == short.class) {
			field.setShort(owner, tag.getShortOr(key, field.getShort(owner)));
		} else if (type == byte.class) {
			field.setByte(owner, tag.getByteOr(key, field.getByte(owner)));
		}
	}

	static List<String> describe(CompoundTag tag) {
		return List.copyOf(tag.keySet());
	}
}
