package net.swofty.dreammanhunt.server.coordinator;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.network.SavestatePayloads;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface Requester {
	@Nullable ServerPlayer player();

	boolean canManage();

	boolean isCommand();

	void success(Component message);

	void failure(Component message);

	static Requester of(ServerPlayer requester) {
		MinecraftServer server = requester.level().getServer();
		UUID uuid = requester.getUUID();
		return new Requester() {
			@Override
			public @Nullable ServerPlayer player() {
				ServerPlayer current = server.getPlayerList().getPlayer(uuid);
				return current != null ? current : requester;
			}

			@Override
			public boolean canManage() {
				ServerPlayer player = this.player();
				return player != null && SavestatePermissions.canManage(player);
			}

			@Override
			public boolean isCommand() {
				return false;
			}

			@Override
			public void success(Component message) {
				send(message);
			}

			@Override
			public void failure(Component message) {
				send(message);
			}

			private void send(Component message) {
				ServerPlayer player = server.getPlayerList().getPlayer(uuid);
				if (player == null || player.hasDisconnected()) {
					return;
				}
				if (ServerPlayNetworking.canSend(player, SavestatePayloads.Notice.TYPE)) {
					ServerPlayNetworking.send(player, new SavestatePayloads.Notice(message));
				} else {
					player.sendSystemMessage(message);
				}
			}
		};
	}

	static Requester of(CommandSourceStack source) {
		return new Requester() {
			@Override
			public @Nullable ServerPlayer player() {
				return source.getPlayer();
			}

			@Override
			public boolean canManage() {
				return SavestatePermissions.canManage(source);
			}

			@Override
			public boolean isCommand() {
				return true;
			}

			@Override
			public void success(Component message) {
				source.sendSuccess(() -> message, false);
			}

			@Override
			public void failure(Component message) {
				source.sendFailure(message);
			}
		};
	}
}
