package net.swofty.dreammanhunt.server.sync;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class LevelRandoms {
	private LevelRandoms() {
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag tag = new CompoundTag();
			AtomicLong random = seed(level, "random");
			AtomicLong sound = seed(level, "soundSeedGenerator");
			if (random != null) {
				tag.putLong("random", random.get());
			}
			if (sound != null) {
				tag.putLong("sound", sound.get());
			}
			try {
				tag.putInt("randValue", field("randValue").getInt(level));
			} catch (ReflectiveOperationException ignored) {
			}
			out.put(level.dimension().identifier().toString(), tag);
		}
		return out;
	}

	public static void apply(MinecraftServer server, CompoundTag tag) {
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag saved = tag.getCompoundOrEmpty(level.dimension().identifier().toString());
			if (saved.isEmpty()) {
				continue;
			}
			AtomicLong random = seed(level, "random");
			AtomicLong sound = seed(level, "soundSeedGenerator");
			if (random != null && saved.contains("random")) {
				random.set(saved.getLongOr("random", random.get()));
			}
			if (sound != null && saved.contains("sound")) {
				sound.set(saved.getLongOr("sound", sound.get()));
			}
			if (saved.contains("randValue")) {
				try {
					field("randValue").setInt(level, saved.getIntOr("randValue", 0));
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}
	}

	private static @Nullable AtomicLong seed(Level level, String name) {
		try {
			Object random = field(name).get(level);
			for (Class<?> type = random.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
				try {
					Field seed = type.getDeclaredField("seed");
					seed.setAccessible(true);
					return seed.get(random) instanceof AtomicLong atomic ? atomic : null;
				} catch (NoSuchFieldException ignored) {
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return null;
	}

	private static Field field(String name) throws NoSuchFieldException {
		Field field = Level.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
