package net.swofty.dreammanhunt.server.coordinator;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.DreamManhunt;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class PlayerFields {
	private static final Set<String> DENY = Set.of(
			"id", "level", "position", "blockPosition", "chunkPosition", "bb", "uuid", "stringUUID", "levelCallback", "dimensions",
			"customData", "inBlockState", "removalReason", "eyeHeight", "useItem", "autoSpinAttackItemStack", "lastDamageSource",
			"locatorBarIcon", "connection", "lastRecordedHealthAndAbsorption", "lastRecordedFoodLevel", "lastRecordedAirLevel",
			"lastRecordedArmor", "lastRecordedLevel", "lastRecordedExperience", "lastSentHealth", "lastSentFood", "lastFoodSaturationZero",
			"lastSentExp", "lastActionTime", "chatVisibility", "particleStatus", "canChatColor", "isChangingDimension", "disconnected",
			"requestedViewDistance", "language", "textFilteringEnabled", "allowsListing", "containerCounter", "wonGame", "postEffectsDirty"
	);
	private static final java.util.Map<Class<?>, List<Field>> FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

	private PlayerFields() {
	}

	static CompoundTag capture(Entity player) {
		CompoundTag tag = new CompoundTag();
		for (Field field : fields(player.getClass())) {
			try {
				Object value = field.get(player);
				Tag encoded = encode(value);
				if (encoded != null) {
					tag.put(key(field), encoded);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
		tag.putByteArray("synched_data", entityData(player));
		return tag;
	}

	static void apply(Entity player, CompoundTag tag) {
		applyEntityData(player, tag.getByteArray("synched_data").orElse(new byte[0]));
		for (Field field : fields(player.getClass())) {
			Tag encoded = tag.get(key(field));
			if (!(encoded instanceof CompoundTag value)) {
				continue;
			}
			try {
				Object decoded = decode(field.getType(), value);
				if (decoded != null || !field.getType().isPrimitive()) {
					field.set(player, decoded);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
		player.refreshDimensions();
	}

	private static String key(Field field) {
		return field.getDeclaringClass().getSimpleName() + "." + field.getName();
	}

	private static List<Field> fields(Class<?> owner) {
		return FIELDS.computeIfAbsent(owner, PlayerFields::collect);
	}

	private static List<Field> collect(Class<?> owner) {
		List<Field> result = new ArrayList<>();
		for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic() || DENY.contains(field.getName())
						|| !supported(field.getType())) {
					continue;
				}
				try {
					field.setAccessible(true);
					result.add(field);
				} catch (RuntimeException ignored) {
				}
			}
		}
		return List.copyOf(result);
	}

	private static boolean supported(Class<?> type) {
		return type.isPrimitive() || type == Integer.class || type == Long.class || type == Float.class || type == Double.class
				|| type == Boolean.class || type.isEnum() || type == Vec3.class || type == BlockPos.class;
	}

	private static Tag encode(Object value) {
		CompoundTag tag = new CompoundTag();
		switch (value) {
			case null -> tag.putBoolean("null", true);
			case Integer i -> tag.putInt("v", i);
			case Long l -> tag.putLong("v", l);
			case Float f -> tag.putFloat("v", f);
			case Double d -> tag.putDouble("v", d);
			case Boolean b -> tag.putBoolean("v", b);
			case Short s -> tag.putShort("v", s);
			case Byte b -> tag.putByte("v", b);
			case Character c -> tag.putInt("v", c);
			case Enum<?> e -> tag.putString("v", e.name());
			case Vec3 v -> {
				tag.putDouble("x", v.x);
				tag.putDouble("y", v.y);
				tag.putDouble("z", v.z);
			}
			case BlockPos p -> tag.putLong("v", p.asLong());
			default -> {
				return null;
			}
		}
		return tag;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object decode(Class<?> type, CompoundTag tag) {
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
		if (type == short.class) {
			return tag.getShortOr("v", (short) 0);
		}
		if (type == byte.class) {
			return tag.getByteOr("v", (byte) 0);
		}
		if (type == char.class) {
			return (char) tag.getIntOr("v", 0);
		}
		if (type.isEnum()) {
			return Enum.valueOf((Class<? extends Enum>) type, tag.getStringOr("v", ""));
		}
		if (type == Vec3.class) {
			return new Vec3(tag.getDoubleOr("x", 0.0), tag.getDoubleOr("y", 0.0), tag.getDoubleOr("z", 0.0));
		}
		if (type == BlockPos.class) {
			return BlockPos.of(tag.getLongOr("v", 0L));
		}
		return null;
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
			DreamManhunt.LOGGER.warn("Could not restore synched data of {}", entity, e);
		} finally {
			buffer.release();
		}
	}
}
