package net.swofty.dreammanhunt.server.sync;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.slf4j.Logger;

public final class ChunkReadiness {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final long MAX_WAIT_MS = 5_000L;
	private static String lastStatus = "";
	private static long waitingSince = -1L;

	private ChunkReadiness() {
	}

	public static CompoundTag capture(MinecraftServer server) {
		CompoundTag out = new CompoundTag();
		for (ServerLevel level : server.getAllLevels()) {
			LongOpenHashSet ticking = tickingChunks(level);
			out.putLongArray(level.dimension().identifier().toString(), ticking.toLongArray());
		}
		return out;
	}

	public static boolean ready(MinecraftServer server) {
		CompoundTag root = SeedStreams.scheduledServerState();
		if (root == null) {
			return true;
		}
		CompoundTag chunks = root.getCompoundOrEmpty("tickingChunks");
		CompoundTag order = root.getCompoundOrEmpty("entityOrder");
		int missingChunks = 0;
		int missingEntities = 0;
		for (ServerLevel level : server.getAllLevels()) {
			String key = level.dimension().identifier().toString();
			LongOpenHashSet ticking = tickingChunks(level);
			for (long chunk : chunks.getLongArray(key).orElse(new long[0])) {
				if (!ticking.contains(chunk)) {
					missingChunks++;
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
		}
		String status = missingChunks + " ticking chunks and " + missingEntities + " entities";
		if (missingChunks > 0 || missingEntities > 0) {
			long now = System.nanoTime() / 1_000_000L;
			if (waitingSince < 0L) {
				waitingSince = now;
			} else if (now - waitingSince > MAX_WAIT_MS) {
				LOGGER.warn("Restore barrier: resuming without {} after {} ms", status, now - waitingSince);
				waitingSince = -1L;
				lastStatus = "";
				return true;
			}
			if (!status.equals(lastStatus)) {
				lastStatus = status;
				LOGGER.info("Restore barrier: waiting for {} to load", status);
			}
			return false;
		}
		lastStatus = "";
		waitingSince = -1L;
		return true;
	}

	private static LongOpenHashSet tickingChunks(ServerLevel level) {
		LongOpenHashSet ticking = new LongOpenHashSet();
		level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk -> ticking.add(chunk.getPos().pack()));
		return ticking;
	}
}
