package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.swofty.dreammanhunt.mixin.common.LivingEntitySyncAccessor;
import net.swofty.dreammanhunt.mixin.common.ServerGamePacketListenerSyncAccessor;
import net.swofty.dreammanhunt.mixin.common.ServerPlayerSyncAccessor;

import java.util.List;
import java.util.Map;

public final class PlayerSyncScenarios {
	private static boolean probesInstalled;

	private PlayerSyncScenarios() {
	}

	static void addTo(List<TimelineScenario> list) {
		installProbes();
		list.add(TimelineScenario.of("playersync-portal", 40, env -> {
			env.cmd("gamemode survival @a");
			env.cmd("tp @a %d %d %d", env.origin().getX(), env.origin().getY(), env.origin().getZ());
			env.server().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlock(env.origin(), Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			});
		}).settle(30).inputs((server, env, tick) -> {
			if (tick == 38) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.level().dimension() != Level.OVERWORLD) {
						player.teleportTo(server.overworld(), env.origin().getX() + 3.5, env.origin().getY(), env.origin().getZ() + 0.5,
								java.util.Set.of(), player.getYRot(), player.getXRot(), true);
					}
					player.setGameMode(GameType.CREATIVE);
				}
				server.overworld().setBlock(env.origin(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}));
		list.add(TimelineScenario.of("playersync-chest", 60, env -> {
			BlockPos chest = env.origin().offset(2, 0, 0);
			env.block(2, 0, 0, "minecraft:chest");
			env.server().runOnServer(server -> {
				ServerLevel level = server.overworld();
				if (level.getBlockEntity(chest) instanceof ChestBlockEntity entity) {
					entity.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
				}
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					MenuProvider provider = level.getBlockState(chest).getMenuProvider(level, chest);
					player.openMenu(provider);
				}
			});
		}).settle(20).inputs((server, env, tick) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (tick == 10) {
					player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
				} else if (tick == 20) {
					player.containerMenu.clicked(5, 0, ContainerInput.PICKUP, player);
				} else if (tick == 40) {
					player.closeContainer();
				}
			}
		}));
	}

	private static synchronized void installProbes() {
		if (probesInstalled) {
			return;
		}
		probesInstalled = true;
		Probes.addServer((server, area, out) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Map<String, String> fields = out.subject("player-protocol:" + player.getPlainTextName());
				fields.put("dimension", player.level().dimension().identifier().toString());
				fields.put("input", player.getLastClientInput().toString());
				fields.put("flags", player.isShiftKeyDown() + "/" + player.isSprinting() + "/" + player.isSwimming() + "/" + player.isFallFlying());
				fields.put("portal", player.portalProcess == null ? "none" : player.portalProcess.getPortalTime() + ":" + player.portalProcess.isInsidePortalThisTick());
				fields.put("camera", player.getCamera() == player ? "self" : player.getCamera().getUUID().toString());
				fields.put("menu", player.containerMenu.getClass().getSimpleName() + "#" + player.containerMenu.containerId
						+ "@" + player.containerMenu.getStateId() + " carried " + player.containerMenu.getCarried());
				fields.put("containerCounter", Integer.toString(((ServerPlayerSyncAccessor) player).dreammanhunt$sync$getContainerCounter()));
				ServerGamePacketListenerSyncAccessor connection = (ServerGamePacketListenerSyncAccessor) player.connection;
				fields.put("floating", connection.dreammanhunt$sync$getClientIsFloating() + ":" + connection.dreammanhunt$sync$getAboveGroundTickCount());
				fields.put("fallFlyTicks", Integer.toString(((LivingEntitySyncAccessor) player).dreammanhunt$sync$getFallFlyTicks()));
				fields.put("vehicle", player.getVehicle() == null ? "none" : player.getVehicle().getUUID().toString());
			}
			ServerLevel level = server.overworld();
			for (BlockPos pos : BlockPos.betweenClosed(area.min(), area.max())) {
				if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
					out.put("chest:" + pos.toShortString(), "openers", ChestBlockEntity.getOpenCount(level, pos));
				}
			}
		});
		Probes.addClient((minecraft, area, out) -> {
			if (minecraft.player == null) {
				return;
			}
			Map<String, String> fields = out.subject("client-player-protocol");
			fields.put("dimension", minecraft.player.level().dimension().identifier().toString());
			fields.put("menu", minecraft.player.containerMenu.getClass().getSimpleName() + "#" + minecraft.player.containerMenu.containerId
					+ "@" + minecraft.player.containerMenu.getStateId());
			fields.put("screen", minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getSimpleName());
			fields.put("camera", minecraft.getCameraEntity() == null ? "null" : minecraft.getCameraEntity() == minecraft.player ? "self"
					: minecraft.getCameraEntity().getUUID().toString());
			fields.put("vehicle", minecraft.player.getVehicle() == null ? "none" : minecraft.player.getVehicle().getUUID().toString());
			fields.put("flags", minecraft.player.isShiftKeyDown() + "/" + minecraft.player.isSprinting() + "/" + minecraft.player.isFallFlying());
		});
	}
}
