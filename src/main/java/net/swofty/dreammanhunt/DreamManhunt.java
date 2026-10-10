package net.swofty.dreammanhunt;

import net.fabricmc.api.ModInitializer;
import net.swofty.dreammanhunt.network.SavestatePayloads;
import net.swofty.dreammanhunt.server.coordinator.SavestateConfig;
import net.swofty.dreammanhunt.server.coordinator.SavestateServerHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DreamManhunt implements ModInitializer {
	public static final String MOD_ID = "dreammanhunt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SavestateConfig.load();
		SavestatePayloads.register();
		SavestateServerHooks.init();
	}
}
