package net.swofty.dreammanhunt.client.mixin.ui;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockStatePredictionHandler.class)
public interface BlockStatePredictionHandlerAccessor {
	@Accessor("serverVerifiedStates")
	Long2ObjectOpenHashMap<?> dreammanhunt$getServerVerifiedStates();
}
