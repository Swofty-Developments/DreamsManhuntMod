package net.swofty.dreammanhunt.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.swofty.dreammanhunt.DreamManhunt;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ThumbnailManager {
	public static final int WIDTH = 320;
	public static final int HEIGHT = 180;
	private static final long CAPTURE_GIVE_UP_MILLIS = 5000L;
	private static final long RETRY_MILLIS = 500L;
	private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

	private static final Set<String> PENDING = new LinkedHashSet<>();
	private static long pendingSince;
	private static final Map<String, Identifier> LOADED = new HashMap<>();
	private static final Map<String, Long> LAST_ATTEMPT = new HashMap<>();

	private ThumbnailManager() {
	}

	public static boolean isValidId(String id) {
		return VALID_ID.matcher(id).matches();
	}

	public static Path directory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve(DreamManhunt.MOD_ID).resolve("thumbnails");
	}

	public static Path file(String id) {
		return directory().resolve(id + ".png");
	}

	public static void requestCapture(String id) {
		if (!isValidId(id)) {
			return;
		}
		if (PENDING.isEmpty()) {
			pendingSince = Util.getMillis();
		}
		PENDING.add(id);
	}

	public static boolean isCapturePending(String id) {
		return PENDING.contains(id);
	}

	public static void tick() {
		if (!PENDING.isEmpty() && Util.getMillis() - pendingSince > CAPTURE_GIVE_UP_MILLIS) {
			DreamManhunt.LOGGER.warn("No level frame rendered in time for savestate thumbnails {}", PENDING);
			PENDING.clear();
		}
	}

	public static void onLevelRendered(RenderTarget target) {
		if (PENDING.isEmpty() || target.getColorTexture() == null) {
			return;
		}
		List<String> ids = new ArrayList<>(PENDING);
		PENDING.clear();
		try {
			Screenshot.takeScreenshot(target, screenshot -> Util.ioPool().execute(() -> write(screenshot, ids)));
		} catch (RuntimeException e) {
			DreamManhunt.LOGGER.warn("Could not capture savestate thumbnail", e);
		}
	}

	private static void write(NativeImage screenshot, List<String> ids) {
		try (screenshot; NativeImage scaled = new NativeImage(WIDTH, HEIGHT, false)) {
			int width = screenshot.getWidth();
			int height = screenshot.getHeight();
			int cropWidth = width;
			int cropHeight = width * HEIGHT / WIDTH;
			if (cropHeight > height) {
				cropHeight = height;
				cropWidth = height * WIDTH / HEIGHT;
			}
			screenshot.resizeSubRectTo((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight, scaled);
			Files.createDirectories(directory());
			for (String id : ids) {
				scaled.writeToFile(file(id));
			}
		} catch (IOException | RuntimeException e) {
			DreamManhunt.LOGGER.warn("Could not save savestate thumbnail for {}", ids, e);
		}
		Minecraft.getInstance().execute(() -> ids.forEach(ThumbnailManager::release));
	}

	public static void delete(String id) {
		if (!isValidId(id)) {
			return;
		}
		PENDING.remove(id);
		release(id);
		Path path = file(id);
		Util.ioPool().execute(() -> {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				DreamManhunt.LOGGER.warn("Could not delete thumbnail {}", path, e);
			}
		});
	}

	public static @Nullable Identifier texture(String id) {
		Identifier loaded = LOADED.get(id);
		if (loaded != null) {
			return loaded;
		}
		if (!isValidId(id)) {
			return null;
		}
		long now = Util.getMillis();
		Long last = LAST_ATTEMPT.get(id);
		if (last != null && now - last < RETRY_MILLIS) {
			return null;
		}
		LAST_ATTEMPT.put(id, now);
		Path path = file(id);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(in);
			Identifier location = Identifier.fromNamespaceAndPath(DreamManhunt.MOD_ID, "savestate_thumbnails/" + id.toLowerCase());
			Minecraft.getInstance().getTextureManager().register(location, new ThumbnailTexture(id, image));
			LOADED.put(id, location);
			LAST_ATTEMPT.remove(id);
			return location;
		} catch (IOException | RuntimeException e) {
			DreamManhunt.LOGGER.warn("Could not load thumbnail {}", path, e);
			return null;
		}
	}

	public static void release(String id) {
		LAST_ATTEMPT.remove(id);
		Identifier location = LOADED.remove(id);
		if (location != null) {
			Minecraft.getInstance().getTextureManager().release(location);
		}
	}

	public static void releaseAll() {
		new ArrayList<>(LOADED.keySet()).forEach(ThumbnailManager::release);
		LAST_ATTEMPT.clear();
	}

	private static final class ThumbnailTexture extends DynamicTexture {
		ThumbnailTexture(String id, NativeImage image) {
			super(() -> "Savestate thumbnail " + id, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}
}
