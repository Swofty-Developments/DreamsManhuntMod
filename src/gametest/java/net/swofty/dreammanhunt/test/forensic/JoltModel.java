package net.swofty.dreammanhunt.test.forensic;

import java.util.Arrays;
import java.util.SplittableRandom;
import net.minecraft.util.RandomSource;

public final class JoltModel {
	public static final int SEED_FACTOR = 312871;
	public static final int FOOD_ICONS = 10;
	private static final long MULTIPLIER = 25214903917L;
	private static final long INCREMENT = 11L;
	private static final long MASK = (1L << 48) - 1;

	private JoltModel() {
	}

	public record Conditions(int heartContainers, boolean heartsShake, int food) {
		public int foodPeriod() {
			return food * 3 + 1;
		}
	}

	public record Pattern(int[] hearts, int[] food, boolean foodJolt) {
		@Override
		public boolean equals(Object other) {
			return other instanceof Pattern p && foodJolt == p.foodJolt && Arrays.equals(hearts, p.hearts) && Arrays.equals(food, p.food);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(hearts) * 31 + Arrays.hashCode(food);
		}

		@Override
		public String toString() {
			return "hearts" + Arrays.toString(hearts) + " food" + Arrays.toString(food);
		}
	}

	private static long seed(int tick) {
		return ((long) (tick * SEED_FACTOR) ^ MULTIPLIER) & MASK;
	}

	private static long step(long seed) {
		return seed * MULTIPLIER + INCREMENT & MASK;
	}

	public static int firstHeartBit(int seedProduct) {
		return (int) (step(((long) seedProduct ^ MULTIPLIER) & MASK) >>> 47);
	}

	public static Pattern predict(int tick, Conditions conditions) {
		long s = seed(tick);
		int[] hearts = new int[conditions.heartContainers()];
		if (conditions.heartsShake()) {
			for (int container = conditions.heartContainers() - 1; container >= 0; container--) {
				s = step(s);
				hearts[container] = (int) (s >>> 47);
			}
		}
		int[] food = new int[FOOD_ICONS];
		boolean jolt = tick % conditions.foodPeriod() == 0;
		if (jolt) {
			for (int i = 0; i < FOOD_ICONS; i++) {
				int sample;
				int modulo;
				do {
					s = step(s);
					sample = (int) (s >>> 17);
					modulo = sample % 3;
				} while (sample - modulo + 2 < 0);
				food[i] = modulo - 1;
			}
		}
		return new Pattern(hearts, food, jolt);
	}

	public static boolean matches(int tick, int heartContainers, boolean heartsShake, int foodPeriod, int[] hearts, int[] food) {
		long s = seed(tick);
		if (heartsShake) {
			for (int container = heartContainers - 1; container >= 0; container--) {
				s = step(s);
				if ((int) (s >>> 47) != hearts[container]) {
					return false;
				}
			}
		}
		if (tick % foodPeriod != 0) {
			return false;
		}
		for (int i = 0; i < FOOD_ICONS; i++) {
			int sample;
			int modulo;
			do {
				s = step(s);
				sample = (int) (s >>> 17);
				modulo = sample % 3;
			} while (sample - modulo + 2 < 0);
			if (modulo - 1 != food[i]) {
				return false;
			}
		}
		return true;
	}

	public static int selfCheck(int samples) {
		SplittableRandom random = new SplittableRandom(26_3L);
		Conditions conditions = new Conditions(10, true, 0);
		for (int n = 0; n < samples; n++) {
			int tick = n < samples / 2 ? n : random.nextInt();
			RandomSource vanilla = RandomSource.create(tick * SEED_FACTOR);
			int[] hearts = new int[10];
			for (int container = 9; container >= 0; container--) {
				hearts[container] = vanilla.nextInt(2);
			}
			int[] food = new int[FOOD_ICONS];
			for (int i = 0; i < FOOD_ICONS; i++) {
				food[i] = vanilla.nextInt(3) - 1;
			}
			Pattern expected = new Pattern(hearts, food, true);
			Pattern actual = predict(tick, conditions);
			if (!expected.equals(actual)) {
				throw new AssertionError("predictor disagrees with LegacyRandomSource at tick " + tick + ": " + actual + " vs " + expected);
			}
			if (firstHeartBit(tick * SEED_FACTOR) != hearts[9]) {
				throw new AssertionError("first heart bit disagrees at tick " + tick);
			}
			if (!matches(tick, 10, true, 1, hearts, food)) {
				throw new AssertionError("matcher rejects the vanilla pattern at tick " + tick);
			}
		}
		return samples;
	}
}
