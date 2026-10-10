package net.swofty.dreammanhunt.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.ui.ClientConfig;
import net.swofty.dreammanhunt.client.ui.CutMarker;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;

public final class DreamManhuntClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		SavestateKeys.register();
		if (!Boolean.getBoolean("dreammanhunt.disableNetworking")) {
			SavestateClientNetwork.init();
		}
		ClientTickEvents.END_CLIENT_TICK.register(SavestateKeys::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> CutMarker.tick());
	}
}
