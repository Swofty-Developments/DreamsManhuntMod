package net.swofty.dreammanhunt.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.client.mixin.audio.MusicManagerAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.SoundEngineAccessor;
import net.swofty.dreammanhunt.client.mixin.audio.SoundManagerAccessor;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.state.audio.AudioState;

public final class AudioGameTest implements FabricClientGameTest {
	private static final String STATE_ID = "audio-gametest";
	private static final double MAX_OFFSET_ERROR_SECONDS = 0.1;
	private static final String LOOP_EVENT = "minecraft:ambient.crimson_forest.loop";
	private static final String RECORD_EVENT = "minecraft:music_disc.cat";
	private static final List<String> POST_CAPTURE_EVENTS = List.of("minecraft:music_disc.13", "minecraft:entity.ender_dragon.growl");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("audio")) {
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("time set 6000");
			singleplayer.getServer().runCommand("weather clear");
			context.waitTicks(40);
			boolean loaded = context.computeOnClient(mc -> ((SoundEngineAccessor) engine(mc)).dma$isLoaded());
			if (!loaded) {
				throw new AssertionError("Sound engine is not running");
			}
			context.runOnClient(mc -> {
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(1.0);
				mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(1.0);
				mc.options.getSoundSourceOptionInstance(SoundSource.AMBIENT).set(1.0);
				mc.getMusicManager().startPlaying(Musics.CREATIVE);
				BiomeAmbientSoundsHandler.LoopSoundInstance loop = new BiomeAmbientSoundsHandler.LoopSoundInstance(SoundEvents.AMBIENT_CRIMSON_FOREST_LOOP.value());
				loop.fadeIn();
				mc.getSoundManager().play(loop);
				mc.getSoundManager().play(SimpleSoundInstance.forJukeboxSong(SoundEvents.MUSIC_DISC_CAT.value(), Vec3.atCenterOf(mc.player.blockPosition())));
			});
			context.waitTicks(110);

			long[] wavBytes = new long[2];
			CompoundTag[] captured = new CompoundTag[1];
			context.runOnClient(mc -> {
				ClientStateManager.get().capturePending();
				ClientStateManager.get().onCapture(STATE_ID);
				wavBytes[0] = wavSize();
				captured[0] = ClientStateManager.get().snapshot(STATE_ID).getCompoundOrEmpty("audio");
			});
			Map<String, double[]> before = offsets(captured[0]);
			System.out.printf("[audio] captured %s, wav bytes %d%n", describe(before), wavBytes[0]);
			String musicEvent = musicEvent(captured[0]);
			if (musicEvent == null) {
				throw new AssertionError("No music captured: " + before.keySet());
			}
			for (String required : List.of(LOOP_EVENT, RECORD_EVENT)) {
				if (!before.containsKey(required)) {
					throw new AssertionError("Capture is missing " + required + ": " + before.keySet());
				}
			}

			context.waitTicks(60);
			singleplayer.getServer().runCommand("playsound minecraft:music_disc.13 record @a ~ ~ ~ 1 1");
			singleplayer.getServer().runCommand("playsound minecraft:entity.ender_dragon.growl hostile @a ~ ~ ~ 1 1");
			context.runOnClient(mc -> mc.getMusicManager().stopPlaying());
			context.waitTicks(20);

			AtomicBoolean ready = new AtomicBoolean();
			context.runOnClient(mc -> {
				ClientStateManager.get().onRestoreBegin(STATE_ID);
				ClientStateManager.get().onRestoreApply(STATE_ID, () -> ready.set(true));
			});
			context.waitFor(mc -> ready.get(), 20 * 20);
			context.waitTicks(4);
			Map<String, double[]> frozenA = context.computeOnClient(AudioGameTest::live);
			context.waitTicks(10);
			Map<String, double[]> frozenB = context.computeOnClient(AudioGameTest::live);
			for (Map.Entry<String, double[]> entry : frozenA.entrySet()) {
				double[] later = frozenB.get(entry.getKey());
				if (later != null && Math.abs(later[0] - entry.getValue()[0]) > 0.0) {
					throw new AssertionError("Sound " + entry.getKey() + " kept playing during the restore barrier: " + entry.getValue()[0] + " -> " + later[0]);
				}
			}

			List<Map<String, double[]>> resumed = new ArrayList<>();
			String[] resumedMusic = new String[1];
			context.runOnClient(mc -> {
				ClientStateManager.get().onRestoreResume(STATE_ID);
				wavBytes[1] = wavSize();
				resumed.add(live(mc));
				SoundInstance music = ((MusicManagerAccessor) mc.getMusicManager()).dma$getCurrentMusic();
				resumedMusic[0] = music == null || music.getSound() == null ? null : music.getIdentifier() + "|" + music.getSound().getLocation();
			});
			Map<String, double[]> after = resumed.getFirst();
			System.out.printf("[audio] resumed %s, wav bytes %d%n", describe(after), wavBytes[1]);

			List<String> problems = new ArrayList<>();
			for (Map.Entry<String, double[]> entry : before.entrySet()) {
				double[] now = after.get(entry.getKey());
				if (now == null) {
					problems.add(entry.getKey() + " not playing after resume");
					continue;
				}
				double error = now[0] - entry.getValue()[0];
				System.out.printf("[audio] %s offset %.4f s -> %.4f s (error %.4f s)%n", entry.getKey(), entry.getValue()[0], now[0], error);
				if (Math.abs(error) > MAX_OFFSET_ERROR_SECONDS) {
					problems.add(entry.getKey() + " offset " + entry.getValue()[0] + " -> " + now[0]);
				}
			}
			for (String event : POST_CAPTURE_EVENTS) {
				if (after.containsKey(event)) {
					problems.add("post-capture sound " + event + " survived the restore");
				}
			}
			String expectedMusic = musicEvent + "|" + musicVariant(captured[0]);
			if (!expectedMusic.equals(resumedMusic[0])) {
				problems.add("music manager track " + resumedMusic[0] + ", expected " + expectedMusic);
			}
			if (!problems.isEmpty()) {
				throw new AssertionError("Audio restore mismatch: " + String.join("; ", problems));
			}

			context.waitTicks(40);
			Map<String, double[]> later = context.computeOnClient(AudioGameTest::live);
			for (String event : List.of(musicEvent, LOOP_EVENT, RECORD_EVENT)) {
				double[] start = after.get(event);
				double[] end = later.get(event);
				if (end == null || end[0] - start[0] < 1.0) {
					throw new AssertionError(event + " did not keep playing after resume: " + (start == null ? "-" : start[0]) + " -> " + (end == null ? "-" : end[0]));
				}
			}
			System.out.printf("[audio] two seconds later %s%n", describe(later));
			context.runOnClient(mc -> ClientStateManager.get().delete(STATE_ID));
		}
	}

	private static net.minecraft.client.sounds.SoundEngine engine(Minecraft mc) {
		return ((SoundManagerAccessor) mc.getSoundManager()).dma$getSoundEngine();
	}

	private static Map<String, double[]> live(Minecraft mc) {
		Map<String, double[]> out = new HashMap<>();
		Map<SoundInstance, net.minecraft.client.sounds.ChannelAccess.ChannelHandle> handles = ((SoundEngineAccessor) engine(mc)).dma$getInstanceToChannel();
		AudioState.channelPositions(mc).forEach((instance, position) -> {
			var handle = handles.get(instance);
			if (handle != null && !handle.isStopped() && position[1] > 0) {
				out.put(instance.getIdentifier().toString(), new double[]{position[0] / (double) position[1], position[1]});
			}
		});
		return out;
	}

	private static Map<String, double[]> offsets(CompoundTag audio) {
		Map<String, double[]> out = new HashMap<>();
		for (Tag element : audio.getListOrEmpty("playing")) {
			if (element instanceof CompoundTag record && record.getIntOr("sampleRate", 0) > 0) {
				int rate = record.getIntOr("sampleRate", 0);
				out.put(record.getStringOr("event", "?"), new double[]{record.getLongOr("offsetFrames", 0L) / (double) rate, rate});
			}
		}
		return out;
	}

	private static String musicEvent(CompoundTag audio) {
		for (Tag element : audio.getListOrEmpty("playing")) {
			if (element instanceof CompoundTag record && record.getBooleanOr("music", false)) {
				return record.getStringOr("event", "?");
			}
		}
		return null;
	}

	private static String musicVariant(CompoundTag audio) {
		for (Tag element : audio.getListOrEmpty("playing")) {
			if (element instanceof CompoundTag record && record.getBooleanOr("music", false)) {
				return record.getCompoundOrEmpty("sound").getStringOr("location", "?");
			}
		}
		return null;
	}

	private static String describe(Map<String, double[]> offsets) {
		StringBuilder builder = new StringBuilder("{");
		offsets.forEach((event, value) -> builder.append(event).append(String.format("=%.4fs@%d ", value[0], (int) value[1])));
		return builder.append("}").toString();
	}

	private static long wavSize() {
		try {
			Path wav = Path.of("dreammanhunt-audio.wav");
			return Files.exists(wav) ? Files.size(wav) : -1L;
		} catch (IOException e) {
			return -1L;
		}
	}
}
