package net.swofty.dreammanhunt.client.state.extra;

import java.lang.reflect.Field;
import net.minecraft.nbt.CompoundTag;

final class Primitives {
	private Primitives() {
	}

	static void put(CompoundTag tag, String key, Class<?> type, Object value) {
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

	static void set(Object owner, Field field, CompoundTag tag, String key, Class<?> type) throws IllegalAccessException {
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
