package net.swofty.dreammanhunt.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;
import net.swofty.dreammanhunt.server.sync.ChunkReadiness;

public final class BarrierGameTest implements FabricClientGameTest {
	private static final long MAX_WAIT_MS = 2_000L;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("barrier")) {
			return;
		}
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			BlockPos spawn = context.computeOnClient(mc -> mc.player.blockPosition());
			int startX = (spawn.getX() & ~15) + 8;
			server.runCommand("gamemode survival @a");
			for (String mode : List.of("jump", "sprint", "jump", "sprint")) {
				server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 -90 0", startX, spawn.getY(), spawn.getZ()));
				context.waitTicks(40);
				context.getInput().holdKey(options -> options.keySprint);
				context.getInput().holdKey(options -> options.keyUp);
				context.waitTicks(150);
				if (mode.equals("jump")) {
					context.getInput().holdKeyFor(options -> options.keyJump, 2);
					context.waitTicks(3);
				}
				boolean airborne = context.computeOnClient(mc -> !mc.player.onGround());
				Set<String> before = ids(server);
				server.runCommand("savestate save barrier-" + mode);
				String id = newId(server, before);
				context.waitTicks(60);
				context.getInput().releaseKey(options -> options.keyUp);
				context.getInput().releaseKey(options -> options.keySprint);
				context.waitTicks(10);

				int restoresBefore = SavestateClientNetwork.restoresCompleted();
				server.runOnServer(s -> ChunkReadiness.resetStats());
				long started = System.nanoTime();
				server.runCommand("savestate load " + id);
				context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
				long total = (System.nanoTime() - started) / 1_000_000L;
				long chunkWait = server.computeOnServer(s -> ChunkReadiness.lastWaitMs());
				boolean timedOut = server.computeOnServer(s -> ChunkReadiness.lastTimedOut());
				String line = String.format(Locale.ROOT, "%s (airborne at save: %s): chunk wait %d ms, restore to resume %d ms, timed out %s",
					mode, airborne, chunkWait, total, timedOut);
				System.out.println("[barrier] " + line);
				if (timedOut || chunkWait >= MAX_WAIT_MS) {
					failures.add(line);
				}
				if (mode.equals("jump") && !airborne) {
					failures.add(mode + ": the player was not airborne at the save");
				}
				server.runCommand("savestate delete " + id);
				context.waitTicks(20);
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Restore barrier held too long: " + String.join("; ", failures));
		}
	}

	private static Set<String> ids(TestServerContext server) {
		return server.computeOnServer(s -> {
			Set<String> out = new HashSet<>();
			for (SavestateInfo info : SavestateEngine.get(s).list()) {
				out.add(info.id());
			}
			return out;
		});
	}

	private static String newId(TestServerContext server, Set<String> before) {
		Set<String> after = ids(server);
		after.removeAll(before);
		if (after.size() != 1) {
			throw new AssertionError("expected one new savestate, got " + after);
		}
		return after.iterator().next();
	}
}
