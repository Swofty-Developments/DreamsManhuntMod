package net.swofty.dreammanhunt.server.sync;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.slf4j.Logger;

public final class ChunkReadiness {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final long MAX_WAIT_MS = 3_000L;
	private static final List<Consumer<MinecraftServer>> RELEASE_LISTENERS = new CopyOnWriteArrayList<>();
	private static String lastStatus = "";
	private static long waitingSince = -1L;
	private static volatile long lastWaitMs;
	private static volatile boolean lastTimedOut;

	private ChunkReadiness() {
	}

	public static void onRelease(Consumer<MinecraftServer> listener) {
		RELEASE_LISTENERS.add(listener);
	}

	public static void removeReleaseListener(Consumer<MinecraftServer> listener) {
		RELEASE_LISTENERS.remove(listener);
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag tag = new CompoundTag();
			tag.putLongArray("ticking", tickingChunks(level).toLongArray());
			LongOpenHashSet holders = holders(level);
			tag.putLongArray("holders", holders.toLongArray());
			tag.putLongArray("full", fullChunks(level, holders).toLongArray());
			tag.putLongArray("poi", poiChunks(level).toLongArray());
			out.put(level.dimension().identifier().toString(), tag);
		}
		return out;
	}

	public static boolean ready(MinecraftServer server) {
		CompoundTag root = SeedStreams.scheduledServerState();
		if (root == null) {
			return release(server);
		}
		CompoundTag chunks = root.getCompoundOrEmpty("tickingChunks");
		CompoundTag order = root.getCompoundOrEmpty("entityOrder");
		int missingTicking = 0;
		int missingHolders = 0;
		int missingFull = 0;
		int missingEntities = 0;
		int lightBusy = 0;
		int missingPoi = 0;
		for (ServerLevel level : server.getAllLevels()) {
			String key = level.dimension().identifier().toString();
			CompoundTag saved = chunks.getCompoundOrEmpty(key);
			LongOpenHashSet ticking = tickingChunks(level);
			for (long chunk : saved.getLongArray("ticking").orElse(new long[0])) {
				if (!ticking.contains(chunk)) {
					missingTicking++;
				}
			}
			LongOpenHashSet holders = holders(level);
			net.minecraft.server.level.DistanceManager tickets = level.getChunkSource().chunkMap.getDistanceManager();
			for (long chunk : saved.getLongArray("holders").orElse(new long[0])) {
				if (!holders.contains(chunk) && net.minecraft.server.level.ChunkLevel.isLoaded(tickets.getChunkLevel(chunk, false))) {
					missingHolders++;
				}
			}
			for (long chunk : saved.getLongArray("full").orElse(new long[0])) {
				if (level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) == null
					&& tickets.getChunkLevel(chunk, false) <= net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.server.level.FullChunkStatus.FULL)) {
					missingFull++;
				}
			}
			for (Tag raw : order.getListOrEmpty(key)) {
				if (raw instanceof StringTag(String value)) {
					try {
						if (level.getEntity(UUID.fromString(value)) == null) {
							missingEntities++;
						}
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			if (lightBusy(level)) {
				lightBusy++;
			}
			LongOpenHashSet poi = poiChunks(level);
			for (long chunk : saved.getLongArray("poi").orElse(new long[0])) {
				if (!poi.contains(chunk) && tickets.getChunkLevel(chunk, false) <= net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.server.level.FullChunkStatus.FULL)) {
					missingPoi++;
					level.getPoiManager().prefetch(ChunkPos.unpack(chunk));
				}
			}
			missingPoi += pendingPoi(level);
		}
		String status = missingTicking + " ticking chunks, " + missingHolders + " chunk holders, " + missingFull + " full chunks, "
			+ missingEntities + " entities, " + missingPoi + " poi chunks and " + lightBusy + " light engines";
		// Holders and full chunks are reported only: waiting for them lets more chunks reach ticking status than at the save.
		if (missingTicking > 0 || missingEntities > 0 || missingPoi > 0 || lightBusy > 0) {
			long now = System.nanoTime() / 1_000_000L;
			if (waitingSince < 0L) {
				waitingSince = now;
			} else if (now - waitingSince > MAX_WAIT_MS) {
				LOGGER.warn("Restore barrier: resuming without {} after {} ms", status, now - waitingSince);
				lastTimedOut = true;
				return release(server);
			}
			if (!status.equals(lastStatus)) {
				lastStatus = status;
				LOGGER.info("Restore barrier: waiting for {}", status);
			}
			return false;
		}
		return release(server);
	}

	public static long lastWaitMs() {
		return lastWaitMs;
	}

	public static boolean lastTimedOut() {
		return lastTimedOut;
	}

	public static void resetStats() {
		lastWaitMs = 0L;
		lastTimedOut = false;
	}

	private static boolean release(MinecraftServer server) {
		lastWaitMs = waitingSince < 0L ? 0L : System.nanoTime() / 1_000_000L - waitingSince;
		lastStatus = "";
		waitingSince = -1L;
		for (Consumer<MinecraftServer> listener : RELEASE_LISTENERS) {
			try {
				listener.accept(server);
			} catch (RuntimeException e) {
				LOGGER.error("Restore release listener failed", e);
			}
		}
		return true;
	}

	private static LongOpenHashSet tickingChunks(ServerLevel level) {
		LongOpenHashSet ticking = new LongOpenHashSet();
		level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk -> ticking.add(chunk.getPos().pack()));
		return ticking;
	}

	private static LongOpenHashSet fullChunks(ServerLevel level, LongOpenHashSet holders) {
		LongOpenHashSet full = new LongOpenHashSet();
		for (long pos : holders) {
			if (level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos)) != null) {
				full.add(pos);
			}
		}
		return full;
	}

	@SuppressWarnings("unchecked")
	private static LongOpenHashSet holders(ServerLevel level) {
		LongOpenHashSet out = new LongOpenHashSet();
		try {
			Field field = ChunkMap.class.getDeclaredField("visibleChunkMap");
			field.setAccessible(true);
			Long2ObjectMap<ChunkHolder> map = (Long2ObjectMap<ChunkHolder>) field.get(level.getChunkSource().chunkMap);
			out.addAll(map.keySet());
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return out;
	}

	private static LongOpenHashSet poiChunks(ServerLevel level) {
		LongOpenHashSet out = new LongOpenHashSet();
		try {
			Object lock = storageField("loadLock").get(level.getPoiManager());
			synchronized (lock) {
				out.addAll((it.unimi.dsi.fastutil.longs.LongSet) storageField("loadedChunks").get(level.getPoiManager()));
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return out;
	}

	private static int pendingPoi(ServerLevel level) {
		try {
			Object lock = storageField("loadLock").get(level.getPoiManager());
			synchronized (lock) {
				return ((Long2ObjectMap<?>) storageField("pendingLoads").get(level.getPoiManager())).size();
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return 0;
		}
	}

	private static Field storageField(String name) throws NoSuchFieldException {
		Field field = net.minecraft.world.level.chunk.storage.SectionStorage.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static boolean lightBusy(ServerLevel level) {
		ThreadedLevelLightEngine engine = level.getChunkSource().getLightEngine();
		try {
			Field field = ThreadedLevelLightEngine.class.getDeclaredField("lightTasks");
			field.setAccessible(true);
			if (field.get(engine) instanceof Collection<?> tasks && !tasks.isEmpty()) {
				return true;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return engine.hasLightWork();
	}
}
