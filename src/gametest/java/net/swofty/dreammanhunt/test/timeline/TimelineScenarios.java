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
		list.add(TimelineScenario.of("creeper", 80, env -> {
			env.cmd("time set 18000");
			env.summon("minecraft:creeper", 4, 0, 3, "");
		}).settle(20));
		list.add(TimelineScenario.of("villagers", 120, env -> {
			env.cmd("time set 6000");
			env.block(4, 0, 4, "minecraft:composter");
			env.block(-4, 0, 4, "minecraft:white_bed[part=foot,facing=south]");
			env.block(-4, 0, 5, "minecraft:white_bed[part=head,facing=south]");
			env.summon("minecraft:villager", 3, 0, 6, "{VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}");
			env.summon("minecraft:villager", -3, 0, 7, "");
		}).settle(40));
		list.add(TimelineScenario.of("wolves", 80, env -> {
			env.cmd("time set 6000");
			env.summon("minecraft:wolf", 3, 0, 3, "");
			env.summon("minecraft:sheep", -4, 0, 6, "");
			env.summon("minecraft:wolf", -3, 0, 4, "{AngerTime:400}");
		}).settle(30));
		list.add(TimelineScenario.of("endermen", 80, env -> {
			env.cmd("time set 18000");
			env.summon("minecraft:enderman", 5, 0, 8, "");
		}).settle(30));
		list.add(TimelineScenario.of("piglins", 80, env -> {
			env.summon("minecraft:piglin", 3, 0, 4, "{IsImmuneToZombification:1b}");
			env.summon("minecraft:item", 2, 1, 3, "{Item:{id:\"minecraft:gold_ingot\",count:1}}");
		}).settle(30));
		list.add(TimelineScenario.of("bees", 80, env -> {
			env.cmd("time set 6000");
			env.block(4, 0, 6, "minecraft:poppy");
			env.summon("minecraft:bee", 2, 2, 4, "");
			env.summon("minecraft:bee", -2, 2, 5, "");
		}).settle(30));
		return list;
	}
}
