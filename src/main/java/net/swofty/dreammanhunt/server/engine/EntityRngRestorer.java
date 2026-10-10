package net.swofty.dreammanhunt.server.engine;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.swofty.dreammanhunt.mixin.server.EntityAccessor;
import net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityRngRestorer {
	private static volatile Map<UUID, Long> pending = new ConcurrentHashMap<>();

	private EntityRngRestorer() {
	}

	static void arm(Map<UUID, Long> seeds) {
		pending = new ConcurrentHashMap<>(seeds);
	}

	static void disarm() {
		pending = new ConcurrentHashMap<>();
	}

	public static void apply(Entity entity) {
		Map<UUID, Long> seeds = pending;
		if (seeds.isEmpty()) {
			return;
		}
		Long seed = seeds.remove(entity.getUUID());
		if (seed != null && ((EntityAccessor) entity).dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
			((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().set(seed);
		}
	}

	static Long seedOf(Entity entity) {
		if (((EntityAccessor) entity).dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
			return ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().get();
		}
		return null;
	}
}
