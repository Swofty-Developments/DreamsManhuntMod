package net.swofty.dreammanhunt.client.mixin.audio;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeAmbientSoundsHandler.class)
public interface BiomeAmbientSoundsHandlerAccessor {
	@Accessor("loopSounds")
	Object2ObjectArrayMap<Holder<SoundEvent>, BiomeAmbientSoundsHandler.LoopSoundInstance> dma$getLoopSounds();

	@Accessor("moodiness")
	void dma$setMoodiness(float moodiness);

	@Accessor("previousLoopSound")
	@Nullable Holder<SoundEvent> dma$getPreviousLoopSound();

	@Accessor("previousLoopSound")
	void dma$setPreviousLoopSound(@Nullable Holder<SoundEvent> sound);
}
