package net.swofty.dreammanhunt.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientSnapshot;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;
import net.swofty.dreammanhunt.client.ui.SavestateList;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;

public final class RedTeamGameTest implements FabricClientGameTest {
	private static final int[] FRAME_OFFSETS = {0, 4, 10, 20, 40};
	private static final int LEAD_TICKS = 6;
	private static final int FUZZ = 24;

	private final Map<String, String> results = new LinkedHashMap<>();
	private BlockPos origin;
	private int sceneIndex;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("redteam")) {
			return;
		}
		String filter = System.getenv().getOrDefault("REDTEAM_SCENES", "");
		Set<String> only = filter.isBlank() ? Set.of() : Set.of(filter.split(","));
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			this.origin = context.computeOnClient(mc -> mc.player.blockPosition());
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty peaceful");
			for (Scene scene : scenes()) {
				if (!only.isEmpty() && !only.contains(scene.name)) {
					continue;
				}
				try {
					this.runScene(context, server, scene);
				} catch (Throwable t) {
					t.printStackTrace();
					this.results.put(scene.name, "ERROR " + t);
					resetClient(context);
				}
			}
		}
		this.writeReport(context);
	}

	private record Scene(String name, Consumer<Env> setup, Consumer<Env> preCapture, boolean viaMenu, int settle) {
		Scene(String name, Consumer<Env> setup) {
			this(name, setup, env -> {
			}, false, 60);
		}

		Scene menu() {
			return new Scene(this.name, this.setup, this.preCapture, true, this.settle);
		}

		Scene before(Consumer<Env> pre) {
			return new Scene(this.name, this.setup, pre, this.viaMenu, this.settle);
		}
	}

	private record Env(ClientGameTestContext context, TestServerContext server, BlockPos o) {
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
			this.cmd("summon %s %.2f %.2f %.2f %s", type, this.o.getX() + 0.5 + dx, this.o.getY() + dy, this.o.getZ() + 0.5 + dz, nbt);
		}

		void tp(double dx, double dy, double dz, float yaw, float pitch) {
			this.cmd("tp @p %.2f %.2f %.2f %.1f %.1f", this.o.getX() + 0.5 + dx, this.o.getY() + dy, this.o.getZ() + 0.5 + dz, yaw, pitch);
		}

		void give(String item) {
			this.cmd("item replace entity @p weapon.mainhand with %s", item);
		}
	}

	private static List<Scene> scenes() {
		List<Scene> list = new ArrayList<>();
		list.add(new Scene("baseline", env -> env.tp(0, 0, 0, 0, 10)));
		list.add(new Scene("nether_portal", env -> {
			env.fill(-2, 0, 4, 2, 4, 4, "minecraft:obsidian");
			env.fill(-1, 1, 4, 1, 3, 4, "minecraft:nether_portal[axis=x]");
			env.tp(0, 0, 0, 0, 0);
		}));
		list.add(new Scene("end_portal_gateway", env -> {
			env.fill(-1, -1, 3, 1, -1, 5, "minecraft:end_portal");
			env.block(2, 1, 5, "minecraft:end_gateway");
			env.tp(0, 0, 0, 0, 35);
		}));
		list.add(new Scene("beacon", env -> {
			env.fill(-1, 0, 5, 1, 0, 7, "minecraft:iron_block");
			env.block(0, 1, 6, "minecraft:beacon");
			env.tp(0, 0, 0, 0, -25);
		}).menu());
		list.add(new Scene("conduit", env -> {
			env.fill(-3, 0, 3, 3, 6, 9, "minecraft:glass");
			env.fill(-2, 1, 4, 2, 5, 8, "minecraft:water");
			for (int x = -2; x <= 2; x++) {
				for (int y = -2; y <= 2; y++) {
					for (int z = -2; z <= 2; z++) {
						int ax = Math.abs(x);
						int ay = Math.abs(y);
						int az = Math.abs(z);
						if ((ax > 1 || ay > 1 || az > 1) && (x == 0 && (ay == 2 || az == 2) || y == 0 && (ax == 2 || az == 2) || z == 0 && (ax == 2 || ay == 2))) {
							env.block(x, 3 + y, 6 + z, "minecraft:prismarine");
						}
					}
				}
			}
			env.block(0, 3, 6, "minecraft:conduit[waterlogged=true]");
			env.block(-5, 4, 1, "minecraft:stone");
			env.tp(-5, 5, 1, -45, 24);
		}));
		list.add(new Scene("enchanting_table", env -> {
			env.block(1, 0, 2, "minecraft:enchanting_table");
			env.block(-1, 0, 3, "minecraft:enchanting_table");
			env.tp(0, 0, 0, 0, 30);
		}));
		list.add(new Scene("spawners", env -> {
			env.block(-1, 0, 3, "minecraft:spawner{SpawnData:{entity:{id:\"minecraft:pig\"}},RequiredPlayerRange:16s}");
			env.block(1, 0, 3, "minecraft:trial_spawner");
			env.block(0, 0, 5, "minecraft:vault");
			env.tp(0, 0, 0, 0, 20);
		}));
		list.add(new Scene("campfire_fire", env -> {
			env.block(-1, 0, 3, "minecraft:campfire");
			env.block(1, 0, 3, "minecraft:soul_campfire");
			env.block(-2, 0, 5, "minecraft:netherrack");
			env.block(-2, 1, 5, "minecraft:fire");
			env.block(2, 0, 5, "minecraft:soul_sand");
			env.block(2, 1, 5, "minecraft:soul_fire");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("fluids", env -> {
			env.fill(-3, 0, 4, 3, 3, 6, "minecraft:stone");
			env.block(-2, 3, 4, "minecraft:water");
			env.block(2, 3, 4, "minecraft:lava");
			env.tp(0, 0, 0, 0, -5);
		}));
		list.add(new Scene("items_orbs_frames", env -> {
			env.block(0, 1, 5, "minecraft:stone");
			env.cmd("summon minecraft:item_frame %d %d %d {Facing:2b,Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}}}",
				env.o.getX(), env.o.getY() + 1, env.o.getZ() + 4);
			env.summon("minecraft:item", -1.5, 0.2, 3, "{Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Age:-32768}");
			env.summon("minecraft:item", 1.5, 0.2, 3, "{Item:{id:\"minecraft:golden_apple\",count:5},PickupDelay:32767,Age:-32768}");
			env.summon("minecraft:item", 0.0, 0.2, 2.5, "{Item:{id:\"minecraft:grass_block\",count:1},PickupDelay:32767,Age:-32768}");
			env.summon("minecraft:end_crystal", 3, 0, 7, "{ShowBottom:0b}");
			env.tp(0, 0, 0, 0, 20);
		}).menu());
		list.add(new Scene("xp_orbs", env -> {
			env.fill(-1, 0, 9, 1, 2, 11, "minecraft:glass");
			env.fill(0, 1, 10, 0, 1, 10, "minecraft:air");
			env.summon("minecraft:experience_orb", 0, 1, 10, "{Value:7}");
			env.summon("minecraft:experience_orb", -3, 0, 12, "{Value:50}");
			env.summon("minecraft:experience_orb", 3, 0, 12, "{Value:500}");
			env.tp(0, 0, -2, 0, 10);
		}));
		list.add(new Scene("armor_stands", env -> {
			env.summon("minecraft:armor_stand", -1, 0, 3, "{ShowArms:1b,equipment:{head:{id:\"minecraft:diamond_helmet\",components:{\"minecraft:enchantments\":{\"minecraft:protection\":1}}},chest:{id:\"minecraft:iron_chestplate\"}}}");
			env.summon("minecraft:armor_stand", 1, 0, 3, "{ShowArms:1b,Small:1b,Pose:{RightArm:[-60f,0f,0f]}}");
			env.tp(0, 0, 0, 0, 15);
		}));
		list.add(new Scene("mobs_land", env -> {
			String nbt = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}";
			env.summon("minecraft:villager", -3, 0, 5, nbt);
			env.summon("minecraft:iron_golem", 0, 0, 7, nbt);
			env.summon("minecraft:sniffer", 3.5, 0, 7, nbt);
			env.summon("minecraft:warden", -4, 0, 9, nbt);
			env.summon("minecraft:enderman", 4, 0, 4, nbt);
			env.summon("minecraft:chicken", -1, 0, 3, nbt);
			env.summon("minecraft:frog", 1, 0, 3, nbt);
			env.tp(0, 0, 0, 0, 10);
		}).menu());
		list.add(new Scene("mobs_air_water", env -> {
			String nbt = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],NoGravity:1b}";
			env.summon("minecraft:allay", -2, 1, 4, nbt);
			env.summon("minecraft:bee", -0.5, 1.2, 3, nbt);
			env.summon("minecraft:breeze", 2, 0, 5, nbt);
			env.summon("minecraft:blaze", 0.5, 1, 7, nbt);
			env.fill(-5, 0, 6, -3, 2, 8, "minecraft:glass");
			env.fill(-4, 1, 7, -4, 2, 7, "minecraft:water");
			env.summon("minecraft:squid", -4, 1, 7, "{NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			env.fill(3, 0, 2, 5, 2, 4, "minecraft:glass");
			env.fill(4, 1, 3, 4, 2, 3, "minecraft:water");
			env.summon("minecraft:axolotl", 4, 1, 3, "{NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			env.tp(0, 0, 0, 0, 0);
		}));
		list.add(new Scene("third_person_self", env -> {
			env.tp(0, 0, 0, 30, 10);
			env.context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		}));
		list.add(new Scene("night_sky", env -> {
			env.cmd("time set 18000");
			env.tp(0, 0, 0, 0, -70);
		}));
		list.add(new Scene("moon_phase", env -> {
			env.cmd("time set 87000");
			env.tp(0, 0, 0, 90, -50);
		}));
		list.add(new Scene("sunset", env -> {
			env.cmd("time set 12500");
			env.tp(0, 0, 0, 90, -10);
		}));
		list.add(new Scene("clouds", env -> {
			env.cmd("time set 2000");
			env.tp(0, 0, 0, 45, -35);
		}));
		list.add(new Scene("rain", env -> {
			env.cmd("weather rain");
			env.tp(0, 0, 0, 0, 0);
		}));
		list.add(new Scene("snow", env -> {
			env.cmd("fillbiome %d %d %d %d %d %d minecraft:snowy_plains", env.o.getX() - 24, env.o.getY() - 8, env.o.getZ() - 24,
				env.o.getX() + 24, env.o.getY() + 24, env.o.getZ() + 24);
			env.cmd("weather rain");
			env.tp(0, 0, 0, 0, 0);
		}));
		list.add(new Scene("thunder", env -> {
			env.cmd("weather thunder");
			env.tp(0, 0, 0, 0, 0);
		}));
		list.add(new Scene("underwater", env -> {
			env.fill(-4, 0, -4, 4, 5, 8, "minecraft:water");
			env.tp(0, 1, 0, 0, 0);
		}));
		list.add(new Scene("nether", env -> {
			env.cmd("execute in minecraft:the_nether run fill -6 70 -6 6 77 12 minecraft:air");
			env.cmd("execute in minecraft:the_nether run fill -6 69 -6 6 69 12 minecraft:netherrack");
			env.cmd("execute in minecraft:the_nether run tp @p 0.5 70 0.5 0 0");
		}));
		list.add(new Scene("end_sky", env -> {
			env.cmd("execute in minecraft:the_end run fill 1495 59 1495 1505 59 1505 minecraft:obsidian");
			env.cmd("execute in minecraft:the_end run tp @p 1500.5 60 1500.5 0 -20");
		}));
		list.add(new Scene("blindness", env -> {
			env.cmd("effect give @p minecraft:blindness 600 0 true");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("darkness", env -> {
			env.cmd("effect give @p minecraft:darkness 600 0 true");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("nausea", env -> {
			env.cmd("effect give @p minecraft:nausea 600 0 true");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("night_vision", env -> {
			env.cmd("time set 18000");
			env.cmd("effect give @p minecraft:night_vision 600 0 true");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("inventory_glint", env -> {
			env.give("minecraft:diamond_sword[enchantments={sharpness:3}]");
			env.cmd("item replace entity @p hotbar.1 with minecraft:golden_apple 7");
			env.tp(0, 0, 0, 0, 10);
		}).before(env -> env.context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player))));
		list.add(new Scene("f3_debug", env -> env.tp(0, 0, 0, 0, 10))
			.before(env -> env.context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(true))));
		list.add(new Scene("f1_hidden", env -> {
			env.tp(0, 0, 0, 0, 10);
			env.context.runOnClient(mc -> ((net.swofty.dreammanhunt.client.mixin.state.HudAccessor) mc.gui.hud).dm$setIsHidden(true));
		}));
		list.add(new Scene("low_health_shake", env -> {
			env.tp(0, 0, 0, 0, 10);
			env.server.runOnServer(s -> {
				for (ServerPlayer player : s.getPlayerList().getPlayers()) {
					player.setHealth(3.0F);
					player.getFoodData().setFoodLevel(5);
					player.getFoodData().setSaturation(0.0F);
				}
			});
		}).menu());
		list.add(new Scene("elytra", env -> {
			env.cmd("item replace entity @p armor.chest with minecraft:elytra");
			env.tp(0, 40, 0, 0, 10);
		}).before(env -> {
			env.context.waitTicks(3);
			env.context.getInput().pressKey(options -> options.keyJump);
			env.context.waitTicks(2);
			env.context.getInput().pressKey(options -> options.keyJump);
			env.context.waitTicks(10);
		}));
		list.add(new Scene("spyglass", env -> {
			env.give("minecraft:spyglass");
			env.tp(0, 0, 0, 0, 0);
		}).before(env -> {
			env.context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			env.context.waitTicks(20);
		}));
		list.add(new Scene("map_item", env -> {
			env.tp(0, 0, 0, 0, 40);
			env.server.runOnServer(s -> {
				for (ServerPlayer player : s.getPlayerList().getPlayers()) {
					ItemStack map = MapItem.create(player.level(), player.getBlockX(), player.getBlockZ(), (byte) 0, true, true);
					player.setItemInHand(InteractionHand.MAIN_HAND, map);
				}
			});
		}));
		list.add(new Scene("compass", env -> {
			env.give("minecraft:compass");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("recovery_compass", env -> {
			env.give("minecraft:recovery_compass");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("clock", env -> {
			env.give("minecraft:clock");
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("lodestone_compass", env -> {
			env.block(6, 0, 6, "minecraft:lodestone");
			env.give(String.format(Locale.ROOT, "minecraft:compass[lodestone_tracker={target:{pos:[%d,%d,%d],dimension:\"minecraft:overworld\"},tracked:false}]",
				env.o.getX() + 6, env.o.getY(), env.o.getZ() + 6));
			env.tp(0, 0, 0, 0, 10);
		}));
		list.add(new Scene("nether_compass_clock", env -> {
			env.cmd("execute in minecraft:the_nether run fill -6 70 -6 6 77 12 minecraft:air");
			env.cmd("execute in minecraft:the_nether run fill -6 69 -6 6 69 12 minecraft:netherrack");
			env.cmd("execute in minecraft:the_nether run tp @p 0.5 70 0.5 0 10");
			env.cmd("item replace entity @p weapon.mainhand with minecraft:compass");
			env.cmd("item replace entity @p weapon.offhand with minecraft:clock");
		}));
		list.add(new Scene("hotbar_pop", env -> {
			env.cmd("item replace entity @p hotbar.2 with minecraft:cobblestone 12");
			env.tp(0, 0, 0, 0, 10);
		}));
		return list;
	}

	private void runScene(ClientGameTestContext context, TestServerContext server, Scene scene) {
		int index = this.sceneIndex++;
		Env env = new Env(context, server, this.origin);
		prepare(context, server, env);
		scene.setup.accept(env);
		context.waitTicks(scene.settle);
		scene.preCapture.accept(env);

		Set<String> before = serverIds(server);
		int restoresBefore = SavestateClientNetwork.restoresCompleted();
		if (scene.viaMenu) {
			context.getInput().pressKey(SavestateKeys.SAVE);
		} else {
			server.runCommand("savestate save redteam-" + scene.name);
		}
		server.waitFor(s -> SavestateEngine.get(s).list().size() > before.size(), 200);
		Set<String> after = serverIds(server);
		after.removeAll(before);
		String id = after.iterator().next();
		context.waitFor(mc -> ClientStateManager.get().has(id), 200);
		CompoundTag snapshot = context.computeOnClient(mc -> ClientStateManager.get().snapshot(id));
		int capturedHud = snapshot.getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1);
		int base = capturedHud + LEAD_TICKS;
		String prefix = String.format(Locale.ROOT, "rt%02d-%s", index, scene.name);

		List<Path> afterCapture = new ArrayList<>();
		recordFrames(context, prefix + "-cap", base, afterCapture);

		disturb(context, server, env);
		context.takeScreenshot(prefix + "-disturbed");

		if (scene.viaMenu) {
			restoreViaMenu(context, id, restoresBefore);
		} else {
			server.runCommand("savestate load " + id);
			context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
		}
		int resumedHud = hudTicks(context);
		List<Path> afterResume = new ArrayList<>();
		String status;
		if (resumedHud > base) {
			status = "LATE resume at hud " + resumedHud + " > " + base;
		} else {
			recordFrames(context, prefix + "-res", base, afterResume);
			StringBuilder line = new StringBuilder();
			double worst = 0;
			for (int i = 0; i < afterCapture.size(); i++) {
				double fraction = compare(afterCapture.get(i), afterResume.get(i));
				worst = Math.max(worst, fraction);
				line.append(String.format(Locale.ROOT, " t%d=%.5f", FRAME_OFFSETS[i], fraction));
			}
			status = String.format(Locale.ROOT, "max=%.5f%s%s", worst, line, scene.viaMenu ? " [key+menu]" : " [command]");
		}
		System.out.printf("[redteam] %s: %s%n", scene.name, status);
		this.results.put(scene.name, status);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		resetClient(context);
		server.runCommand("savestate delete " + id);
	}

	private static void prepare(ClientGameTestContext context, TestServerContext server, Env env) {
		resetClient(context);
		server.runCommand("execute in minecraft:overworld run tp @p " + (env.o.getX() + 0.5) + " " + env.o.getY() + " " + (env.o.getZ() + 0.5) + " 0 0");
		context.waitTicks(5);
		server.runCommand("kill @e[type=!minecraft:player]");
		env.fill(-12, 0, -6, 12, 12, 16, "minecraft:air");
		env.fill(-12, 13, -6, 12, 45, 16, "minecraft:air");
		server.runCommand("effect clear @a");
		server.runCommand("weather clear");
		server.runCommand("time set 6000");
		server.runCommand("clear @a");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				player.setHealth(16.0F);
				player.getFoodData().setFoodLevel(6);
				player.getFoodData().setSaturation(0.0F);
			}
		});
		context.waitTicks(5);
		server.runCommand("kill @e[type=!minecraft:player]");
	}

	private static void resetClient(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			((net.swofty.dreammanhunt.client.mixin.state.HudAccessor) mc.gui.hud).dm$setIsHidden(false);
			mc.debugEntries.setOverlayVisible(false);
		});
	}

	private static void disturb(ClientGameTestContext context, TestServerContext server, Env env) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
		});
		context.waitTicks(80);
		context.getInput().holdKeyFor(options -> options.keyUp, 15);
		context.getInput().lookAt(140.0F, -20.0F);
		context.getInput().pressKey(options -> options.keyAttack);
		server.runCommand("damage @p 2 minecraft:generic");
		server.runCommand("give @p minecraft:cobblestone 3");
		context.waitTicks(40);
		context.getInput().lookAt(-60.0F, 30.0F);
		context.getInput().holdKeyFor(options -> options.keyLeft, 10);
		context.waitTicks(60);
	}

	private static void restoreViaMenu(ClientGameTestContext context, String id, int restoresBefore) {
		context.getInput().pressKey(SavestateKeys.MENU);
		context.waitForScreen(SavestateMenuScreen.class);
		context.waitFor(mc -> mc.gui.screen() instanceof SavestateMenuScreen menu && menu.hasList()
			&& menu.list().children().stream().anyMatch(entry -> entry.info().id().equals(id)), 200);
		context.waitTicks(3);
		SavestateList.Entry entry = context.computeOnClient(mc -> ((SavestateMenuScreen) mc.gui.screen()).list().children().stream()
			.filter(e -> e.info().id().equals(id)).findFirst().orElseThrow());
		double guiX = entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0;
		double guiY = entry.getContentY() + entry.getContentHeight() / 2.0;
		double[] window = context.computeOnClient(mc -> {
			Window w = mc.getWindow();
			return new double[]{guiX * w.getScreenWidth() / w.getGuiScaledWidth(), guiY * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > restoresBefore, 1200);
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

	private static int hudTicks(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ClientSnapshot.counters(mc).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1));
	}

	private static void recordFrames(ClientGameTestContext context, String prefix, int base, List<Path> shots) {
		for (int offset : FRAME_OFFSETS) {
			int target = base + offset;
			while (hudTicks(context) < target) {
				context.waitTick();
			}
			int hud = hudTicks(context);
			if (hud != target) {
				throw new AssertionError(prefix + ": ran past hud tick " + target + " (at " + hud + ")");
			}
			shots.add(context.takeScreenshot(prefix + "-t" + offset));
		}
	}

	private static double compare(Path expectedPath, Path actualPath) {
		try {
			BufferedImage expected = ImageIO.read(expectedPath.toFile());
			BufferedImage actual = ImageIO.read(actualPath.toFile());
			int w = Math.min(expected.getWidth(), actual.getWidth());
			int h = Math.min(expected.getHeight(), actual.getHeight());
			BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			long differing = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = expected.getRGB(x, y);
					int q = actual.getRGB(x, y);
					int delta = Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)),
						Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255))));
					if (delta > FUZZ) {
						differing++;
						diff.setRGB(x, y, 0xFF0000);
					} else {
						int grey = (((q >> 16) & 255) + ((q >> 8) & 255) + (q & 255)) / 9;
						diff.setRGB(x, y, (grey << 16) | (grey << 8) | grey);
					}
				}
			}
			if (differing > 0) {
				String name = actualPath.getFileName().toString().replace(".png", "-diff.png");
				ImageIO.write(diff, "png", actualPath.resolveSibling(name).toFile());
			}
			return differing / (double) (w * h);
		} catch (IOException e) {
			throw new AssertionError("Failed to read screenshots", e);
		}
	}

	private void writeReport(ClientGameTestContext context) {
		StringBuilder out = new StringBuilder();
		this.results.forEach((name, status) -> out.append(String.format(Locale.ROOT, "%-22s %s%n", name, status)));
		System.out.println("[redteam] results\n" + out);
		try {
			Files.writeString(Path.of("redteam-results.txt"), out.toString());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
