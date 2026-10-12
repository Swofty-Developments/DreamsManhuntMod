package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class DragonState {
	private static final int ARENA_RADIUS = 9;

	private DragonState() {
	}

	static CompoundTag capture(EnderDragon dragon) {
		CompoundTag tag = new CompoundTag();
		DragonPhaseInstance phase = dragon.getPhaseManager().getCurrentPhase();
		tag.putInt("phase", phase.getPhase().getId());
		tag.put("phase_state", BrainState.walk(phase));
		DragonFlightHistory history = dragon.flightHistory;
		Object samples = get(history, "samples");
		ListTag list = new ListTag();
		if (samples != null) {
			for (int i = 0; i < Array.getLength(samples); i++) {
				CompoundTag entry = new CompoundTag();
				if (Array.get(samples, i) instanceof DragonFlightHistory.Sample sample) {
					entry.putDouble("y", sample.y());
					entry.putFloat("yRot", sample.yRot());
				}
				list.add(entry);
			}
		}
		tag.put("history", list);
		tag.putInt("history_head", (Integer) get(history, "head"));
		return tag;
	}

	static void apply(EnderDragon dragon, CompoundTag tag, ServerLevel level) {
		EnderDragonPhaseManager manager = dragon.getPhaseManager();
		tag.getInt("phase").ifPresent(id -> {
			DragonPhaseInstance phase = manager.getPhase(EnderDragonPhase.getById(id));
			set(manager, "currentPhase", phase);
			BrainState.apply(phase, tag.getCompoundOrEmpty("phase_state"), level);
		});
		Object samples = get(dragon.flightHistory, "samples");
		ListTag list = tag.getListOrEmpty("history");
		if (samples != null && list.size() == Array.getLength(samples)) {
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompoundOrEmpty(i);
				Array.set(samples, i, entry.contains("y") ? new DragonFlightHistory.Sample(entry.getDoubleOr("y", 0.0), entry.getFloatOr("yRot", 0.0F)) : null);
			}
		}
		tag.getInt("history_head").ifPresent(head -> set(dragon.flightHistory, "head", head));
	}

	static CompoundTag captureFight(ServerLevel level) {
		EnderDragonFight fight = level.getDragonFight();
		CompoundTag tag = new CompoundTag();
		if (fight == null) {
			return tag;
		}
		tag.put("fields", AiState.captureFields(fight));
		ServerBossEvent event = (ServerBossEvent) get(fight, "dragonEvent");
		if (event != null) {
			tag.put("event", AiState.captureFields(event));
			ListTag players = new ListTag();
			for (ServerPlayer player : event.getPlayers()) {
				CompoundTag ref = new CompoundTag();
				ref.store("u", UUIDUtil.CODEC, player.getUUID());
				players.add(ref);
			}
			tag.put("players", players);
		}
		return tag;
	}

	static void applyFight(ServerLevel level, CompoundTag tag) {
		EnderDragonFight fight = level.getDragonFight();
		if (fight == null || tag.isEmpty()) {
			return;
		}
		AiState.applyFields(fight, tag.getCompoundOrEmpty("fields"), level);
		ServerBossEvent event = (ServerBossEvent) get(fight, "dragonEvent");
		if (event != null) {
			AiState.applyFields(event, tag.getCompoundOrEmpty("event"), level);
		}
	}

	static void attachFightPlayers(MinecraftServer server, ServerLevel level, CompoundTag tag) {
		EnderDragonFight fight = level.getDragonFight();
		ServerBossEvent event = fight == null ? null : (ServerBossEvent) get(fight, "dragonEvent");
		if (event == null || tag.isEmpty()) {
			return;
		}
		for (Tag element : tag.getListOrEmpty("players")) {
			if (element instanceof CompoundTag ref) {
				UUID uuid = ref.read("u", UUIDUtil.CODEC).orElse(null);
				ServerPlayer player = uuid == null ? null : server.getPlayerList().getPlayer(uuid);
				if (player != null && player.level() == level) {
					event.addPlayer(player);
				}
			}
		}
		if (!event.getPlayers().isEmpty()) {
			ChunkPos center = new ChunkPos(0, 0);
			CompletableFuture<?> loaded = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.DRAGON, center, ARENA_RADIUS);
			server.managedBlock(loaded::isDone);
			level.waitForEntities(center, ARENA_RADIUS);
		}
	}

	private static Object get(Object owner, String name) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(owner);
			} catch (NoSuchFieldException e) {
				continue;
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}
		return null;
	}

	private static void set(Object owner, String name, Object value) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(owner, value);
				return;
			} catch (NoSuchFieldException e) {
				continue;
			} catch (ReflectiveOperationException e) {
				return;
			}
		}
	}
}
