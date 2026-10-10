package net.swofty.dreammanhunt.client.mixin.audio;

import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAudioAccessor {
	@Accessor("ambientSoundHandlers")
	List<AmbientSoundHandler> dma$getAmbientSoundHandlers();
}
