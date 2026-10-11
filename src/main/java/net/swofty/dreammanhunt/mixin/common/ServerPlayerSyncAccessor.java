package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
public interface ServerPlayerSyncAccessor {
	@Accessor("containerCounter")
	int dreammanhunt$sync$getContainerCounter();

	@Accessor("containerCounter")
	void dreammanhunt$sync$setContainerCounter(int counter);
}
