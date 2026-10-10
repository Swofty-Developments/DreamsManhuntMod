package net.swofty.dreammanhunt.client.state;

public final class ClientStateManager {
	private static final ClientStateManager INSTANCE = new ClientStateManager();

	private ClientStateManager() {
	}

	public static ClientStateManager get() {
		return INSTANCE;
	}

	public void capturePending() {
	}

	public void onCapture(String stateId) {
	}

	public boolean has(String stateId) {
		return false;
	}

	public void delete(String stateId) {
	}

	public void onRestoreBegin(String stateId) {
	}

	public void onRestoreApply(String stateId, Runnable sendReady) {
		sendReady.run();
	}

	public void onRestoreResume(String stateId) {
	}

	public boolean isRestoring() {
		return false;
	}
}
