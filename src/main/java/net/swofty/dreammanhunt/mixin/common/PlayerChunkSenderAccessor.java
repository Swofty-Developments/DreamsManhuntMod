package net.swofty.dreammanhunt.mixin.common;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerChunkSender.class)
public interface PlayerChunkSenderAccessor {
	@Accessor("pendingChunks")
	LongSet dreammanhunt$pendingChunks();

	@Accessor("unacknowledgedBatches")
	int dreammanhunt$unacknowledgedBatches();
}
