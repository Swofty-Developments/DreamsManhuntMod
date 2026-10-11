package net.swofty.dreammanhunt.client.mixin.ui;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {
	@Accessor("blockStatePredictionHandler")
	BlockStatePredictionHandler dreammanhunt$getBlockStatePredictionHandler();
}
