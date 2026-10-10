package net.swofty.dreammanhunt.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

public final class WorldSyncGameTest implements FabricClientGameTest {
	private static final AtomicBoolean LISTENER = new AtomicBoolean();
	private static final Map<Long, List<String>> RECORDING = new ConcurrentHashMap<>();
	private static volatile Scene active;
	private static volatile BlockPos activeOrigin;
	private static volatile boolean recording;

	private final Map<String, String> results = new LinkedHashMap<>();

	private record Scene(String name, int ticks, int radius, int height, Consumer<Env> setup, int settle) {
	}

	private record Env(TestServerContext server, BlockPos o) {
		void cmd(String format, Object... args) {
			this.server.runCommand(String.format(Locale.ROOT, format, args));
		}

		void block(int dx, int dy, int dz, String state) {
			this.cmd("setblock %d %d %d %s", this.o.getX() + dx, this.o.getY() + dy, this.o.getZ() + dz, state);
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
			this.cmd("fill %d %d %d %d %d %d %s", this.o.getX() + x0, this.o.getY() + y0, this.o.getZ() + z0,
				this.o.getX() + x1, this.o.getY() + y1, this.o.getZ() + z1, state);
		}

		void summon(String type, double dx, double dy, double dz, String nbt) {
			this.cmd(("summon %s %.2f %.2f %.2f " + nbt).trim(), type, this.o.getX() + 0.5 + dx, this.o.getY() + dy, this.o.getZ() + 0.5 + dz);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("worldsync")) {
			return;
		}
		installListener();
		String filter = System.getenv().getOrDefault("WORLDSYNC_SCENES", "");
		Set<String> only = filter.isBlank() ? Set.of() : Set.of(filter.split(","));
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			BlockPos spawn = context.computeOnClient(mc -> mc.player.blockPosition());
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty peaceful");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @a");
			int index = 0;
			for (Scene scene : scenes()) {
				if (!only.isEmpty() && !only.contains(scene.name)) {
					continue;
				}
				BlockPos origin = spawn.offset(48 * (index++ % 4) + 24, 0, 48 * (index / 4) + 24);
				try {
					this.results.put(scene.name, runScene(context, server, scene, origin));
				} catch (Throwable t) {
					t.printStackTrace();
					this.results.put(scene.name, "ERROR " + t);
				}
				System.out.printf("[worldsync] %s: %s%n", scene.name, this.results.get(scene.name));
			}
		}
		StringBuilder out = new StringBuilder();
		this.results.forEach((name, status) -> out.append(String.format(Locale.ROOT, "%-18s %s%n", name, status)));
		System.out.println("[worldsync] results\n" + out);
		try {
			Files.writeString(Path.of("worldsync-results.txt"), out.toString());
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static List<Scene> scenes() {
		List<Scene> list = new ArrayList<>();
		list.add(new Scene("redstone", 120, 8, 6, env -> {
			env.fill(-8, -1, -8, 8, -1, 8, "minecraft:stone");
			env.block(0, 0, 0, "minecraft:observer[facing=east]");
			env.block(1, 0, 0, "minecraft:observer[facing=west]");
			env.block(-1, 0, 0, "minecraft:sticky_piston[facing=north]");
			env.block(-1, 0, -1, "minecraft:slime_block");
			env.block(2, 0, 0, "minecraft:note_block");
			env.block(0, 0, 1, "minecraft:redstone_wire");
			env.block(0, 0, 2, "minecraft:repeater[facing=north,delay=4]");
			env.block(0, 0, 3, "minecraft:repeater[facing=north,delay=3]");
			env.block(0, 0, 4, "minecraft:redstone_lamp");
			env.block(1, 0, 1, "minecraft:redstone_wire");
			env.block(2, 0, 1, "minecraft:comparator[facing=west]");
			env.block(3, 0, 1, "minecraft:redstone_wire");
			env.block(4, 0, 1, "minecraft:bell[attachment=floor,facing=north]");
			env.block(3, 0, 2, "minecraft:piston[facing=south]");
			env.block(3, 0, 3, "minecraft:cobblestone");
			env.block(-2, 0, 2, "minecraft:sculk_sensor");
			env.block(-3, 0, 2, "minecraft:redstone_lamp");
		}, 23));
		list.add(new Scene("hoppers", 160, 6, 5, env -> {
			env.fill(-6, -1, -6, 6, -1, 6, "minecraft:stone");
			env.block(0, 3, 0, "minecraft:chest{Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:64},{Slot:1b,id:\"minecraft:raw_gold\",count:64}]}");
			env.block(0, 2, 0, "minecraft:hopper[facing=down]");
			env.block(0, 1, 0, "minecraft:furnace[facing=north]{Items:[{Slot:1b,id:\"minecraft:coal\",count:64}]}");
			env.block(0, 0, 0, "minecraft:hopper[facing=east]");
			env.block(1, 0, 0, "minecraft:chest");
			env.block(2, 0, 2, "minecraft:campfire{Items:[{Slot:0b,id:\"minecraft:beef\",count:1},{Slot:1b,id:\"minecraft:porkchop\",count:1}]}");
			env.block(-2, 0, 0, "minecraft:hopper[facing=east]{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:64}]}");
			env.block(-1, 0, 0, "minecraft:hopper[facing=down]");
			env.block(-1, -1, 0, "minecraft:chest");
			env.block(-2, 0, 2, "minecraft:brewing_stand{Fuel:20b,Items:[{Slot:0b,id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:awkward\"}}},{Slot:3b,id:\"minecraft:sugar\",count:4}]}");
		}, 15));
		list.add(new Scene("growth", 200, 6, 4, env -> {
			env.cmd("gamerule random_tick_speed 60");
			env.fill(-6, -1, -6, 6, -1, 6, "minecraft:farmland[moisture=7]");
			env.fill(-6, 0, -6, 6, 0, -1, "minecraft:wheat[age=0]");
			env.fill(-6, 0, 1, 6, 0, 6, "minecraft:carrots[age=0]");
			env.fill(-6, -1, 0, 6, -1, 0, "minecraft:water");
		}, 5));
		list.add(new Scene("fluids", 120, 8, 6, env -> {
			env.fill(-8, -1, -8, 8, -1, 8, "minecraft:stone");
			env.fill(-3, 0, -3, 3, 2, 3, "minecraft:stone");
			env.block(0, 3, 0, "minecraft:water");
			env.block(2, 3, 2, "minecraft:lava");
		}, 3));
		list.add(new Scene("falling_tnt", 120, 8, 10, env -> {
			env.fill(-8, -1, -8, 8, -1, 8, "minecraft:stone");
			env.fill(-2, 4, -2, 2, 6, 2, "minecraft:sand");
			env.fill(3, 0, -3, 6, 2, 3, "minecraft:oak_planks");
			env.summon("minecraft:tnt", 4, 3, 0, "{fuse:45s}");
			env.summon("minecraft:tnt", 5, 3, 1, "{fuse:52s}");
			env.block(-2, 3, -2, "minecraft:air");
		}, 2));
		list.add(new Scene("fire", 160, 8, 6, env -> {
			env.cmd("gamerule random_tick_speed 30");
			env.fill(-8, -1, -8, 8, -1, 8, "minecraft:stone");
			env.fill(-4, 0, -4, 4, 0, 4, "minecraft:oak_planks");
			env.fill(-4, 1, -4, 4, 1, 4, "minecraft:white_wool");
			env.block(0, 2, 0, "minecraft:fire");
			env.block(3, 2, 3, "minecraft:fire");
		}, 10));
		list.add(new Scene("items_xp", 160, 6, 5, env -> {
			env.fill(-6, -1, -6, 6, -1, 6, "minecraft:stone");
			for (int i = 0; i < 10; i++) {
				env.summon("minecraft:item", (i % 4) * 0.7 - 1, 1 + i * 0.3, (i / 4) * 0.7 - 1, "{Item:{id:\"minecraft:cobblestone\",count:3},PickupDelay:32767}");
				env.summon("minecraft:experience_orb", (i % 3) * 0.6 + 2, 1 + i * 0.2, (i / 3) * 0.6, "{Value:" + (i + 1) + "}");
			}
		}, 2));
		list.add(new Scene("mobs", 160, 8, 5, env -> {
			env.fill(-8, -1, -8, 8, -1, 8, "minecraft:grass_block");
			env.fill(-6, 0, -6, 6, 1, -6, "minecraft:oak_fence");
			env.fill(-6, 0, 6, 6, 1, 6, "minecraft:oak_fence");
			env.fill(-6, 0, -6, -6, 1, 6, "minecraft:oak_fence");
			env.fill(6, 0, -6, 6, 1, 6, "minecraft:oak_fence");
			for (int i = 0; i < 6; i++) {
				env.summon("minecraft:cow", i - 3, 0, 0, "{PersistenceRequired:1b}");
				env.summon("minecraft:sheep", i - 3, 0, 2, "{PersistenceRequired:1b}");
				env.summon("minecraft:chicken", i - 3, 0, -2, "{PersistenceRequired:1b}");
			}
		}, 20));
		list.add(new Scene("spawning", 200, 24, 8, env -> {
			env.cmd("difficulty normal");
			env.cmd("gamerule spawn_mobs true");
			env.cmd("time set 18000");
			env.fill(-24, -1, -24, 24, -1, 24, "minecraft:stone");
		}, 5));
		return list;
	}

	private static void installListener() {
		if (!LISTENER.compareAndSet(false, true)) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!recording || active == null) {
				return;
			}
			ServerLevel level = server.overworld();
			RECORDING.put(level.getGameTime(), fingerprint(level, active, activeOrigin));
		});
	}

	private static List<String> fingerprint(ServerLevel level, Scene scene, BlockPos o) {
		List<String> out = new ArrayList<>();
		int r = scene.radius;
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-r, -2, -r), o.offset(r, scene.height, r))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				out.add("B " + (pos.getX() - o.getX()) + "," + (pos.getY() - o.getY()) + "," + (pos.getZ() - o.getZ()) + "=" + state);
			}
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				out.add("E " + (pos.getX() - o.getX()) + "," + (pos.getY() - o.getY()) + "," + (pos.getZ() - o.getZ()) + "=" + blockEntity.saveWithFullMetadata(level.registryAccess()));
			}
		}
		AABB box = new AABB(o).inflate(r + 8, scene.height + 8, r + 8);
		TreeMap<String, String> entities = new TreeMap<>();
		for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			entity.saveWithoutId(output);
			CompoundTag tag = output.buildResult();
			entities.put(entity.getUUID().toString(), entity.getType().builtInRegistryHolder().key().identifier().getPath() + " id=" + entity.getId() + " " + tag);
		}
		entities.forEach((uuid, value) -> out.add("N " + uuid + " " + value));
		long now = level.getGameTime();
		for (int cx = (o.getX() - r) >> 4; cx <= (o.getX() + r) >> 4; cx++) {
			for (int cz = (o.getZ() - r) >> 4; cz <= (o.getZ() + r) >> 4; cz++) {
				net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
				out.add("C " + cx + "," + cz + " loaded=" + (chunk != null) + " ticking=" + level.shouldTickBlocksAt(key) + " entities=" + level.isPositionEntityTicking(new BlockPos(cx << 4, o.getY(), cz << 4)));
				if (chunk != null) {
					List<String> ticks = new ArrayList<>();
					if (chunk.getBlockTicks() instanceof net.minecraft.world.ticks.LevelChunkTicks<?> blockTicks) {
						blockTicks.getAll().forEach(tick -> ticks.add("block " + tick.type() + "@" + tick.pos().subtract(o).toShortString() + " in " + (tick.triggerTick() - now) + " " + tick.priority()));
					}
					if (chunk.getFluidTicks() instanceof net.minecraft.world.ticks.LevelChunkTicks<?> fluidTicks) {
						fluidTicks.getAll().forEach(tick -> ticks.add("fluid " + tick.type() + "@" + tick.pos().subtract(o).toShortString() + " in " + (tick.triggerTick() - now) + " " + tick.priority()));
					}
					ticks.sort(null);
					ticks.forEach(line -> out.add("T " + line));
				}
			}
		}
		return out;
	}

	private String runScene(ClientGameTestContext context, TestServerContext server, Scene scene, BlockPos origin) {
		Env env = new Env(server, origin);
		server.runCommand("kill @e[type=!minecraft:player]");
		server.runCommand("gamerule random_tick_speed 3");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty peaceful");
		server.runCommand("time set 6000");
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d %d", origin.getX(), origin.getY() + 12, origin.getZ() - 14));
		server.waitFor(s -> {
			ServerLevel level = s.overworld();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (level.getChunkSource().getChunkNow((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz) == null) {
						return false;
					}
				}
			}
			return true;
		}, 600);
		context.waitTicks(10);
		env.fill(-26, -1, -26, 26, 14, 26, "minecraft:air");
		server.runCommand("kill @e[type=!minecraft:player]");
		context.waitTicks(5);
		scene.setup.accept(env);
		context.waitTicks(scene.settle);

		Set<String> before = serverIds(server);
		startRecording(scene, origin);
		long savedTime = server.computeOnServer(s -> {
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "savestate save worldsync-" + scene.name);
			return s.overworld().getGameTime();
		});
		String id = newId(server, before);
		Map<Long, List<String>> first = record(context, server, scene, origin, savedTime);

		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		startRecording(scene, origin);
		server.runCommand("savestate load " + id);
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
		Map<Long, List<String>> second = record(context, server, scene, origin, savedTime);
		server.runCommand("savestate delete " + id);
		return compare(scene, first, second, savedTime);
	}

	private static void startRecording(Scene scene, BlockPos origin) {
		RECORDING.clear();
		active = scene;
		activeOrigin = origin;
		recording = true;
	}

	private static Map<Long, List<String>> record(ClientGameTestContext context, TestServerContext server, Scene scene, BlockPos origin, long from) {
		recording = true;
		server.waitFor(s -> s.overworld().getGameTime() >= from + scene.ticks, scene.ticks * 4 + 400);
		recording = false;
		Map<Long, List<String>> copy = new TreeMap<>(RECORDING);
		RECORDING.clear();
		return copy;
	}

	private static String compare(Scene scene, Map<Long, List<String>> first, Map<Long, List<String>> second, long from) {
		for (long tick = from + 1; tick <= from + scene.ticks; tick++) {
			List<String> a = first.get(tick);
			List<String> b = second.get(tick);
			if (a == null || b == null) {
				return "missing tick " + (tick - from) + " (uncut " + (a != null) + ", restored " + (b != null) + ")";
			}
			if (!a.equals(b)) {
				Set<String> onlyA = new HashSet<>(a);
				b.forEach(onlyA::remove);
				Set<String> onlyB = new HashSet<>(b);
				a.forEach(onlyB::remove);
				StringBuilder detail = new StringBuilder();
				onlyA.stream().sorted().limit(6).forEach(line -> detail.append("\n    uncut:    ").append(abbreviate(line)));
				onlyB.stream().sorted().limit(6).forEach(line -> detail.append("\n    restored: ").append(abbreviate(line)));
				return "DIVERGES at +" + (tick - from) + " (" + onlyA.size() + "/" + onlyB.size() + " lines)" + detail;
			}
		}
		return "MATCH for " + scene.ticks + " ticks";
	}

	private static String abbreviate(String line) {
		return line.length() > 900 ? line.substring(0, 900) + "..." : line;
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

	private static String newId(TestServerContext server, Set<String> before) {
		Set<String> after = serverIds(server);
		after.removeAll(before);
		if (after.size() != 1) {
			throw new AssertionError("expected one new savestate, got " + after);
		}
		return after.iterator().next();
	}

}
