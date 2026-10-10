package net.swofty.dreammanhunt.server.engine;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.mixin.server.LevelAccessor;
import net.swofty.dreammanhunt.mixin.server.PlayerListAccessor;
import net.swofty.dreammanhunt.mixin.server.ServerGamePacketListenerImplInvoker;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerRestorer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PREPARE_CHUNK_RADIUS = 3;
	private final MinecraftServer server;
	private final PlayerList playerList;
	private final PlayerListAccessor accessor;

	record Detached(ServerPlayer player, ServerGamePacketListenerImpl connection, CompoundTag liveData, CompoundTag liveRuntime, CompoundTag liveEntity) {
	}

	PlayerRestorer(MinecraftServer server) {
		this.server = server;
		this.playerList = server.getPlayerList();
		this.accessor = (PlayerListAccessor) this.playerList;
	}

	List<Detached> detachAll() {
		List<ServerPlayer> players = new ArrayList<>(this.playerList.getPlayers());
		List<Detached> detached = new ArrayList<>(players.size());
		for (ServerPlayer player : players) {
			detached.add(new Detached(player, player.connection, this.serialize(player), PlayerRuntime.capture(player), EntityRuntimeRestorer.capture(player)));
			player.getStats().save();
			player.getAdvancements().save();
			player.closeContainer();
			boolean alive = player.isAlive();
			player.getChunkTrackingView().forEach(pos -> {
				player.connection.chunkSender.dropChunk(player, pos);
				if (!alive) {
					player.connection.send(new ClientboundForgetLevelChunkPacket(pos));
				}
			});
		}
		for (ServerPlayer player : players) {
			player.level().removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
			this.accessor.dreammanhunt$getPlayers().remove(player);
		}
		return detached;
	}

	private CompoundTag serialize(ServerPlayer player) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
			player.saveWithoutId(output);
			return output.buildResult();
		}
	}

	List<ServerPlayer> attachAll(List<Detached> detached, Map<UUID, CompoundTag> snapshotRuntime, boolean preferLive) {
		List<ServerPlayer> attached = new ArrayList<>(detached.size());
		List<ServerPlayer> previous = new ArrayList<>(detached.size());
		for (Detached entry : detached) {
			if (!entry.connection().isAcceptingMessages()) {
				this.accessor.dreammanhunt$getPlayersByUuid().remove(entry.player().getUUID(), entry.player());
				continue;
			}
			attached.add(this.attach(entry, snapshotRuntime, preferLive));
			previous.add(entry.player());
			Failpoint.check("players");
		}
		ClientboundPlayerInfoUpdatePacket info = new ClientboundPlayerInfoUpdatePacket(
				EnumSet.of(
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
						ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
				),
				this.playerList.getPlayers()
		);
		GameRules rules = this.server.getGlobalGameRules();
		for (ServerPlayer player : attached) {
			player.connection.send(info);
			this.accessor.dreammanhunt$updateEntireScoreboard(this.server.getScoreboard(), player);
			this.server.getCustomBossEvents().onPlayerConnect(player);
			player.connection.send(new ClientboundEntityEventPacket(player, rules.get(GameRules.REDUCED_DEBUG_INFO) ? EntityEvent.REDUCED_DEBUG_INFO : EntityEvent.FULL_DEBUG_INFO));
			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, rules.get(GameRules.IMMEDIATE_RESPAWN) ? 1.0F : 0.0F));
			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LIMITED_CRAFTING, rules.get(GameRules.LIMITED_CRAFTING) ? 1.0F : 0.0F));
			player.getAdvancements().flushDirty(player, false);
		}
		for (int i = 0; i < attached.size(); i++) {
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(previous.get(i), attached.get(i), true);
		}
		return attached;
	}

	private ServerPlayer attach(Detached entry, Map<UUID, CompoundTag> snapshotRuntime, boolean preferLive) {
		ServerPlayer old = entry.player();
		UUID uuid = old.getUUID();
		Optional<CompoundTag> snapshotData = preferLive ? Optional.empty() : this.playerList.loadPlayerData(old.nameAndId());
		CompoundTag runtime = snapshotData.isPresent() ? snapshotRuntime.get(uuid) : entry.liveRuntime();
		if (snapshotData.isPresent() || preferLive) {
			PlayerAdvancements advancements = this.accessor.dreammanhunt$getAdvancements().remove(uuid);
			if (advancements != null) {
				advancements.clearTriggers();
			}
			this.accessor.dreammanhunt$getStats().remove(uuid);
		}
		CompoundTag data = snapshotData.orElse(entry.liveData());

		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(old.problemPath(), LOGGER)) {
			ValueInput input = TagValueInput.create(reporter, this.server.registryAccess(), data);
			ServerPlayer.SavedPosition saved = input.read(ServerPlayer.SavedPosition.MAP_CODEC).orElse(ServerPlayer.SavedPosition.EMPTY);
			ServerLevel level = saved.dimension().map(this.server::getLevel).orElseGet(this.server::overworld);
			Vec3 position = saved.position().orElseGet(old::position);
			Vec2 rotation = saved.rotation().orElseGet(() -> new Vec2(old.getYRot(), old.getXRot()));
			this.prepareChunks(level, position);

			ServerPlayer player = EntityIds.deferred(() -> new ServerPlayer(this.server, level, old.getGameProfile(), old.clientInformation()));
			player.load(input);
			if (snapshotData.isEmpty()) {
				EntityRuntimeRestorer.applyTag(player, entry.liveEntity());
			}
			player.snapTo(position, rotation.x, rotation.y);
			ServerGamePacketListenerImpl connection = entry.connection();
			player.connection = connection;
			connection.player = player;
			player.setId(old.getId());

			LevelData levelData = level.getLevelData();
			connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), (byte) 0));
			connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
			connection.resetPosition();
			((ServerGamePacketListenerImplInvoker) connection).dreammanhunt$restartClientLoadTimerAfterRespawn();
			connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getRespawnData()));
			connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
			connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
			connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
			connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
			this.playerList.sendActivePlayerEffects(player);
			player.sendPostEffects();
			this.playerList.sendLevelInfo(player, level);
			LevelAccessor weather = (LevelAccessor) level;
			connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, weather.dreammanhunt$getRainLevel()));
			connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, weather.dreammanhunt$getThunderLevel()));
			this.playerList.sendPlayerPermissionLevel(player);
			player.getRecipeBook().sendInitialRecipeBook(player);
			if (runtime != null) {
				PlayerRuntime.applyCooldowns(player, runtime);
				PlayerRuntime.applyMenu(player, runtime);
			}

			level.addRespawnedPlayer(player);
			this.accessor.dreammanhunt$getPlayers().add(player);
			this.accessor.dreammanhunt$getPlayersByUuid().put(uuid, player);
			player.initInventoryMenu();
			player.resetSentInfo();
			player.setHealth(player.getHealth());
			connection.send(new ClientboundSetHealthPacket(player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
			player.loadAndSpawnEnderPearls(input);
			player.loadAndSpawnParentVehicle(input);
			return player;
		}
	}

	private void prepareChunks(ServerLevel level, Vec3 position) {
		ChunkPos chunk = ChunkPos.containing(BlockPos.containing(position));
		CompletableFuture<?> loaded = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN, chunk, PREPARE_CHUNK_RADIUS);
		this.server.managedBlock(loaded::isDone);
		level.waitForEntities(chunk, PREPARE_CHUNK_RADIUS);
	}
}
