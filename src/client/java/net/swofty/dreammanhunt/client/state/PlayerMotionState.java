package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

final class PlayerMotionState {
	private static final Set<String> EXCLUDED = Set.of("Entity.id");

	private PlayerMotionState() {
	}

	static CompoundTag capture(LocalPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", player.level().dimension().identifier().toString());
		CompoundTag primitives = new CompoundTag();
		try {
			for (Field field : Reflect.instanceFields(player.getClass())) {
				if (isRestorable(field)) {
					putPrimitive(primitives, key(field), field.getType(), field.get(player));
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		tag.put("primitives", primitives);
		putVec(tag, "pos", player.position());
		putVec(tag, "delta", player.getDeltaMovement());
		putVec(tag, "stuck", Reflect.get(player, "stuckSpeedMultiplier"));
		putVec(tag, "knownSpeed", Reflect.get(player, "lastKnownSpeed"));
		tag.putString("pose", player.getPose().name());
		tag.putByte("flags", player.getEntityData().get(sharedFlags()));
		Input keys = player.input.keyPresses;
		tag.putIntArray("keys", new int[]{b(keys.forward()), b(keys.backward()), b(keys.left()), b(keys.right()), b(keys.jump()), b(keys.shift()), b(keys.sprint())});
		Input lastSent = Reflect.get(player, "lastSentInput");
		tag.putIntArray("lastSentKeys", new int[]{b(lastSent.forward()), b(lastSent.backward()), b(lastSent.left()), b(lastSent.right()), b(lastSent.jump()), b(lastSent.shift()), b(lastSent.sprint())});
		Vec2 move = player.input.getMoveVector();
		tag.putFloat("moveX", move.x);
		tag.putFloat("moveY", move.y);
		tag.put("random", captureRandom(Reflect.get(player, "random")));
		tag.put("levelRandom", captureRandom(player.level().getRandom()));
		tag.putFloat("health", player.getHealth());
		tag.putFloat("absorption", player.getAbsorptionAmount());
		tag.putInt("food", player.getFoodData().getFoodLevel());
		tag.putFloat("saturation", player.getFoodData().getSaturationLevel());
		tag.putFloat("xpProgress", player.experienceProgress);
		tag.putInt("xpTotal", player.totalExperience);
		tag.putInt("xpLevel", player.experienceLevel);
		return tag;
	}

	static void apply(LocalPlayer player, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		player.setHealth(tag.getFloatOr("health", player.getHealth()));
		player.setAbsorptionAmount(tag.getFloatOr("absorption", player.getAbsorptionAmount()));
		player.getFoodData().setFoodLevel(tag.getIntOr("food", player.getFoodData().getFoodLevel()));
		player.getFoodData().setSaturation(tag.getFloatOr("saturation", player.getFoodData().getSaturationLevel()));
		player.setExperienceValues(tag.getFloatOr("xpProgress", player.experienceProgress), tag.getIntOr("xpTotal", player.totalExperience), tag.getIntOr("xpLevel", player.experienceLevel));
		if (!player.level().dimension().identifier().toString().equals(tag.getStringOr("dimension", ""))) {
			return;
		}
		try {
			player.setPose(Pose.valueOf(tag.getStringOr("pose", Pose.STANDING.name())));
		} catch (IllegalArgumentException ignored) {
		}
		byte flags = tag.getByteOr("flags", player.getEntityData().get(sharedFlags()));
		player.setSprinting((flags & (1 << 3)) != 0);
		player.getEntityData().set(sharedFlags(), flags);
		CompoundTag primitives = tag.getCompoundOrEmpty("primitives");
		try {
			for (Field field : Reflect.instanceFields(player.getClass())) {
				if (isRestorable(field) && primitives.contains(key(field))) {
					setPrimitive(player, field, primitives, key(field));
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		Vec3 pos = getVec(tag, "pos");
		if (pos != null) {
			player.setPos(pos.x, pos.y, pos.z);
		}
		Vec3 delta = getVec(tag, "delta");
		if (delta != null) {
			player.setDeltaMovement(delta);
		}
		Vec3 stuck = getVec(tag, "stuck");
		if (stuck != null) {
			Reflect.set(player, "stuckSpeedMultiplier", stuck);
		}
		Vec3 knownSpeed = getVec(tag, "knownSpeed");
		if (knownSpeed != null) {
			Reflect.set(player, "lastKnownSpeed", knownSpeed);
		}
		player.input.keyPresses = input(tag.getIntArray("keys").orElse(new int[0]), player.input.keyPresses);
		Reflect.set(player.input, "moveVector", new Vec2(tag.getFloatOr("moveX", 0.0F), tag.getFloatOr("moveY", 0.0F)));
		Reflect.set(player, "lastSentInput", input(tag.getIntArray("lastSentKeys").orElse(new int[0]), Reflect.get(player, "lastSentInput")));
		applyRandom(Reflect.get(player, "random"), tag.getCompoundOrEmpty("random"));
		applyRandom(player.level().getRandom(), tag.getCompoundOrEmpty("levelRandom"));
	}

	static void syncServer(LocalPlayer player) {
		Reflect.set(player, "positionReminder", 20);
	}

	private static boolean isRestorable(Field field) {
		int modifiers = field.getModifiers();
		return field.getType().isPrimitive() && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers) && !field.isSynthetic() && !EXCLUDED.contains(key(field));
	}

	private static String key(Field field) {
		return field.getDeclaringClass().getSimpleName() + "." + field.getName();
	}

	@SuppressWarnings("unchecked")
	private static EntityDataAccessor<Byte> sharedFlags() {
		try {
			return (EntityDataAccessor<Byte>) Reflect.field(Entity.class, "DATA_SHARED_FLAGS_ID").get(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int b(boolean value) {
		return value ? 1 : 0;
	}

	private static Input input(int[] keys, Input fallback) {
		if (keys.length != 7) {
			return fallback;
		}
		return new Input(keys[0] != 0, keys[1] != 0, keys[2] != 0, keys[3] != 0, keys[4] != 0, keys[5] != 0, keys[6] != 0);
	}

	private static CompoundTag captureRandom(RandomSource random) {
		CompoundTag tag = new CompoundTag();
		tag.putString("class", random.getClass().getName());
		try {
			for (Field field : Reflect.instanceFields(random.getClass())) {
				Object value = field.get(random);
				if (value instanceof AtomicLong atomic) {
					tag.putLong(field.getName(), atomic.get());
				} else if (field.getType().isPrimitive() && !Modifier.isFinal(field.getModifiers())) {
					putPrimitive(tag, field.getName(), field.getType(), value);
				} else if (value != null && value.getClass().getName().endsWith("MarsagliaPolarGaussian")) {
					CompoundTag gaussian = new CompoundTag();
					for (Field inner : Reflect.instanceFields(value.getClass())) {
						if (inner.getType().isPrimitive() && !Modifier.isFinal(inner.getModifiers())) {
							putPrimitive(gaussian, inner.getName(), inner.getType(), inner.get(value));
						}
					}
					tag.put(field.getName(), gaussian);
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return tag;
	}

	private static void applyRandom(RandomSource random, CompoundTag tag) {
		if (tag.isEmpty() || !random.getClass().getName().equals(tag.getStringOr("class", ""))) {
			return;
		}
		try {
			for (Field field : Reflect.instanceFields(random.getClass())) {
				if (!tag.contains(field.getName())) {
					continue;
				}
				Object value = field.get(random);
				if (value instanceof AtomicLong atomic) {
					atomic.set(tag.getLongOr(field.getName(), atomic.get()));
				} else if (field.getType().isPrimitive() && !Modifier.isFinal(field.getModifiers())) {
					setPrimitive(random, field, tag, field.getName());
				} else if (value != null && value.getClass().getName().endsWith("MarsagliaPolarGaussian")) {
					CompoundTag gaussian = tag.getCompoundOrEmpty(field.getName());
					for (Field inner : Reflect.instanceFields(value.getClass())) {
						if (inner.getType().isPrimitive() && !Modifier.isFinal(inner.getModifiers()) && gaussian.contains(inner.getName())) {
							setPrimitive(value, inner, gaussian, inner.getName());
						}
					}
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void putVec(CompoundTag tag, String key, Vec3 vec) {
		if (vec != null) {
			tag.putDouble(key + "X", vec.x);
			tag.putDouble(key + "Y", vec.y);
			tag.putDouble(key + "Z", vec.z);
		}
	}

	private static Vec3 getVec(CompoundTag tag, String key) {
		if (!tag.contains(key + "X")) {
			return null;
		}
		return new Vec3(tag.getDoubleOr(key + "X", 0.0), tag.getDoubleOr(key + "Y", 0.0), tag.getDoubleOr(key + "Z", 0.0));
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

	private static void setPrimitive(Object owner, Field field, CompoundTag tag, String key) throws IllegalAccessException {
		Class<?> type = field.getType();
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
}
