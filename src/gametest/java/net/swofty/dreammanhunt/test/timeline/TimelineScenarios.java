package net.swofty.dreammanhunt.test.timeline;

import java.util.ArrayList;
import java.util.List;

public final class TimelineScenarios {
	private TimelineScenarios() {
	}

	public static List<TimelineScenario> all() {
		List<TimelineScenario> list = new ArrayList<>();
		list.add(TimelineScenario.of("idle", 40, env -> {
		}).settle(10));
		list.add(TimelineScenario.of("zombies", 100, env -> {
			env.cmd("time set 18000");
			env.summon("minecraft:zombie", 6, 0, 4, "");
			env.summon("minecraft:zombie", -5, 0, 7, "");
			env.summon("minecraft:skeleton", 2, 0, 12, "");
		}).settle(40));
		return list;
	}
}
