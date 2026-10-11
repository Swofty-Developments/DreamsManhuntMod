package net.swofty.dreammanhunt.server.sync;

import java.lang.reflect.Field;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

public final class SpawnFlags {
	private SpawnFlags() {
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			try {
				out.putBoolean(level.dimension().identifier().toString(), field().getBoolean(level.getChunkSource()));
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return out;
	}

	public static void apply(MinecraftServer server, CompoundTag tag) {
		for (ServerLevel level : server.getAllLevels()) {
			String key = level.dimension().identifier().toString();
			if (tag.contains(key)) {
				level.getChunkSource().setSpawnSettings(tag.getBooleanOr(key, level.isSpawningMonsters()));
			}
		}
	}

	private static Field field() throws NoSuchFieldException {
		Field field = ServerChunkCache.class.getDeclaredField("spawnEnemies");
		field.setAccessible(true);
		return field;
	}
}
