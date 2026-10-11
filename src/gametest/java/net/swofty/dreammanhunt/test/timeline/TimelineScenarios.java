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
		list.add(TimelineScenario.of("villager_panic", 80, env -> {
			env.cmd("time set 6000");
			env.summon("minecraft:villager", 3, 0, 5, "");
			env.summon("minecraft:zombie", 8, 0, 9, "{NoAI:0b}");
		}).settle(40));
		list.add(TimelineScenario.of("villager_gossip", 160, env -> {
			env.cmd("time set 6000");
			env.block(0, 0, 6, "minecraft:bell");
			env.summon("minecraft:villager", 2, 0, 5, "{Gossips:[{Type:\"major_positive\",Value:20,Target:[I;1,2,3,4]}]}");
			env.summon("minecraft:villager", -2, 0, 5, "");
		}).settle(40));
		list.add(TimelineScenario.of("piglin_barter", 120, env -> {
			env.cmd("item replace entity @p weapon.mainhand with minecraft:gold_ingot 8");
			env.summon("minecraft:piglin", 2, 0, 3, "{IsImmuneToZombification:1b}");
			env.summon("minecraft:hoglin", -5, 0, 7, "{IsImmuneToZombification:1b}");
			env.summon("minecraft:item", 1, 1, 2, "{Item:{id:\"minecraft:gold_ingot\",count:1}}");
		}).settle(30));
		list.add(TimelineScenario.of("hoglins", 80, env -> {
			env.summon("minecraft:hoglin", 4, 0, 5, "{IsImmuneToZombification:1b}");
		}).settle(30));
		list.add(TimelineScenario.of("axolotls", 80, env -> {
			env.fill(-6, 0, 2, 6, 3, 10, "minecraft:water");
			env.summon("minecraft:axolotl", 0, 1, 5, "");
			env.summon("minecraft:tropical_fish", 3, 1, 7, "");
		}).settle(30));
		list.add(TimelineScenario.of("frogs", 80, env -> {
			env.summon("minecraft:frog", 2, 0, 4, "");
			env.summon("minecraft:slime", -3, 0, 6, "{Size:0}");
		}).settle(30));
		list.add(TimelineScenario.of("goats", 100, env -> {
			env.summon("minecraft:goat", 3, 0, 5, "");
		}).settle(40));
		list.add(TimelineScenario.of("allays", 80, env -> {
			env.summon("minecraft:allay", 2, 1, 3, "");
			env.summon("minecraft:item", -4, 1, 6, "{Item:{id:\"minecraft:diamond\",count:1}}");
		}).settle(30));
		list.add(TimelineScenario.of("warden", 80, env -> {
			env.cmd("time set 18000");
			env.summon("minecraft:warden", 6, 0, 10, "");
		}).settle(40));
		list.add(TimelineScenario.of("sniffers", 100, env -> {
			env.fill(-8, -1, 2, 8, -1, 12, "minecraft:moss_block");
			env.summon("minecraft:sniffer", 2, 0, 6, "");
		}).settle(40));
		list.add(TimelineScenario.of("armadillos", 80, env -> {
			env.summon("minecraft:armadillo", 3, 0, 4, "");
		}).settle(30));
		list.add(TimelineScenario.of("raid", 100, env -> {
			env.cmd("time set 6000");
			for (int i = 0; i < 4; i++) {
				env.block(-6 + i * 3, 0, 12, "minecraft:white_bed[part=foot,facing=south]");
				env.block(-6 + i * 3, 0, 13, "minecraft:white_bed[part=head,facing=south]");
			}
			env.summon("minecraft:villager", 0, 0, 8, "");
			env.summon("minecraft:villager", 3, 0, 9, "");
			env.cmd("effect give @p minecraft:raid_omen 1 0");
		}).settle(400).radius(48));
		list.add(TimelineScenario.of("ender_dragon", 100, env -> {
			env.cmd("execute in minecraft:the_end run tp @p 0.5 75 40.5");
		}).settle(100).area(net.minecraft.world.level.Level.END, new net.minecraft.core.BlockPos(0, 70, 0), 120));
		return list;
	}
}
