package net.swofty.dreammanhunt.client.state.audio;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundHandler;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.swofty.dreammanhunt.client.mixin.audio.BiomeAmbientSoundsHandlerAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.ChannelHandleAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.ClientLevelAudioAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.LegacyRandomAudioAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.LevelEventHandlerAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.LocalPlayerAudioAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.MusicManagerAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.PlayerAudioAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.SoundEngineAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.SoundManagerAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.UnderwaterAmbientSoundHandlerAccessor;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;

public final class AudioState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final long CHANNEL_QUERY_TIMEOUT_MS = 500L;
	private static final Map<SoundInstance, Sound> FORCED = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<SoundInstance, Long> SEEKS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<SoundInstance, CompletableFuture<AudioStream>> STREAMS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final List<Channel> BATCH = new ArrayList<>();
	private static volatile boolean batchOpen;
	private static boolean silenced;
	private static boolean replaying;
	private static @Nullable Prepared prepared;

	private AudioState() {
	}

	public static CompoundTag capture(Minecraft minecraft) {
		CompoundTag root = new CompoundTag();
		SoundEngine engine = engine(minecraft);
		SoundEngineAccessor access = (SoundEngineAccessor) engine;
		if (!access.dma$isLoaded()) {
			return root;
		}
		int tickCount = access.dma$getTickCount();
		root.putInt("tickCount", tickCount);
		CompoundTag gains = new CompoundTag();
		access.dma$getGainBySource().forEach((source, gain) -> gains.putFloat(source.getName(), gain));
		root.put("gainBySource", gains);

		SoundInstance music = ((MusicManagerAccessor) minecraft.getMusicManager()).dma$getCurrentMusic();
		Map<SoundInstance, BlockPos> jukeboxes = new IdentityHashMap<>();
		if (minecraft.level != null) {
			jukeboxes(minecraft).forEach((pos, instance) -> jukeboxes.put(instance, pos));
		}
		Map<SoundInstance, Holder<SoundEvent>> biomeLoops = new IdentityHashMap<>();
		BiomeAmbientSoundsHandler biome = handler(minecraft.player, BiomeAmbientSoundsHandler.class);
		if (biome != null) {
			((BiomeAmbientSoundsHandlerAccessor) biome).dma$getLoopSounds().forEach((holder, instance) -> biomeLoops.put(instance, holder));
		}

		Map<SoundInstance, long[]> positions = channelPositions(engine);
		ListTag playing = new ListTag();
		for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : access.dma$getInstanceToChannel().entrySet()) {
			SoundInstance instance = entry.getKey();
			if (entry.getValue().isStopped()) {
				continue;
			}
			CompoundTag record = SoundRecords.describe(instance);
			long[] position = positions.get(instance);
			record.putLong("offsetFrames", position == null ? 0L : position[0]);
			record.putInt("sampleRate", position == null ? 0 : (int) position[1]);
			Integer deleteTime = access.dma$getSoundDeleteTime().get(instance);
			record.putInt("deleteIn", deleteTime == null ? 0 : deleteTime - tickCount);
			if (instance == music) {
				record.putBoolean("music", true);
			}
			BlockPos jukebox = jukeboxes.get(instance);
			if (jukebox != null) {
				record.putLong("jukebox", jukebox.asLong());
			}
			Holder<SoundEvent> loopKey = biomeLoops.get(instance);
			if (loopKey != null) {
				record.putString("biomeLoop", holderId(loopKey));
			}
			playing.add(record);
		}
		root.put("playing", playing);

		ListTag queued = new ListTag();
		access.dma$getQueuedSounds().forEach((instance, due) -> {
			CompoundTag record = SoundRecords.describe(instance);
			record.putInt("dueIn", due - tickCount);
			queued.add(record);
		});
		root.put("queued", queued);
		ListTag queuedTickable = new ListTag();
		access.dma$getQueuedTickableSounds().forEach(instance -> queuedTickable.add(SoundRecords.describe(instance)));
		root.put("queuedTickable", queuedTickable);

		MusicManagerAccessor musicManager = (MusicManagerAccessor) minecraft.getMusicManager();
		CompoundTag musicTag = new CompoundTag();
		musicTag.putInt("nextSongDelay", musicManager.dma$getNextSongDelay());
		musicTag.putFloat("currentGain", musicManager.dma$getCurrentGain());
		musicTag.putBoolean("toastShown", musicManager.dma$getToastShown());
		putSeed(musicTag, "random", musicManager.dma$getRandom());
		root.put("musicManager", musicTag);

		CompoundTag ambient = new CompoundTag();
		if (biome != null) {
			ambient.putFloat("moodiness", biome.getMoodiness());
			Holder<SoundEvent> previous = ((BiomeAmbientSoundsHandlerAccessor) biome).dma$getPreviousLoopSound();
			if (previous != null) {
				ambient.putString("previousLoopSound", holderId(previous));
			}
		}
		UnderwaterAmbientSoundHandler underwater = handler(minecraft.player, UnderwaterAmbientSoundHandler.class);
		if (underwater != null) {
			ambient.putInt("underwaterTickDelay", ((UnderwaterAmbientSoundHandlerAccessor) underwater).dma$getTickDelay());
		}
		if (minecraft.player != null) {
			ambient.putBoolean("wasUnderwater", ((PlayerAudioAccessor) minecraft.player).dma$getWasUnderwater());
		}
		if (minecraft.level != null) {
			putSeed(ambient, "levelRandom", minecraft.level.getRandom());
		}
		root.put("ambient", ambient);
		return root;
	}

	public static void apply(Minecraft minecraft, CompoundTag audio) {
		Prepared current = prepared;
		if (current != null && current.source == audio) {
			replay(minecraft);
			return;
		}
		if (!silenced) {
			silence(minecraft);
		}
		prepared = prepare(minecraft, audio);
	}

	public static void onFrame(Minecraft minecraft) {
		boolean restoring = ClientStateManager.get().isRestoring();
		if (restoring && !silenced) {
			silence(minecraft);
		} else if (!restoring && silenced) {
			if (prepared != null) {
				replay(minecraft);
			} else {
				silenced = false;
				engine(minecraft).resume();
			}
		}
	}

	public static void onTickStart(Minecraft minecraft) {
		if (silenced && prepared != null && !ClientStateManager.get().isRestoring()) {
			replay(minecraft);
		}
	}

	public static boolean blocking() {
		return !replaying && (silenced || ClientStateManager.get().isRestoring());
	}

	public static @Nullable Sound forcedSound(Object instance) {
		return instance instanceof SoundInstance sound ? FORCED.get(sound) : null;
	}

	public static @Nullable CompletableFuture<AudioStream> preparedStream(SoundInstance instance) {
		return STREAMS.remove(instance);
	}

	public static void beforeAttach(SoundInstance instance, ChannelAccess.ChannelHandle handle, boolean streaming) {
		if (replaying) {
			handle.execute(channel -> {
				((ChannelPosition) channel).dma$holdStart();
				BATCH.add(channel);
			});
		}
		Long frames = SEEKS.remove(instance);
		if (frames == null || frames <= 0L) {
			return;
		}
		long seek = frames;
		if (streaming) {
			handle.execute(channel -> ((ChannelPosition) channel).dma$addPlayedFrames(seek));
		} else {
			handle.execute(channel -> ((ChannelPosition) channel).dma$seekFrames((int) Math.min(seek, Integer.MAX_VALUE)));
		}
	}

	public static boolean batchOpen() {
		return batchOpen;
	}

	private static void startBatch() {
		batchOpen = false;
		List<Integer> sources = new ArrayList<>();
		for (Channel channel : BATCH) {
			if (((ChannelPosition) channel).dma$takeHeldStart()) {
				sources.add(((ChannelPosition) channel).dma$source());
			}
		}
		BATCH.clear();
		if (!sources.isEmpty()) {
			AL10.alSourcePlayv(sources.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	public static Map<SoundInstance, long[]> channelPositions(Minecraft minecraft) {
		return channelPositions(engine(minecraft));
	}

	private static Map<SoundInstance, long[]> channelPositions(SoundEngine engine) {
		SoundEngineAccessor access = (SoundEngineAccessor) engine;
		Map<SoundInstance, ChannelAccess.ChannelHandle> handles = new IdentityHashMap<>(access.dma$getInstanceToChannel());
		Map<SoundInstance, long[]> out = Collections.synchronizedMap(new IdentityHashMap<>());
		CompletableFuture<Void> done = new CompletableFuture<>();
		access.dma$getExecutor().execute(() -> {
			try {
				Map<SoundInstance, long[]> raw = new IdentityHashMap<>();
				long reference = 0L;
				for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : handles.entrySet()) {
					Channel channel = ((ChannelHandleAccessor) entry.getValue()).dma$getChannel();
					if (channel != null) {
						long[] position = ((ChannelPosition) channel).dma$position();
						raw.put(entry.getKey(), position);
						reference = Math.max(reference, position[2]);
					}
				}
				for (Map.Entry<SoundInstance, long[]> entry : raw.entrySet()) {
					long[] position = entry.getValue();
					long frames = position[0];
					if (position[3] != 0L && position[2] > 0L) {
						double pitch = Float.intBitsToFloat((int) position[4]);
						frames += Math.round((reference - position[2]) * position[1] * pitch / 1.0E9);
					}
					out.put(entry.getKey(), new long[]{frames, position[1]});
				}
			} finally {
				done.complete(null);
			}
		});
		try {
			done.get(CHANNEL_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			LOGGER.warn("Timed out reading sound channel positions", e);
		}
		synchronized (out) {
			return new IdentityHashMap<>(out);
		}
	}

	private static void silence(Minecraft minecraft) {
		silenced = true;
		engine(minecraft).pauseAllExcept();
	}

	private static Prepared prepare(Minecraft minecraft, CompoundTag audio) {
		SoundEngineAccessor access = (SoundEngineAccessor) engine(minecraft);
		List<PreparedSound> sounds = new ArrayList<>();
		for (Tag element : audio.getListOrEmpty("playing")) {
			if (!(element instanceof CompoundTag record)) {
				continue;
			}
			Sound variant = SoundRecords.variant(record);
			CompletableFuture<AudioStream> stream = null;
			if (variant != null && access.dma$isLoaded()) {
				long offset = record.getLongOr("offsetFrames", 0L);
				boolean looping = record.getBooleanOr("looping", false) && record.getIntOr("delay", 0) <= 0;
				if (variant.shouldStream()) {
					stream = access.dma$getSoundBuffers().getStream(variant.getPath(), looping).thenApply(opened -> {
						try {
							return SeekingAudioStream.skip(opened, offset);
						} catch (IOException e) {
							throw new CompletionException(e);
						}
					});
				} else {
					access.dma$getSoundBuffers().getCompleteBuffer(variant.getPath());
				}
			}
			sounds.add(new PreparedSound(record, variant, stream));
		}
		return new Prepared(audio, sounds);
	}

	private static void replay(Minecraft minecraft) {
		Prepared current = prepared;
		prepared = null;
		silenced = false;
		if (current == null) {
			return;
		}
		CompoundTag audio = current.source;
		SoundEngine engine = engine(minecraft);
		SoundEngineAccessor access = (SoundEngineAccessor) engine;
		if (!access.dma$isLoaded() || audio.isEmpty() || minecraft.level == null) {
			engine.resume();
			return;
		}
		replaying = true;
		try {
			engine.stopAll();
			MusicManagerAccessor musicManager = (MusicManagerAccessor) minecraft.getMusicManager();
			musicManager.dma$setCurrentMusic(null);
			Map<BlockPos, SoundInstance> jukeboxes = jukeboxes(minecraft);
			jukeboxes.clear();
			BiomeAmbientSoundsHandler biome = handler(minecraft.player, BiomeAmbientSoundsHandler.class);
			if (biome != null) {
				((BiomeAmbientSoundsHandlerAccessor) biome).dma$getLoopSounds().clear();
			}

			int tickCount = audio.getIntOr("tickCount", access.dma$getTickCount());
			access.dma$setTickCount(tickCount);
			CompoundTag gains = audio.getCompoundOrEmpty("gainBySource");
			for (String key : gains.keySet()) {
				access.dma$getGainBySource().put(SoundRecords.source(key), gains.getFloatOr(key, 1.0F));
			}

			access.dma$getExecutor().execute(() -> {
				BATCH.clear();
				batchOpen = true;
			});
			for (PreparedSound sound : current.sounds) {
				CompoundTag record = sound.record;
				SoundInstance instance = SoundRecords.recreate(minecraft, record);
				if (instance == null || sound.variant == null) {
					continue;
				}
				FORCED.put(instance, sound.variant);
				SEEKS.put(instance, record.getLongOr("offsetFrames", 0L));
				if (sound.stream != null) {
					STREAMS.put(instance, sound.stream);
				}
				SoundEngine.PlayResult result = engine.play(instance);
				SEEKS.remove(instance);
				STREAMS.remove(instance);
				if (result == SoundEngine.PlayResult.NOT_STARTED) {
					continue;
				}
				access.dma$getSoundDeleteTime().put(instance, tickCount + record.getIntOr("deleteIn", 20));
				if (record.getBooleanOr("music", false)) {
					musicManager.dma$setCurrentMusic(instance);
				}
				if (record.contains("jukebox")) {
					jukeboxes.put(BlockPos.of(record.getLongOr("jukebox", 0L)), instance);
				}
				String loopKey = record.getStringOr("biomeLoop", "");
				if (biome != null && !loopKey.isEmpty() && instance instanceof BiomeAmbientSoundsHandler.LoopSoundInstance loop) {
					Holder<SoundEvent> holder = holder(minecraft, loopKey);
					if (holder != null) {
						((BiomeAmbientSoundsHandlerAccessor) biome).dma$getLoopSounds().put(holder, loop);
					}
				}
			}

			access.dma$getExecutor().execute(AudioState::startBatch);
			for (Tag element : audio.getListOrEmpty("queued")) {
				if (element instanceof CompoundTag record) {
					SoundInstance instance = SoundRecords.recreate(minecraft, record);
					Sound variant = SoundRecords.variant(record);
					if (instance != null) {
						if (variant != null) {
							FORCED.put(instance, variant);
						}
						access.dma$getQueuedSounds().put(instance, tickCount + record.getIntOr("dueIn", 0));
					}
				}
			}
			for (Tag element : audio.getListOrEmpty("queuedTickable")) {
				if (element instanceof CompoundTag record && SoundRecords.recreate(minecraft, record) instanceof TickableSoundInstance tickable) {
					Sound variant = SoundRecords.variant(record);
					if (variant != null) {
						FORCED.put(tickable, variant);
					}
					access.dma$getQueuedTickableSounds().add(tickable);
				}
			}

			CompoundTag musicTag = audio.getCompoundOrEmpty("musicManager");
			musicManager.dma$setNextSongDelay(musicTag.getIntOr("nextSongDelay", musicManager.dma$getNextSongDelay()));
			musicManager.dma$setCurrentGain(musicTag.getFloatOr("currentGain", musicManager.dma$getCurrentGain()));
			musicManager.dma$setToastShown(musicTag.getBooleanOr("toastShown", musicManager.dma$getToastShown()));
			setSeed(musicTag, "random", musicManager.dma$getRandom());

			CompoundTag ambient = audio.getCompoundOrEmpty("ambient");
			if (biome != null) {
				BiomeAmbientSoundsHandlerAccessor biomeAccess = (BiomeAmbientSoundsHandlerAccessor) biome;
				biomeAccess.dma$setMoodiness(ambient.getFloatOr("moodiness", biome.getMoodiness()));
				String previous = ambient.getStringOr("previousLoopSound", "");
				biomeAccess.dma$setPreviousLoopSound(previous.isEmpty() ? null : holder(minecraft, previous));
			}
			UnderwaterAmbientSoundHandler underwater = handler(minecraft.player, UnderwaterAmbientSoundHandler.class);
			if (underwater != null) {
				((UnderwaterAmbientSoundHandlerAccessor) underwater).dma$setTickDelay(ambient.getIntOr("underwaterTickDelay", 0));
			}
			if (minecraft.player != null && ambient.contains("wasUnderwater")) {
				((PlayerAudioAccessor) minecraft.player).dma$setWasUnderwater(ambient.getBooleanOr("wasUnderwater", false));
			}
			setSeed(ambient, "levelRandom", minecraft.level.getRandom());
		} catch (Throwable t) {
			LOGGER.error("Failed to restore audio", t);
		} finally {
			replaying = false;
		}
	}

	private static SoundEngine engine(Minecraft minecraft) {
		return ((SoundManagerAccessor) minecraft.getSoundManager()).dma$getSoundEngine();
	}

	private static Map<BlockPos, SoundInstance> jukeboxes(Minecraft minecraft) {
		LevelEventHandler handler = ((ClientLevelAudioAccessor) minecraft.level).dma$getLevelEventHandler();
		return ((LevelEventHandlerAccessor) handler).dma$getPlayingJukeboxSongs();
	}

	private static <T extends AmbientSoundHandler> @Nullable T handler(@Nullable LocalPlayer player, Class<T> type) {
		if (player == null) {
			return null;
		}
		for (AmbientSoundHandler handler : ((LocalPlayerAudioAccessor) player).dma$getAmbientSoundHandlers()) {
			if (type.isInstance(handler)) {
				return type.cast(handler);
			}
		}
		return null;
	}

	private static String holderId(Holder<SoundEvent> holder) {
		return holder.value().location().toString();
	}

	private static @Nullable Holder<SoundEvent> holder(Minecraft minecraft, String id) {
		Identifier location = Identifier.tryParse(id);
		if (location == null || minecraft.level == null || minecraft.player == null) {
			return null;
		}
		AmbientSounds sounds = minecraft.level.environmentAttributes().getValue(EnvironmentAttributes.AMBIENT_SOUNDS, minecraft.player.position());
		Holder<SoundEvent> current = sounds.loop().orElse(null);
		if (current != null && current.value().location().equals(location)) {
			return current;
		}
		return minecraft.level.registryAccess().lookupOrThrow(Registries.SOUND_EVENT).get(ResourceKey.create(Registries.SOUND_EVENT, location))
			.<Holder<SoundEvent>>map(reference -> reference)
			.orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(location)));
	}

	private static void putSeed(CompoundTag tag, String key, RandomSource random) {
		if (random instanceof LegacyRandomAudioAccessor legacy) {
			tag.putLong(key, legacy.dma$getSeed().get());
		}
	}

	private static void setSeed(CompoundTag tag, String key, RandomSource random) {
		if (random instanceof LegacyRandomAudioAccessor legacy && tag.contains(key)) {
			legacy.dma$getSeed().set(tag.getLongOr(key, 0L));
		}
	}

	private record PreparedSound(CompoundTag record, @Nullable Sound variant, @Nullable CompletableFuture<AudioStream> stream) {
	}

	private record Prepared(CompoundTag source, List<PreparedSound> sounds) {
	}
}
