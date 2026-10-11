package net.swofty.dreammanhunt.server.coordinator;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.TickRateManager;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.mixin.common.PlayerChunkSenderAccessor;
import net.swofty.dreammanhunt.network.SavestatePayloads;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SavestateCoordinator {
	private static final Map<MinecraftServer, SavestateCoordinator> INSTANCES = new ConcurrentHashMap<>();
	private static final Set<TickRateManager> FROZEN = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final MinecraftServer server;
	private boolean busy;
	private @Nullable Barrier barrier;

	private SavestateCoordinator(MinecraftServer server) {
		this.server = server;
	}

	public static SavestateCoordinator get(MinecraftServer server) {
		return INSTANCES.computeIfAbsent(server, SavestateCoordinator::new);
	}

	public static @Nullable SavestateCoordinator peek(MinecraftServer server) {
		return INSTANCES.get(server);
	}

	public static void remove(MinecraftServer server) {
		SavestateCoordinator coordinator = INSTANCES.remove(server);
		if (coordinator != null && coordinator.barrier != null) {
			FROZEN.remove(server.tickRateManager());
			coordinator.barrier = null;
		}
	}

	public static boolean isFrozen(TickRateManager manager) {
		return !FROZEN.isEmpty() && FROZEN.contains(manager);
	}

	public static boolean isPlayerTickBlocked(MinecraftServer server) {
		return !FROZEN.isEmpty() && FROZEN.contains(server.tickRateManager());
	}

	public static Component tr(String key, Object... args) {
		return Component.translatableWithFallback(key, Language.getInstance().getOrDefault(key), args);
	}

	public boolean isBusy() {
		return this.busy;
	}

	public @Nullable String restoringId() {
		return this.barrier == null ? null : this.barrier.id;
	}

	public Set<UUID> pendingReady() {
		return this.barrier == null ? Set.of() : Set.copyOf(this.barrier.pending);
	}

	public SavestateEngine engine() {
		return SavestateEngine.get(this.server);
	}

	public static boolean hasMod(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, SavestatePayloads.Capture.TYPE);
	}

	private void sendToModded(CustomPacketPayload payload) {
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			if (hasMod(player)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public List<SavestateInfo> listStates() {
		try {
			return this.engine().list();
		} catch (RuntimeException e) {
			DreamManhunt.LOGGER.error("Could not list savestates", e);
			return List.of();
		}
	}

	public void sendList(ServerPlayer player) {
		if (hasMod(player)) {
			ServerPlayNetworking.send(player, new SavestatePayloads.StateList(this.listStates(), SavestatePermissions.canManage(player)));
		}
	}

	public void broadcastList() {
		List<SavestateInfo> states = this.listStates();
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			if (hasMod(player)) {
				ServerPlayNetworking.send(player, new SavestatePayloads.StateList(states, SavestatePermissions.canManage(player)));
			}
		}
	}

	private boolean checkAllowed(Requester requester) {
		if (!requester.canManage()) {
			requester.failure(tr("dreammanhunt.notice.no_permission"));
			return false;
		}
		if (this.busy) {
			requester.failure(tr("dreammanhunt.notice.busy"));
			return false;
		}
		return true;
	}

	private static String cleanName(String raw) {
		String name = raw == null ? "" : raw.strip().replaceAll("\\p{Cntrl}", "");
		if (name.length() > SavestatePayloads.MAX_NAME_LENGTH) {
			name = name.substring(0, SavestatePayloads.MAX_NAME_LENGTH);
		}
		return name;
	}

	private String defaultName() {
		return Language.getInstance().getOrDefault("dreammanhunt.default_name", "Savestate %s").formatted(this.listStates().size() + 1);
	}

	public Optional<SavestateInfo> save(Requester requester, String rawName) {
		if (!this.checkAllowed(requester)) {
			return Optional.empty();
		}
		String name = cleanName(rawName);
		if (name.isEmpty()) {
			name = this.defaultName();
		}
		String id = UUID.randomUUID().toString();
		this.busy = true;
		try {
			this.sendToModded(new SavestatePayloads.Capture(id));
			Map<UUID, CompoundTag> playerSync = PlayerSync.captureAll(this.server);
			long start = System.nanoTime();
			SavestateInfo info = this.engine().save(id, name, requester.player());
			PlayerSync.write(this.server, id, playerSync);
			DreamManhunt.LOGGER.info("Saved savestate '{}' ({}) in {} ms", info.name(), id, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
			if (requester.isCommand()) {
				requester.success(tr("dreammanhunt.notice.saved", info.name()));
			}
			this.broadcastList();
			return Optional.of(info);
		} catch (Exception e) {
			DreamManhunt.LOGGER.error("Savestate save {} failed", id, e);
			this.sendToModded(new SavestatePayloads.Discard(id));
			requester.failure(tr("dreammanhunt.notice.save_failed", String.valueOf(e.getMessage())));
			return Optional.empty();
		} finally {
			this.busy = false;
		}
	}

	public boolean delete(Requester requester, String idOrName) {
		if (!this.checkAllowed(requester)) {
			return false;
		}
		Optional<SavestateInfo> info = this.find(idOrName);
		if (info.isEmpty()) {
			requester.failure(tr("dreammanhunt.notice.not_found", idOrName));
			return false;
		}
		boolean deleted;
		try {
			deleted = this.engine().delete(info.get().id());
		} catch (RuntimeException e) {
			DreamManhunt.LOGGER.error("Savestate delete {} failed", info.get().id(), e);
			deleted = false;
		}
		if (!deleted) {
			requester.failure(tr("dreammanhunt.notice.delete_failed", info.get().name()));
			return false;
		}
		this.sendToModded(new SavestatePayloads.Discard(info.get().id()));
		if (requester.isCommand()) {
			requester.success(tr("dreammanhunt.notice.deleted", info.get().name()));
		}
		this.broadcastList();
		return true;
	}

	public Optional<SavestateInfo> find(String idOrName) {
		try {
			return this.engine().find(idOrName);
		} catch (RuntimeException e) {
			DreamManhunt.LOGGER.error("Could not look up savestate {}", idOrName, e);
			return Optional.empty();
		}
	}

	public boolean restore(Requester requester, String idOrName) {
		if (!this.checkAllowed(requester)) {
			return false;
		}
		Optional<SavestateInfo> found = this.find(idOrName);
		if (found.isEmpty()) {
			requester.failure(tr("dreammanhunt.notice.not_found", idOrName));
			return false;
		}
		SavestateInfo info = found.get();
		String id = info.id();
		this.busy = true;

		Set<UUID> participants = new LinkedHashSet<>();
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			if (hasMod(player)) {
				participants.add(player.getUUID());
				ServerPlayNetworking.send(player, new SavestatePayloads.RestoreBegin(id));
			}
		}

		boolean failed = false;
		Map<UUID, CompoundTag> savedSync = PlayerSync.read(this.server, id);
		Map<UUID, CompoundTag> liveSync = PlayerSync.captureAll(this.server);
		Map<UUID, CompoundTag> appliedSync = new HashMap<>();
		long start = System.nanoTime();
		try {
			this.engine().restore(id);
			for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
				CompoundTag sync = savedSync.getOrDefault(player.getUUID(), liveSync.get(player.getUUID()));
				if (sync != null) {
					PlayerSync.apply(player, sync);
					PlayerSync.reopenContainer(player, sync);
					PlayerSync.respawnFishingHook(player, sync);
					appliedSync.put(player.getUUID(), sync);
				}
			}
			DreamManhunt.LOGGER.info("Restored savestate '{}' ({}) in {} ms", info.name(), id, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		} catch (Exception e) {
			failed = true;
			DreamManhunt.LOGGER.error("Savestate restore {} failed", id, e);
			requester.failure(tr("dreammanhunt.notice.restore_failed", String.valueOf(e.getMessage())));
		}

		DreamManhunt.LOGGER.info("Restore {} barrier: waiting for {}", id, this.names(participants));
		ServerTickRateManager tickRate = this.server.tickRateManager();
		Barrier barrier = new Barrier(id, info.name(), requester, participants, tickRate.isFrozen(), tickRate.frozenTicksToRun(),
				System.nanoTime() + TimeUnit.SECONDS.toNanos(SavestateConfig.get().readyTimeoutSeconds()), failed);
		barrier.reopen.putAll(appliedSync);
		this.barrier = barrier;
		tickRate.setFrozenTicksToRun(0);
		tickRate.setFrozen(true);
		FROZEN.add(tickRate);

		for (UUID uuid : participants) {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			if (player != null && hasMod(player)) {
				ServerPlayNetworking.send(player, new SavestatePayloads.RestoreApply(id));
			} else {
				barrier.pending.remove(uuid);
			}
		}
		this.checkComplete();
		return !failed;
	}

	public void onReady(ServerPlayer player, String id) {
		Barrier barrier = this.barrier;
		if (barrier == null || !barrier.id.equals(id)) {
			return;
		}
		if (barrier.pending.remove(player.getUUID())) {
			DreamManhunt.LOGGER.info("Restore {} barrier: {} ready after {} ms, {} pending", id, player.getPlainTextName(),
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - barrier.startNanos), barrier.pending.size());
		}
		this.checkComplete();
	}

	public void onLeave(UUID uuid) {
		Barrier barrier = this.barrier;
		if (barrier == null) {
			return;
		}
		if (barrier.pending.remove(uuid)) {
			DreamManhunt.LOGGER.info("Restore {} barrier: participant {} left, {} pending", barrier.id, uuid, barrier.pending.size());
		}
		barrier.participants.remove(uuid);
		this.checkComplete();
	}

	public void beforeTick() {
		if (this.barrier != null) {
			this.checkComplete();
		}
	}

	public void afterTick() {
		if (this.barrier != null) {
			this.syncEntityTracking();
		}
	}

	private boolean chunksDelivered(Barrier barrier) {
		for (UUID uuid : barrier.participants) {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				continue;
			}
			PlayerChunkSenderAccessor sender = (PlayerChunkSenderAccessor) player.connection.chunkSender;
			if (!sender.dreammanhunt$pendingChunks().isEmpty() || sender.dreammanhunt$unacknowledgedBatches() > 0) {
				return false;
			}
		}
		return true;
	}

	private void syncEntityTracking() {
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			player.level().getChunkSource().move(player);
		}
	}

	public void poll() {
		Barrier barrier = this.barrier;
		if (barrier != null && System.nanoTime() - barrier.deadlineNanos > 0) {
			DreamManhunt.LOGGER.warn("Savestate restore {} timed out waiting for {}", barrier.id, barrier.pending);
			this.finish(barrier, true);
		}
	}

	private void checkComplete() {
		Barrier barrier = this.barrier;
		if (barrier == null || !barrier.pending.isEmpty()) {
			return;
		}
		if (!this.chunksDelivered(barrier) || !net.swofty.dreammanhunt.server.sync.ChunkReadiness.ready(this.server)) {
			if (!barrier.waitingForChunks) {
				barrier.waitingForChunks = true;
				DreamManhunt.LOGGER.info("Restore {} barrier: everyone is ready, waiting for chunk delivery", barrier.id);
			}
			return;
		}
		this.finish(barrier, false);
	}

	private void finish(Barrier barrier, boolean timedOut) {
		if (this.barrier != barrier) {
			return;
		}
		this.barrier = null;
		this.syncEntityTracking();
		barrier.reopen.forEach((uuid, sync) -> {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				PlayerSync.applyCamera(player, sync);
			}
		});
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			PlayerSync.linkFishingHook(player);
		}
		ServerTickRateManager tickRate = this.server.tickRateManager();
		FROZEN.remove(tickRate);
		tickRate.setFrozen(barrier.restoredFrozen);
		if (barrier.restoredFrozen && barrier.restoredStepTicks > 0) {
			tickRate.stepGameIfPaused(barrier.restoredStepTicks);
		}
		for (UUID uuid : barrier.participants) {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			if (player != null && hasMod(player)) {
				ServerPlayNetworking.send(player, new SavestatePayloads.RestoreResume(barrier.id));
			}
		}
		barrier.reopen.forEach((uuid, sync) -> {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				PlayerSync.resendContainer(player);
			}
		});
		this.busy = false;
		DreamManhunt.LOGGER.info("Restore {} barrier: resumed after {} ms (timed out: {})", barrier.id,
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - barrier.startNanos), timedOut);
		if (timedOut) {
			barrier.requester.failure(tr("dreammanhunt.notice.restore_timeout", barrier.pending.size()));
		} else if (!barrier.failed && barrier.requester.isCommand()) {
			barrier.requester.success(tr("dreammanhunt.notice.restored", barrier.name));
		}
	}

	private List<String> names(Set<UUID> uuids) {
		List<String> names = new ArrayList<>();
		for (UUID uuid : uuids) {
			ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
			names.add(player == null ? uuid.toString() : player.getPlainTextName());
		}
		return names;
	}

	private static final class Barrier {
		final String id;
		final String name;
		final Requester requester;
		final Set<UUID> participants;
		final Set<UUID> pending;
		final boolean restoredFrozen;
		final int restoredStepTicks;
		final long deadlineNanos;
		final boolean failed;
		final long startNanos = System.nanoTime();
		final Map<UUID, CompoundTag> reopen = new HashMap<>();
		boolean waitingForChunks;

		Barrier(String id, String name, Requester requester, Set<UUID> participants, boolean restoredFrozen, int restoredStepTicks,
				long deadlineNanos, boolean failed) {
			this.id = id;
			this.name = name;
			this.requester = requester;
			this.participants = new LinkedHashSet<>(participants);
			this.pending = new LinkedHashSet<>(participants);
			this.restoredFrozen = restoredFrozen;
			this.restoredStepTicks = restoredStepTicks;
			this.deadlineNanos = deadlineNanos;
			this.failed = failed;
		}
	}
}
