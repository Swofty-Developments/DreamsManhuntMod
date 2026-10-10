package net.swofty.dreammanhunt.server.coordinator;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.network.SavestatePayloads;

import java.util.UUID;

public final class SavestateServerHooks {
	private SavestateServerHooks() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(SavestatePayloads.SaveRequest.TYPE, (payload, context) ->
				SavestateCoordinator.get(context.server()).save(Requester.of(context.player()), payload.name()));
		ServerPlayNetworking.registerGlobalReceiver(SavestatePayloads.ListRequest.TYPE, (payload, context) ->
				SavestateCoordinator.get(context.server()).sendList(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreRequest.TYPE, (payload, context) ->
				SavestateCoordinator.get(context.server()).restore(Requester.of(context.player()), payload.id()));
		ServerPlayNetworking.registerGlobalReceiver(SavestatePayloads.DeleteRequest.TYPE, (payload, context) ->
				SavestateCoordinator.get(context.server()).delete(Requester.of(context.player()), payload.id()));
		ServerPlayNetworking.registerGlobalReceiver(SavestatePayloads.RestoreReady.TYPE, (payload, context) ->
				SavestateCoordinator.get(context.server()).onReady(context.player(), payload.id()));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			UUID uuid = player.getUUID();
			server.execute(() -> {
				SavestateCoordinator coordinator = SavestateCoordinator.peek(server);
				if (coordinator != null) {
					coordinator.onLeave(uuid);
				}
			});
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> SavestateConfig.load());
		ServerLifecycleEvents.SERVER_STOPPED.register(SavestateCoordinator::remove);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SavestateCommands.register(dispatcher));
	}
}
