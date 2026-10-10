package net.swofty.dreammanhunt.test.forensic;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Hud;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.state.ClientStateManager;

public final class FrameRecorder {
	private static final AtomicBoolean INSTALLED = new AtomicBoolean();
	private static final Field TICK_COUNT;
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "forensic-frame-writer");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicInteger PENDING = new AtomicInteger();

	private static volatile long clock;
	private static volatile Recording active;
	private static volatile int seenSaveRequests;
	private static volatile int seenRestores;
	private static volatile int liveHud = -1;
	private static volatile boolean overrideArmed;

	static {
		try {
			TICK_COUNT = Hud.class.getDeclaredField("tickCount");
			TICK_COUNT.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private FrameRecorder() {
	}

	public record Frame(int index, long clock, int hudTick, Path crop) {
	}

	public static final class Recording {
		private final String name;
		private final Path dir;
		private final HudDecoder.Geometry geometry;
		private final int fullFrameEvery;
		private final List<Frame> frames = new ArrayList<>();
		private volatile long saveClock = -1;
		private volatile long resumeClock = -1;
		private volatile int hudAtResume = -1;
		private volatile int overriddenTo = -1;

		private Recording(String name, Path dir, HudDecoder.Geometry geometry, int fullFrameEvery) {
			this.name = name;
			this.dir = dir;
			this.geometry = geometry;
			this.fullFrameEvery = fullFrameEvery;
		}

		public String name() {
			return name;
		}

		public Path dir() {
			return dir;
		}

		public HudDecoder.Geometry geometry() {
			return geometry;
		}

		public synchronized List<Frame> frames() {
			return List.copyOf(frames);
		}

		public long saveClock() {
			return saveClock;
		}

		public long resumeClock() {
			return resumeClock;
		}

		public int hudAtResume() {
			return hudAtResume;
		}

		public int overriddenTo() {
			return overriddenTo;
		}
	}

	public static void install() {
		if (!INSTALLED.compareAndSet(false, true)) {
			return;
		}
		ClientTickEvents.START_CLIENT_TICK.register(FrameRecorder::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(FrameRecorder::onEndTick);
	}

	public static int hudTick(Minecraft minecraft) {
		try {
			return TICK_COUNT.getInt(minecraft.gui.hud);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static void setHudTick(Minecraft minecraft, int value) {
		try {
			TICK_COUNT.setInt(minecraft.gui.hud, value);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	public static long clock() {
		return clock;
	}

	public static void armTickRestoreSkip() {
		overrideArmed = true;
	}

	public static Recording start(Minecraft minecraft, String name, Path root, int fullFrameEvery) {
		HudDecoder.Geometry geometry = HudDecoder.Geometry.hudCrop(minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight(),
				minecraft.getWindow().getGuiScale());
		Path dir = root.resolve(name);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		Recording recording = new Recording(name, dir, geometry, fullFrameEvery);
		seenSaveRequests = SavestateClientNetwork.saveRequests();
		seenRestores = SavestateClientNetwork.restoresCompleted();
		active = recording;
		return recording;
	}

	public static Recording stop() {
		Recording recording = active;
		active = null;
		return recording;
	}

	public static void awaitWrites() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
		while (PENDING.get() > 0) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(PENDING.get() + " frame writes still pending");
			}
			Thread.onSpinWait();
		}
	}

	private static void onStartTick(Minecraft minecraft) {
		clock++;
		Recording recording = active;
		int restores = SavestateClientNetwork.restoresCompleted();
		if (restores != seenRestores) {
			seenRestores = restores;
			if (recording != null) {
				recording.resumeClock = clock;
				recording.hudAtResume = hudTick(minecraft);
			}
			if (overrideArmed) {
				overrideArmed = false;
				setHudTick(minecraft, liveHud);
				if (recording != null) {
					recording.overriddenTo = liveHud;
				}
			}
		}
		if (recording == null || minecraft.level == null) {
			return;
		}
		int index;
		synchronized (recording) {
			index = recording.frames.size();
		}
		int truth = hudTick(minecraft);
		long frameClock = clock;
		Path crop = recording.dir.resolve(String.format("f%05d-c%d.png", index, frameClock));
		boolean full = recording.fullFrameEvery > 0 && index % recording.fullFrameEvery == 0;
		Path fullPath = recording.dir.resolve(String.format("full-f%05d-c%d.png", index, frameClock));
		HudDecoder.Geometry g = recording.geometry;
		synchronized (recording) {
			recording.frames.add(new Frame(index, frameClock, truth, crop));
		}
		PENDING.incrementAndGet();
		Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
			try (NativeImage owned = image) {
				int w = Math.min(g.cropWidth(), owned.getWidth() - g.originX());
				int h = Math.min(g.cropHeight(), owned.getHeight() - g.originY());
				int[] pixels = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						pixels[y * w + x] = owned.getPixel(g.originX() + x, g.originY() + y);
					}
				}
				if (full) {
					owned.writeToFile(fullPath);
				}
				WRITER.execute(() -> {
					try {
						BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
						out.setRGB(0, 0, w, h, pixels, 0, w);
						ImageIO.write(out, "png", crop.toFile());
					} catch (IOException e) {
						throw new RuntimeException(e);
					} finally {
						PENDING.decrementAndGet();
					}
				});
			} catch (IOException | RuntimeException e) {
				PENDING.decrementAndGet();
				throw new RuntimeException(e);
			}
		});
	}

	private static void onEndTick(Minecraft minecraft) {
		if (!ClientStateManager.get().isRestoring() && minecraft.level != null) {
			liveHud = hudTick(minecraft);
		}
		int requests = SavestateClientNetwork.saveRequests();
		if (requests != seenSaveRequests) {
			seenSaveRequests = requests;
			Recording recording = active;
			if (recording != null && recording.saveClock < 0) {
				recording.saveClock = clock;
			}
		}
	}
}
