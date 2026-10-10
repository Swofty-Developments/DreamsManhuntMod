package net.swofty.dreammanhunt.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;
import net.swofty.dreammanhunt.client.ui.SavestateList;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.client.ui.ThumbnailManager;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;
import net.swofty.dreammanhunt.test.forensic.FrameRecorder;
import net.swofty.dreammanhunt.test.forensic.HudDecoder;
import net.swofty.dreammanhunt.test.forensic.JoltModel;
import net.swofty.dreammanhunt.test.forensic.TickCracker;

public final class ForensicGameTest implements FabricClientGameTest {
	private static final int GUI_SCALE = 1;
	private static final int VALIDATION_FRAMES = 120;
	private static final int PRE_ROLL = 50;
	private static final int POST_ROLL = 60;
	private static final int SMALL_RANGE = 1 << 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("forensic")) {
			return;
		}
		int checked = JoltModel.selfCheck(200_000);
		log("predictor matches LegacyRandomSource on %d seeds", checked);
		FrameRecorder.install();
		Path root = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve("forensic");
		clean(root);
		HudDecoder decoder = context.computeOnClient(mc -> HudDecoder.load(mc.getResourceManager()));

		FrameRecorder.Recording validation;
		FrameRecorder.Recording restore;
		FrameRecorder.Recording control;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			setUp(context, singleplayer.getServer());
			context.waitTicks(40);

			validation = context.computeOnClient(mc -> FrameRecorder.start(mc, "validate", root, 20));
			context.waitTicks(VALIDATION_FRAMES);
			FrameRecorder.stop();

			restore = saveCutRestore(context, singleplayer.getServer(), root, "restore", false);
			control = saveCutRestore(context, singleplayer.getServer(), root, "control", true);
		}
		FrameRecorder.awaitWrites();

		validate(decoder, validation);
		Analysis seamless = analyse(decoder, restore, root);
		Analysis cut = analyse(decoder, control, root);

		if (seamless.lines.size() != 1) {
			throw new AssertionError("restore: expected one line through every frame, found " + describe(seamless.lines));
		}
		if (seamless.lines.getFirst().support() != seamless.videoTimes.length) {
			throw new AssertionError("restore: the line covers " + seamless.lines.getFirst().support() + " of " + seamless.videoTimes.length + " frames");
		}
		if (!seamless.breaks.isEmpty()) {
			throw new AssertionError("restore: cracked uptime breaks at video frames " + seamless.breaks);
		}
		for (int i = 0; i < seamless.cracked.length; i++) {
			if (seamless.cracked[i] != seamless.frames.get(i).hudTick()) {
				throw new AssertionError("restore: frame " + i + " cracked as " + seamless.cracked[i] + " but Hud.tickCount was " + seamless.frames.get(i).hudTick());
			}
		}
		if (restore.hudAtResume() != seamless.frames.get(seamless.cutIndex - 1).hudTick()) {
			throw new AssertionError("restore: Hud.tickCount at resume " + restore.hudAtResume() + " != tick of the last frame before the save "
					+ seamless.frames.get(seamless.cutIndex - 1).hudTick());
		}

		if (cut.lines.size() != 2) {
			throw new AssertionError("control: expected two lines (before and after the cut), found " + describe(cut.lines));
		}
		if (!cut.breaks.equals(List.of(cut.cutIndex))) {
			throw new AssertionError("control: expected exactly one break at the cut (frame " + cut.cutIndex + "), found " + cut.breaks);
		}
		long jump = cut.lines.get(1).offset() - cut.lines.get(0).offset();
		log("RESULT restore: %d frames, one line (uptime = video frame + %d), cut at frame %d, cracked %d -> %d, no break",
				seamless.videoTimes.length, seamless.lines.getFirst().offset(), seamless.cutIndex,
				seamless.cracked[seamless.cutIndex - 1], seamless.cracked[seamless.cutIndex]);
		log("RESULT control: %d frames, two lines, break at frame %d (cracked %d -> %d, jump %+d ticks), Hud.tickCount forced to %d after resume",
				cut.videoTimes.length, cut.cutIndex, cut.cracked[cut.cutIndex - 1], cut.cracked[cut.cutIndex], jump, control.overriddenTo());
	}

	private static void log(String format, Object... args) {
		System.out.println("[forensic] " + String.format(Locale.ROOT, format, args));
	}

	private static void clean(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ServerPlayer onlyPlayer(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.size() != 1) {
			throw new AssertionError("expected one player, found " + players.size());
		}
		return players.getFirst();
	}

	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			s.setDifficulty(Difficulty.EASY, true);
			onlyPlayer(s).setGameMode(GameType.SURVIVAL);
		});
		server.runCommand("time set 1000");
		server.runCommand("weather clear");
		context.runOnClient(mc -> {
			mc.options.guiScale().set(GUI_SCALE);
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			mc.player.setYRot(0.0F);
			mc.player.setXRot(-90.0F);
		});
		setStats(server, 4.0F, 0, 0.0F);
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		if (scale != GUI_SCALE) {
			throw new AssertionError("gui scale is " + scale);
		}
	}

	private static void setStats(TestServerContext server, float health, int food, float saturation) {
		server.runOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			player.setHealth(health);
			player.getFoodData().setFoodLevel(food);
			player.getFoodData().setSaturation(saturation);
		});
	}

	private static Set<String> serverIds(TestServerContext server) {
		return server.computeOnServer(s -> {
			Set<String> ids = new HashSet<>();
			for (SavestateInfo info : SavestateEngine.get(s).list()) {
				ids.add(info.id());
			}
			return ids;
		});
	}

	private static String awaitNewState(ClientGameTestContext context, TestServerContext server, Set<String> before) {
		server.waitFor(s -> SavestateEngine.get(s).list().size() > before.size(), 200);
		Set<String> after = serverIds(server);
		after.removeAll(before);
		if (after.size() != 1) {
			throw new AssertionError("expected one new savestate, got " + after);
		}
		String id = after.iterator().next();
		context.waitFor(mc -> SavestateClientNetwork.states() != null
				&& SavestateClientNetwork.states().stream().anyMatch(info -> info.id().equals(id)), 200);
		context.waitFor(mc -> Files.isRegularFile(ThumbnailManager.file(id)) && ClientStateManager.get().has(id), 200);
		return id;
	}

	private static FrameRecorder.Recording saveCutRestore(ClientGameTestContext context, TestServerContext server, Path root, String name, boolean skipTickRestore) {
		setStats(server, 4.0F, 0, 0.0F);
		context.waitTicks(10);
		FrameRecorder.Recording recording = context.computeOnClient(mc -> FrameRecorder.start(mc, name, root, 10));
		context.waitTicks(PRE_ROLL);

		Set<String> before = serverIds(server);
		context.getInput().pressKey(SavestateKeys.SAVE);
		String id = awaitNewState(context, server, before);
		if (recording.saveClock() < 0) {
			throw new AssertionError(name + ": the client capture was not seen");
		}

		server.runOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			player.level().setBlockAndUpdate(player.blockPosition().offset(2, 0, 2), Blocks.GOLD_BLOCK.defaultBlockState());
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
			player.setHealth(14.0F);
			player.getFoodData().setFoodLevel(17);
			player.getFoodData().setSaturation(3.0F);
			player.connection.teleport(player.getX() + 12.0, player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		});
		server.runCommand("damage @p 2 minecraft:generic");
		context.waitTicks(40);

		context.getInput().pressKey(SavestateKeys.MENU);
		context.waitForScreen(SavestateMenuScreen.class);
		context.waitFor(mc -> mc.gui.screen() instanceof SavestateMenuScreen menu && menu.hasList()
				&& menu.list().children().stream().anyMatch(entry -> entry.info().id().equals(id)), 200);
		context.waitTicks(5);
		SavestateList.Entry entry = context.computeOnClient(mc -> ((SavestateMenuScreen) mc.gui.screen()).list().children().stream()
				.filter(e -> e.info().id().equals(id)).findFirst().orElseThrow());
		if (skipTickRestore) {
			FrameRecorder.armTickRestoreSkip();
		}
		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		clickAt(context, entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0, entry.getContentY() + entry.getContentHeight() / 2.0);
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
		server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 200);
		context.waitTicks(POST_ROLL);
		FrameRecorder.stop();
		if (recording.resumeClock() < 0) {
			throw new AssertionError(name + ": the resume was not seen");
		}
		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			throw new AssertionError(name + ": a screen is still open after the restore");
		}
		context.runOnClient(mc -> ClientStateManager.get().delete(id));
		return recording;
	}

	private static void clickAt(ClientGameTestContext context, double guiX, double guiY) {
		double[] window = context.computeOnClient(mc -> {
			Window w = mc.getWindow();
			return new double[]{guiX * w.getScreenWidth() / w.getGuiScaledWidth(), guiY * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static HudDecoder.Frame decode(HudDecoder decoder, FrameRecorder.Recording recording, FrameRecorder.Frame frame) {
		try {
			BufferedImage image = ImageIO.read(frame.crop().toFile());
			if (image == null) {
				throw new AssertionError("unreadable frame " + frame.crop());
			}
			return decoder.decode(image, recording.geometry());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void validate(HudDecoder decoder, FrameRecorder.Recording recording) {
		List<FrameRecorder.Frame> frames = recording.frames();
		int exact = 0;
		int jolts = 0;
		int truthFound = 0;
		int uniqueSmall = 0;
		long candidateTotal = 0;
		double minScore = 1.0;
		double minMargin = 1.0;
		List<String> failures = new ArrayList<>();
		int[] videoTimes = new int[frames.size()];
		int[][] candidates = new int[frames.size()][];
		for (int i = 0; i < frames.size(); i++) {
			FrameRecorder.Frame frame = frames.get(i);
			videoTimes[i] = (int) (frame.clock() - frames.getFirst().clock());
			HudDecoder.Frame decoded = decode(decoder, recording, frame);
			minScore = Math.min(minScore, decoded.minScore());
			minMargin = Math.min(minMargin, decoded.minMargin());
			JoltModel.Pattern predicted = JoltModel.predict(frame.hudTick(), decoded.conditions());
			JoltModel.Pattern observed = new JoltModel.Pattern(decoded.heartShifts(), decoded.foodShifts(), decoded.foodJolt());
			if (decoded.confident() && predicted.equals(observed)) {
				exact++;
			} else if (failures.size() < 5) {
				failures.add("frame " + i + " tick " + frame.hudTick() + ": decoded " + observed + " (score " + decoded.minScore() + ", margin "
						+ decoded.minMargin() + ", health " + decoded.healthHalves() + ", food " + decoded.foodLevel() + ") predicted " + predicted);
			}
			if (decoded.foodJolt()) {
				jolts++;
			}
			candidates[i] = decoded.foodJolt() ? TickCracker.crack(decoded) : new int[0];
			candidateTotal += candidates[i].length;
			if (java.util.Arrays.binarySearch(candidates[i], frame.hudTick()) >= 0) {
				truthFound++;
			}
			int small = 0;
			for (int candidate : candidates[i]) {
				if (candidate < SMALL_RANGE) {
					small++;
				}
			}
			if (small == 1) {
				uniqueSmall++;
			}
		}
		List<TickCracker.Line> lines = TickCracker.lines(videoTimes, candidates);
		long expectedOffset = frames.getFirst().hudTick();
		log("validate: %d frames, decoded pattern == predicted(Hud.tickCount) on %d, food jolt frames %d, min template score %.3f, min margin %.3f",
				frames.size(), exact, jolts, minScore, minMargin);
		log("validate: brute force over [%d, %d): true tick among candidates in %d/%d frames, mean %.1f candidates per frame, unique within [0, 2^20) in %d/%d",
				TickCracker.RANGE_START, TickCracker.RANGE_END, truthFound, frames.size(), candidateTotal / (double) frames.size(), uniqueSmall, frames.size());
		log("validate: lines %s, expected offset %d", describe(lines), expectedOffset);
		if (exact != frames.size()) {
			throw new AssertionError("decoder disagrees with the predictor on " + (frames.size() - exact) + " frames: " + failures);
		}
		if (truthFound != frames.size()) {
			throw new AssertionError("brute force missed the true tick on " + (frames.size() - truthFound) + " frames");
		}
		if (lines.size() != 1 || lines.getFirst().offset() != expectedOffset || lines.getFirst().support() != frames.size()) {
			throw new AssertionError("validation line " + describe(lines) + " does not recover Hud.tickCount (offset " + expectedOffset + ")");
		}
		for (int i = 1; i < frames.size(); i++) {
			if (frames.get(i).hudTick() - frames.get(i - 1).hudTick() != frames.get(i).clock() - frames.get(i - 1).clock()) {
				throw new AssertionError("validation frames are not one per Hud tick at frame " + i);
			}
		}
	}

	private static final class Analysis {
		List<FrameRecorder.Frame> frames;
		int[] videoTimes;
		int[][] candidates;
		int[] cracked;
		int cutIndex;
		List<TickCracker.Line> lines;
		List<Integer> breaks;
	}

	private static Analysis analyse(HudDecoder decoder, FrameRecorder.Recording recording, Path root) {
		String name = recording.name();
		List<FrameRecorder.Frame> all = recording.frames();
		long lastBefore = recording.saveClock() + 1;
		long firstAfter = recording.resumeClock() + 1;
		List<FrameRecorder.Frame> pre = all.stream().filter(f -> f.clock() <= lastBefore).toList();
		List<FrameRecorder.Frame> post = all.stream().filter(f -> f.clock() >= firstAfter).toList();
		if (pre.isEmpty() || pre.getLast().clock() != lastBefore || post.isEmpty() || post.getFirst().clock() != firstAfter) {
			throw new AssertionError(name + ": cut frames missing (save clock " + recording.saveClock() + ", resume clock " + recording.resumeClock() + ")");
		}
		Analysis analysis = new Analysis();
		analysis.frames = new ArrayList<>(pre);
		analysis.frames.addAll(post);
		analysis.cutIndex = pre.size();
		int n = analysis.frames.size();
		analysis.videoTimes = new int[n];
		for (int i = 0; i < n; i++) {
			FrameRecorder.Frame frame = analysis.frames.get(i);
			analysis.videoTimes[i] = i < pre.size()
					? (int) (frame.clock() - pre.getFirst().clock())
					: (int) (lastBefore - pre.getFirst().clock()) + 1 + (int) (frame.clock() - firstAfter);
			if (i > 0 && analysis.videoTimes[i] != analysis.videoTimes[i - 1] + 1) {
				throw new AssertionError(name + ": recording dropped a frame before video frame " + i);
			}
		}
		analysis.candidates = new int[n][];
		HudDecoder.Frame[] decoded = new HudDecoder.Frame[n];
		for (int i = 0; i < n; i++) {
			decoded[i] = decode(decoder, recording, analysis.frames.get(i));
			HudDecoder.Frame frame = decoded[i];
			if (!frame.confident() || !frame.shiftsInVanillaRange()) {
				throw new AssertionError(name + ": video frame " + i + " (" + analysis.frames.get(i).crop().getFileName() + ") did not decode cleanly: score "
						+ frame.minScore() + " margin " + frame.minMargin() + " hearts " + java.util.Arrays.toString(frame.heartShifts())
						+ " food " + java.util.Arrays.toString(frame.foodShifts()));
			}
			if (!frame.foodJolt() || !frame.heartsShake()) {
				throw new AssertionError(name + ": video frame " + i + " shows no jolt (health " + frame.healthHalves() + ", food " + frame.foodLevel() + ")");
			}
			analysis.candidates[i] = TickCracker.crack(frame);
		}
		analysis.lines = TickCracker.lines(analysis.videoTimes, analysis.candidates);
		analysis.cracked = new int[n];
		for (int i = 0; i < n; i++) {
			TickCracker.Line best = null;
			for (TickCracker.Line line : analysis.lines) {
				if (line.frames().contains(i) && (best == null || line.support() > best.support())) {
					best = line;
				}
			}
			analysis.cracked[i] = best == null ? -1 : (int) (best.offset() + analysis.videoTimes[i]);
		}
		analysis.breaks = new ArrayList<>();
		for (int i = 1; i < n; i++) {
			if (analysis.cracked[i] < 0 || analysis.cracked[i - 1] < 0
					|| analysis.cracked[i] - analysis.cracked[i - 1] != analysis.videoTimes[i] - analysis.videoTimes[i - 1]) {
				analysis.breaks.add(i);
			}
		}
		int blinking = 0;
		for (HudDecoder.Frame frame : decoded) {
			if (frame.blinking()) {
				blinking++;
			}
		}
		long candidateTotal = 0;
		for (int[] c : analysis.candidates) {
			candidateTotal += c.length;
		}
		log("%s: save at clock %d, resume at clock %d, edited video = %d frames before the cut + %d after, mean %.1f candidates per frame over 2^31, blinking heart frames %d",
				name, recording.saveClock(), recording.resumeClock(), pre.size(), post.size(), candidateTotal / (double) n, blinking);
		log("%s: lines %s, breaks at video frames %s", name, describe(analysis.lines), analysis.breaks);
		int from = Math.max(0, analysis.cutIndex - 3);
		int to = Math.min(n, analysis.cutIndex + 3);
		for (int i = from; i < to; i++) {
			log("%s: video frame %d clock %d cracked uptime %d (Hud.tickCount %d)%s", name, analysis.videoTimes[i], analysis.frames.get(i).clock(),
					analysis.cracked[i], analysis.frames.get(i).hudTick(), i == analysis.cutIndex ? "  <- first frame after the cut" : "");
		}
		reportHiddenFrames(decoder, recording, all, lastBefore, firstAfter, analysis.frames.get(analysis.cutIndex - 1).hudTick());
		writeSeries(root.resolve(name + "-series.csv"), analysis);
		plot(root.resolve(name + "-plot.png"), name, analysis);
		return analysis;
	}

	private static void reportHiddenFrames(HudDecoder decoder, FrameRecorder.Recording recording, List<FrameRecorder.Frame> all, long lastBefore,
			long firstAfter, int savedTick) {
		List<String> cracked = new ArrayList<>();
		for (FrameRecorder.Frame frame : all) {
			if (frame.clock() <= lastBefore || frame.clock() >= firstAfter) {
				continue;
			}
			HudDecoder.Frame decoded = decode(decoder, recording, frame);
			if (!decoded.confident() || !decoded.shiftsInVanillaRange() || !decoded.foodJolt() || !decoded.heartsShake()) {
				continue;
			}
			int[] candidates = TickCracker.crack(decoded, 0, SMALL_RANGE);
			cracked.add("clock " + frame.clock() + " -> " + (candidates.length == 1 ? Integer.toString(candidates[0]) : java.util.Arrays.toString(candidates)));
		}
		log("%s: frames cut out by the editor that show the saved HUD (4 HP, food 0) and their cracked tick (saved tick %d): %s",
				recording.name(), savedTick, cracked);
	}

	private static String describe(List<TickCracker.Line> lines) {
		List<String> parts = new ArrayList<>();
		for (TickCracker.Line line : lines) {
			parts.add("offset " + line.offset() + " x" + line.support() + " (frames " + line.frames().getFirst() + ".." + line.frames().getLast() + ")");
		}
		return parts.toString();
	}

	private static void writeSeries(Path path, Analysis analysis) {
		StringBuilder out = new StringBuilder("video_frame,recorder_clock,cracked_uptime,candidates_2_31,hud_tick_truth,segment\n");
		for (int i = 0; i < analysis.frames.size(); i++) {
			out.append(analysis.videoTimes[i]).append(',').append(analysis.frames.get(i).clock()).append(',').append(analysis.cracked[i]).append(',')
					.append(analysis.candidates[i].length).append(',').append(analysis.frames.get(i).hudTick()).append(',')
					.append(i < analysis.cutIndex ? "before" : "after").append('\n');
		}
		try {
			Files.writeString(path, out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void plot(Path path, String name, Analysis analysis) {
		int width = 900;
		int height = 500;
		int margin = 50;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		int n = analysis.frames.size();
		long minY = Long.MAX_VALUE;
		long maxY = Long.MIN_VALUE;
		for (int value : analysis.cracked) {
			minY = Math.min(minY, value);
			maxY = Math.max(maxY, value);
		}
		long spanY = Math.max(1, maxY - minY);
		int spanX = Math.max(1, analysis.videoTimes[n - 1]);
		g.setColor(Color.GRAY);
		g.drawRect(margin, margin, width - 2 * margin, height - 2 * margin);
		int cutX = margin + (int) ((analysis.videoTimes[analysis.cutIndex] - 0.5) * (width - 2 * margin) / spanX);
		g.setColor(new Color(220, 120, 0));
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
		g.drawLine(cutX, margin, cutX, height - margin);
		g.setStroke(new BasicStroke(1));
		for (int i = 0; i < n; i++) {
			int x = margin + analysis.videoTimes[i] * (width - 2 * margin) / spanX;
			int y = height - margin - (int) ((analysis.cracked[i] - minY) * (height - 2 * margin) / spanY);
			g.setColor(i < analysis.cutIndex ? new Color(30, 90, 200) : new Color(200, 40, 40));
			g.fillOval(x - 3, y - 3, 6, 6);
		}
		g.setColor(Color.BLACK);
		g.drawString(name + ": video frame (x) vs cracked Hud.tickCount (y), dashed = cut, lines " + analysis.lines.size(), margin, margin - 20);
		g.drawString(Long.toString(minY), 5, height - margin);
		g.drawString(Long.toString(maxY), 5, margin + 10);
		g.drawString("0", margin, height - margin + 15);
		g.drawString(Integer.toString(spanX), width - margin - 20, height - margin + 15);
		g.dispose();
		try {
			ImageIO.write(image, "png", path.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
