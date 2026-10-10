package net.swofty.dreammanhunt.test.timeline;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public final class TimelineRecorder {
	private static boolean registered;
	private static volatile Recording active;

	public static final class Recording {
		final TimelineScenario scenario;
		final TimelineEnv env;
		final TimelineScenario.Area area;
		final long baseGameTime;
		final Map<Integer, Fingerprint> server = new ConcurrentSkipListMap<>();
		final Map<Integer, Fingerprint> client = new ConcurrentSkipListMap<>();
		private long lastServerGameTime;
		private long lastClientGameTime;

		Recording(TimelineScenario scenario, TimelineEnv env, TimelineScenario.Area area, long baseGameTime) {
			this.scenario = scenario;
			this.env = env;
			this.area = area;
			this.baseGameTime = baseGameTime;
			this.lastServerGameTime = baseGameTime;
			this.lastClientGameTime = baseGameTime;
		}

		boolean serverDone() {
			return this.server.size() >= this.scenario.ticks() + 1;
		}

		boolean clientDone() {
			return this.client.size() >= this.scenario.ticks();
		}
	}

	private TimelineRecorder() {
	}

	static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(TimelineRecorder::onServerTick);
		ClientTickEvents.END_CLIENT_TICK.register(TimelineRecorder::onClientTick);
	}

	static void start(Recording recording) {
		active = recording;
	}

	static void stop() {
		active = null;
	}

	private static void onServerTick(MinecraftServer server) {
		Recording recording = active;
		if (recording == null || recording.serverDone()) {
			return;
		}
		long gameTime = server.overworld().getGameTime();
		if (gameTime == recording.lastServerGameTime) {
			return;
		}
		recording.lastServerGameTime = gameTime;
		int tick = (int) (gameTime - recording.baseGameTime);
		if (tick <= 0) {
			return;
		}
		recording.server.put(tick, Probes.captureServer(server, recording.area));
		recording.scenario.inputs().apply(server, recording.env, tick);
	}

	private static void onClientTick(Minecraft minecraft) {
		Recording recording = active;
		if (recording == null || recording.clientDone() || minecraft.level == null) {
			return;
		}
		long gameTime = minecraft.level.getGameTime();
		if (gameTime == recording.lastClientGameTime) {
			return;
		}
		recording.lastClientGameTime = gameTime;
		int tick = (int) (gameTime - recording.baseGameTime);
		if (tick <= 0 || tick > recording.scenario.ticks()) {
			return;
		}
		recording.client.put(tick, Probes.captureClient(minecraft, recording.area));
	}
}
