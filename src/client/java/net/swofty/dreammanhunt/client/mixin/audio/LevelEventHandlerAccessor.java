package net.swofty.dreammanhunt.client.mixin.audio;

import java.util.Map;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelEventHandler.class)
public interface LevelEventHandlerAccessor {
	@Accessor("playingJukeboxSongs")
	Map<BlockPos, SoundInstance> dma$getPlayingJukeboxSongs();
}
