package net.swofty.dreammanhunt.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.client.ui.ThumbnailManager;
import net.swofty.dreammanhunt.network.SavestatePayloads;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SavestateClientNetwork {
	private static @Nullable List<SavestateInfo> states;
	private static boolean canManage;
	private static int listVersion;
	private static @Nullable String lastRestoreId;
	private static int saveRequests;
	private static int restoresCompleted;
	private static volatile boolean readySuppressedForTesting;

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
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.Discard.TYPE, (payload, context) -> {
			ClientStateManager.get().delete(payload.id());
			ThumbnailManager.delete(payload.id());
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreBegin.TYPE, (payload, context) -> {
			lastRestoreId = payload.id();
			if (context.client().gui.screen() instanceof SavestateMenuScreen) {
				context.client().gui.setScreen(null);
			}
			ClientStateManager.get().onRestoreBegin(payload.id());
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreApply.TYPE, (payload, context) -> {
			String id = payload.id();
			AtomicBoolean sent = new AtomicBoolean();
			ClientStateManager.get().onRestoreApply(id, () -> {
				if (!sent.compareAndSet(false, true)) {
					DreamManhunt.LOGGER.warn("RestoreReady for {} sent more than once", id);
					return;
				}
				if (readySuppressedForTesting) {
					DreamManhunt.LOGGER.warn("Not sending RestoreReady for {} (suppressed for testing)", id);
					return;
				}
				Minecraft.getInstance().execute(() -> {
					if (ClientPlayNetworking.canSend(SavestatePayloads.RestoreReady.TYPE)) {
						ClientPlayNetworking.send(new SavestatePayloads.RestoreReady(id));
					}
				});
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreResume.TYPE, (payload, context) -> {
			ClientStateManager.get().onRestoreResume(payload.id());
			restoresCompleted++;
		});
		ClientPlayNetworking.registerGlobalReceiver(SavestatePayloads.Notice.TYPE, (payload, context) ->
				showNotice(payload.text()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SavestateClientNetwork::reset));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
	}

	private static void reset() {
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

	public static int restoresCompleted() {
		return restoresCompleted;
	}

	public static int saveRequests() {
		return saveRequests;
	}

	public static void setReadySuppressedForTesting(boolean suppressed) {
		readySuppressedForTesting = suppressed;
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
		saveRequests++;
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
}
