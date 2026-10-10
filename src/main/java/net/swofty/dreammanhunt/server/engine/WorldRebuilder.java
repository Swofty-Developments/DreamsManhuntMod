package net.swofty.dreammanhunt.server.engine;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerQueue;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreboardSaveData;
import net.swofty.dreammanhunt.mixin.server.GameRulesAccessor;
import net.swofty.dreammanhunt.mixin.server.MinecraftServerAccessor;
import net.swofty.dreammanhunt.mixin.server.SavedDataStorageAccessor;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class WorldRebuilder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final MinecraftServer server;
	private final MinecraftServerAccessor accessor;

	WorldRebuilder(MinecraftServer server) {
		this.server = server;
		this.accessor = (MinecraftServerAccessor) server;
	}

	void clearScoreboard() {
		ServerScoreboard scoreboard = this.server.getScoreboard();
		for (Objective objective : new ArrayList<>(scoreboard.getObjectives())) {
			scoreboard.removeObjective(objective);
		}
		for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
			scoreboard.removePlayerTeam(team);
		}
	}

	void detachCustomBossEvents() {
		for (CustomBossEvent event : new ArrayList<>(this.server.getCustomBossEvents().getEvents())) {
			event.removeAllPlayers();
		}
	}

	void closeLevels() {
		List<ServerLevel> levels = new ArrayList<>(this.accessor.dreammanhunt$getLevels().values());
		for (ServerLevel level : levels) {
			ServerLevelEvents.UNLOAD.invoker().onLevelUnload(this.server, level);
		}
		for (ServerLevel level : levels) {
			try {
				level.close();
			} catch (IOException e) {
				LOGGER.error("Failed to close level {} during savestate restore", level.dimension().identifier(), e);
			}
		}
		((SavedDataStorageAccessor) this.server.getDataStorage()).dreammanhunt$getPendingWriteFuture().join();
	}

	GameRuleMap reloadGlobalData() {
		SavedDataStorage storage = this.server.getDataStorage();
		((SavedDataStorageAccessor) storage).dreammanhunt$getPendingWriteFuture().join();
		GameRuleMap liveRules = ((GameRulesAccessor) this.server.getGlobalGameRules()).dreammanhunt$getRules();
		((SavedDataStorageAccessor) storage).dreammanhunt$getCache().clear();
		storage.set(WorldGenSettings.TYPE, this.server.getWorldGenSettings());
		GameRuleMap restoredRules = storage.computeIfAbsent(GameRuleMap.TYPE);
		storage.set(GameRuleMap.TYPE, liveRules);

		this.accessor.dreammanhunt$setRandomSequences(storage.computeIfAbsent(RandomSequences.TYPE));
		this.accessor.dreammanhunt$setWeatherData(storage.computeIfAbsent(WeatherData.TYPE));
		ServerClockManager clocks = storage.computeIfAbsent(ServerClockManager.TYPE);
		clocks.init(this.server);
		this.accessor.dreammanhunt$setClockManager(clocks);
		this.accessor.dreammanhunt$setCustomBossEvents(storage.computeIfAbsent(CustomBossEvents.TYPE));
		this.accessor.dreammanhunt$setScheduledEvents(storage.computeIfAbsent(TimerQueue.TYPE));
		this.accessor.dreammanhunt$setStopwatches(storage.computeIfAbsent(Stopwatches.TYPE));
		this.accessor.dreammanhunt$setCommandStorage(new CommandStorage(storage));
		this.server.getScoreboard().load(storage.computeIfAbsent(ScoreboardSaveData.TYPE).getData());
		return restoredRules;
	}

	void applyGameRules(GameRuleMap restoredRules) {
		GameRules restored = new GameRules(this.server.getWorldData().enabledFeatures(), restoredRules);
		this.server.getGlobalGameRules().setAll(restored, this.server);
	}

	void createLevels() {
		WorldData worldData = this.server.getWorldData();
		ServerLevelData levelData = worldData.overworldData();
		boolean isDebug = worldData.isDebugWorld();
		Registry<LevelStem> dimensions = this.server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
		long biomeZoomSeed = BiomeManager.obfuscateSeed(this.server.getWorldGenSettings().options().seed());
		Executor executor = this.accessor.dreammanhunt$getExecutor();
		LevelStorageSource.LevelStorageAccess storageSource = this.accessor.dreammanhunt$getStorageSource();
		List<CustomSpawner> overworldSpawners = ImmutableList.of(
				new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(this.server.getDataStorage())
		);

		Map<ResourceKey<Level>, ServerLevel> created = new LinkedHashMap<>();
		ServerLevel overworld = new ServerLevel(
				this.server, executor, storageSource, levelData, Level.OVERWORLD, dimensions.getValueOrThrow(LevelStem.OVERWORLD), isDebug, biomeZoomSeed, overworldSpawners, true
		);
		created.put(Level.OVERWORLD, overworld);
		for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : dimensions.entrySet()) {
			if (entry.getKey() == LevelStem.OVERWORLD) {
				continue;
			}
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.getKey().identifier());
			ServerLevel level = new ServerLevel(
					this.server, executor, storageSource, new DerivedLevelData(worldData, levelData), dimension, entry.getValue(), isDebug, biomeZoomSeed, ImmutableList.of(), false
			);
			created.put(dimension, level);
		}

		Map<ResourceKey<Level>, ServerLevel> levels = this.accessor.dreammanhunt$getLevels();
		levels.clear();
		levels.putAll(created);
		for (ServerLevel level : created.values()) {
			level.getWorldBorder().setAbsoluteMaxSize(this.server.getAbsoluteMaxWorldSize());
			this.server.getPlayerList().addWorldborderListener(level);
		}
		for (ServerLevel level : created.values()) {
			TicketStorage savedTickets = level.getDataStorage().get(TicketStorage.TYPE);
			if (savedTickets != null) {
				savedTickets.activateAllDeactivatedTickets();
			}
		}
		for (ServerLevel level : created.values()) {
			ServerLevelEvents.LOAD.invoker().onLevelLoad(this.server, level);
		}
		this.server.updateMobSpawningFlags();
	}
}
