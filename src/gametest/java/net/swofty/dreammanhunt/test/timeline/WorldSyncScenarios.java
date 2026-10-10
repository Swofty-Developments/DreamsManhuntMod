package net.swofty.dreammanhunt.test.timeline;

import java.util.List;

public final class WorldSyncScenarios {
	private WorldSyncScenarios() {
	}

	public static List<TimelineScenario> all() {
		return List.of(
			TimelineScenario.of("idle_long", 220, env -> {
			}).settle(10),
			TimelineScenario.of("spawning", 220, env -> {
				env.cmd("difficulty normal");
				env.cmd("gamerule spawn_mobs true");
				env.cmd("gamerule spawn_monsters true");
				env.cmd("time set 18000");
			}).settle(10)
		);
	}
}
