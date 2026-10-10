package net.swofty.dreammanhunt.client.state;

import net.minecraft.util.Util;

public final class VisualClock {
	private static long offsetNanos;
	private static long frozenAtNanos = Long.MIN_VALUE;

	private VisualClock() {
	}

	public static long nanos() {
		long frozen = frozenAtNanos;
		return frozen != Long.MIN_VALUE ? frozen : Util.getNanos() + offsetNanos;
	}

	public static long millis() {
		return nanos() / 1_000_000L;
	}

	public static void freeze() {
		if (frozenAtNanos == Long.MIN_VALUE) {
			frozenAtNanos = Util.getNanos() + offsetNanos;
		}
	}

	public static void resumeAt(long visualNanos) {
		offsetNanos = visualNanos - Util.getNanos();
		frozenAtNanos = Long.MIN_VALUE;
	}

	public static void unfreeze() {
		long frozen = frozenAtNanos;
		if (frozen != Long.MIN_VALUE) {
			resumeAt(frozen);
		}
	}

	public static long offsetNanos() {
		return offsetNanos;
	}
}
