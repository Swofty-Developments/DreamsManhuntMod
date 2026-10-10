package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.swofty.dreammanhunt.mixin.server.CatSpawnerAccessor;
import net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor;
import net.swofty.dreammanhunt.mixin.server.LevelAccessor;
import net.swofty.dreammanhunt.mixin.server.MinecraftServerAccessor;
import net.swofty.dreammanhunt.mixin.server.PatrolSpawnerAccessor;
import net.swofty.dreammanhunt.mixin.server.PhantomSpawnerAccessor;
import net.swofty.dreammanhunt.mixin.server.ServerLevelAccessor;
import net.swofty.dreammanhunt.mixin.server.ServerTickRateManagerAccessor;
import net.swofty.dreammanhunt.mixin.server.TickRateManagerAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RuntimeState {
	private final CompoundTag tag;

	private RuntimeState(CompoundTag tag) {
		this.tag = tag;
	}

	static RuntimeState capture(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("server_tick_count", ((MinecraftServerAccessor) server).dreammanhunt$getTickCount());

		ServerTickRateManager tickRate = server.tickRateManager();
		ServerTickRateManagerAccessor sprint = (ServerTickRateManagerAccessor) tickRate;
		CompoundTag tickTag = new CompoundTag();
		tickTag.putFloat("tick_rate", tickRate.tickrate());
		tickTag.putBoolean("frozen", tickRate.isFrozen());
		tickTag.putInt("frozen_ticks_to_run", tickRate.frozenTicksToRun());
		tickTag.putLong("remaining_sprint_ticks", sprint.dreammanhunt$getRemainingSprintTicks());
		tickTag.putLong("scheduled_sprint_ticks", sprint.dreammanhunt$getScheduledCurrentSprintTicks());
		tickTag.putBoolean("previous_frozen", sprint.dreammanhunt$getPreviousIsFrozen());
		tag.put("tick_rate", tickTag);

		WorldData worldData = server.getWorldData();
		ServerLevelData overworldData = worldData.overworldData();
		CompoundTag levelData = new CompoundTag();
		levelData.putLong("game_time", overworldData.getGameTime());
		levelData.store("spawn", LevelData.RespawnData.CODEC, overworldData.getRespawnData());
		levelData.putString("game_type", worldData.getGameType().getName());
		levelData.putString("difficulty", worldData.getDifficulty().getSerializedName());
		levelData.putBoolean("difficulty_locked", worldData.isDifficultyLocked());
		levelData.putBoolean("allow_commands", overworldData.isAllowCommands());
		tag.put("level_data", levelData);

		ListTag levels = new ListTag();
		ListTag entities = new ListTag();
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag levelTag = new CompoundTag();
			levelTag.putString("dimension", level.dimension().identifier().toString());
			LevelAccessor accessor = (LevelAccessor) level;
			if (accessor.dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
				levelTag.putLong("random", ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().get());
			}
			levelTag.putInt("rand_value", accessor.dreammanhunt$getRandValue());
			levelTag.putInt("empty_time", ((ServerLevelAccessor) level).dreammanhunt$getEmptyTime());
			CompoundTag spawners = new CompoundTag();
			for (CustomSpawner spawner : ((ServerLevelAccessor) level).dreammanhunt$getCustomSpawners()) {
				switch (spawner) {
					case PhantomSpawner phantom -> spawners.putInt("phantom", ((PhantomSpawnerAccessor) phantom).dreammanhunt$getNextTick());
					case PatrolSpawner patrol -> spawners.putInt("patrol", ((PatrolSpawnerAccessor) patrol).dreammanhunt$getNextTick());
					case CatSpawner cat -> spawners.putInt("cat", ((CatSpawnerAccessor) cat).dreammanhunt$getNextTick());
					default -> {
					}
				}
			}
			levelTag.put("spawners", spawners);
			levels.add(levelTag);
			for (Entity entity : level.getAllEntities()) {
				entities.add(EntityRuntimeRestorer.capture(entity));
			}
		}
		tag.put("levels", levels);
		ListTag players = new ListTag();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			players.add(PlayerRuntime.capture(player));
		}
		tag.put("players", players);
		tag.put("entities", entities);
		return new RuntimeState(tag);
	}

	static RuntimeState read(Path file) throws IOException {
		return new RuntimeState(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
	}

	void write(Path file) throws IOException {
		NbtIo.writeCompressed(this.tag, file);
	}

	void applyLevelData(MinecraftServer server) {
		CompoundTag levelData = this.tag.getCompoundOrEmpty("level_data");
		WorldData worldData = server.getWorldData();
		ServerLevelData overworldData = worldData.overworldData();
		overworldData.setGameTime(levelData.getLongOr("game_time", overworldData.getGameTime()));
		levelData.read("spawn", LevelData.RespawnData.CODEC).ifPresent(overworldData::setSpawn);
		worldData.setGameType(GameType.byName(levelData.getStringOr("game_type", worldData.getGameType().getName()), worldData.getGameType()));
		Difficulty difficulty = Difficulty.byName(levelData.getStringOr("difficulty", worldData.getDifficulty().getSerializedName()));
		if (difficulty != null) {
			worldData.setDifficulty(difficulty);
		}
		worldData.setDifficultyLocked(levelData.getBooleanOr("difficulty_locked", worldData.isDifficultyLocked()));
		overworldData.setAllowCommands(levelData.getBooleanOr("allow_commands", overworldData.isAllowCommands()));
	}

	void applyServerCounters(MinecraftServer server) {
		((MinecraftServerAccessor) server).dreammanhunt$setTickCount(this.tag.getIntOr("server_tick_count", server.getTickCount()));
	}

	void applyLevelRandom(MinecraftServer server) {
		Map<String, CompoundTag> byDimension = new HashMap<>();
		for (Tag element : this.tag.getListOrEmpty("levels")) {
			if (element instanceof CompoundTag levelTag) {
				byDimension.put(levelTag.getStringOr("dimension", ""), levelTag);
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			CompoundTag levelTag = byDimension.get(level.dimension().identifier().toString());
			if (levelTag == null) {
				continue;
			}
			LevelAccessor accessor = (LevelAccessor) level;
			if (accessor.dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
				levelTag.getLong("random").ifPresent(seed -> ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().set(seed));
			}
			levelTag.getInt("rand_value").ifPresent(accessor::dreammanhunt$setRandValue);
			levelTag.getInt("empty_time").ifPresent(((ServerLevelAccessor) level)::dreammanhunt$setEmptyTime);
			CompoundTag spawners = levelTag.getCompoundOrEmpty("spawners");
			for (CustomSpawner spawner : ((ServerLevelAccessor) level).dreammanhunt$getCustomSpawners()) {
				switch (spawner) {
					case PhantomSpawner phantom -> spawners.getInt("phantom").ifPresent(((PhantomSpawnerAccessor) phantom)::dreammanhunt$setNextTick);
					case PatrolSpawner patrol -> spawners.getInt("patrol").ifPresent(((PatrolSpawnerAccessor) patrol)::dreammanhunt$setNextTick);
					case CatSpawner cat -> spawners.getInt("cat").ifPresent(((CatSpawnerAccessor) cat)::dreammanhunt$setNextTick);
					default -> {
					}
				}
			}
		}
	}

	void armEntityRuntime() {
		Map<UUID, CompoundTag> runtime = new HashMap<>();
		for (Tag element : this.tag.getListOrEmpty("entities")) {
			if (element instanceof CompoundTag entityTag) {
				entityTag.read("uuid", UUIDUtil.CODEC).ifPresent(uuid -> runtime.put(uuid, entityTag));
			}
		}
		EntityRuntimeRestorer.arm(runtime);
	}

	Map<UUID, CompoundTag> playerRuntime() {
		Map<UUID, CompoundTag> players = new HashMap<>();
		for (Tag element : this.tag.getListOrEmpty("players")) {
			if (element instanceof CompoundTag playerTag) {
				playerTag.read("uuid", UUIDUtil.CODEC).ifPresent(uuid -> players.put(uuid, playerTag));
			}
		}
		return players;
	}

	void applyTickRate(MinecraftServer server) {
		CompoundTag tickTag = this.tag.getCompoundOrEmpty("tick_rate");
		ServerTickRateManager tickRate = server.tickRateManager();
		TickRateManagerAccessor base = (TickRateManagerAccessor) tickRate;
		ServerTickRateManagerAccessor sprint = (ServerTickRateManagerAccessor) tickRate;
		tickRate.setTickRate(tickTag.getFloatOr("tick_rate", tickRate.tickrate()));
		boolean frozen = tickTag.getBooleanOr("frozen", false);
		int frozenTicksToRun = tickTag.getIntOr("frozen_ticks_to_run", 0);
		base.dreammanhunt$setIsFrozen(frozen);
		base.dreammanhunt$setFrozenTicksToRun(frozenTicksToRun);
		base.dreammanhunt$setRunGameElements(!frozen || frozenTicksToRun > 0);
		sprint.dreammanhunt$setRemainingSprintTicks(tickTag.getLongOr("remaining_sprint_ticks", 0L));
		sprint.dreammanhunt$setScheduledCurrentSprintTicks(tickTag.getLongOr("scheduled_sprint_ticks", 0L));
		sprint.dreammanhunt$setPreviousIsFrozen(tickTag.getBooleanOr("previous_frozen", false));
		sprint.dreammanhunt$updateStateToClients();
	}
}
