package net.swofty.dreammanhunt.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientSnapshot;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

public final class TrackingSyncGameTest implements FabricClientGameTest {
	private static final int LEAD_TICKS = 1;
	private static final int LOG_TICKS = 40;
	private static final int[] FRAME_OFFSETS = {0, 3, 10, 20, 40};

	private static boolean scriptRegistered;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("trackingsync")) {
			return;
		}
		if (!scriptRegistered) {
			scriptRegistered = true;
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_LEVEL_TICK.register(TrackingSyncGameTest::moveScripted);
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			BlockPos o = context.computeOnClient(mc -> mc.player.blockPosition());
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("difficulty easy");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("kill @e[type=!minecraft:player]");
			context.runOnClient(mc -> mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF));
			int x = o.getX();
			int y = o.getY();
			int z = o.getZ();
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 12, y, z - 2, x + 12, y + 6, z + 24));
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:water", x + 6, y, z + 14));
			server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 25", x, y + 1, z - 1));
			context.waitTicks(40);
			String nbt = "{PersistenceRequired:1b,Silent:1b}";
			summon(server, "minecraft:zombie", x - 3, y, z + 8, nbt);
			summon(server, "minecraft:chicken", x + 2, y, z + 6, nbt);
			summon(server, "minecraft:villager", x - 1, y, z + 10, nbt);
			summon(server, "minecraft:horse", x + 3, y, z + 10, "{PersistenceRequired:1b,Silent:1b,Tame:1b,Passengers:[{id:\"minecraft:zombie\",PersistenceRequired:1b,Silent:1b}]}");
			summon(server, "minecraft:item", x, y + 2, z + 5, "{Item:{id:\"minecraft:diamond\",count:1},Motion:[0.08d,0.25d,0.05d],PickupDelay:32767s,Age:-32768s}");
			summon(server, "minecraft:oak_boat", x + 5, y, z + 13, "{}");
			String scripted = "{PersistenceRequired:1b,Silent:1b,NoAI:1b,NoGravity:1b,Tags:[\"trk_scripted\"]}";
			summon(server, "minecraft:zombie", x - 6, y, z + 12, scripted);
			summon(server, "minecraft:chicken", x - 4, y, z + 16, scripted);
			summon(server, "minecraft:villager", x + 7, y, z + 9, scripted);
			summon(server, "minecraft:horse", x + 8, y, z + 18, "{PersistenceRequired:1b,Silent:1b,NoAI:1b,NoGravity:1b,Tame:1b,Tags:[\"trk_scripted\"],Passengers:[{id:\"minecraft:skeleton\",PersistenceRequired:1b,Silent:1b,NoAI:1b}]}");
			context.waitTicks(60);
			summon(server, "minecraft:arrow", x - 6, y + 1, z + 4, "{Motion:[0.6d,0.35d,0.9d],pickup:0b}");
			context.waitTicks(3);

			Set<String> before = ids(server);
			int restoresBefore = SavestateClientNetwork.restoresCompleted();
			server.runCommand("savestate save trackingsync");
			server.waitFor(s -> SavestateEngine.get(s).list().size() > before.size(), 200);
			Set<String> after = ids(server);
			after.removeAll(before);
			String id = after.iterator().next();
			context.waitFor(mc -> ClientStateManager.get().has(id), 200);
			int capturedHud = context.computeOnClient(mc -> ClientStateManager.get().snapshot(id)).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1);
			int base = capturedHud + LEAD_TICKS;

			Timeline uncut = record(context, server, base, "trk-cap");
			context.waitTicks(40);
			server.runCommand("savestate load " + id);
			context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
			int resumedHud = context.computeOnClient(TrackingSyncGameTest::hudTicks);
			if (resumedHud > base) {
				throw new AssertionError("Resumed too late: hud " + resumedHud + " > " + base);
			}
			Timeline restored = record(context, server, base, "trk-res");
			report(uncut, restored);
			server.runCommand("savestate delete " + id);
		}
	}

	private record Timeline(List<Map<String, double[]>> client, List<Map<String, double[]>> server, List<Path> frames) {
	}

	private static Timeline record(ClientGameTestContext context, TestServerContext server, int base, String prefix) {
		List<Map<String, double[]>> client = new ArrayList<>();
		List<Map<String, double[]>> serverSide = new ArrayList<>();
		List<Path> frames = new ArrayList<>();
		int frameIndex = 0;
		for (int offset = 0; offset <= LOG_TICKS; offset++) {
			int target = base + offset;
			while (context.computeOnClient(TrackingSyncGameTest::hudTicks) < target) {
				context.waitTick();
			}
			int hud = context.computeOnClient(TrackingSyncGameTest::hudTicks);
			if (hud != target) {
				throw new AssertionError(prefix + " overshot tick " + target + " (hud " + hud + ")");
			}
			client.add(context.computeOnClient(mc -> positions(mc.level.entitiesForRendering(), mc.player)));
			serverSide.add(server.computeOnServer(s -> positions(s.overworld().getAllEntities(), null)));
			if (frameIndex < FRAME_OFFSETS.length && FRAME_OFFSETS[frameIndex] == offset) {
				frames.add(context.takeScreenshot(prefix + "-t" + offset));
				frameIndex++;
			}
		}
		return new Timeline(client, serverSide, frames);
	}

	private static Map<String, double[]> positions(Iterable<? extends Entity> entities, Entity skip) {
		Map<String, double[]> out = new TreeMap<>();
		for (Entity entity : entities) {
			if (entity == skip || entity instanceof net.minecraft.world.entity.player.Player) {
				continue;
			}
			String key = entity.getType().toShortString() + "/" + entity.getUUID().toString().substring(0, 8);
			double head = entity instanceof LivingEntity living ? living.getYHeadRot() : 0.0;
			out.put(key, new double[]{entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot(), head, entity.xo, entity.yo, entity.zo});
		}
		return out;
	}

	private static void report(Timeline uncut, Timeline restored) {
		Map<String, double[]> worstClient = new TreeMap<>();
		Map<String, double[]> worstServer = new TreeMap<>();
		Map<String, Integer> firstClientTick = new TreeMap<>();
		for (int tick = 0; tick < uncut.client().size(); tick++) {
			compareMaps(uncut.client().get(tick), restored.client().get(tick), worstClient, firstClientTick, tick);
			compareMaps(uncut.server().get(tick), restored.server().get(tick), worstServer, new TreeMap<>(), tick);
		}
		for (String key : worstClient.keySet()) {
			double[] c = worstClient.get(key);
			double[] s = worstServer.getOrDefault(key, new double[]{-1, -1});
			System.out.printf(Locale.ROOT, "[trackingsync] %-28s client maxPos=%.6f maxRot=%.4f firstTick=%s | server maxPos=%.6f maxRot=%.4f%n", key, c[0], c[1],
				firstClientTick.getOrDefault(key, -1), s[0], s[1]);
		}
		for (int i = 0; i < uncut.frames().size(); i++) {
			System.out.printf(Locale.ROOT, "[trackingsync] frame t%d differs %.5f%n", FRAME_OFFSETS[i], compare(uncut.frames().get(i), restored.frames().get(i)));
		}
		for (String key : worstClient.keySet()) {
			double[] s = worstServer.get(key);
			if (worstClient.get(key)[0] > 1e-9 && s != null && s[0] < 1e-9) {
				for (int tick = 0; tick < Math.min(12, uncut.client().size()); tick++) {
					double[] u = uncut.client().get(tick).get(key);
					double[] r = restored.client().get(tick).get(key);
					double[] su = uncut.server().get(tick).get(key);
					System.out.printf(Locale.ROOT, "[trackingsync-debug] %s t%d uncut=%s restored=%s server=%s%n", key, tick, fmt(u), fmt(r), fmt(su));
				}
			}
		}
	}

	private static String fmt(double[] v) {
		return v == null ? "null" : String.format(Locale.ROOT, "(%.5f,%.5f,%.5f|o %.5f,%.5f,%.5f)", v[0], v[1], v[2], v[6], v[7], v[8]);
	}

	private static void compareMaps(Map<String, double[]> a, Map<String, double[]> b, Map<String, double[]> worst, Map<String, Integer> first, int tick) {
		Set<String> keys = new HashSet<>(a.keySet());
		keys.addAll(b.keySet());
		for (String key : keys) {
			double[] x = a.get(key);
			double[] y = b.get(key);
			double[] w = worst.computeIfAbsent(key, k -> new double[2]);
			if (x == null || y == null) {
				w[0] = Double.POSITIVE_INFINITY;
				first.putIfAbsent(key, tick);
				continue;
			}
			double pos = 0;
			for (int i : new int[]{0, 1, 2, 6, 7, 8}) {
				pos = Math.max(pos, Math.abs(x[i] - y[i]));
			}
			double rot = 0;
			for (int i : new int[]{3, 4, 5}) {
				rot = Math.max(rot, Math.abs(net.minecraft.util.Mth.wrapDegrees(x[i] - y[i])));
			}
			if ((pos > 1e-9 || rot > 1e-6) && !first.containsKey(key)) {
				first.put(key, tick);
			}
			w[0] = Math.max(w[0], pos);
			w[1] = Math.max(w[1], rot);
		}
	}

	private static double compare(Path expectedPath, Path actualPath) {
		try {
			BufferedImage expected = ImageIO.read(expectedPath.toFile());
			BufferedImage actual = ImageIO.read(actualPath.toFile());
			long differing = 0;
			for (int yy = 0; yy < expected.getHeight(); yy++) {
				for (int xx = 0; xx < expected.getWidth(); xx++) {
					int p = expected.getRGB(xx, yy);
					int q = actual.getRGB(xx, yy);
					int delta = Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)),
						Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255))));
					if (delta > 24) {
						differing++;
					}
				}
			}
			return differing / (double) (expected.getWidth() * expected.getHeight());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void moveScripted(ServerLevel level) {
		if (!level.tickRateManager().runsNormally()) {
			return;
		}
		for (Entity entity : level.getAllEntities()) {
			if (!entity.entityTags().contains("trk_scripted")) {
				continue;
			}
			int t = entity.tickCount;
			double speed = 0.11 + 0.05 * Math.sin(t * 0.13);
			float yaw = (float) (Math.sin(t * 0.05 + entity.getId()) * 120.0);
			double rad = Math.toRadians(yaw);
			double dx = -Math.sin(rad) * speed;
			double dz = Math.cos(rad) * speed;
			double dy = (t % 37 < 4) ? 0.12 : (t % 37 < 8 ? -0.12 : 0.0);
			entity.setPos(entity.getX() + dx, entity.getY() + dy, entity.getZ() + dz);
			entity.setYRot(yaw);
			entity.setXRot((float) (Math.sin(t * 0.21) * 20.0));
			if (entity instanceof LivingEntity living) {
				living.setYHeadRot(yaw + (float) (Math.sin(t * 0.31) * 50.0));
				living.yBodyRot = yaw;
			}
			entity.setOnGround(t % 37 >= 8);
		}
	}

	private static void summon(TestServerContext server, String type, int x, int y, int z, String nbt) {
		server.runCommand(String.format(Locale.ROOT, "summon %s %d.5 %d %d.5 %s", type, x, y, z, nbt));
	}

	private static Set<String> ids(TestServerContext server) {
		return server.computeOnServer(s -> {
			Set<String> ids = new HashSet<>();
			for (SavestateInfo info : SavestateEngine.get(s).list()) {
				ids.add(info.id());
			}
			return ids;
		});
	}

	private static int hudTicks(net.minecraft.client.Minecraft mc) {
		return ClientSnapshot.counters(mc).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1);
	}
}
