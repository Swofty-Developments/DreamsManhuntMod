package net.swofty.dreammanhunt.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.chat.Component;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.mixin.ui.BlockStatePredictionHandlerAccessor;
import net.swofty.dreammanhunt.client.mixin.ui.ClientLevelPredictionAccessor;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.CutMarker;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.client.ui.ThumbnailManager;
import net.swofty.dreammanhunt.network.SavestatePayloads;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SavestateClientNetwork {
	private static @Nullable List<SavestateInfo> states;
	private static boolean canManage;
	private static int listVersion;
	private static @Nullable String lastRestoreId;
	private static int saveRequests;
	private static int restoresCompleted;
	private static volatile boolean readySuppressedForTesting;
	private static volatile @Nullable Runnable heldReady;
	private static final List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();

	private SavestateClientNetwork() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.StateList.TYPE, (payload, context) -> {
			states = List.copyOf(payload.states());
			canManage = payload.canManage();
			listVersion++;
			if (context.client().gui.screen() instanceof SavestateMenuScreen menu) {
				menu.onStateList();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.Capture.TYPE, (payload, context) -> {
			ClientStateManager.get().onCapture(payload.id());
			ThumbnailManager.requestCapture(payload.id());
			CutMarker.onCaptured();
			OBSERVERS.forEach(observer -> observer.onCapture(payload.id()));
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.Discard.TYPE, (payload, context) -> {
			ClientStateManager.get().delete(payload.id());
			ThumbnailManager.delete(payload.id());
			CutMarker.onDiscarded();
			OBSERVERS.forEach(observer -> observer.onDiscard(payload.id()));
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreBegin.TYPE, (payload, context) -> {
			lastRestoreId = payload.id();
			if (context.client().gui.screen() instanceof SavestateMenuScreen) {
				context.client().gui.setScreen(null);
			}
			dropBlockPredictions(context.client());
			ClientStateManager.get().onRestoreBegin(payload.id());
			CutMarker.onRestoreBegin();
			OBSERVERS.forEach(observer -> observer.onRestoreBegin(payload.id()));
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreApply.TYPE, (payload, context) -> {
			String id = payload.id();
			AtomicBoolean sent = new AtomicBoolean();
			ClientStateManager.get().onRestoreApply(id, () -> {
				if (!sent.compareAndSet(false, true)) {
					DreamManhunt.LOGGER.warn("RestoreReady for {} sent more than once", id);
					return;
				}
				Runnable send = () -> Minecraft.getInstance().execute(() -> {
					if (ClientPlayNetworking.canSend(SavestatePayloads.RestoreReady.TYPE)) {
						ClientPlayNetworking.send(new SavestatePayloads.RestoreReady(id));
						OBSERVERS.forEach(observer -> observer.onReadySent(id));
					}
				});
				if (readySuppressedForTesting) {
					DreamManhunt.LOGGER.warn("Holding RestoreReady for {} (suppressed for testing)", id);
					heldReady = send;
					return;
				}
				send.run();
			});
			OBSERVERS.forEach(observer -> observer.onRestoreApply(id));
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreResume.TYPE, (payload, context) -> {
			ClientStateManager.get().onRestoreResume(payload.id());
			CutMarker.onRestoreResumed();
			restoresCompleted++;
			OBSERVERS.forEach(observer -> observer.onRestoreResume(payload.id()));
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.Notice.TYPE, (payload, context) ->
				showNotice(payload.text()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SavestateClientNetwork::reset));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
	}

	private static void reset() {
		CutMarker.onDisconnect();
		heldReady = null;
		states = null;
		canManage = false;
		listVersion++;
		lastRestoreId = null;
	}

	public static boolean serverHasMod() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(SavestatePayloads.SaveRequest.TYPE);
	}

	public static @Nullable List<SavestateInfo> states() {
		return states;
	}

	public static boolean canManage() {
		return canManage;
	}

	public static int listVersion() {
		return listVersion;
	}

	public static @Nullable String lastRestoreId() {
		return lastRestoreId;
	}

	public static void dropBlockPredictions(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		BlockStatePredictionHandler handler = ((ClientLevelPredictionAccessor) level).dreammanhunt$getBlockStatePredictionHandler();
		((BlockStatePredictionHandlerAccessor) handler).dreammanhunt$getServerVerifiedStates().clear();
		handler.onTeleport();
	}

	public static void addObserver(Observer observer) {
		OBSERVERS.add(observer);
	}

	public static int restoresCompleted() {
		return restoresCompleted;
	}

	public static int saveRequests() {
		return saveRequests;
	}

	public static void setReadySuppressedForTesting(boolean suppressed) {
		readySuppressedForTesting = suppressed;
		if (!suppressed) {
			heldReady = null;
		}
	}

	public static boolean hasHeldReady() {
		return heldReady != null;
	}

	public static void releaseHeldReady() {
		Runnable send = heldReady;
		heldReady = null;
		readySuppressedForTesting = false;
		if (send != null) {
			send.run();
		}
	}

	public static void requestList() {
		if (serverHasMod()) {
			ClientPlayNetworking.send(SavestatePayloads.ListRequest.INSTANCE);
		}
	}

	public static boolean requestSave(String name) {
		if (!serverHasMod()) {
			showNotice(Component.translatable("dreammanhunt.notice.server_missing_mod"));
			return false;
		}
		if (ClientStateManager.get().isRestoring()) {
			return false;
		}
		ClientStateManager.get().capturePending();
		CutMarker.onCaptured();
		saveRequests++;
		OBSERVERS.forEach(Observer::onSaveRequested);
		ClientPlayNetworking.send(new SavestatePayloads.SaveRequest(name));
		return true;
	}

	public static void requestRestore(String id) {
		if (serverHasMod()) {
			ClientPlayNetworking.send(new SavestatePayloads.RestoreRequest(id));
		}
	}

	public static void requestDelete(String id) {
		if (serverHasMod()) {
			ClientPlayNetworking.send(new SavestatePayloads.DeleteRequest(id));
		}
	}

	public static void showNotice(Component text) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof SavestateMenuScreen menu) {
			menu.showNotice(text);
		} else {
			minecraft.gui.hud.setOverlayMessage(text, false);
		}
	}

	public interface Observer {
		default void onSaveRequested() {
		}

		default void onCapture(String id) {
		}

		default void onDiscard(String id) {
		}

		default void onRestoreBegin(String id) {
		}

		default void onRestoreApply(String id) {
		}

		default void onReadySent(String id) {
		}

		default void onRestoreResume(String id) {
		}
	}
}
