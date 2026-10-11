package net.swofty.dreammanhunt.server.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.storage.LevelResource;
import net.swofty.dreammanhunt.api.SavestateInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SavestateEngine {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<MinecraftServer, SavestateEngine> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
	private static final String META_FILE = "meta.json";
	private static final String RUNTIME_FILE = "runtime.nbt";
	private static final String WORLD_DIR = "world";

	private final MinecraftServer server;
	private final Path worldDir;
	private final Path statesDir;

	private SavestateEngine(MinecraftServer server) {
		this.server = server;
		this.worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		this.statesDir = this.worldDir.resolve("dreammanhunt").resolve("savestates");
	}

	public static SavestateEngine get(MinecraftServer server) {
		return ENGINES.computeIfAbsent(server, SavestateEngine::new);
	}

	public SavestateInfo save(String id, String name, @Nullable ServerPlayer creator) throws SavestateException {
		Path target = this.stateDir(id);
		if (Files.exists(target)) {
			throw new SavestateException("Savestate " + id + " already exists");
		}
		long started = System.nanoTime();
		RuntimeState runtime = RuntimeState.capture(this.server);
		this.server.saveEverything(true, true, true);

		ServerLevel overworld = this.server.overworld();
		Level creatorLevel = creator != null ? creator.level() : overworld;
		SavestateInfo info = new SavestateInfo(
				id,
				name,
				System.currentTimeMillis(),
				overworld.getGameTime(),
				overworld.getOverworldClockTime(),
				creator != null ? creator.getGameProfile().name() : "Server",
				creatorLevel.dimension().identifier().toString()
		);

		Path staging = this.statesDir.resolve(id + ".partial");
		try {
			SnapshotFiles.deleteRecursively(staging);
			SnapshotFiles.copyTree(this.worldDir, staging.resolve(WORLD_DIR), SnapshotFiles.WORLD_EXCLUSIONS);
			runtime.write(staging.resolve(RUNTIME_FILE));
			JsonElement json = SavestateInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow();
			Files.writeString(staging.resolve(META_FILE), json.toString(), StandardCharsets.UTF_8);
			Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			try {
				SnapshotFiles.deleteRecursively(staging);
			} catch (IOException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw new SavestateException("Failed to write savestate " + id, e);
		}
		LOGGER.info("Saved savestate {} ({}) in {} ms", id, name, (System.nanoTime() - started) / 1_000_000L);
		return info;
	}

	public List<SavestateInfo> list() {
		if (!Files.isDirectory(this.statesDir)) {
			return List.of();
		}
		List<SavestateInfo> result = new ArrayList<>();
		try (Stream<Path> dirs = Files.list(this.statesDir)) {
			for (Path dir : dirs.toList()) {
				if (Files.isDirectory(dir) && ID_PATTERN.matcher(dir.getFileName().toString()).matches()) {
					this.readMeta(dir).ifPresent(result::add);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to list savestates in {}", this.statesDir, e);
		}
		result.sort(Comparator.comparingLong(SavestateInfo::createdAtEpochMillis).thenComparing(SavestateInfo::id));
		return result;
	}

	public Optional<SavestateInfo> find(String idOrName) {
		List<SavestateInfo> all = this.list();
		for (SavestateInfo info : all) {
			if (info.id().equals(idOrName)) {
				return Optional.of(info);
			}
		}
		for (int i = all.size() - 1; i >= 0; i--) {
			if (all.get(i).name().equalsIgnoreCase(idOrName)) {
				return Optional.of(all.get(i));
			}
		}
		return Optional.empty();
	}

	public void restore(String id) throws SavestateException {
		Path dir = this.stateDir(id);
		Path snapshotWorld = dir.resolve(WORLD_DIR);
		if (!Files.isDirectory(snapshotWorld) || !Files.isRegularFile(dir.resolve(RUNTIME_FILE))) {
			throw new SavestateException("Savestate " + id + " does not exist");
		}
		RuntimeState runtime;
		try {
			runtime = RuntimeState.read(dir.resolve(RUNTIME_FILE));
		} catch (IOException e) {
			throw new SavestateException("Failed to read savestate " + id, e);
		}

		long started = System.nanoTime();
		RuntimeState live = RuntimeState.capture(this.server);
		WorldRebuilder world = new WorldRebuilder(this.server);
		PlayerRestorer players = new PlayerRestorer(this.server);

		world.clearScoreboard();
		world.detachCustomBossEvents();
		BossBarRegistry.detachEveryone();
		List<PlayerRestorer.Detached> detached = players.detachAll();
		world.flushGlobalData();
		long detachedAt = System.nanoTime();
		world.closeLevels();
		long closedAt = System.nanoTime();

		Path backup = this.statesDir.getParent().resolve("restore-backup-" + id + "-" + System.currentTimeMillis());
		boolean copyStarted = false;
		long copiedAt;
		try {
			SnapshotFiles.moveTree(this.worldDir, backup, SnapshotFiles.WORLD_EXCLUSIONS);
			Failpoint.check("copy");
			copyStarted = true;
			SnapshotFiles.copyTree(snapshotWorld, this.worldDir, Set.of());
			copiedAt = System.nanoTime();
			this.rebuild(runtime, world, players, detached, false);
		} catch (Throwable failure) {
			this.rollBack(id, failure, live, world, players, detached, backup, copyStarted);
			return;
		}
		long rebuiltAt = System.nanoTime();
		deleteInBackground(backup);
		LOGGER.info(
				"Restored savestate {} in {} ms (detach+flush {} ms, close levels {} ms, swap files {} ms, rebuild {} ms)",
				id,
				(rebuiltAt - started) / 1_000_000L,
				(detachedAt - started) / 1_000_000L,
				(closedAt - detachedAt) / 1_000_000L,
				(copiedAt - closedAt) / 1_000_000L,
				(rebuiltAt - copiedAt) / 1_000_000L
		);
	}

	private void rebuild(RuntimeState state, WorldRebuilder world, PlayerRestorer players, List<PlayerRestorer.Detached> detached, boolean preferLive) {
		state.applyLevelData(this.server);
		GameRuleMap restoredRules = world.reloadGlobalData();
		state.armEntityRuntime();
		state.applyEntityCounter();
		world.createLevels();
		Failpoint.check("levels");
		world.applyGameRules(restoredRules);
		state.applyLevelRandom(this.server);
		state.applyServerCounters(this.server);
		state.applyTickRate(this.server);
		players.attachAll(detached, state.playerRuntime(), preferLive);
		state.resolveAiReferences();
	}

	private void rollBack(
			String id,
			Throwable failure,
			RuntimeState live,
			WorldRebuilder world,
			PlayerRestorer players,
			List<PlayerRestorer.Detached> detached,
			Path backup,
			boolean copyStarted
	) throws SavestateException {
		LOGGER.error("Restoring savestate {} failed, rolling the world back", id, failure);
		try {
			players.detachAll();
			if (world.levelsOpen()) {
				world.closeLevels();
			}
			if (Files.isDirectory(backup)) {
				if (copyStarted) {
					SnapshotFiles.clearTree(this.worldDir, SnapshotFiles.WORLD_EXCLUSIONS);
				}
				SnapshotFiles.moveTree(backup, this.worldDir, Set.of());
				SnapshotFiles.deleteRecursively(backup);
			}
			this.rebuild(live, world, players, detached, true);
		} catch (Throwable rollbackFailure) {
			rollbackFailure.addSuppressed(failure);
			LOGGER.error("Rolling back the failed restore of {} failed too; the pre-restore world is kept in {}", id, backup, rollbackFailure);
			throw new SavestateException("Restore of " + id + " failed and could not be rolled back", rollbackFailure);
		}
		throw new SavestateException("Restore of " + id + " failed; the world was rolled back", failure);
	}

	private static void deleteInBackground(Path dir) {
		Util.ioPool().execute(() -> {
			try {
				SnapshotFiles.deleteRecursively(dir);
			} catch (IOException e) {
				LOGGER.warn("Failed to delete restore backup {}", dir, e);
			}
		});
	}

	public boolean delete(String id) {
		Path dir;
		try {
			dir = this.stateDir(id);
		} catch (SavestateException e) {
			return false;
		}
		if (!Files.isDirectory(dir)) {
			return false;
		}
		try {
			SnapshotFiles.deleteRecursively(dir);
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to delete savestate {}", id, e);
			return false;
		}
	}

	private Path stateDir(String id) throws SavestateException {
		if (id == null || !ID_PATTERN.matcher(id).matches()) {
			throw new SavestateException("Invalid savestate id: " + id);
		}
		return this.statesDir.resolve(id);
	}

	private Optional<SavestateInfo> readMeta(Path dir) {
		Path meta = dir.resolve(META_FILE);
		if (!Files.isRegularFile(meta)) {
			return Optional.empty();
		}
		try {
			JsonElement json = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8));
			return SavestateInfo.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(error -> LOGGER.warn("Bad savestate meta {}: {}", meta, error));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Failed to read savestate meta {}", meta, e);
			return Optional.empty();
		}
	}
}
