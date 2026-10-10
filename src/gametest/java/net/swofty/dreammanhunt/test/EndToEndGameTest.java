package net.swofty.dreammanhunt.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.ClientConfig;
import net.swofty.dreammanhunt.client.ui.CutMarker;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;
import net.swofty.dreammanhunt.client.ui.SavestateList;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.client.ui.ThumbnailManager;
import net.swofty.dreammanhunt.server.coordinator.SavestateConfig;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import net.swofty.dreammanhunt.server.coordinator.SavestatePermissions;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EndToEndGameTest implements FabricClientGameTest {
	private static final Identifier ADVANCEMENT = Identifier.withDefaultNamespace("story/mine_stone");
	private static final AtomicBoolean LISTENERS = new AtomicBoolean();
	private static volatile int seenSaveRequests;
	private static volatile int hudTickAtCapture = -1;
	private static volatile int seenRestores;
	private static volatile int hudTickAtResume = -1;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("e2e")) {
			return;
		}
		installListeners();
		checkKeyMappings();

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);
			String id = saveAndRestore(context, singleplayer.getServer(), "singleplayer");
			saveFromMenuThenDelete(context, singleplayer.getServer());
			cutMarker(context, singleplayer.getServer());
			saveMarker(context, singleplayer.getServer());
			barrierTimesOut(context, singleplayer.getServer(), id);
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties())) {
			String id;
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.waitTicks(20);
				deniedWithoutPermission(context, server);
				String name = context.computeOnClient(mc -> mc.player.getName().getString());
				server.runCommand("op " + name);
				context.waitTicks(10);
				id = saveAndRestore(context, server, "dedicated");
				barrierTimesOut(context, server, id);

				SavestateClientNetwork.setReadySuppressedForTesting(true);
				server.runCommand("savestate load " + id);
				check(server.computeOnServer(s -> SavestateCoordinator.get(s).isBusy()), "restore barrier should be waiting before the client leaves");
			}
			long start = System.nanoTime();
			server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 200);
			long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			check(waited < 5000, "barrier should finish as soon as the only participant leaves, took " + waited + " ms");
			check(server.computeOnServer(s -> !s.tickRateManager().isFrozen() && !SavestateCoordinator.isPlayerTickBlocked(s)),
					"ticking should resume after the participant left");
			SavestateClientNetwork.setReadySuppressedForTesting(false);
		}
	}

	private static Properties serverProperties() {
		Properties properties = new Properties();
		try (ServerSocket socket = new ServerSocket(0)) {
			properties.setProperty("server-port", Integer.toString(socket.getLocalPort()));
		} catch (IOException e) {
			throw new AssertionError("no free port for the dedicated server", e);
		}
		return properties;
	}

	private record Frame(long frame, boolean cut, boolean save, int restores, boolean restoring) {
	}

	private static final List<Frame> FRAMES = new CopyOnWriteArrayList<>();
	private static volatile boolean recordFrames;

	private static void installListeners() {
		if (!LISTENERS.compareAndSet(false, true)) {
			return;
		}
		CutMarker.addFrameListener((frame, cut, save) -> {
			if (recordFrames) {
				FRAMES.add(new Frame(frame, cut, save, SavestateClientNetwork.restoresCompleted(), ClientStateManager.get().isRestoring()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			int requests = SavestateClientNetwork.saveRequests();
			if (requests != seenSaveRequests) {
				seenSaveRequests = requests;
				hudTickAtCapture = hudTick(mc);
			}
		});
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			int restores = SavestateClientNetwork.restoresCompleted();
			if (restores != seenRestores) {
				seenRestores = restores;
				hudTickAtResume = hudTick(mc);
			}
		});
	}

	private static int hudTick(Minecraft minecraft) {
		try {
			Field field = Hud.class.getDeclaredField("tickCount");
			field.setAccessible(true);
			return field.getInt(minecraft.gui.hud);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Hud.tickCount not readable", e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static boolean fakeEngine() {
		return Boolean.getBoolean("dreammanhunt.e2e.fakeEngine");
	}

	private static void checkKeyMappings() {
		check(SavestateKeys.SAVE.getCategory().equals(SavestateKeys.CATEGORY), "save key must live in the DreamManhunt category");
		check(SavestateKeys.MENU.getCategory().equals(SavestateKeys.CATEGORY), "menu key must live in the DreamManhunt category");
		check(SavestateKeys.CATEGORY.id().equals(Identifier.fromNamespaceAndPath("dreammanhunt", "main")), "category id");
		check(SavestateKeys.SAVE.getDefaultKey().getValue() == InputConstants.KEY_K, "save key defaults to K");
		check(SavestateKeys.MENU.getDefaultKey().getValue() == InputConstants.KEY_J, "menu key defaults to J");
	}

	private static ServerPlayer onlyPlayer(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		check(players.size() == 1, "expected exactly one player, found " + players.size());
		return players.getFirst();
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
		check(after.size() == 1, "expected exactly one new savestate, got " + after);
		String id = after.iterator().next();
		context.waitFor(mc -> SavestateClientNetwork.states() != null
				&& SavestateClientNetwork.states().stream().anyMatch(info -> info.id().equals(id)), 200);
		context.waitFor(mc -> Files.isRegularFile(ThumbnailManager.file(id)), 200);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> ClientStateManager.get().has(id)), "client snapshot missing for " + id);
		try (InputStream in = Files.newInputStream(ThumbnailManager.file(id)); NativeImage image = NativeImage.read(in)) {
			check(image.getWidth() == ThumbnailManager.WIDTH && image.getHeight() == ThumbnailManager.HEIGHT,
					"thumbnail size " + image.getWidth() + "x" + image.getHeight());
		} catch (IOException e) {
			throw new AssertionError("thumbnail unreadable", e);
		}
		return id;
	}

	private record Baseline(Vec3 position, BlockPos markerPos, BlockState marker, ItemStack slot0, boolean advancementDone) {
	}

	private static Baseline baseline(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			ServerLevel level = player.level();
			BlockPos marker = player.blockPosition().offset(2, 0, 2);
			AdvancementHolder advancement = s.getAdvancements().get(ADVANCEMENT);
			return new Baseline(player.position(), marker, level.getBlockState(marker), player.getInventory().getItem(0).copy(),
					advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone());
		});
	}

	private static void mutate(TestServerContext server, Baseline base) {
		server.runOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			player.level().setBlockAndUpdate(base.markerPos(), Blocks.GOLD_BLOCK.defaultBlockState());
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
			player.connection.teleport(base.position().x + 12.0, base.position().y, base.position().z, player.getYRot(), player.getXRot());
		});
		server.runCommand("advancement grant @a only " + ADVANCEMENT);
	}

	private static void verifyRestored(TestServerContext server, Baseline base, String label) {
		server.runOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			double distance = player.position().distanceTo(base.position());
			check(distance < 0.5, label + ": player not back at the saved position (off by " + distance + ")");
			if (fakeEngine()) {
				return;
			}
			check(player.level().getBlockState(base.markerPos()).equals(base.marker()), label + ": block not restored");
			check(ItemStack.matches(player.getInventory().getItem(0), base.slot0()), label + ": inventory not restored");
			AdvancementHolder advancement = s.getAdvancements().get(ADVANCEMENT);
			check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone() == base.advancementDone(),
					label + ": advancement progress not restored");
		});
	}

	private static void clearToasts(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(2);
	}

	private static void screenshotAtGuiScales(ClientGameTestContext context, String name) {
		int original = context.computeOnClient(mc -> mc.options.guiScale().get());
		for (int scale = 1; scale <= 2; scale++) {
			int guiScale = scale;
			context.runOnClient(mc -> mc.options.guiScale().set(guiScale));
			context.waitTicks(3);
			context.takeScreenshot(name + "-gui" + guiScale);
		}
		context.runOnClient(mc -> mc.options.guiScale().set(original));
		context.waitTicks(3);
	}

	private static SavestateMenuScreen openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(SavestateKeys.MENU);
		context.waitForScreen(SavestateMenuScreen.class);
		context.waitFor(mc -> mc.gui.screen() instanceof SavestateMenuScreen menu && menu.hasList(), 200);
		return context.computeOnClient(mc -> (SavestateMenuScreen) mc.gui.screen());
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

	private static SavestateList.Entry entry(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> {
			SavestateMenuScreen menu = (SavestateMenuScreen) mc.gui.screen();
			check(menu != null && menu.list() != null, "menu not open");
			return menu.list().children().stream()
					.filter(entry -> entry.info().id().equals(id))
					.findFirst()
					.orElseThrow(() -> new AssertionError("menu has no entry for " + id));
		});
	}

	private static String saveAndRestore(ClientGameTestContext context, TestServerContext server, String label) {
		Set<String> before = serverIds(server);
		Baseline base = baseline(server);

		context.getInput().pressKey(SavestateKeys.SAVE);
		String id = awaitNewState(context, server, before);
		int captured = context.computeOnClient(mc -> ClientStateManager.get().snapshot(id).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1));
		check(captured >= hudTickAtCapture && hudTickAtCapture >= 0, "snapshot hud tick " + captured + " is before the save key tick " + hudTickAtCapture);

		mutate(server, base);
		context.waitTicks(20);
		context.takeScreenshot("e2e-" + label + "-mutated");

		openMenu(context);
		context.waitTicks(5);
		clearToasts(context);
		context.takeScreenshot("e2e-" + label + "-menu");
		check(context.computeOnClient(mc -> {
			SavestateMenuScreen menu = (SavestateMenuScreen) mc.gui.screen();
			return menu.list().children().getFirst().info().id().equals(id);
		}), "newest savestate must be listed first");
		SavestateList.Entry entry = entry(context, id);

		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		clickAt(context, entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0, entry.getContentY() + entry.getContentHeight() / 2.0);
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
		context.waitTick();
		server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 200);
		check(context.computeOnClient(mc -> mc.gui.screen() == null), "menu should be closed after choosing a savestate");
		check(hudTickAtResume == captured, label + ": Hud.tickCount after resume " + hudTickAtResume + " != captured " + captured);
		check(server.computeOnServer(s -> !s.tickRateManager().isFrozen() && !SavestateCoordinator.isPlayerTickBlocked(s)),
				label + ": ticking should resume after the barrier");
		verifyRestored(server, base, label);
		context.waitTicks(10);
		context.takeScreenshot("e2e-" + label + "-restored");
		return id;
	}

	private static void saveFromMenuThenDelete(ClientGameTestContext context, TestServerContext server) {
		Set<String> before = serverIds(server);
		openMenu(context);
		context.clickScreenButton(Component.translatable("dreammanhunt.menu.save_new").getString());
		check(context.computeOnClient(mc -> mc.gui.screen() == null), "menu should close before saving");
		String id = awaitNewState(context, server, before);

		openMenu(context);
		context.waitTicks(5);
		clearToasts(context);
		screenshotAtGuiScales(context, "e2e-singleplayer-menu-two-states");
		Button delete = entry(context, id).deleteButton();
		clickAt(context, delete.getX() + delete.getWidth() / 2.0, delete.getY() + delete.getHeight() / 2.0);
		context.waitForScreen(ConfirmScreen.class);
		context.takeScreenshot("e2e-singleplayer-delete-confirm");
		context.clickScreenButton(Component.translatable("dreammanhunt.menu.delete").getString());
		context.waitForScreen(SavestateMenuScreen.class);
		server.waitFor(s -> SavestateEngine.get(s).find(id).isEmpty(), 200);
		context.waitFor(mc -> SavestateClientNetwork.states().stream().noneMatch(info -> info.id().equals(id)), 200);
		context.waitFor(mc -> !Files.exists(ThumbnailManager.file(id)), 200);
		check(context.computeOnClient(mc -> !ClientStateManager.get().has(id)), "client snapshot should be dropped after delete");
		context.waitTicks(2);
		context.takeScreenshot("e2e-singleplayer-menu-after-delete");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static void barrierTimesOut(ClientGameTestContext context, TestServerContext server, String id) {
		SavestateConfig original = SavestateConfig.get();
		SavestateConfig.set(new SavestateConfig(original.allowEveryone(), original.permissionLevel(), 2));
		SavestateClientNetwork.setReadySuppressedForTesting(true);
		try {
			int restoresBefore = SavestateClientNetwork.restoresCompleted();
			long start = System.nanoTime();
			server.runCommand("savestate load " + id);
			check(server.computeOnServer(s -> SavestateCoordinator.get(s).isBusy() && s.tickRateManager().isFrozen()
					&& SavestateCoordinator.isPlayerTickBlocked(s)), "barrier should freeze the server while waiting");
			long[] frozenA = server.computeOnServer(s -> new long[]{s.overworld().getGameTime(), onlyPlayer(s).tickCount});
			context.waitTicks(10);
			long[] frozenB = server.computeOnServer(s -> new long[]{s.overworld().getGameTime(), onlyPlayer(s).tickCount});
			check(frozenA[0] == frozenB[0], "level time advanced during the barrier");
			check(frozenA[1] == frozenB[1], "player ticked during the barrier");
			if (server.computeOnServer(s -> s instanceof IntegratedServer)) {
				context.setScreen(() -> new PauseScreen(true));
				context.waitTicks(5);
				check(server.computeOnServer(s -> !s.isPaused()), "a pause screen must not pause the integrated server during a restore barrier");
				context.setScreen(() -> null);
			}
			server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 400);
			long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			check(waited >= 1500 && waited < 10000, "barrier timeout took " + waited + " ms, expected about 2000");
			context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 200);
			check(server.computeOnServer(s -> !s.tickRateManager().isFrozen() && !SavestateCoordinator.isPlayerTickBlocked(s)),
					"ticking should resume after the timeout");
		} finally {
			SavestateClientNetwork.setReadySuppressedForTesting(false);
			SavestateConfig.set(original);
		}
		context.waitTicks(10);
	}

	private static void deniedWithoutPermission(ClientGameTestContext context, TestServerContext server) {
		Set<String> before = serverIds(server);
		context.getInput().pressKey(SavestateKeys.SAVE);
		context.waitTicks(20);
		check(serverIds(server).equals(before), "a player without permission must not be able to save");
		check(!server.computeOnServer(s -> SavestatePermissions.canManage(onlyPlayer(s))), "non-operators are denied by default");
		SavestateConfig original = SavestateConfig.get();
		SavestateConfig.set(new SavestateConfig(true, original.permissionLevel(), original.readyTimeoutSeconds()));
		boolean everyone = server.computeOnServer(s -> SavestatePermissions.canManage(onlyPlayer(s)));
		SavestateConfig.set(original);
		check(everyone, "allowEveryone must grant permission");

		SavestateMenuScreen menu = openMenu(context);
		check(!menu.canManage(), "menu must be read-only without permission");
		check(menu.list() != null && menu.list().children().size() == before.size(), "menu should list what the server has");
		clearToasts(context);
		context.takeScreenshot("e2e-dedicated-read-only-empty");

		server.runCommand("savestate list");
		server.runCommand("savestate save Console state");
		Set<String> after = serverIds(server);
		check(after.size() == before.size() + 1, "console save should work");
		context.waitFor(mc -> mc.gui.screen() instanceof SavestateMenuScreen open && open.list() != null
				&& open.list().children().size() == after.size(), 200);
		context.waitTicks(5);
		clearToasts(context);
		context.takeScreenshot("e2e-dedicated-read-only");
		String consoleId = after.stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
		SavestateList.Entry entry = entry(context, consoleId);
		clickAt(context, entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0, entry.getContentY() + entry.getContentHeight() / 2.0);
		context.waitTicks(10);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof SavestateMenuScreen), "read-only click must not restore");
		check(!server.computeOnServer(s -> SavestateCoordinator.get(s).isBusy()), "read-only click must not start a restore");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static NativeImage readImage(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			return NativeImage.read(in);
		} catch (IOException e) {
			throw new AssertionError("unreadable image " + path, e);
		}
	}

	private static double markerFraction(NativeImage image, int argb, int fromX, int toX) {
		long hits = 0;
		long total = 0;
		for (int y = 0; y < image.getHeight(); y += 2) {
			for (int x = fromX; x < toX; x += 2) {
				total++;
				if ((image.getPixel(x, y) & 0xFFFFFF) == (argb & 0xFFFFFF)) {
					hits++;
				}
			}
		}
		return total == 0 ? 0.0 : hits / (double) total;
	}

	private static final int GREEN_ARGB = 0xFF00FF00;
	private static final int RED_ARGB = 0xFFFF0000;

	private static void checkMarker(Path shot, int argb, boolean expected, String what) {
		try (NativeImage image = readImage(shot)) {
			int inside = (int) (image.getWidth() * (CutMarker.WIDTH_FRACTION - 0.01F));
			double band = markerFraction(image, argb, 0, inside);
			double rest = markerFraction(image, argb, (int) (image.getWidth() * (CutMarker.WIDTH_FRACTION + 0.02F)), image.getWidth());
			System.out.printf("[e2e] %s: marker colour covers %.4f of the left band and %.4f of the rest (%s)%n", what, band, rest, shot.getFileName());
			if (expected) {
				check(band == 1.0, what + ": marker band not solid (" + band + ")");
				check(rest < 0.01, what + ": marker colour outside the band (" + rest + ")");
			} else {
				check(band < 0.05, what + ": unexpected marker in the band (" + band + ")");
			}
		}
	}

	private static double[] imageDiff(Path a, Path b) {
		try (NativeImage x = readImage(a); NativeImage y = readImage(b)) {
			check(x.getWidth() == y.getWidth() && x.getHeight() == y.getHeight(), "screenshot sizes differ");
			long total = 0;
			long over = 0;
			int pixels = x.getWidth() * x.getHeight();
			for (int j = 0; j < x.getHeight(); j++) {
				for (int i = 0; i < x.getWidth(); i++) {
					int p = x.getPixel(i, j);
					int q = y.getPixel(i, j);
					int worst = 0;
					for (int shift = 0; shift <= 16; shift += 8) {
						int delta = Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF));
						total += delta;
						worst = Math.max(worst, delta);
					}
					if (worst > 16) {
						over++;
					}
				}
			}
			return new double[]{total / (pixels * 3.0), over / (double) pixels};
		}
	}

	private static int currentHud(ClientGameTestContext context) {
		return context.computeOnClient(EndToEndGameTest::hudTick);
	}

	private static void cutMarker(ClientGameTestContext context, TestServerContext server) {
		check(ClientConfig.get().cutMarker() && !ClientConfig.get().saveMarker(), "cut marker on and save marker off by default");
		Set<String> before = serverIds(server);
		FRAMES.clear();
		recordFrames = true;
		int requests = SavestateClientNetwork.saveRequests();
		context.getInput().pressKey(SavestateKeys.SAVE);
		context.waitFor(mc -> SavestateClientNetwork.saveRequests() > requests && seenSaveRequests == SavestateClientNetwork.saveRequests(), 100);
		int captured = hudTickAtCapture;
		Map<Integer, Path> afterCapture = new HashMap<>();
		for (int offset = 0; offset <= 3; offset++) {
			int target = captured + offset;
			while (currentHud(context) < target) {
				context.waitTick();
			}
			int hud = currentHud(context);
			check(hud == target, "post-capture frames ran past hud " + target + " (at " + hud + ")");
			Path shot = context.takeScreenshot("e2e-cut-after-capture-hud" + hud);
			afterCapture.put(hud, shot);
			checkMarker(shot, GREEN_ARGB, false, "frame after the save at hud " + hud);
			checkMarker(shot, RED_ARGB, false, "frame after the save at hud " + hud + " with saveMarker off");
		}
		String id = awaitNewState(context, server, before);
		recordFrames = false;
		check(FRAMES.stream().noneMatch(Frame::cut), "no cut marker during the save flow");
		check(FRAMES.stream().noneMatch(Frame::save), "no save marker while saveMarker is off");
		checkMarker(ThumbnailManager.file(id), GREEN_ARGB, false, "thumbnail");

		server.runOnServer(s -> {
			ServerPlayer player = onlyPlayer(s);
			player.connection.teleport(player.getX() + 6.0, player.getY(), player.getZ(), player.getYRot() + 40.0F, player.getXRot());
		});
		context.waitTicks(20);

		SavestateConfig original = SavestateConfig.get();
		SavestateConfig.set(new SavestateConfig(original.allowEveryone(), original.permissionLevel(), 60));
		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		try {
			SavestateClientNetwork.setReadySuppressedForTesting(true);
			FRAMES.clear();
			recordFrames = true;
			server.runCommand("savestate load " + id);
			context.waitFor(mc -> SavestateClientNetwork.hasHeldReady(), 20 * 30);
			context.waitTicks(5);
			Path frozen = context.takeScreenshot("e2e-cut-frozen");
			checkMarker(frozen, GREEN_ARGB, true, "frozen frame");
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(3);
			checkMarker(context.takeScreenshot("e2e-cut-frozen-over-screen"), GREEN_ARGB, true, "frozen frame under an open screen");
			context.setScreen(() -> null);
			context.waitTicks(3);
			Path lastFrozen = context.takeScreenshot("e2e-cut-last-frozen");
			checkMarker(lastFrozen, GREEN_ARGB, true, "last frozen frame");
			check(SavestateClientNetwork.restoresCompleted() == restoresBefore, "restore resumed before the ready was released");
			context.runOnClient(mc -> SavestateClientNetwork.releaseHeldReady());
			context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 20 * 30);
			int hud = currentHud(context);
			Path resumed = context.takeScreenshot("e2e-cut-first-resumed-hud" + hud);
			context.waitTicks(10);
			recordFrames = false;
			checkMarker(resumed, GREEN_ARGB, false, "first resumed frame");
			Path reference = afterCapture.get(hud);
			check(reference != null, "no post-capture frame at hud " + hud + " (captured at " + captured + ", have " + afterCapture.keySet() + ")");
			double[] diff = imageDiff(reference, resumed);
			System.out.printf("[e2e] first resumed frame vs frame after the save at hud %d: mean %.4f, over16 %.4f%%%n", hud, diff[0], diff[1] * 100);
			check(diff[0] <= 3.0 && diff[1] <= 0.02, "first resumed frame differs from the frame after the save at hud " + hud
					+ " (mean " + diff[0] + ", over16 " + diff[1] + ")");
		} finally {
			SavestateClientNetwork.setReadySuppressedForTesting(false);
			SavestateConfig.set(original);
		}

		int firstCut = -1;
		for (int i = 0; i < FRAMES.size(); i++) {
			if (FRAMES.get(i).cut()) {
				firstCut = i;
				break;
			}
		}
		check(firstCut >= 0, "no frame carried the cut marker");
		int firstResumed = -1;
		for (int i = firstCut; i < FRAMES.size(); i++) {
			if (FRAMES.get(i).restores() > restoresBefore) {
				firstResumed = i;
				break;
			}
		}
		check(firstResumed > firstCut, "no resumed frame recorded");
		for (int i = 0; i < firstCut; i++) {
			check(!FRAMES.get(i).restoring(), "frame " + i + " was frozen without the cut marker");
		}
		for (int i = firstCut; i < firstResumed; i++) {
			check(FRAMES.get(i).cut(), "frozen frame " + i + " lacks the cut marker");
		}
		for (int i = firstResumed; i < FRAMES.size(); i++) {
			check(!FRAMES.get(i).cut(), "resumed frame " + i + " carries the cut marker");
		}
		System.out.printf("[e2e] cut marker on %d frozen frames, first resumed frame #%d has none, %d resumed frames checked%n",
				firstResumed - firstCut, FRAMES.get(firstResumed).frame(), FRAMES.size() - firstResumed);
	}

	private static void saveMarker(ClientGameTestContext context, TestServerContext server) {
		ClientConfig original = ClientConfig.get();
		ClientConfig.set(new ClientConfig(original.cutMarker(), true));
		try {
			Set<String> before = serverIds(server);
			int requests = SavestateClientNetwork.saveRequests();
			context.getInput().pressKey(SavestateKeys.SAVE);
			context.waitFor(mc -> SavestateClientNetwork.saveRequests() > requests, 100);
			context.waitTicks(2);
			checkMarker(context.takeScreenshot("e2e-save-marker-on"), RED_ARGB, true, "frame right after the save with saveMarker on");
			String id = awaitNewState(context, server, before);
			checkMarker(ThumbnailManager.file(id), RED_ARGB, false, "thumbnail with saveMarker on");
			context.waitTicks(CutMarker.SAVE_MARKER_TICKS + 2);
			checkMarker(context.takeScreenshot("e2e-save-marker-gone"), RED_ARGB, false, "frame after the save marker expired");
		} finally {
			ClientConfig.set(original);
		}
	}
}
