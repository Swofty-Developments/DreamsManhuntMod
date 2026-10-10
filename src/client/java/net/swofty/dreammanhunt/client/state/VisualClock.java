package net.swofty.dreammanhunt.client.state;

import java.util.function.LongSupplier;
import net.minecraft.util.Util;

public final class VisualClock {
	private static long offsetNanos;
	private static long frozenAtNanos = Long.MIN_VALUE;
	private static LongSupplier base = Util::getNanos;

	private VisualClock() {
	}

	public static long nanos() {
		long frozen = frozenAtNanos;
		return frozen != Long.MIN_VALUE ? frozen : base.getAsLong() + offsetNanos;
	}

	public static long millis() {
		return nanos() / 1_000_000L;
	}

	public static void freeze() {
		if (frozenAtNanos == Long.MIN_VALUE) {
			frozenAtNanos = base.getAsLong() + offsetNanos;
		}
	}

	public static void resumeAt(long visualNanos) {
		offsetNanos = visualNanos - base.getAsLong();
		frozenAtNanos = Long.MIN_VALUE;
	}

	public static void unfreeze() {
		long frozen = frozenAtNanos;
		if (frozen != Long.MIN_VALUE) {
			resumeAt(frozen);
		}
	}

	public static void useBaseSource(LongSupplier source) {
		long visual = nanos();
		base = source;
		offsetNanos = visual - source.getAsLong();
	}

	public static long offsetNanos() {
		return offsetNanos;
	}
}
