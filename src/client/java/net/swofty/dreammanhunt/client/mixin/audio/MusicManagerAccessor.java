package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicManager.class)
public interface MusicManagerAccessor {
	@Accessor("currentMusic")
	@Nullable SoundInstance dma$getCurrentMusic();

	@Accessor("currentMusic")
	void dma$setCurrentMusic(@Nullable SoundInstance music);

	@Accessor("nextSongDelay")
	int dma$getNextSongDelay();

	@Accessor("nextSongDelay")
	void dma$setNextSongDelay(int delay);

	@Accessor("currentGain")
	float dma$getCurrentGain();

	@Accessor("currentGain")
	void dma$setCurrentGain(float gain);

	@Accessor("toastShown")
	boolean dma$getToastShown();

	@Accessor("toastShown")
	void dma$setToastShown(boolean shown);

	@Accessor("random")
	RandomSource dma$getRandom();
}
