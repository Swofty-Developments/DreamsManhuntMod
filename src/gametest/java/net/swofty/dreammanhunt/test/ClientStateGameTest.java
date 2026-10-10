package net.swofty.dreammanhunt.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.client.state.ClientSnapshot;
import net.swofty.dreammanhunt.client.state.ClientStateManager;

public final class ClientStateGameTest implements FabricClientGameTest {
	private static final String STATE_ID = "clientstate-gametest";
	private static final int[] FRAME_OFFSETS = {0, 3, 6, 9, 13, 20, 40};
	private static final List<String> VOLATILE_KEYS = List.of("visualNanos");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("clientstate")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			for (int round = 1; round <= 2; round++) {
				runRound(context, singleplayer, round);
			}
		}
	}

	private void runRound(ClientGameTestContext context, TestSingleplayerContext singleplayer, int round) {
		String id = STATE_ID + "-" + round;
		singleplayer.getServer().runCommand("tick unfreeze");
		singleplayer.getServer().runCommand("time set 6000");
		singleplayer.getServer().runCommand("weather clear");
		singleplayer.getServer().runCommand("clear @a");
		singleplayer.getServer().runCommand("give @a minecraft:enchanted_golden_apple 3");
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.setYRot(30.0F);
			mc.player.setXRot(20.0F);
		});
		setSurvivalStats(singleplayer, 16.0F, 6);
		context.waitTicks(60);
		singleplayer.getServer().runCommand("tick freeze");
		context.waitTicks(260);

		CompoundTag[] captured = new CompoundTag[1];
		context.runOnClient(mc -> {
			ClientStateManager.get().capturePending();
			ClientStateManager.get().onCapture(id);
			captured[0] = ClientSnapshot.counters(mc);
		});
		List<Path> afterCapture = new ArrayList<>();
		List<Integer> captureTicks = new ArrayList<>();
		recordFrames(context, "r" + round + "-capture", afterCapture, captureTicks);

		context.waitTicks(150);
		context.runOnClient(mc -> {
			mc.options.fov().set(110);
			mc.options.bobView().set(!mc.options.bobView().get());
			mc.options.gamma().set(1.0);
		});
		context.getInput().pressKey(options -> options.keyAttack);
		singleplayer.getServer().runCommand("damage @p 3 minecraft:generic");
		context.waitTicks(4);
		context.takeScreenshot("r" + round + "-disturbed");
		context.waitTicks(30);

		setSurvivalStats(singleplayer, 16.0F, 6);
		context.waitTicks(5);

		AtomicBoolean ready = new AtomicBoolean();
		AtomicInteger readyCalls = new AtomicInteger();
		context.runOnClient(mc -> {
			ClientStateManager.get().onRestoreBegin(id);
			ClientStateManager.get().onRestoreApply(id, () -> {
				readyCalls.incrementAndGet();
				ready.set(true);
			});
		});
		context.waitFor(mc -> ready.get(), 20 * 20);
		context.waitTicks(10);
		CompoundTag[] resumed = new CompoundTag[1];
		context.runOnClient(mc -> {
			ClientStateManager.get().onRestoreResume(id);
			resumed[0] = ClientSnapshot.counters(mc);
		});
		if (readyCalls.get() != 1) {
			throw new AssertionError("sendReady called " + readyCalls.get() + " times");
		}
		List<String> mismatches = new ArrayList<>();
		diff("", captured[0], resumed[0], mismatches);
		if (!mismatches.isEmpty()) {
			throw new AssertionError("Counters differ after resume: " + String.join("; ", mismatches));
		}
		boolean optionsBack = context.computeOnClient(mc -> mc.options.fov().get() != 110 && mc.options.gamma().get() != 1.0);
		if (!optionsBack) {
			throw new AssertionError("Options were not restored");
		}

		List<Path> afterResume = new ArrayList<>();
		List<Integer> resumeTicks = new ArrayList<>();
		recordFrames(context, "r" + round + "-resume", afterResume, resumeTicks);
		if (!captureTicks.equals(resumeTicks)) {
			throw new AssertionError("Hud.tickCount sequence differs: " + captureTicks + " vs " + resumeTicks);
		}
		for (int i = 0; i < afterCapture.size(); i++) {
			compare(afterCapture.get(i), afterResume.get(i));
		}

		boolean missing = context.computeOnClient(mc -> !ClientStateManager.get().has("does-not-exist"));
		AtomicInteger missingReady = new AtomicInteger();
		context.runOnClient(mc -> {
			ClientStateManager.get().onRestoreBegin("does-not-exist");
			ClientStateManager.get().onRestoreApply("does-not-exist", missingReady::incrementAndGet);
		});
		context.waitFor(mc -> missingReady.get() == 1, 20 * 20);
		context.runOnClient(mc -> ClientStateManager.get().onRestoreResume("does-not-exist"));
		if (!missing || missingReady.get() != 1 || context.computeOnClient(mc -> ClientStateManager.get().isRestoring())) {
			throw new AssertionError("Missing snapshot restore did not complete cleanly");
		}
		context.runOnClient(mc -> ClientStateManager.get().delete(id));
	}

	private static void setSurvivalStats(TestSingleplayerContext singleplayer, float health, int food) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setHealth(health);
				player.getFoodData().setFoodLevel(food);
				player.getFoodData().setSaturation(0.0F);
			}
		});
	}

	private static void recordFrames(ClientGameTestContext context, String prefix, List<Path> shots, List<Integer> ticks) {
		int baseHud = context.computeOnClient(ClientStateGameTest::hudTicks);
		long baseClient = context.computeOnClient(ClientStateGameTest::clientTicks);
		for (int offset : FRAME_OFFSETS) {
			int target = baseHud + offset;
			while (context.computeOnClient(ClientStateGameTest::hudTicks) < target) {
				context.waitTick();
			}
			int hud = context.computeOnClient(ClientStateGameTest::hudTicks);
			long client = context.computeOnClient(ClientStateGameTest::clientTicks);
			if (hud != target) {
				throw new AssertionError(prefix + ": the previous screenshot ran past tick offset " + offset + " (hud " + hud + ", wanted " + target + ")");
			}
			if (client - baseClient != offset) {
				throw new AssertionError(prefix + ": Hud.tickCount and Minecraft.clientTickCount drifted apart at offset " + offset
					+ " (hud +" + offset + ", client +" + (client - baseClient) + ")");
			}
			ticks.add(hud);
			shots.add(context.takeScreenshot(prefix + "-t" + offset));
			long consumed = context.computeOnClient(ClientStateGameTest::clientTicks) - client;
			System.out.printf("[clientstate] %s-t%d at hud tick %d, screenshot readback took %d tick(s)%n", prefix, offset, hud, consumed);
		}
	}

	private static long clientTicks(Minecraft mc) {
		return ClientSnapshot.counters(mc).getCompoundOrEmpty("timing").getLongOr("clientTickCount", -1L);
	}

	private static int hudTicks(Minecraft mc) {
		return ClientSnapshot.counters(mc).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1);
	}

	private static void diff(String path, Tag expected, Tag actual, List<String> out) {
		if (expected instanceof CompoundTag e && actual instanceof CompoundTag a) {
			for (String key : e.keySet()) {
				if (VOLATILE_KEYS.contains(key)) {
					continue;
				}
				Tag other = a.get(key);
				if (other == null) {
					out.add(path + key + " missing");
				} else {
					diff(path + key + ".", e.get(key), other, out);
				}
			}
		} else if (!expected.equals(actual)) {
			out.add(path + " expected " + expected + " got " + actual);
		}
	}

	private static void compare(Path expectedPath, Path actualPath) {
		try {
			BufferedImage expected = ImageIO.read(expectedPath.toFile());
			BufferedImage actual = ImageIO.read(actualPath.toFile());
			if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
				throw new AssertionError("Screenshot sizes differ: " + expectedPath + " vs " + actualPath);
			}
			long differing = 0;
			long hudDiffering = 0;
			int hudTop = expected.getHeight() * 3 / 4;
			for (int y = 0; y < expected.getHeight(); y++) {
				for (int x = 0; x < expected.getWidth(); x++) {
					int p = expected.getRGB(x, y);
					int q = actual.getRGB(x, y);
					int delta = Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)),
						Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255))));
					if (delta > 24) {
						differing++;
						if (y >= hudTop) {
							hudDiffering++;
						}
					}
				}
			}
			double fraction = differing / (double) (expected.getWidth() * expected.getHeight());
			System.out.printf("[clientstate] %s vs %s: %.5f differing, %d in hud band%n", expectedPath.getFileName(), actualPath.getFileName(), fraction, hudDiffering);
			if (fraction > 0.002 || hudDiffering > 40) {
				throw new AssertionError(String.format("Screenshots differ: %s vs %s (%.5f, hud %d)", expectedPath, actualPath, fraction, hudDiffering));
			}
		} catch (IOException e) {
			throw new AssertionError("Failed to read screenshots", e);
		}
	}
}
