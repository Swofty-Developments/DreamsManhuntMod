package net.swofty.dreammanhunt.client.state;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ClientStateManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ClientStateManager INSTANCE = new ClientStateManager();
	private static final long READY_TIMEOUT_MS = 15_000L;
	private static final long RESUME_TIMEOUT_MS = 30_000L;
	private static final long READY_STABLE_MS = 250L;
	private static final int READY_STABLE_FRAMES = 5;

	private final Map<String, CompoundTag> cache = new ConcurrentHashMap<>();
	private @Nullable CompoundTag pending;
	private long pendingAtMs;
	private boolean captureRequestedInTick;
	private boolean inClientTick;

	private Phase phase = Phase.IDLE;
	private @Nullable String restoringId;
	private @Nullable CompoundTag restoring;
	private @Nullable Runnable sendReady;
	private long phaseStartedMs;
	private long readySinceMs = -1L;
	private int readyFrames;
	private boolean applied;

	private ClientStateManager() {
	}

	public static ClientStateManager get() {
		return INSTANCE;
	}

	public void capturePending() {
		this.pendingAtMs = Util.getMillis();
		if (this.inClientTick) {
			this.captureRequestedInTick = true;
			this.pending = null;
			return;
		}
		this.pending = this.captureSafely();
	}

	public void onCapture(String stateId) {
		if (this.captureRequestedInTick) {
			this.finishDeferredCapture();
		}
		CompoundTag tag = this.captureSafely();
		this.pending = null;
		if (tag == null) {
			return;
		}
		this.cache.put(stateId, tag);
		ClientStateStore.write(stateId, tag);
	}

	public boolean has(String stateId) {
		return this.cache.containsKey(stateId) || ClientStateStore.exists(stateId);
	}

	public void delete(String stateId) {
		this.cache.remove(stateId);
		ClientStateStore.delete(stateId);
	}

	public void onRestoreBegin(String stateId) {
		if (this.phase != Phase.IDLE) {
			LOGGER.warn("Restore {} began while {} was still in progress", stateId, this.restoringId);
			this.finishSendReady();
		}
		this.restoringId = stateId;
		this.restoring = this.load(stateId);
		this.applied = false;
		this.sendReady = null;
		this.phase = Phase.FROZEN;
		this.phaseStartedMs = Util.getMillis();
		VisualClock.freeze();
		Minecraft minecraft = Minecraft.getInstance();
		if (this.restoring != null) {
			try {
				ClientSnapshot.applyEarly(minecraft, this.restoring);
				if (minecraft.level != null && minecraft.player != null) {
					ClientSnapshot.applyBarrierTiming(minecraft, this.restoring);
				}
			} catch (Throwable t) {
				LOGGER.error("Failed to restore settings for {}", stateId, t);
			}
		}
	}

	public void onRestoreApply(String stateId, Runnable sendReady) {
		if (this.phase == Phase.IDLE || !stateId.equals(this.restoringId)) {
			this.onRestoreBegin(stateId);
		}
		this.finishSendReady();
		this.sendReady = sendReady;
		this.phase = Phase.WAITING_FOR_VIEW;
		this.phaseStartedMs = Util.getMillis();
		this.readySinceMs = -1L;
		this.readyFrames = 0;
	}

	public void onRestoreResume(String stateId) {
		if (this.phase == Phase.IDLE) {
			return;
		}
		this.resumeNow(Minecraft.getInstance());
	}

	public boolean isRestoring() {
		return this.phase != Phase.IDLE;
	}

	public float barrierPartialTick() {
		if (this.phase == Phase.IDLE || this.restoring == null) {
			return Float.NaN;
		}
		return this.restoring.getCompoundOrEmpty("timing").getFloatOr("deltaTickResidual", Float.NaN);
	}

	public @Nullable CompoundTag snapshot(String stateId) {
		return this.load(stateId);
	}

	public void onClientTickStart() {
		this.inClientTick = true;
	}

	public void onClientTickEnd() {
		this.inClientTick = false;
		if (this.captureRequestedInTick) {
			this.finishDeferredCapture();
		}
	}

	public void onFrame(Minecraft minecraft) {
		if (this.phase == Phase.IDLE) {
			return;
		}
		if (this.phase == Phase.WAITING_FOR_VIEW) {
			this.pollReady(minecraft);
			return;
		}
		long waited = Util.getMillis() - this.phaseStartedMs;
		if (waited > RESUME_TIMEOUT_MS) {
			LOGGER.warn("No resume for restore {} after {} ms, resuming locally", this.restoringId, waited);
			this.resumeNow(minecraft);
		}
	}

	public void onDisconnect() {
		if (this.phase != Phase.IDLE) {
			this.finishSendReady();
			this.reset();
			VisualClock.unfreeze();
		}
		this.pending = null;
		this.captureRequestedInTick = false;
		this.inClientTick = false;
	}

	private void finishDeferredCapture() {
		this.captureRequestedInTick = false;
		this.pending = this.captureSafely();
	}

	private void pollReady(Minecraft minecraft) {
		long now = Util.getMillis();
		boolean timedOut = now - this.phaseStartedMs > READY_TIMEOUT_MS;
		if (ViewReadiness.isReady(minecraft)) {
			if (this.readySinceMs < 0L) {
				this.readySinceMs = now;
			}
			this.readyFrames++;
		} else {
			this.readySinceMs = -1L;
			this.readyFrames = 0;
		}
		boolean stable = this.readySinceMs >= 0L && now - this.readySinceMs >= READY_STABLE_MS && this.readyFrames >= READY_STABLE_FRAMES;
		if (!stable && !timedOut) {
			return;
		}
		if (!stable) {
			LOGGER.warn("View for restore {} not ready after {} ms, continuing anyway", this.restoringId, now - this.phaseStartedMs);
		}
		this.applyNow(minecraft);
		this.phase = Phase.APPLIED;
		this.phaseStartedMs = now;
		this.finishSendReady();
	}

	private void applyNow(Minecraft minecraft) {
		if (this.applied) {
			return;
		}
		this.applied = true;
		if (this.restoring == null || minecraft.level == null || minecraft.player == null) {
			return;
		}
		try {
			ClientSnapshot.apply(minecraft, this.restoring);
			ClientSnapshot.applyBarrierTiming(minecraft, this.restoring);
		} catch (Throwable t) {
			LOGGER.error("Failed to apply client state {}", this.restoringId, t);
		}
	}

	private void resumeNow(Minecraft minecraft) {
		this.applyNow(minecraft);
		this.finishSendReady();
		boolean resumed = false;
		if (this.restoring != null && minecraft.level != null && minecraft.player != null) {
			try {
				ClientSnapshot.resume(minecraft, this.restoring);
				resumed = true;
			} catch (Throwable t) {
				LOGGER.error("Failed to resume client state {}", this.restoringId, t);
			}
		}
		if (!resumed) {
			VisualClock.unfreeze();
		}
		try {
			PhysicalKeys.sync(minecraft);
		} catch (Throwable t) {
			LOGGER.warn("Could not resync held keys after restore {}", this.restoringId, t);
		}
		this.reset();
	}

	private void finishSendReady() {
		Runnable callback = this.sendReady;
		this.sendReady = null;
		if (callback != null) {
			try {
				callback.run();
			} catch (Throwable t) {
				LOGGER.error("sendReady failed for restore {}", this.restoringId, t);
			}
		}
	}

	private void reset() {
		this.phase = Phase.IDLE;
		this.restoringId = null;
		this.restoring = null;
		this.sendReady = null;
		this.applied = false;
		this.readySinceMs = -1L;
		this.readyFrames = 0;
	}

	private @Nullable CompoundTag load(String stateId) {
		CompoundTag cached = this.cache.get(stateId);
		if (cached != null) {
			return cached;
		}
		CompoundTag read = ClientStateStore.read(stateId);
		if (read != null) {
			this.cache.put(stateId, read);
		}
		return read;
	}

	private @Nullable CompoundTag captureSafely() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return null;
		}
		try {
			long started = Util.getNanos();
			CompoundTag tag = ClientSnapshot.capture(minecraft);
			LOGGER.info("Captured client state at hud tick {} in {} us", tag.getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1), (Util.getNanos() - started) / 1000L);
			return tag;
		} catch (Throwable t) {
			LOGGER.error("Failed to capture client state", t);
			return null;
		}
	}

	private enum Phase {
		IDLE,
		FROZEN,
		WAITING_FOR_VIEW,
		APPLIED
	}
}
