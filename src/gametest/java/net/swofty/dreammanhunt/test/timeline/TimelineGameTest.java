package net.swofty.dreammanhunt.test.timeline;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.swofty.dreammanhunt.test.TestFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TimelineGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("timeline")) {
			return;
		}
		String filter = System.getenv().getOrDefault("TIMELINE_SCENES", "");
		Set<String> only = filter.isBlank() ? Set.of() : Set.of(filter.split(","));
		boolean strict = "1".equals(System.getenv("TIMELINE_STRICT"));
		List<TimelineRunner.Result> results = new ArrayList<>();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			BlockPos origin = context.computeOnClient(mc -> mc.player.blockPosition());
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty normal");
			TimelineRunner runner = new TimelineRunner(context, server, origin);
			for (TimelineScenario scenario : TimelineScenarios.all()) {
				if (!only.isEmpty() && !only.contains(scenario.name())) {
					continue;
				}
				results.add(runner.run(scenario, 24));
			}
		}
		System.out.println("[timeline] ===== summary =====");
		for (TimelineRunner.Result result : results) {
			System.out.println("[timeline] " + result.summary());
		}
		if (strict && results.stream().anyMatch(r -> !r.server().isEmpty() || !r.client().isEmpty())) {
			throw new AssertionError("Timeline divergence: " + results.stream().map(TimelineRunner.Result::summary).toList());
		}
	}
}
