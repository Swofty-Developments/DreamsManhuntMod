package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor("playerIo")
	PlayerDataStorage dreammanhunt$getPlayerIo();

	@Accessor("players")
	List<ServerPlayer> dreammanhunt$getPlayers();

	@Accessor("playersByUUID")
	Map<UUID, ServerPlayer> dreammanhunt$getPlayersByUuid();

	@Accessor("stats")
	Map<UUID, ServerStatsCounter> dreammanhunt$getStats();

	@Accessor("advancements")
	Map<UUID, PlayerAdvancements> dreammanhunt$getAdvancements();

	@Invoker("updateEntireScoreboard")
	void dreammanhunt$updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player);
}
