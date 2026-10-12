package net.swofty.dreammanhunt.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.swofty.dreammanhunt.client.mixin.ui.ClientLevelPredictionAccessor;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.mixin.common.LivingEntitySyncAccessor;
import net.swofty.dreammanhunt.mixin.common.ServerGamePacketListenerSyncAccessor;
import net.swofty.dreammanhunt.server.coordinator.Requester;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class PlayerSyncGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("playersync")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);
			TestServerContext server = singleplayer.getServer();
			sneakAcrossRestore(context, server);
			inFlightMoveAcrossRestore(context, server);
			inFlightPredictionAcrossRestore(context, server);
			openChestAcrossRestore(context, server);
			portalTimerAcrossRestore(context, server);
			connectionAndLivingCounters(context, server);
			spectatorCameraAcrossRestore(context, server);
			vehicleAcrossRestore(context, server);
			fishingAcrossRestore(context, server);
		}
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(freePort())) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.waitTicks(20);
				chatAcrossRestore(context, server);
			}
		}
	}

	private static Properties freePort() {
		Properties properties = new Properties();
		try (ServerSocket socket = new ServerSocket(0)) {
			properties.setProperty("server-port", Integer.toString(socket.getLocalPort()));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		return properties;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		System.out.println("[playersync] ok: " + message);
	}

	private static ServerPlayer player(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		check(players.size() == 1, "one player online");
		return players.getFirst();
	}

	private static String save(TestServerContext server, String name) {
		return server.computeOnServer(s -> SavestateCoordinator.get(s)
				.save(Requester.of(s.createCommandSourceStack()), name)
				.orElseThrow(() -> new AssertionError("save failed"))
				.id());
	}

	private static void restore(ClientGameTestContext context, TestServerContext server, String id) {
		int before = SavestateClientNetwork.restoresCompleted();
		server.runOnServer(s -> check(SavestateCoordinator.get(s).restore(Requester.of(s.createCommandSourceStack()), id), "restore started"));
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > before, 20 * 60);
		server.waitFor(s -> !SavestateCoordinator.get(s).isBusy(), 200);
	}

	private static void sneakAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		context.getInput().holdShift();
		context.waitTicks(10);
		check(server.computeOnServer(s -> player(s).isShiftKeyDown() && player(s).getLastClientInput().shift()), "server sees the held sneak before the save");
		String id = save(server, "sneak");
		context.getInput().releaseShift();
		context.waitTicks(10);
		check(server.computeOnServer(s -> !player(s).isShiftKeyDown()), "server sees the released sneak");
		context.getInput().holdShift();
		restore(context, server, id);
		context.waitTicks(2);
		boolean clientCrouching = context.computeOnClient(mc -> mc.player.isShiftKeyDown());
		boolean serverShift = server.computeOnServer(s -> player(s).isShiftKeyDown());
		boolean serverInput = server.computeOnServer(s -> player(s).getLastClientInput().shift());
		Pose serverPose = server.computeOnServer(s -> player(s).getPose());
		System.out.printf("[playersync] after restore: client sneaking %s, server shift %s, server input.shift %s, server pose %s%n",
				clientCrouching, serverShift, serverInput, serverPose);
		check(clientCrouching == serverShift && serverShift == serverInput, "server and client agree on sneaking after the restore");
		check(serverPose == Pose.CROUCHING, "server pose is crouching after the restore (" + serverPose + ")");
		context.getInput().releaseShift();
		context.waitTicks(5);
	}

	private static void inFlightMoveAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		context.waitTicks(5);
		double[] start = context.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
		String[] id = new String[1];
		context.runOnClient(mc -> {
			mc.player.setPos(start[0] + 0.25, start[1], start[2]);
			mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(start[0] + 0.25, start[1], start[2], mc.player.onGround(), false));
			mc.getConnection().send(ServerboundClientTickEndPacket.INSTANCE);
		});
		id[0] = server.computeOnServer(s -> SavestateCoordinator.get(s).save(Requester.of(s.createCommandSourceStack()), "inflight").orElseThrow().id());
		double savedServerX = server.computeOnServer(s -> SavestateCoordinator.get(s).engine().find(id[0]).isPresent() ? player(s).getX() : Double.NaN);
		context.waitTicks(5);
		boolean inFlight = Math.abs(savedServerX - (start[0] + 0.25)) > 1.0E-6;
		System.out.printf("[playersync] in-flight move at the save: server x %.4f, client x %.4f (in flight: %s)%n", savedServerX, start[0] + 0.25, inFlight);
		context.runOnClient(mc -> mc.player.setPos(start[0] + 1.5, start[1], start[2]));
		context.waitTicks(5);
		double[] firstTick = new double[]{Double.NaN};
		int before = SavestateClientNetwork.restoresCompleted();
		server.runOnServer(s -> SavestateCoordinator.get(s).restore(Requester.of(s.createCommandSourceStack()), id[0]));
		server.waitFor(s -> {
			if (!SavestateCoordinator.get(s).isBusy() && Double.isNaN(firstTick[0])) {
				firstTick[0] = player(s).getX();
			}
			return !Double.isNaN(firstTick[0]);
		}, 20 * 60);
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > before, 20 * 60);
		double clientX = context.computeOnClient(mc -> mc.player.getX());
		System.out.printf("[playersync] after restore: server x at the first resumed tick %.4f, client x %.4f%n", firstTick[0], clientX);
		check(Math.abs(firstTick[0] - (start[0] + 0.25)) < 1.0E-6, "the server player starts the resumed timeline where the client was at the save");
		context.waitTicks(5);
	}

	private static void inFlightPredictionAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = server.computeOnServer(s -> player(s).blockPosition().offset(3, 0, 0));
		server.runOnServer(s -> player(s).level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
		context.waitTicks(5);
		String id = save(server, "prediction");
		context.waitTicks(5);
		int sequence = context.computeOnClient(mc -> {
			BlockStatePredictionHandler handler = ((ClientLevelPredictionAccessor) mc.level).dreammanhunt$getBlockStatePredictionHandler();
			handler.startPredicting();
			handler.retainKnownServerState(pos, Blocks.GOLD_BLOCK.defaultBlockState(), mc.player);
			handler.close();
			return handler.currentSequence();
		});
		restore(context, server, id);
		context.waitTicks(5);
		context.runOnClient(mc -> mc.level.handleBlockChangedAck(sequence));
		context.waitTicks(2);
		BlockState client = context.computeOnClient(mc -> mc.level.getBlockState(pos));
		BlockState serverState = server.computeOnServer(s -> player(s).level().getBlockState(pos));
		System.out.printf("[playersync] late ack for sequence %d: client %s, server %s%n", sequence, client, serverState);
		check(client.equals(serverState), "a late block-change ack for a pre-restore prediction leaves no ghost block");
	}

	private static void openChestAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = server.computeOnServer(s -> player(s).blockPosition().offset(-2, 0, 1));
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
			}
		});
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
			player(s).openMenu(provider);
		});
		context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>, 100);
		context.waitTicks(15);
		int savedId = server.computeOnServer(s -> player(s).containerMenu.containerId);
		int savedState = server.computeOnServer(s -> player(s).containerMenu.getStateId());
		String id = save(server, "chest");
		server.runOnServer(s -> player(s).closeContainer());
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		context.waitTicks(10);
		check(server.computeOnServer(s -> ChestBlockEntity.getOpenCount(player(s).level(), pos)) == 0, "chest closed before the restore");

		restore(context, server, id);
		context.waitTicks(3);
		boolean serverChest = server.computeOnServer(s -> player(s).containerMenu instanceof ChestMenu);
		int serverId = server.computeOnServer(s -> player(s).containerMenu.containerId);
		int serverState = server.computeOnServer(s -> player(s).containerMenu.getStateId());
		boolean clientScreen = context.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> screen && screen.getMenu() instanceof ChestMenu);
		int clientId = context.computeOnClient(mc -> mc.player.containerMenu.containerId);
		int clientState = context.computeOnClient(mc -> mc.player.containerMenu.getStateId());
		int openers = server.computeOnServer(s -> ChestBlockEntity.getOpenCount(player(s).level(), pos));
		System.out.printf("[playersync] chest after restore: server chest menu %s id %d state %d, client chest screen %s id %d state %d, openers %d%n",
				serverChest, serverId, serverState, clientScreen, clientId, clientState, openers);
		check(serverChest && clientScreen, "the chest that was open at the save is open again on both sides");
		check(serverId == clientId && serverState == clientState, "container id and state id agree after the restore");
		check(serverId == savedId && serverState == savedState, "container id and state id match the save (" + savedId + "/" + savedState + " -> " + serverId + "/" + serverState + ")");
		check(openers == 1, "the chest has one opener after the restore");
		context.runOnClient(mc -> mc.gameMode.handleContainerInput(clientId, 0, 0, ContainerInput.PICKUP, mc.player));
		context.waitTicks(5);
		ItemStack carried = server.computeOnServer(s -> player(s).containerMenu.getCarried().copy());
		check(carried.is(Items.DIAMOND) && carried.getCount() == 3, "a click in the reopened chest is accepted by the server (carried " + carried + ")");
		context.runOnClient(mc -> mc.gameMode.handleContainerInput(clientId, 0, 0, ContainerInput.PICKUP, mc.player));
		context.waitTicks(3);
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(5);
	}

	private static void portalTimerAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = server.computeOnServer(s -> player(s).blockPosition());
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.SURVIVAL);
			player.level().setBlock(pos, Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		});
		context.waitTicks(30);
		int[] saved = server.computeOnServer(s -> {
			int time = player(s).portalProcess == null ? -1 : player(s).portalProcess.getPortalTime();
			SavestateCoordinator.get(s).save(Requester.of(s.createCommandSourceStack()), "portal").orElseThrow();
			return new int[]{time};
		});
		String id = server.computeOnServer(s -> SavestateCoordinator.get(s).listStates().getLast().id());
		check(saved[0] > 10, "player is in the portal at the save (portal time " + saved[0] + ")");
		server.runOnServer(s -> {
			player(s).level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			player(s).portalProcess = null;
		});
		context.waitTicks(10);
		int[] restored = new int[1];
		int before = SavestateClientNetwork.restoresCompleted();
		server.runOnServer(s -> {
			SavestateCoordinator.get(s).restore(Requester.of(s.createCommandSourceStack()), id);
			ServerPlayer player = player(s);
			restored[0] = player.portalProcess == null ? -1 : player.portalProcess.getPortalTime();
		});
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > before, 20 * 60);
		server.runOnServer(s -> {
			player(s).level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			player(s).portalProcess = null;
			player(s).setGameMode(GameType.CREATIVE);
		});
		System.out.printf("[playersync] portal time at save %d, after restore %d%n", saved[0], restored[0]);
		check(restored[0] == saved[0], "the nether portal timer continues from the saved tick");
		context.waitTicks(5);
	}

	private static void connectionAndLivingCounters(ClientGameTestContext context, TestServerContext server) {
		String id = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			ServerGamePacketListenerSyncAccessor connection = (ServerGamePacketListenerSyncAccessor) player.connection;
			connection.dreammanhunt$sync$setClientIsFloating(true);
			connection.dreammanhunt$sync$setAboveGroundTickCount(37);
			((LivingEntitySyncAccessor) player).dreammanhunt$sync$setFallFlyTicks(23);
			return SavestateCoordinator.get(s).save(Requester.of(s.createCommandSourceStack()), "counters").orElseThrow().id();
		});
		int[] values = new int[3];
		int before = SavestateClientNetwork.restoresCompleted();
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			((ServerGamePacketListenerSyncAccessor) player.connection).dreammanhunt$sync$setAboveGroundTickCount(0);
			SavestateCoordinator.get(s).restore(Requester.of(s.createCommandSourceStack()), id);
			ServerPlayer restored = player(s);
			ServerGamePacketListenerSyncAccessor connection = (ServerGamePacketListenerSyncAccessor) restored.connection;
			values[0] = connection.dreammanhunt$sync$getAboveGroundTickCount();
			values[1] = connection.dreammanhunt$sync$getClientIsFloating() ? 1 : 0;
			values[2] = ((LivingEntitySyncAccessor) restored).dreammanhunt$sync$getFallFlyTicks();
			connection.dreammanhunt$sync$setClientIsFloating(false);
			connection.dreammanhunt$sync$setAboveGroundTickCount(0);
		});
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > before, 20 * 60);
		check(values[0] == 37 && values[1] == 1, "the connection's floating counters come back with the save (" + values[0] + ", " + values[1] + ")");
		check(values[2] == 23, "elytra fall-fly ticks come back with the save (" + values[2] + ")");
		context.waitTicks(5);
	}

	private static void spectatorCameraAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		UUID stand = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			Entity entity = EntityTypes.ARMOR_STAND.create(player.level(), EntitySpawnReason.COMMAND);
			entity.snapTo(player.getX() + 4.0, player.getY(), player.getZ() + 4.0, 0.0F, 0.0F);
			entity.setNoGravity(true);
			player.level().addFreshEntity(entity);
			player.setGameMode(GameType.SPECTATOR);
			return entity.getUUID();
		});
		context.waitTicks(10);
		server.runOnServer(s -> player(s).setCamera(player(s).level().getEntity(stand)));
		context.waitFor(mc -> mc.getCameraEntity() != null && mc.getCameraEntity().getUUID().equals(stand), 100);
		String id = save(server, "camera");
		server.runOnServer(s -> player(s).setCamera(null));
		context.waitFor(mc -> mc.getCameraEntity() == mc.player, 100);
		restore(context, server, id);
		context.waitTicks(5);
		UUID serverCamera = server.computeOnServer(s -> player(s).getCamera().getUUID());
		UUID clientCamera = context.computeOnClient(mc -> mc.getCameraEntity() == null ? null : mc.getCameraEntity().getUUID());
		System.out.printf("[playersync] camera after restore: server %s, client %s, stand %s%n", serverCamera, clientCamera, stand);
		check(stand.equals(serverCamera) && stand.equals(clientCamera), "the spectator camera target comes back on both sides");
		server.runOnServer(s -> {
			player(s).setCamera(null);
			player(s).setGameMode(GameType.CREATIVE);
			Entity entity = player(s).level().getEntity(stand);
			if (entity != null) {
				entity.discard();
			}
		});
		context.waitTicks(10);
	}

	private static void vehicleAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		UUID boat = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			Entity entity = EntityTypes.OAK_BOAT.create(player.level(), EntitySpawnReason.COMMAND);
			entity.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
			player.level().addFreshEntity(entity);
			player.startRiding(entity, true, true);
			return entity.getUUID();
		});
		context.waitFor(mc -> mc.player.getVehicle() != null, 100);
		context.waitTicks(10);
		String id = save(server, "boat");
		server.runOnServer(s -> player(s).stopRiding());
		context.waitFor(mc -> mc.player.getVehicle() == null, 100);
		restore(context, server, id);
		context.waitTicks(5);
		UUID serverVehicle = server.computeOnServer(s -> player(s).getVehicle() == null ? null : player(s).getVehicle().getUUID());
		UUID clientVehicle = context.computeOnClient(mc -> mc.player.getVehicle() == null ? null : mc.player.getVehicle().getUUID());
		boolean clientControls = context.computeOnClient(mc -> mc.player.getVehicle() != null && mc.player.getVehicle().isLocalInstanceAuthoritative());
		System.out.printf("[playersync] vehicle after restore: server %s, client %s, client controls it %s%n", serverVehicle, clientVehicle, clientControls);
		check(serverVehicle != null && serverVehicle.equals(clientVehicle), "the player rides the same boat on both sides after the restore");
		check(clientControls, "the client drives the boat after the restore");
		server.runOnServer(s -> {
			Entity vehicle = player(s).getVehicle();
			player(s).stopRiding();
			if (vehicle != null) {
				vehicle.discard();
			}
		});
		context.waitTicks(10);
	}

	private static void fishingAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
			BlockPos below = player.blockPosition().offset(0, -1, 4);
			player.level().setBlockAndUpdate(below, Blocks.WATER.defaultBlockState());
			FishingHook hook = new FishingHook(player, player.level(), 0, 0);
			hook.snapTo(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5, 0.0F, 0.0F);
			hook.setDeltaMovement(0.0, 0.0, 0.0);
			player.level().addFreshEntity(hook);
		});
		context.waitFor(mc -> mc.player.fishing != null, 100);
		context.waitTicks(10);
		UUID hook = server.computeOnServer(s -> player(s).fishing.getUUID());
		String id = save(server, "fishing");
		server.runOnServer(s -> {
			player(s).fishing.discard();
			player(s).fishing = null;
		});
		context.waitFor(mc -> mc.player.fishing == null, 100);
		int[] seen = new int[1];
		int before = SavestateClientNetwork.restoresCompleted();
		server.runOnServer(s -> {
			SavestateCoordinator.get(s).restore(Requester.of(s.createCommandSourceStack()), id);
			for (Entity entity : player(s).level().getAllEntities()) {
				if (entity instanceof FishingHook bobber) {
					seen[0]++;
					System.out.printf("[playersync] restored bobber %s owner %s removed %s%n", bobber.getUUID(), bobber.getPlayerOwner(), bobber.isRemoved());
				}
			}
		});
		context.waitFor(mc -> SavestateClientNetwork.restoresCompleted() > before, 20 * 60);
		System.out.printf("[playersync] bobbers right after the engine restore: %d%n", seen[0]);
		context.waitTicks(5);
		UUID serverHook = server.computeOnServer(s -> player(s).fishing == null ? null : player(s).fishing.getUUID());
		boolean serverAlive = server.computeOnServer(s -> player(s).fishing != null && !player(s).fishing.isRemoved());
		UUID clientHook = context.computeOnClient(mc -> mc.player.fishing == null ? null : mc.player.fishing.getUUID());
		System.out.printf("[playersync] fishing after restore: server %s (alive %s), client %s, saved %s%n", serverHook, serverAlive, clientHook, hook);
		check(hook.equals(serverHook) && serverAlive, "the server player owns the restored bobber again");
		check(hook.equals(clientHook), "the client player owns the restored bobber again");
		server.runOnServer(s -> {
			if (player(s).fishing != null) {
				player(s).fishing.discard();
			}
			player(s).setGameMode(GameType.CREATIVE);
			player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		context.waitTicks(5);
	}

	private static void chatAcrossRestore(ClientGameTestContext context, TestServerContext server) {
		String name = context.computeOnClient(mc -> mc.player.getName().getString());
		server.runCommand("op " + name);
		context.waitTicks(5);
		for (int i = 0; i < 3; i++) {
			int index = i;
			context.runOnClient(mc -> mc.getConnection().sendChat("before save " + index));
			context.waitTicks(4);
		}
		String id = save(server, "chat");
		for (int i = 0; i < 3; i++) {
			int index = i;
			context.runOnClient(mc -> mc.getConnection().sendChat("after save " + index));
			context.waitTicks(4);
		}
		restore(context, server, id);
		for (int i = 0; i < 5; i++) {
			int index = i;
			context.runOnClient(mc -> mc.getConnection().sendChat("after restore " + index));
			context.waitTicks(4);
		}
		context.runOnClient(mc -> mc.getConnection().sendCommand("me still here"));
		context.waitTicks(20);
		boolean connected = context.computeOnClient(mc -> mc.getConnection() != null && mc.level != null);
		int online = server.computeOnServer(s -> s.getPlayerList().getPlayerCount());
		check(connected && online == 1, "chat after a restore on a dedicated server keeps the player connected");
	}
}
