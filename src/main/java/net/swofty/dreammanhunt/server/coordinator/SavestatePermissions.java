package net.swofty.dreammanhunt.server.coordinator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.DreamManhunt;

public final class SavestatePermissions {
	public static final Identifier MANAGE = Identifier.fromNamespaceAndPath(DreamManhunt.MOD_ID, "manage");

	private SavestatePermissions() {
	}

	public static boolean canManage(ServerPlayer player) {
		SavestateConfig config = SavestateConfig.get();
		if (config.allowEveryone()) {
			return true;
		}
		if (player.level().getServer().isSingleplayerOwner(player.nameAndId())) {
			return true;
		}
		return player.checkPermission(MANAGE, config.permissionLevel());
	}

	public static boolean canManage(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			return canManage(player);
		}
		SavestateConfig config = SavestateConfig.get();
		return config.allowEveryone() || source.checkPermission(MANAGE, config.permissionLevel());
	}
}
