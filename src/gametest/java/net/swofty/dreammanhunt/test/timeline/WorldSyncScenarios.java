package net.swofty.dreammanhunt.test.timeline;

import java.util.List;

public final class WorldSyncScenarios {
	static {
		Probes.addServer(WorldSyncScenarios::blocks);
	}

	private WorldSyncScenarios() {
	}

	private static void blocks(net.minecraft.server.MinecraftServer server, TimelineScenario.Area area, Fingerprint out) {
		net.minecraft.server.level.ServerLevel level = server.getLevel(area.dimension());
		if (level == null) {
			return;
		}
		int floor = area.min().getY() + Math.max(8, (area.max().getX() - area.min().getX()) / 4);
		List<String> blocks = new java.util.ArrayList<>();
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(area.min().getX(), floor, area.min().getZ(), area.max().getX(), floor + 2, area.max().getZ())) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				blocks.add(pos.getX() + "," + pos.getY() + "," + pos.getZ() + "=" + state);
			}
		}
		out.put("blocks:" + level.dimension().identifier().getPath(), "scene", blocks);
	}

	private static void chunkBorderTicks(TimelineEnv env) {
		int bx = 16 - Math.floorMod(env.origin().getX(), 16);
		env.fill(bx - 8, -1, -12, bx + 8, -1, 16, "minecraft:stone");
		for (int row = 0; row < 6; row++) {
			int z = -10 + row * 4;
			env.block(bx - 2, 0, z, "minecraft:sticky_piston[facing=east]");
			env.block(bx - 1, 0, z, "minecraft:stone");
			env.block(bx + 1, 0, z, "minecraft:cobblestone");
			env.block(bx + 2, 0, z, "minecraft:sticky_piston[facing=west]");
			env.block(bx - 3, 0, z, "minecraft:repeater[facing=west,delay=4]");
			env.block(bx + 3, 0, z, "minecraft:repeater[facing=east,delay=4]");
			env.block(bx - 4, 0, z, "minecraft:redstone_wire");
			env.block(bx + 4, 0, z, "minecraft:redstone_wire");
			env.fill(bx - 4, 0, z + 1, bx + 4, 0, z + 1, "minecraft:redstone_wire");
		}
		env.fill(bx - 6, 0, 14, bx + 6, 0, 14, "minecraft:stone");
		env.block(bx - 3, 1, 14, "minecraft:water");
		env.block(bx + 2, 1, 14, "minecraft:lava");
		for (int row = 0; row < 6; row++) {
			env.block(row % 2 == 0 ? bx - 1 : bx + 1, 0, -10 + row * 4 + 1, "minecraft:redstone_block");
		}
	}

	public static List<TimelineScenario> all() {
		return List.of(
			TimelineScenario.of("idle_long", 220, env -> {
			}).settle(10),
			TimelineScenario.of("chunk_border_ticks", 120, WorldSyncScenarios::chunkBorderTicks).settle(1),
			TimelineScenario.of("spawning", 220, env -> {
				env.cmd("difficulty normal");
				env.cmd("gamerule spawn_mobs true");
				env.cmd("gamerule spawn_monsters true");
				env.cmd("time set 18000");
			}).settle(10)
		);
	}
}
