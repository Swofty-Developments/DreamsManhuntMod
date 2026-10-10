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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
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

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("engine")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			this.runScenario(context, singleplayer.getServer(), singleplayer.getConnection(), "sp");
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
		context.waitTicks(10);
		connection.waitForChunksRender();

		String id = UUID.randomUUID().toString();
		long saveMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).save(id, "engine-" + label, firstPlayer(s));
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			return (System.nanoTime() - start) / 1_000_000L;
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
		server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
		context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.NETHER && mc.player != null, 400);
		context.waitTicks(40);
		context.takeScreenshot("engine-" + label + "-mutated");

		long restoreMillis = server.computeOnServer(s -> {
			long start = System.nanoTime();
			try {
				SavestateEngine.get(s).restore(id);
			} catch (SavestateException e) {
				throw new RuntimeException(e);
			}
			return (System.nanoTime() - start) / 1_000_000L;
		});
		System.out.println("[EngineGameTest] " + label + " restore took " + restoreMillis + " ms");

		context.waitFor(mc -> mc.level != null && mc.player != null && mc.level.dimension() == Level.OVERWORLD && mc.gui.screen() == null, 600);
		connection.waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot("engine-" + label + "-after-restore");

		List<String> failures = new ArrayList<>();
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
			check(failures, !level.isRaining() && !level.isThundering(), "server weather restored");
			Objective objective = s.getScoreboard().getObjective("dmscore");
			check(failures, objective != null && s.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), objective).get() == 42, "server scoreboard restored");
			check(failures, s.getScoreboard().getObjective("dmother") == null, "server later objective gone");
			check(failures, s.getPlayerList().getPlayers().size() == 1 && s.getPlayerList().getPlayer(player.getUUID()) == player, "server player list consistent");
			check(failures, player.connection.player == player, "connection points at restored player");
		});

		context.runOnClient(mc -> {
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
			check(failures, mc.level.getRainLevel(1.0F) == 0.0F, "client weather restored");
			Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
			check(failures, sidebar != null && sidebar.getName().equals("dmscore"), "client sidebar restored");
			long dayTime = mc.level.getOverworldClockTime();
			check(failures, dayTime >= savedDayTime && dayTime < savedDayTime + 200, "client time restored, got " + dayTime);
		});

		server.runOnServer(s -> SavestateEngine.get(s).delete(id));
		if (!failures.isEmpty()) {
			throw new AssertionError("[" + label + "] savestate restore mismatches: " + String.join("; ", failures));
		}
		System.out.println("[EngineGameTest] " + label + " all assertions passed (save " + saveMillis + " ms, restore " + restoreMillis + " ms)");
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
