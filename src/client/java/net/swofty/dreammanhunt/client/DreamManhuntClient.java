package net.swofty.dreammanhunt.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;

public final class DreamManhuntClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SavestateKeys.register();
		if (!Boolean.getBoolean("dreammanhunt.disableNetworking")) {
			SavestateClientNetwork.init();
		}
		ClientTickEvents.END_CLIENT_TICK.register(SavestateKeys::tick);
	}
}
