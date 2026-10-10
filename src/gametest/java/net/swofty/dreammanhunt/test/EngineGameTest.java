package net.swofty.dreammanhunt.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.swofty.dreammanhunt.mixin.server.LevelAccessor;
import net.swofty.dreammanhunt.mixin.server.ServerLevelAccessor;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;
import net.swofty.dreammanhunt.server.engine.SavestateException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EngineGameTest implements FabricClientGameTest {
	private static final Identifier SAVED_ADVANCEMENT = Identifier.withDefaultNamespace("story/mine_stone");
	private static final Identifier LATER_ADVANCEMENT = Identifier.withDefaultNamespace("story/upgrade_tools");
	private static final String SAVED_PIG = "SavedPig";
	private static final String LATER_PIG = "LaterPig";
	private static final String FAILPOINT = "dreammanhunt.engine.failRestoreAt";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("engine")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			this.runScenario(context, singleplayer.getServer(), singleplayer.getConnection(), "sp");
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			this.runScaleScenario(context, singleplayer.getServer(), singleplayer.getConnection());
		}
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				this.runScenario(context, server, connection, "ds");
			}
		}
	}

	private void runScenario(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String label) {
		connection.waitForChunksRender();
		context.waitTicks(20);
		server.runCommand("difficulty easy");
		server.runCommand("gamemode survival @a");
		server.runCommand("time set 6000");
		server.runCommand("weather clear 100000");

		BlockPos playerPos = server.computeOnServer(s -> firstPlayer(s).blockPosition());
		BlockPos diamondPos = playerPos.offset(0, 1, 4);
		BlockPos chestPos = playerPos.offset(2, 0, 3);
		BlockPos pigPos = playerPos.offset(-2, 0, 3);
		server.runCommand("tp @a " + (playerPos.getX() + 0.5) + " " + playerPos.getY() + " " + (playerPos.getZ() + 0.5) + " 0 15");
		server.runCommand(fill(playerPos.offset(-3, 0, 5), playerPos.offset(3, 3, 5), "minecraft:gold_block"));
		server.runCommand(setblock(diamondPos, "minecraft:diamond_block"));
		server.runCommand("summon minecraft:pig " + pigPos.getX() + " " + pigPos.getY() + " " + pigPos.getZ() + " {CustomName:\"" + SAVED_PIG + "\",NoAI:1b,Silent:1b}");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:diamond_sword 1");
		server.runCommand("give @a minecraft:cobblestone 23");
		server.runCommand("xp set @a 17 levels");
		server.runCommand("effect give @a minecraft:speed 600 1 true");
		server.runCommand("advancement grant @a only " + SAVED_ADVANCEMENT);
		server.runCommand("advancement revoke @a only " + LATER_ADVANCEMENT);
		server.runCommand("scoreboard objectives add dmscore dummy");
		server.runCommand("scoreboard players set @a dmscore 42");
		server.runCommand("scoreboard objectives setdisplay sidebar dmscore");
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
			chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
			chest.setItem(5, new ItemStack(Items.APPLE, 3));
			ServerPlayer player = firstPlayer(s);
			player.setHealth(13.0F);
			player.getFoodData().setFoodLevel(15);
		});
		server.runOnServer(s -> {
			ServerPlayer player = firstPlayer(s);
			player.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 400);
			player.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
			player.inventoryMenu.setCarried(new ItemStack(Items.STICK, 3));
			player.inventoryMenu.broadcastChanges();
		});
		context.waitTicks(10);
		connection.waitForChunksRender();

		server.runCommand("weather rain 100000");
		context.waitTicks(15);
		String id = UUID.randomUUID().toString();
		float[] savedCooldown = new float[1];
		float[] savedWeather = new float[4];
		long[] savedRuntime = new long[7];
		long saveMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).save(id, "engine-" + label, firstPlayer(s));
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			long elapsed = (System.nanoTime() - start) / 1_000_000L;
			savedRuntime[0] = s.getTickCount();
			savedRuntime[1] = s.overworld().getRandom().nextLong();
			Entity pig = findNamed(s.overworld().getAllEntities(), SAVED_PIG);
			savedRuntime[2] = pig == null ? -1 : pig.tickCount;
			savedRuntime[3] = pig == null ? -1 : pig.getRandom().nextLong();
			LevelAccessor overworldAccess = (LevelAccessor) s.overworld();
			savedRuntime[4] = overworldAccess.dreammanhunt$getSoundSeedGenerator().nextLong();
			savedRuntime[5] = pig == null ? -1 : pig.getId();
			savedRuntime[6] = ServerLevelAccessor.dreammanhunt$getEntityCounter().get();
			savedWeather[0] = overworldAccess.dreammanhunt$getORainLevel();
			savedWeather[1] = overworldAccess.dreammanhunt$getRainLevel();
			savedWeather[2] = overworldAccess.dreammanhunt$getOThunderLevel();
			savedWeather[3] = overworldAccess.dreammanhunt$getThunderLevel();
			savedCooldown[0] = firstPlayer(s).getCooldowns().getCooldownPercent(new ItemStack(Items.ENDER_PEARL), 0.0F);
			return elapsed;
		});
		System.out.println("[EngineGameTest] " + label + " save took " + saveMillis + " ms");
		context.takeScreenshot("engine-" + label + "-after-save");
		long savedDayTime = server.computeOnServer(s -> s.overworld().getOverworldClockTime());

		server.runCommand(fill(playerPos.offset(-3, 0, 5), playerPos.offset(3, 3, 5), "minecraft:air"));
		server.runCommand(setblock(diamondPos, "minecraft:stone"));
		server.runCommand(setblock(chestPos, "minecraft:air"));
		server.runCommand("kill @e[type=minecraft:pig]");
		server.runCommand("summon minecraft:pig " + pigPos.getX() + " " + pigPos.getY() + " " + (pigPos.getZ() - 1) + " {CustomName:\"" + LATER_PIG + "\",NoAI:1b,Silent:1b}");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:dirt 5");
		server.runCommand("xp set @a 2 levels");
		server.runCommand("effect clear @a");
		server.runCommand("advancement revoke @a only " + SAVED_ADVANCEMENT);
		server.runCommand("advancement grant @a only " + LATER_ADVANCEMENT);
		server.runCommand("scoreboard objectives remove dmscore");
		server.runCommand("scoreboard objectives add dmother dummy");
		server.runCommand("scoreboard objectives setdisplay sidebar dmother");
		server.runCommand("time set 18000");
		server.runCommand("weather thunder 100000");
		server.runOnServer(s -> {
			ServerPlayer player = firstPlayer(s);
			player.setHealth(20.0F);
			player.getFoodData().setFoodLevel(20);
		});
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			ServerPlayer player = firstPlayer(s);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(new ItemStack(Items.ENDER_PEARL)));
			player.getCooldowns().addCooldown(new ItemStack(Items.CHORUS_FRUIT), 300);
			player.inventoryMenu.getCraftSlots().setItem(0, ItemStack.EMPTY);
			player.inventoryMenu.getCraftSlots().setItem(3, new ItemStack(Items.FLINT, 1));
			player.inventoryMenu.setCarried(ItemStack.EMPTY);
			player.inventoryMenu.broadcastChanges();
		});
		server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
		context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.NETHER && mc.player != null, 400);
		context.waitTicks(40);
		context.takeScreenshot("engine-" + label + "-mutated");

		List<String> failures = new ArrayList<>();
		long restoreMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).restore(id);
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			long elapsed = (System.nanoTime() - start) / 1_000_000L;
			check(failures, s.getTickCount() == savedRuntime[0], "server tick count restored, got " + s.getTickCount() + " saved " + savedRuntime[0]);
			check(failures, s.overworld().getRandom().nextLong() == savedRuntime[1], "overworld random restored");
			Entity pig = findNamed(s.overworld().getAllEntities(), SAVED_PIG);
			check(failures, pig != null && pig.tickCount == savedRuntime[2], "pig tick count restored");
			check(failures, pig != null && pig.getRandom().nextLong() == savedRuntime[3], "pig random restored");
			check(failures, ((LevelAccessor) s.overworld()).dreammanhunt$getSoundSeedGenerator().nextLong() == savedRuntime[4], "overworld sound seed restored");
			ServerPlayer restored = firstPlayer(s);
			float cooldown = restored.getCooldowns().getCooldownPercent(new ItemStack(Items.ENDER_PEARL), 0.0F);
			check(failures, cooldown == savedCooldown[0] && cooldown > 0.0F, "server pearl cooldown restored exactly, got " + cooldown + " saved " + savedCooldown[0]);
			check(failures, !restored.getCooldowns().isOnCooldown(new ItemStack(Items.CHORUS_FRUIT)), "server later cooldown gone");
			check(failures, pig != null && pig.getId() == savedRuntime[5], "server pig keeps its saved entity id, got " + (pig == null ? -1 : pig.getId()) + " saved " + savedRuntime[5]);
			check(failures, ServerLevelAccessor.dreammanhunt$getEntityCounter().get() == savedRuntime[6], "entity counter restored, got " + ServerLevelAccessor.dreammanhunt$getEntityCounter().get() + " saved " + savedRuntime[6]);
			Entity probe = EntityTypes.ARMOR_STAND.create(s.overworld(), EntitySpawnReason.COMMAND);
			check(failures, probe != null && probe.getId() == savedRuntime[6] + 1, "next entity gets the uncut id, got " + (probe == null ? -1 : probe.getId()) + " expected " + (savedRuntime[6] + 1));
			LevelAccessor weather = (LevelAccessor) s.overworld();
			check(failures, weather.dreammanhunt$getORainLevel() == savedWeather[0] && weather.dreammanhunt$getRainLevel() == savedWeather[1]
					&& weather.dreammanhunt$getOThunderLevel() == savedWeather[2] && weather.dreammanhunt$getThunderLevel() == savedWeather[3],
					"server weather levels restored exactly, got " + weather.dreammanhunt$getRainLevel() + " saved " + savedWeather[1]);
			s.tickRateManager().setFrozen(true);
			return elapsed;
		});
		System.out.println("[EngineGameTest] " + label + " restore took " + restoreMillis + " ms");

		context.waitFor(mc -> mc.level != null && mc.player != null && mc.level.dimension() == Level.OVERWORLD && mc.gui.screen() == null, 600);
		connection.waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot("engine-" + label + "-after-restore");

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = firstPlayer(s);
			check(failures, level.getBlockState(diamondPos).is(Blocks.DIAMOND_BLOCK), "server diamond block restored");
			check(failures, level.getBlockState(playerPos.offset(3, 3, 5)).is(Blocks.GOLD_BLOCK), "server gold wall restored");
			check(failures, level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest
					&& chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7
					&& chest.getItem(5).is(Items.APPLE) && chest.getItem(5).getCount() == 3, "server chest contents restored");
			check(failures, countNamed(level.getAllEntities(), SAVED_PIG) == 1, "server saved pig present once");
			check(failures, countNamed(level.getAllEntities(), LATER_PIG) == 0, "server later pig gone");
			check(failures, player.level().dimension() == Level.OVERWORLD, "server player back in overworld");
			check(failures, player.blockPosition().equals(playerPos), "server player position restored, got " + player.blockPosition());
			check(failures, player.gameMode() == GameType.SURVIVAL, "server game mode restored");
			check(failures, count(player.getInventory(), Items.COBBLESTONE) == 23, "server cobblestone restored");
			check(failures, count(player.getInventory(), Items.DIAMOND_SWORD) == 1, "server sword restored");
			check(failures, count(player.getInventory(), Items.DIRT) == 0, "server dirt gone");
			check(failures, player.experienceLevel == 17, "server xp restored, got " + player.experienceLevel);
			check(failures, player.hasEffect(MobEffects.SPEED), "server effect restored");
			check(failures, player.getHealth() == 13.0F, "server health restored, got " + player.getHealth());
			check(failures, player.getFoodData().getFoodLevel() == 15, "server food restored, got " + player.getFoodData().getFoodLevel());
			check(failures, isDone(s, player, SAVED_ADVANCEMENT), "server saved advancement done");
			check(failures, !isDone(s, player, LATER_ADVANCEMENT), "server later advancement not done");
			long dayTime = level.getOverworldClockTime();
			check(failures, dayTime >= savedDayTime && dayTime < savedDayTime + 200, "server time restored, got " + dayTime + " saved " + savedDayTime);
			check(failures, ((LevelAccessor) level).dreammanhunt$getRainLevel() == savedWeather[1] && savedWeather[1] > 0.0F && savedWeather[1] < 1.0F, "server weather still at the saved mid-transition level, got " + ((LevelAccessor) level).dreammanhunt$getRainLevel());
			Objective objective = s.getScoreboard().getObjective("dmscore");
			check(failures, objective != null && s.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), objective).get() == 42, "server scoreboard restored");
			check(failures, s.getScoreboard().getObjective("dmother") == null, "server later objective gone");
			check(failures, s.getPlayerList().getPlayers().size() == 1 && s.getPlayerList().getPlayer(player.getUUID()) == player, "server player list consistent");
			check(failures, player.connection.player == player, "connection points at restored player");
			check(failures, player.inventoryMenu.getCraftSlots().getItem(0).is(Items.OAK_PLANKS) && player.inventoryMenu.getCraftSlots().getItem(0).getCount() == 2, "server crafting grid restored");
			check(failures, player.inventoryMenu.getCraftSlots().getItem(3).isEmpty(), "server later grid item gone");
			check(failures, player.inventoryMenu.getCarried().is(Items.STICK) && player.inventoryMenu.getCarried().getCount() == 3, "server carried item restored");
		});

		context.runOnClient(mc -> {
			check(failures, mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)), "client pearl cooldown restored");
			check(failures, !mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.CHORUS_FRUIT)), "client later cooldown gone");
			check(failures, mc.player.inventoryMenu.getCraftSlots().getItem(0).is(Items.OAK_PLANKS), "client crafting grid restored");
			check(failures, mc.player.inventoryMenu.getCraftSlots().getItem(3).isEmpty(), "client later grid item gone");
			check(failures, mc.player.inventoryMenu.getCarried().is(Items.STICK) && mc.player.inventoryMenu.getCarried().getCount() == 3, "client carried item restored");
			check(failures, mc.level.getBlockState(diamondPos).is(Blocks.DIAMOND_BLOCK), "client diamond block restored");
			check(failures, mc.level.getBlockState(playerPos.offset(3, 3, 5)).is(Blocks.GOLD_BLOCK), "client gold wall restored");
			check(failures, mc.level.getBlockState(chestPos).is(Blocks.CHEST), "client chest restored");
			check(failures, countNamed(mc.level.entitiesForRendering(), SAVED_PIG) == 1, "client saved pig present once");
			check(failures, countNamed(mc.level.entitiesForRendering(), LATER_PIG) == 0, "client later pig gone");
			check(failures, mc.player.blockPosition().equals(playerPos), "client player position restored, got " + mc.player.blockPosition());
			check(failures, mc.gameMode.getPlayerMode() == GameType.SURVIVAL, "client game mode restored");
			check(failures, count(mc.player.getInventory(), Items.COBBLESTONE) == 23, "client cobblestone restored");
			check(failures, count(mc.player.getInventory(), Items.DIAMOND_SWORD) == 1, "client sword restored");
			check(failures, count(mc.player.getInventory(), Items.DIRT) == 0, "client dirt gone");
			check(failures, mc.player.experienceLevel == 17, "client xp restored, got " + mc.player.experienceLevel);
			check(failures, mc.player.hasEffect(MobEffects.SPEED), "client effect restored");
			check(failures, mc.player.getHealth() == 13.0F, "client health restored, got " + mc.player.getHealth());
			check(failures, mc.player.getFoodData().getFoodLevel() == 15, "client food restored, got " + mc.player.getFoodData().getFoodLevel());
			check(failures, clientDone(mc, SAVED_ADVANCEMENT), "client saved advancement done");
			check(failures, !clientDone(mc, LATER_ADVANCEMENT), "client later advancement not done");
			check(failures, ((LevelAccessor) mc.level).dreammanhunt$getRainLevel() == savedWeather[1], "client rain level restored exactly, got " + ((LevelAccessor) mc.level).dreammanhunt$getRainLevel() + " saved " + savedWeather[1]);
			check(failures, ((LevelAccessor) mc.level).dreammanhunt$getThunderLevel() == savedWeather[3], "client thunder level restored exactly");
			Entity clientPig = findNamed(mc.level.entitiesForRendering(), SAVED_PIG);
			check(failures, clientPig != null && clientPig.getId() == savedRuntime[5], "client pig has its saved entity id, got " + (clientPig == null ? -1 : clientPig.getId()));
			Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
			check(failures, sidebar != null && sidebar.getName().equals("dmscore"), "client sidebar restored");
			long dayTime = mc.level.getOverworldClockTime();
			check(failures, dayTime >= savedDayTime && dayTime < savedDayTime + 200, "client time restored, got " + dayTime);
		});

		server.runOnServer(s -> s.tickRateManager().setFrozen(false));
		if (label.equals("ds") && failures.isEmpty()) {
			this.runOpenMenuCheck(context, server, playerPos.offset(-1, 0, 2), failures);
		}
		if (label.equals("sp") && failures.isEmpty()) {
			this.runRollbackCheck(context, server, connection, id, playerPos.offset(1, 0, 2), failures);
		}
		server.runOnServer(s -> SavestateEngine.get(s).delete(id));
		if (!failures.isEmpty()) {
			throw new AssertionError("[" + label + "] savestate restore mismatches: " + String.join("; ", failures));
		}
		System.out.println("[EngineGameTest] " + label + " all assertions passed (save " + saveMillis + " ms, restore " + restoreMillis + " ms)");
	}

	private void runScaleScenario(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		connection.waitForChunksRender();
		int radius = 16;
		long genStart = System.nanoTime();
		server.runOnServer(s -> {
			for (ServerLevel level : List.of(s.overworld(), s.getLevel(Level.NETHER))) {
				for (int x = -radius; x < radius; x++) {
					for (int z = -radius; z < radius; z++) {
						level.setChunkForced(x, z, true);
					}
				}
			}
		});
		int expected = 4 * radius * radius;
		server.waitFor(s -> loadedForced(s.overworld(), radius) >= expected && loadedForced(s.getLevel(Level.NETHER), radius) >= expected, 20 * 600);
		System.out.println("[EngineGameTest] scale: generated " + 2 * expected + " forced chunks in " + (System.nanoTime() - genStart) / 1_000_000L + " ms");
		int[] chunkCounts = server.computeOnServer(s -> new int[]{s.overworld().getChunkSource().getLoadedChunksCount(), s.getLevel(Level.NETHER).getChunkSource().getLoadedChunksCount()});
		System.out.println("[EngineGameTest] scale: loaded chunks overworld=" + chunkCounts[0] + " nether=" + chunkCounts[1]);

		String id = UUID.randomUUID().toString();
		long saveMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).save(id, "scale", firstPlayer(s));
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			return (System.nanoTime() - start) / 1_000_000L;
		});
		server.runOnServer(s -> {
			for (ServerLevel level : List.of(s.overworld(), s.getLevel(Level.NETHER))) {
				for (int x = -radius; x < radius; x++) {
					for (int z = -radius; z < radius; z++) {
						level.setBlockAndUpdate(new BlockPos(x * 16 + 8, 120, z * 16 + 8), Blocks.GLASS.defaultBlockState());
					}
				}
			}
		});
		long restoreMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).restore(id);
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			return (System.nanoTime() - start) / 1_000_000L;
		});
		List<String> failures = new ArrayList<>();
		server.runOnServer(s -> {
			for (ServerLevel level : List.of(s.overworld(), s.getLevel(Level.NETHER))) {
				check(failures, !level.getBlockState(new BlockPos(8, 120, 8)).is(Blocks.GLASS), "scale: mutation reverted in " + level.dimension().identifier());
				check(failures, !level.getBlockState(new BlockPos((radius - 1) * 16 + 8, 120, (radius - 1) * 16 + 8)).is(Blocks.GLASS), "scale: far mutation reverted in " + level.dimension().identifier());
			}
		});
		context.waitFor(mc -> mc.level != null && mc.player != null && mc.gui.screen() == null, 600);
		long[] sizes = server.computeOnServer(s -> {
			java.nio.file.Path dir = s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dreammanhunt").resolve("savestates").resolve(id);
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir)) {
				long[] result = new long[2];
				files.filter(java.nio.file.Files::isRegularFile).forEach(file -> {
					result[0]++;
					try {
						result[1] += java.nio.file.Files.size(file);
					} catch (java.io.IOException ignored) {
					}
				});
				return result;
			} catch (java.io.IOException e) {
				return new long[]{-1, -1};
			}
		});
		server.runOnServer(s -> SavestateEngine.get(s).delete(id));
		System.out.println("[EngineGameTest] scale: snapshot " + sizes[0] + " files, " + sizes[1] / 1024 + " KiB; save " + saveMillis + " ms, restore " + restoreMillis + " ms");
		if (!failures.isEmpty()) {
			throw new AssertionError("[scale] " + String.join("; ", failures));
		}
	}

	private static int loadedForced(ServerLevel level, int radius) {
		int loaded = 0;
		for (int x = -radius; x < radius; x++) {
			for (int z = -radius; z < radius; z++) {
				if (level.getChunkSource().getChunkNow(x, z) != null) {
					loaded++;
				}
			}
		}
		return loaded;
	}

	private void runOpenMenuCheck(ClientGameTestContext context, TestServerContext server, BlockPos anvilPos, List<String> failures) {
		server.runCommand(setblock(anvilPos, "minecraft:anvil"));
		server.runCommand("clear @a");
		context.waitTicks(2);
		String menuId = UUID.randomUUID().toString();
		server.runOnServer(s -> {
			ServerPlayer player = firstPlayer(s);
			ServerLevel level = s.overworld();
			player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) -> new AnvilMenu(containerId, inventory, ContainerLevelAccess.create(level, anvilPos)), Component.literal("anvil")));
			player.containerMenu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
			player.containerMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 2));
			player.containerMenu.setCarried(new ItemStack(Items.FLINT, 4));
			try {
				SavestateEngine.get(s).save(menuId, "menu", player);
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
		});
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			try {
				SavestateEngine.get(s).restore(menuId);
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			ServerPlayer player = firstPlayer(s);
			check(failures, player.containerMenu == player.inventoryMenu, "open anvil closed after restore");
			check(failures, count(player.getInventory(), Items.IRON_SWORD) == 1 && count(player.getInventory(), Items.IRON_INGOT) == 2, "anvil inputs returned to the inventory");
			check(failures, count(player.getInventory(), Items.FLINT) == 4, "anvil carried item returned to the inventory");
			SavestateEngine.get(s).delete(menuId);
		});
		context.waitFor(mc -> mc.player != null && mc.gui.screen() == null, 600);
		context.waitTicks(5);
		context.runOnClient(mc -> check(failures, count(mc.player.getInventory(), Items.IRON_SWORD) == 1 && count(mc.player.getInventory(), Items.FLINT) == 4, "client sees returned anvil items"));
	}

	private void runRollbackCheck(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String id, BlockPos markerPos, List<String> failures) {
		server.runCommand(setblock(markerPos, "minecraft:emerald_block"));
		server.runCommand("xp set @a 33 levels");
		context.waitTicks(5);
		String failure = server.computeOnServer(s -> {
			System.setProperty(FAILPOINT, "players");
			try {
				SavestateEngine.get(s).restore(id);
				return "restore did not fail";
			} catch (SavestateException e) {
				return e.getMessage();
			} finally {
				System.clearProperty(FAILPOINT);
			}
		});
		System.out.println("[EngineGameTest] injected failure result: " + failure);
		check(failures, failure.endsWith("the world was rolled back"), "injected failure rolled back, got: " + failure);
		context.waitFor(mc -> mc.level != null && mc.player != null && mc.gui.screen() == null, 600);
		connection.waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot("engine-sp-after-rollback");
		server.runOnServer(s -> {
			ServerPlayer player = firstPlayer(s);
			check(failures, s.overworld().getBlockState(markerPos).is(Blocks.EMERALD_BLOCK), "rollback kept the post-save marker block");
			check(failures, player.experienceLevel == 33, "rollback kept live xp, got " + player.experienceLevel);
			check(failures, s.getPlayerList().getPlayers().size() == 1 && player.connection.player == player, "rollback player list consistent");
			check(failures, count(player.getInventory(), Items.COBBLESTONE) == 23, "rollback kept live inventory");
			try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dreammanhunt"))) {
				check(failures, entries.noneMatch(path -> path.getFileName().toString().startsWith("restore-backup")), "rollback left no backup behind");
			} catch (java.io.IOException e) {
				failures.add("could not list dreammanhunt folder: " + e);
			}
		});
		context.runOnClient(mc -> {
			check(failures, mc.level.getBlockState(markerPos).is(Blocks.EMERALD_BLOCK), "client sees marker after rollback");
			check(failures, mc.player.experienceLevel == 33, "client xp after rollback");
		});
	}

	private static ServerPlayer firstPlayer(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static String fill(BlockPos from, BlockPos to, String block) {
		return "fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + block;
	}

	private static String setblock(BlockPos pos, String block) {
		return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
	}

	private static void check(List<String> failures, boolean ok, String what) {
		if (!ok) {
			failures.add(what);
		}
	}

	private static int count(Inventory inventory, Item item) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int countNamed(Iterable<? extends Entity> entities, String name) {
		int total = 0;
		for (Entity entity : entities) {
			Component customName = entity.getCustomName();
			if (customName != null && customName.getString().equals(name)) {
				total++;
			}
		}
		return total;
	}

	private static Entity findNamed(Iterable<? extends Entity> entities, String name) {
		for (Entity entity : entities) {
			Component customName = entity.getCustomName();
			if (customName != null && customName.getString().equals(name)) {
				return entity;
			}
		}
		return null;
	}

	private static boolean isDone(MinecraftServer server, ServerPlayer player, Identifier id) {
		AdvancementHolder holder = server.getAdvancements().get(id);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private static boolean clientDone(Minecraft mc, Identifier id) {
		AdvancementHolder holder = mc.player.connection.getAdvancements().get(id);
		if (holder == null) {
			return false;
		}
		AdvancementProgress progress = mc.player.connection.getAdvancements().progress().get(holder);
		return progress != null && progress.isDone();
	}
}
