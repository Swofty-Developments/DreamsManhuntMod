package net.swofty.dreammanhunt.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.client.state.ClientSnapshot;
import net.swofty.dreammanhunt.client.mixin.state.MinecraftAccessor;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.state.VisualClock;

public final class ClientStateGameTest implements FabricClientGameTest {
	private static final String STATE_ID = "clientstate-gametest";
	private static final int[] FRAME_OFFSETS = {0, 3, 6, 9, 13, 20, 40};
	private static final List<String> VOLATILE_KEYS = List.of("visualNanos", "LocalPlayer.positionReminder");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("clientstate")) {
			return;
		}
		context.runOnClient(mc -> VisualClock.useBaseSource(() -> ((MinecraftAccessor) mc).dm$getClientTickCount() * 50_000_000L));
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			for (int round = 1; round <= 2; round++) {
				runRound(context, singleplayer, round);
			}
			runMotionRound(context, singleplayer);
			runScreenRound(context, singleplayer, "inventory");
			runScreenRound(context, singleplayer, "chat");
		} finally {
			context.runOnClient(mc -> VisualClock.useBaseSource(net.minecraft.util.Util::getNanos));
		}
	}

	private void runRound(ClientGameTestContext context, TestSingleplayerContext singleplayer, int round) {
		String id = STATE_ID + "-" + round;
		singleplayer.getServer().runCommand("tick unfreeze");
		singleplayer.getServer().runCommand("time set 6000");
		singleplayer.getServer().runCommand("weather clear");
		singleplayer.getServer().runCommand("clear @a");
		singleplayer.getServer().runCommand("kill @e[type=!minecraft:player]");
		singleplayer.getServer().runCommand("give @a minecraft:enchanted_golden_apple 3");
		singleplayer.getServer().runCommand("give @a minecraft:ender_pearl 16");
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.setYRot(30.0F);
			mc.player.setXRot(20.0F);
		});
		setSurvivalStats(singleplayer, 16.0F, 6);
		context.waitTicks(40);
		singleplayer.getServer().runCommand("execute as @p at @p rotated ~ 0 run summon minecraft:frog ^-0.8 ^ ^3.5 {NoAI:1b,Silent:1b}");
		singleplayer.getServer().runCommand("execute as @p at @p rotated ~ 0 run summon minecraft:creeper ^1.6 ^ ^5 {NoAI:1b,Silent:1b}");
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 600);
			}
		});
		context.waitTicks(15);
		singleplayer.getServer().runCommand("execute as @p at @p run particle minecraft:end_rod ^ ^1.5 ^3 0.6 0.4 0.6 0 40 force");
		context.waitTicks(4);
		singleplayer.getServer().runCommand("tick freeze");
		context.waitTicks(80);
		context.runOnClient(mc -> poseMobs(mc, true));
		context.runOnClient(mc -> {
			for (int i = 0; i < 4; i++) {
				mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Line " + i + " added before the save, fading out after it"));
			}
		});
		context.waitTicks(145);
		context.runOnClient(mc -> SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.LOW_DISK_SPACE,
			Component.literal("Advancement-style toast"), Component.literal("on screen at the save")));
		context.waitTicks(30);

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
			mc.particleEngine.clearParticles();
			mc.gui.toastManager().clear();
			SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Different toast"), null);
			mc.player.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 50);
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Message from after the save"));
			poseMobs(mc, false);
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
		assertChatFades(afterCapture, afterResume);

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

	private static void poseMobs(Minecraft mc, boolean posed) {
		try {
			poseMobsUnchecked(mc, posed);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void poseMobsUnchecked(Minecraft mc, boolean posed) throws ReflectiveOperationException {
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof Frog frog) {
				int index = 0;
				for (Field field : Frog.class.getDeclaredFields()) {
					if (field.getType() == AnimationState.class) {
						field.setAccessible(true);
						AnimationState state = (AnimationState) field.get(frog);
						if (posed) {
							state.start(frog.tickCount - 4 - 3 * index++);
						} else {
							state.stop();
						}
					}
				}
			} else if (entity instanceof Creeper creeper) {
				for (String name : List.of("swell", "oldSwell")) {
					Field field = Creeper.class.getDeclaredField(name);
					field.setAccessible(true);
					field.setInt(creeper, posed ? (name.equals("swell") ? 22 : 21) : 0);
				}
			}
		}
	}

	private static void assertChatFades(List<Path> afterCapture, List<Path> afterResume) {
		try {
			BufferedImage first = ImageIO.read(afterCapture.get(0).toFile());
			int x1 = (int) (first.getWidth() * 0.28);
			int y0 = (int) (first.getHeight() * 0.40);
			int y1 = (int) (first.getHeight() * 0.84);
			long fadeChange = differingPixels(first, ImageIO.read(afterCapture.get(3).toFile()), 0, y0, x1, y1);
			if (fadeChange == 0) {
				throw new AssertionError("Chat did not fade between the capture frames, the scene does not exercise chat fading");
			}
			for (int i = 0; i < afterCapture.size(); i++) {
				long chatDiff = differingPixels(ImageIO.read(afterCapture.get(i).toFile()), ImageIO.read(afterResume.get(i).toFile()), 0, y0, x1, y1);
				System.out.printf("[clientstate] chat area %s vs %s: %d differing pixels (fade change across capture frames: %d)%n",
					afterCapture.get(i).getFileName(), afterResume.get(i).getFileName(), chatDiff, fadeChange);
				if (chatDiff != 0) {
					throw new AssertionError("Chat area differs after resume at " + afterResume.get(i));
				}
			}
		} catch (IOException e) {
			throw new AssertionError("Failed to read screenshots", e);
		}
	}

	private static long differingPixels(BufferedImage a, BufferedImage b, int x0, int y0, int x1, int y1) {
		long count = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					count++;
				}
			}
		}
		return count;
	}

	private void runMotionRound(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		String id = STATE_ID + "-motion";
		singleplayer.getServer().runCommand("tick unfreeze");
		singleplayer.getServer().runCommand("gamerule advance_time false");
		singleplayer.getServer().runCommand("time set 6000");
		singleplayer.getServer().runCommand("weather clear");
		singleplayer.getServer().runCommand("kill @e[type=!minecraft:player]");
		singleplayer.getServer().runCommand("clear @a");
		singleplayer.getServer().runCommand("give @a minecraft:enchanted_golden_apple 3");
		context.runOnClient(mc -> {
			mc.particleEngine.clearParticles();
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.player.getInventory().setSelectedSlot(0);
		});
		setStats(singleplayer, 20.0F, 20, 20.0F);
		double[] start = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			return new double[]{Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5};
		});
		singleplayer.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", (int) start[0] - 9, (int) Math.floor(start[1]), (int) start[2] - 3, (int) start[0] + 9, (int) Math.floor(start[1]) + 2, (int) start[2] + 20));
		singleplayer.getServer().runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d minecraft:water", (int) Math.floor(start[0]), (int) Math.floor(start[1]), (int) Math.floor(start[2]) + 6));
		teleport(singleplayer, start[0], start[1], start[2] - 2.0, 0.0F, 15.0F);
		context.waitTicks(80);

		holdMotionKeys(context, true);
		context.waitTicks(6);
		for (int i = 0; i < 60 && !context.computeOnClient(mc -> mc.player.isInWater() && mc.player.getDeltaMovement().horizontalDistance() > 0.05); i++) {
			context.waitTick();
		}
		CompoundTag[] captured = new CompoundTag[1];
		context.runOnClient(mc -> {
			ClientStateManager.get().capturePending();
			ClientStateManager.get().onCapture(id);
			captured[0] = ClientStateManager.get().snapshot(id);
		});
		String motionState = context.computeOnClient(mc -> String.format(java.util.Locale.ROOT, "pos=%s delta=%s inWater=%s sprinting=%s onGround=%s",
			mc.player.position(), mc.player.getDeltaMovement(), mc.player.isInWater(), mc.player.isSprinting(), mc.player.onGround()));
		System.out.println("[clientstate] motion at capture: " + motionState);
		boolean moving = context.computeOnClient(mc -> mc.player.getDeltaMovement().horizontalDistance() > 0.05 && mc.player.isInWater());
		if (!moving) {
			context.takeScreenshot("motion-debug-capture");
			throw new AssertionError("Motion scene did not capture a moving player in water: " + motionState);
		}
		List<Path> afterCapture = new ArrayList<>();
		List<Integer> captureTicks = new ArrayList<>();
		recordFrames(context, "motion-capture", afterCapture, captureTicks);
		holdMotionKeys(context, false);

		teleport(singleplayer, start[0] + 3.0, start[1], start[2] + 12.0, 90.0F, 0.0F);
		context.waitTicks(40);

		CompoundTag motion = captured[0].getCompoundOrEmpty("player").getCompoundOrEmpty("motion").getCompoundOrEmpty("primitives");
		teleport(singleplayer, motion.getDoubleOr("LocalPlayer.xLast", 0.0), motion.getDoubleOr("LocalPlayer.yLast", 0.0), motion.getDoubleOr("LocalPlayer.zLast", 0.0),
			motion.getFloatOr("LocalPlayer.yRotLast", 0.0F), motion.getFloatOr("LocalPlayer.xRotLast", 0.0F));
		setStats(singleplayer, 20.0F, 20, 20.0F);
		context.waitTicks(5);
		holdMotionKeys(context, true);
		AtomicBoolean ready = new AtomicBoolean();
		context.runOnClient(mc -> {
			ClientStateManager.get().onRestoreBegin(id);
			ClientStateManager.get().onRestoreApply(id, () -> ready.set(true));
		});
		context.waitFor(mc -> ready.get(), 20 * 20);
		context.waitTicks(5);
		context.runOnClient(mc -> ClientStateManager.get().onRestoreResume(id));
		List<Path> afterResume = new ArrayList<>();
		List<Integer> resumeTicks = new ArrayList<>();
		recordFrames(context, "motion-resume", afterResume, resumeTicks);
		holdMotionKeys(context, false);
		context.waitTicks(30);
		double[] serverPos = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			return new double[]{player.getX(), player.getY(), player.getZ()};
		});
		double[] clientPos = context.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
		double gap = Math.sqrt(Math.pow(serverPos[0] - clientPos[0], 2) + Math.pow(serverPos[1] - clientPos[1], 2) + Math.pow(serverPos[2] - clientPos[2], 2));
		System.out.printf("[clientstate] motion: server and client positions differ by %.4f blocks after resume%n", gap);
		if (gap > 0.25) {
			throw new AssertionError("Server did not follow the restored client position: " + java.util.Arrays.toString(serverPos) + " vs " + java.util.Arrays.toString(clientPos));
		}
		if (!captureTicks.equals(resumeTicks)) {
			throw new AssertionError("Hud.tickCount sequence differs: " + captureTicks + " vs " + resumeTicks);
		}
		for (int i = 0; i < afterCapture.size(); i++) {
			compare(afterCapture.get(i), afterResume.get(i));
		}
		singleplayer.getServer().runCommand("gamerule advance_time true");
		context.runOnClient(mc -> ClientStateManager.get().delete(id));
	}

	private void runScreenRound(ClientGameTestContext context, TestSingleplayerContext singleplayer, String kind) {
		String id = STATE_ID + "-screen-" + kind;
		singleplayer.getServer().runCommand("tick freeze");
		context.runOnClient(mc -> {
			if (kind.equals("inventory")) {
				mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			} else {
				net.minecraft.client.gui.screens.ChatScreen chat = new net.minecraft.client.gui.screens.ChatScreen("half typed message", false);
				mc.gui.setScreen(chat);
			}
		});
		context.getInput().setCursorPos(300, 260);
		context.waitTicks(10);
		if (kind.equals("chat")) {
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT);
		}
		context.waitTicks(3);
		CompoundTag[] captured = new CompoundTag[1];
		context.runOnClient(mc -> {
			ClientStateManager.get().capturePending();
			ClientStateManager.get().onCapture(id);
			captured[0] = ClientSnapshot.counters(mc);
		});
		List<Path> afterCapture = new ArrayList<>();
		List<Integer> captureTicks = new ArrayList<>();
		recordFrames(context, "screen-" + kind + "-capture", afterCapture, captureTicks);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.getInput().setCursorPos(20, 20);
		context.waitTicks(20);
		AtomicBoolean ready = new AtomicBoolean();
		context.runOnClient(mc -> {
			ClientStateManager.get().onRestoreBegin(id);
			ClientStateManager.get().onRestoreApply(id, () -> ready.set(true));
		});
		context.waitFor(mc -> ready.get(), 20 * 20);
		context.waitTicks(3);
		context.runOnClient(mc -> ClientStateManager.get().onRestoreResume(id));
		String screen = context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		System.out.println("[clientstate] screen round " + kind + ": open after resume = " + screen);
		List<Path> afterResume = new ArrayList<>();
		List<Integer> resumeTicks = new ArrayList<>();
		recordFrames(context, "screen-" + kind + "-resume", afterResume, resumeTicks);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		if (!captureTicks.equals(resumeTicks)) {
			throw new AssertionError("Hud.tickCount sequence differs: " + captureTicks + " vs " + resumeTicks);
		}
		for (int i = 0; i < afterCapture.size(); i++) {
			compare(afterCapture.get(i), afterResume.get(i));
		}
		context.runOnClient(mc -> ClientStateManager.get().delete(id));
	}

	private static void holdMotionKeys(ClientGameTestContext context, boolean hold) {
		if (hold) {
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.getInput().holdKey(options -> options.keyJump);
		} else {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyJump);
		}
	}

	private static void teleport(TestSingleplayerContext singleplayer, double x, double y, double z, float yRot, float xRot) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
				player.connection.teleport(x, y, z, yRot, xRot);
			}
		});
	}

	private static void setStats(TestSingleplayerContext singleplayer, float health, int food, float saturation) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setHealth(health);
				player.getFoodData().setFoodLevel(food);
				player.getFoodData().setSaturation(saturation);
			}
		});
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
