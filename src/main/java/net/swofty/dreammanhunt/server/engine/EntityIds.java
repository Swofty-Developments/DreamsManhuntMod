package net.swofty.dreammanhunt.server.engine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.swofty.dreammanhunt.mixin.server.ServerLevelAccessor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class EntityIds {
	private static final AtomicInteger PLACEHOLDERS = new AtomicInteger();
	private static final ThreadLocal<int[]> DEFERRING = ThreadLocal.withInitial(() -> new int[1]);

	private EntityIds() {
	}

	public static <T> T deferred(Supplier<T> action) {
		int[] depth = DEFERRING.get();
		depth[0]++;
		try {
			return action.get();
		} finally {
			depth[0]--;
		}
	}

	public static int next(ServerLevel level) {
		if (DEFERRING.get()[0] > 0) {
			return PLACEHOLDERS.decrementAndGet();
		}
		return allocate(level.getServer());
	}

	static int allocate(MinecraftServer server) {
		AtomicInteger counter = ServerLevelAccessor.dreammanhunt$getEntityCounter();
		int id = 0;
		while (id <= 0 || isLive(server, id)) {
			id = counter.incrementAndGet();
		}
		return id;
	}

	static boolean isLive(MinecraftServer server, int id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getChunkSource().hasEntityWithId(id)) {
				return true;
			}
		}
		return false;
	}

	static int counter() {
		return ServerLevelAccessor.dreammanhunt$getEntityCounter().get();
	}

	static void setCounter(int value) {
		ServerLevelAccessor.dreammanhunt$getEntityCounter().set(value);
	}
}
