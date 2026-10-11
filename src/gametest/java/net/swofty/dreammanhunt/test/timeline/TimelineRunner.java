package net.swofty.dreammanhunt.test.timeline;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TimelineRunner {
	public record Divergence(String subject, int tick, String field, String a, String b, List<String> others) {
		@Override
		public String toString() {
			StringBuilder text = new StringBuilder("tick " + this.tick + " " + this.subject + " " + this.field + ": A=" + clip(this.a) + " B=" + clip(this.b));
			for (String other : this.others) {
				text.append("\n[timeline]       also ").append(other);
			}
			return text.toString();
		}
	}

	public record Result(String scenario, int ticks, List<Divergence> server, List<Divergence> client) {
		public int firstServerTick() {
			return this.server.stream().mapToInt(Divergence::tick).min().orElse(-1);
		}

		public String summary() {
			int first = this.firstServerTick();
			int firstClient = this.client.stream().mapToInt(Divergence::tick).min().orElse(-1);
			return this.scenario + ": server " + (first < 0 ? "identical for " + this.ticks + " ticks" : "first divergence at tick " + first + " (" + this.server.size() + " subjects)")
					+ ", client " + (firstClient < 0 ? "identical" : "first divergence at tick " + firstClient + " (" + this.client.size() + " subjects)");
		}
	}

	private static final int MAX_EXTRA_FIELDS = Integer.parseInt(System.getenv().getOrDefault("TIMELINE_FIELDS", "12"));
	private static final int[] TRACE_RANGE = traceRange();
	private final ClientGameTestContext context;
	private final TestServerContext server;
	private final BlockPos origin;

	public TimelineRunner(ClientGameTestContext context, TestServerContext server, BlockPos origin) {
		this.context = context;
		this.server = server;
		this.origin = origin;
		TimelineRecorder.register();
	}

	public Result run(TimelineScenario scenario, int radius) {
		TimelineEnv env = new TimelineEnv(this.context, this.server, this.origin);
		int areaRadius = scenario.radius();
		BlockPos center = scenario.center() == null ? this.origin : scenario.center();
		TimelineScenario.Area area = new TimelineScenario.Area(scenario.dimension() == null ? Level.OVERWORLD : scenario.dimension(),
				center.offset(-areaRadius, -Math.max(8, areaRadius / 2), -areaRadius), center.offset(areaRadius, Math.max(24, areaRadius), areaRadius));
		this.prepare(env, radius);
		scenario.setup().accept(env);
		this.context.waitTicks(scenario.settleTicks());

		String name = "timeline-" + scenario.name();
		TimelineRecorder.Recording[] recordingA = new TimelineRecorder.Recording[1];
		RngTrace.Trace[] traceA = new RngTrace.Trace[1];
		RngTrace.Trace[] traceB = new RngTrace.Trace[1];
		String id = this.server.computeOnServer(s -> {
			TimelineEnv.run(s, "savestate save " + name);
			SavestateInfo info = SavestateEngine.get(s).find(name).orElseThrow();
			recordingA[0] = new TimelineRecorder.Recording(scenario, env, area, info.gameTime());
			TimelineRecorder.setCurrentTick(0);
			recordingA[0].server.put(0, Probes.captureServer(s, area));
			if (TRACE_RANGE != null) {
				traceA[0] = RngTrace.start(s, TRACE_RANGE[0], TRACE_RANGE[1]);
			}
			TimelineRecorder.start(recordingA[0]);
			return info.id();
		});
		this.context.waitFor(mc -> recordingA[0].serverDone() && recordingA[0].clientDone(), scenario.ticks() * 4 + 200);
		TimelineRecorder.stop();
		RngTrace.stop();
		this.context.waitTicks(20);

		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		TimelineRecorder.Recording[] recordingB = new TimelineRecorder.Recording[1];
		this.server.runOnServer(s -> {
			TimelineEnv.run(s, "savestate load " + id);
			recordingB[0] = new TimelineRecorder.Recording(scenario, env, area, recordingA[0].baseGameTime);
			TimelineRecorder.setCurrentTick(0);
			recordingB[0].server.put(0, Probes.captureServer(s, area));
			if (TRACE_RANGE != null) {
				traceB[0] = RngTrace.start(s, TRACE_RANGE[0], TRACE_RANGE[1]);
			}
			TimelineRecorder.start(recordingB[0]);
		});
		this.context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore && recordingB[0].serverDone() && recordingB[0].clientDone(),
				scenario.ticks() * 4 + 1200);
		TimelineRecorder.stop();
		RngTrace.stop();
		if (traceA[0] != null && traceB[0] != null) {
			for (String line : RngTrace.compare(traceA[0], traceB[0])) {
				System.out.println("[timeline] rng " + scenario.name() + " " + line);
			}
		}
		this.server.runOnServer(s -> SavestateEngine.get(s).delete(id));

		Result result = new Result(scenario.name(), scenario.ticks(), compare(recordingA[0].server, recordingB[0].server), compare(recordingA[0].client, recordingB[0].client));
		this.report(result);
		return result;
	}

	private static int[] traceRange() {
		String raw = System.getenv("TIMELINE_TRACE_RNG");
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String[] parts = raw.trim().split("-");
		int from = Integer.parseInt(parts[0]);
		int to = parts.length > 1 ? Integer.parseInt(parts[1]) : from;
		return new int[]{from, to};
	}

	private void prepare(TimelineEnv env, int radius) {
		env.cmd("execute in minecraft:overworld run tp @p %.1f %d %.1f 0 0", this.origin.getX() + 0.5, this.origin.getY(), this.origin.getZ() + 0.5);
		this.context.waitTicks(5);
		env.cmd("kill @e[type=!minecraft:player]");
		env.fill(-radius, -1, -radius, radius, -1, radius, "minecraft:grass_block");
		env.fill(-radius, 0, -radius, radius, 20, radius, "minecraft:air");
		env.cmd("effect clear @a");
		env.cmd("gamemode survival @a");
		env.cmd("effect give @a minecraft:resistance infinite 4 true");
		env.cmd("effect give @a minecraft:regeneration infinite 4 true");
		env.cmd("attribute @p minecraft:knockback_resistance base set 1");
		env.cmd("kill @e[type=minecraft:item]");
		this.context.waitTicks(5);
	}

	static List<Divergence> compare(Map<Integer, Fingerprint> a, Map<Integer, Fingerprint> b) {
		Map<String, Map<String, Divergence>> firstByField = new java.util.LinkedHashMap<>();
		Set<Integer> ticks = new java.util.TreeSet<>(a.keySet());
		ticks.addAll(b.keySet());
		for (int tick : ticks) {
			Fingerprint fa = a.get(tick);
			Fingerprint fb = b.get(tick);
			if (fa == null || fb == null) {
				continue;
			}
			Set<String> subjects = new LinkedHashSet<>(fa.subjects().keySet());
			subjects.addAll(fb.subjects().keySet());
			for (String subject : subjects) {
				Map<String, Divergence> fields = firstByField.computeIfAbsent(subject, k -> new java.util.LinkedHashMap<>());
				Map<String, String> sa = fa.subjects().get(subject);
				Map<String, String> sb = fb.subjects().get(subject);
				if (sa == null || sb == null) {
					fields.putIfAbsent("presence", new Divergence(subject, tick, "presence", sa == null ? "absent" : "present", sb == null ? "absent" : "present", List.of()));
					continue;
				}
				Set<String> names = new LinkedHashSet<>(sa.keySet());
				names.addAll(sb.keySet());
				for (String field : names) {
					String va = sa.get(field);
					String vb = sb.get(field);
					if (!java.util.Objects.equals(va, vb)) {
						fields.putIfAbsent(field, new Divergence(subject, tick, field, va, vb, List.of()));
					}
				}
			}
		}
		List<Divergence> result = new ArrayList<>();
		for (Map<String, Divergence> fields : firstByField.values()) {
			if (fields.isEmpty()) {
				continue;
			}
			List<Divergence> sorted = new ArrayList<>(fields.values());
			sorted.sort(java.util.Comparator.comparingInt(Divergence::tick));
			Divergence first = sorted.getFirst();
			List<String> others = new ArrayList<>();
			for (Divergence other : sorted.subList(1, sorted.size())) {
				if (others.size() >= MAX_EXTRA_FIELDS) {
					others.add("... " + (sorted.size() - 1 - MAX_EXTRA_FIELDS) + " more fields");
					break;
				}
				others.add("tick " + other.tick() + " " + other.field() + ": A=" + clip(other.a()) + " B=" + clip(other.b()));
			}
			result.add(new Divergence(first.subject(), first.tick(), first.field(), first.a(), first.b(), others));
		}
		result.sort(java.util.Comparator.comparingInt(Divergence::tick));
		return result;
	}

	private void report(Result result) {
		StringBuilder text = new StringBuilder("[timeline] ").append(result.summary()).append('\n');
		for (Divergence divergence : result.server()) {
			text.append("[timeline]   server ").append(divergence).append('\n');
		}
		for (Divergence divergence : result.client()) {
			text.append("[timeline]   client ").append(divergence).append('\n');
		}
		System.out.print(text);
		try {
			Path dir = Path.of("timeline");
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(result.scenario() + ".txt"), text);
		} catch (IOException e) {
			System.out.println("[timeline] could not write report: " + e);
		}
	}

	private static String clip(String value) {
		if (value == null) {
			return "null";
		}
		return value.length() > 300 ? value.substring(0, 300) + "..." : value;
	}
}
