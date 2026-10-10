package net.swofty.dreammanhunt.client.mixin.audio;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
	@Accessor("loaded")
	boolean dma$isLoaded();

	@Accessor("tickCount")
	int dma$getTickCount();

	@Accessor("tickCount")
	void dma$setTickCount(int tickCount);

	@Accessor("instanceToChannel")
	Map<SoundInstance, ChannelAccess.ChannelHandle> dma$getInstanceToChannel();

	@Accessor("tickingSounds")
	List<TickableSoundInstance> dma$getTickingSounds();

	@Accessor("queuedSounds")
	Map<SoundInstance, Integer> dma$getQueuedSounds();

	@Accessor("soundDeleteTime")
	Map<SoundInstance, Integer> dma$getSoundDeleteTime();

	@Accessor("queuedTickableSounds")
	List<TickableSoundInstance> dma$getQueuedTickableSounds();

	@Accessor("gainBySource")
	Object2FloatMap<SoundSource> dma$getGainBySource();

	@Accessor("soundBuffers")
	SoundBufferLibrary dma$getSoundBuffers();

	@Accessor("executor")
	SoundEngineExecutor dma$getExecutor();
}
