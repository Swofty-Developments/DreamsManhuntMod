package net.swofty.dreammanhunt.test.forensic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class TickCracker {
	public static final int RANGE_START = 0;
	public static final long RANGE_END = 1L << 31;
	private static final int CHUNK = 1 << 22;
	private static final int MAX_CANDIDATES = 1 << 16;
	public static final int MIN_LINE_SUPPORT = 5;

	private TickCracker() {
	}

	public static int[] crack(HudDecoder.Frame frame) {
		return crack(frame, RANGE_START, RANGE_END);
	}

	public static int[] crack(HudDecoder.Frame frame, int rangeStart, long rangeEnd) {
		JoltModel.Conditions conditions = frame.conditions();
		int[] hearts = frame.heartShifts();
		int[] food = frame.foodShifts();
		int containers = conditions.heartContainers();
		boolean shake = conditions.heartsShake();
		int period = conditions.foodPeriod();
		int chunks = (int) ((rangeEnd - rangeStart + CHUNK - 1) / CHUNK);
		int firstBit = shake ? hearts[containers - 1] : -1;
		List<int[]> parts = IntStream.range(0, chunks).parallel().mapToObj(chunk -> {
			int from = (int) (rangeStart + (long) chunk * CHUNK);
			int length = (int) Math.min(CHUNK, rangeEnd - from);
			int[] found = new int[16];
			int count = 0;
			int product = from * JoltModel.SEED_FACTOR;
			for (int k = 0; k < length; k++, product += JoltModel.SEED_FACTOR) {
				if (firstBit >= 0 && JoltModel.firstHeartBit(product) != firstBit) {
					continue;
				}
				int t = from + k;
				if (JoltModel.matches(t, containers, shake, period, hearts, food)) {
					if (count == found.length) {
						found = Arrays.copyOf(found, count * 2);
					}
					found[count++] = t;
				}
			}
			return Arrays.copyOf(found, count);
		}).toList();
		int total = 0;
		for (int[] part : parts) {
			total += part.length;
		}
		if (total > MAX_CANDIDATES) {
			throw new AssertionError("jolt pattern too weak to crack: " + total + " candidates");
		}
		int[] all = new int[total];
		int at = 0;
		for (int[] part : parts) {
			System.arraycopy(part, 0, all, at, part.length);
			at += part.length;
		}
		Arrays.sort(all);
		return all;
	}

	public record Line(long offset, List<Integer> frames) {
		public int support() {
			return frames.size();
		}
	}

	public static List<Line> lines(int[] videoTimes, int[][] candidates) {
		Map<Long, List<Integer>> byOffset = new HashMap<>();
		for (int i = 0; i < videoTimes.length; i++) {
			for (int candidate : candidates[i]) {
				byOffset.computeIfAbsent((long) candidate - videoTimes[i], k -> new ArrayList<>()).add(i);
			}
		}
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<Long, List<Integer>> entry : byOffset.entrySet()) {
			if (entry.getValue().size() >= MIN_LINE_SUPPORT) {
				lines.add(new Line(entry.getKey(), List.copyOf(entry.getValue())));
			}
		}
		lines.sort((a, b) -> Integer.compare(a.frames().getFirst(), b.frames().getFirst()));
		return lines;
	}
}
