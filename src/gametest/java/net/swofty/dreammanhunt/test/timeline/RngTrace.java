package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.swofty.dreammanhunt.mixin.server.LevelAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RngTrace {
	public record Call(int tick, int index, int bits, int value, String stack) {
		String signature() {
			return this.tick + " " + this.bits + " " + this.stack;
		}

		@Override
		public String toString() {
			return "t" + this.tick + " #" + this.index + " bits=" + this.bits + " value=" + this.value + " at " + this.stack;
		}
	}

	public static final class Trace {
		final Map<String, List<Call>> byLevel = new java.util.TreeMap<>();
		final Map<Object, String> sources = Collections.synchronizedMap(new IdentityHashMap<>());
		volatile int tick;
		final int from;
		final int to;

		Trace(int from, int to) {
			this.from = from;
			this.to = to;
		}
	}

	private static final Set STACK_SKIP = new Set();
	private static final int FRAMES = Integer.parseInt(System.getenv().getOrDefault("TIMELINE_TRACE_FRAMES", "3"));
	private static volatile Trace current;

	private RngTrace() {
	}

	public static boolean active() {
		Trace trace = current;
		return trace != null && trace.tick >= trace.from && trace.tick <= trace.to;
	}

	public static void record(Object random, int bits, int value) {
		Trace trace = current;
		if (trace == null) {
			return;
		}
		String level = trace.sources.get(random);
		if (level == null) {
			return;
		}
		List<Call> calls = trace.byLevel.computeIfAbsent(level, k -> new ArrayList<>());
		calls.add(new Call(trace.tick, calls.size(), bits, value, stack()));
	}

	static Trace start(MinecraftServer server, int from, int to, TimelineScenario.Area area) {
		Trace trace = new Trace(from, to);
		for (ServerLevel level : server.getAllLevels()) {
			trace.sources.put(((LevelAccessor) level).dreammanhunt$getRandom(), level.dimension().identifier().getPath());
		}
		trace.tick = 1;
		current = trace;
		registerEntities(server, area);
		return trace;
	}

	static void registerEntities(MinecraftServer server, TimelineScenario.Area area) {
		Trace trace = current;
		if (trace == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (area.contains(entity)) {
					trace.sources.putIfAbsent(entity.getRandom(), "entity:" + Probes.entityKey(entity));
				}
			}
		}
	}

	static void setTick(int tick) {
		Trace trace = current;
		if (trace != null) {
			trace.tick = tick;
		}
	}

	static void stop() {
		current = null;
	}

	static List<String> compare(Trace a, Trace b) {
		List<String> report = new ArrayList<>();
		java.util.Set<String> levels = new java.util.TreeSet<>(a.byLevel.keySet());
		levels.addAll(b.byLevel.keySet());
		for (String level : levels) {
			List<Call> ca = a.byLevel.getOrDefault(level, List.of());
			List<Call> cb = b.byLevel.getOrDefault(level, List.of());
			int first = -1;
			for (int i = 0; i < Math.max(ca.size(), cb.size()); i++) {
				if (i >= ca.size() || i >= cb.size() || !ca.get(i).signature().equals(cb.get(i).signature()) || ca.get(i).value() != cb.get(i).value()) {
					first = i;
					break;
				}
			}
			if (first < 0) {
				report.add(level + ": identical (" + ca.size() + " calls)");
				continue;
			}
			report.add(level + ": first difference at call #" + first + " (A has " + ca.size() + " calls, B has " + cb.size() + ")");
			for (int i = Math.max(0, first - 3); i < Math.min(Math.max(ca.size(), cb.size()), first + 6); i++) {
				report.add("    A " + (i < ca.size() ? ca.get(i) : "-"));
				report.add("    B " + (i < cb.size() ? cb.get(i) : "-"));
			}
		}
		return report;
	}

	private static String stack() {
		StackTraceElement[] frames = Thread.currentThread().getStackTrace();
		StringBuilder builder = new StringBuilder();
		int taken = 0;
		for (StackTraceElement frame : frames) {
			String cls = frame.getClassName();
			if (STACK_SKIP.skip(cls, frame.getMethodName())) {
				continue;
			}
			if (taken > 0) {
				builder.append(" < ");
			}
			builder.append(cls.substring(cls.lastIndexOf('.') + 1)).append('#').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
			if (++taken >= FRAMES) {
				break;
			}
		}
		if (!Thread.currentThread().getName().equals("Server thread")) {
			builder.append(" [").append(Thread.currentThread().getName()).append(']');
		}
		return builder.toString();
	}

	private static final class Set {
		boolean skip(String cls, String method) {
			return cls.startsWith("java.") || cls.startsWith("jdk.") || cls.contains("RandomSource") || cls.contains("RngTrace")
					|| cls.contains("MarsagliaPolarGaussian") || cls.endsWith(".Mth") || cls.contains("LegacyRandomSourceTraceMixin")
					|| cls.contains("RandomSupport") || method.startsWith("handler$") || method.contains("dreammanhuntTest$");
		}
	}
}
