package net.swofty.dreammanhunt.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;
import net.swofty.dreammanhunt.client.ui.SavestateList;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

import java.util.HashSet;
import java.util.Set;

public final class MiningGameTest implements FabricClientGameTest {
	private static final BlockPos LOG = new BlockPos(0, -59, -2);
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("mining")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(20);

			int fullTicks = mineFromScratch(context, server);
			log("mining an oak log by hand takes %d ticks", fullTicks);

			placeLog(context, server);
			context.getInput().holdMouse(LEFT);
			context.waitTicks(fullTicks / 2);
			Set<String> before = serverIds(server);
			context.getInput().pressKey(SavestateKeys.SAVE);
			String id = awaitNewState(context, server, before);
			float progress = context.computeOnClient(mc -> ClientStateManager.get().snapshot(id).getCompoundOrEmpty("mining").getFloatOr("progress", -1.0F));
			if (progress < 0.0F) {
				log("snapshot has no mining progress, assuming half way");
				progress = 0.5F;
			}
			check(progress > 0.2F && progress < 0.9F, "saved mining progress " + progress + " should be part way");
			context.getInput().releaseMouse(LEFT);
			context.waitTicks(30);
			check(server.computeOnServer(s -> !s.overworld().getBlockState(LOG).isAir()), "the log must still stand before the restore");

			int restores = SavestateClientNetwork.restoresCompleted();
			context.getInput().pressKey(SavestateKeys.MENU);
			context.waitForScreen(SavestateMenuScreen.class);
			context.waitFor(mc -> mc.gui.screen() instanceof SavestateMenuScreen menu && menu.hasList(), 200);
			context.waitTicks(5);
			SavestateList.Entry entry = context.computeOnClient(mc -> ((SavestateMenuScreen) mc.gui.screen()).list().children().stream()
					.filter(e -> e.info().id().equals(id)).findFirst().orElseThrow());
			clickAt(context, entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0, entry.getContentY() + entry.getContentHeight() / 2.0);
			context.getInput().holdMouse(LEFT);
			context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restores, 1200);
			server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 200);

			int expected = Math.round((1.0F - progress) * fullTicks);
			int ticks = 0;
			while (!server.computeOnServer(s -> s.overworld().getBlockState(LOG).isAir())) {
				check(ticks < fullTicks + 40, "the log never broke after the restore (server rejected the restored mining)");
				context.waitTick();
				ticks++;
			}
			context.getInput().releaseMouse(LEFT);
			log("after the restore the log broke in %d ticks, expected about %d of %d", ticks, expected, fullTicks);
			check(Math.abs(ticks - expected) <= 3, "restored mining took " + ticks + " ticks, expected about " + expected);
			context.waitTicks(20);
			check(server.computeOnServer(s -> s.overworld().getBlockState(LOG).isAir()), "the broken log came back on the server");
			check(context.computeOnClient(mc -> mc.level.getBlockState(LOG).isAir()), "the broken log came back on the client");
			log("PASS");
		}
	}

	private static int mineFromScratch(ClientGameTestContext context, TestServerContext server) {
		placeLog(context, server);
		context.getInput().holdMouse(LEFT);
		int ticks = 0;
		while (!server.computeOnServer(s -> s.overworld().getBlockState(LOG).isAir())) {
			check(ticks < 400, "could not mine the reference log");
			context.waitTick();
			ticks++;
		}
		context.getInput().releaseMouse(LEFT);
		context.waitTicks(10);
		return ticks;
	}

	private static void placeLog(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			s.overworld().setBlockAndUpdate(LOG, Blocks.OAK_LOG.defaultBlockState());
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				player.getInventory().clearContent();
			}
		});
		context.waitTicks(5);
		context.getInput().lookAt(LOG);
		context.waitTicks(2);
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
		check(after.size() == 1, "expected one new savestate, got " + after);
		String id = after.iterator().next();
		context.waitFor(mc -> ClientStateManager.get().has(id), 200);
		return id;
	}

	private static void clickAt(ClientGameTestContext context, double guiX, double guiY) {
		double[] window = context.computeOnClient(mc -> {
			Window w = mc.getWindow();
			return new double[]{guiX * w.getScreenWidth() / w.getGuiScaledWidth(), guiY * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTick();
		context.getInput().pressMouse(LEFT);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void log(String format, Object... args) {
		System.out.println("[mining] " + String.format(format, args));
	}
}
