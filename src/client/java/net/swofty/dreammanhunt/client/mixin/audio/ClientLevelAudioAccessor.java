package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientLevelAudioAccessor {
	@Accessor("levelEventHandler")
	LevelEventHandler dma$getLevelEventHandler();
}
