package net.swofty.dreammanhunt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.swofty.dreammanhunt.client.state.ClientStateManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CutMarker {
	public static final int CUT_COLOR = 0xFF00FF00;
	public static final int SAVE_COLOR = 0xFFFF0000;
	public static final float WIDTH_FRACTION = 0.06F;
	public static final int SAVE_MARKER_TICKS = 10;

	private static final List<FrameListener> LISTENERS = new CopyOnWriteArrayList<>();
	private static boolean restoreFrozen;
	private static int saveMarkerTicks;
	private static long frames;

	private CutMarker() {
	}

	public static void onRestoreBegin() {
		restoreFrozen = true;
		saveMarkerTicks = 0;
	}

	public static void onRestoreResumed() {
		restoreFrozen = false;
	}

	public static void onCaptured() {
		if (saveMarkerTicks == 0) {
			saveMarkerTicks = SAVE_MARKER_TICKS;
		}
	}

	public static void onDiscarded() {
		saveMarkerTicks = 0;
	}

	public static void onDisconnect() {
		restoreFrozen = false;
		saveMarkerTicks = 0;
	}

	public static void tick() {
		if (saveMarkerTicks > 0) {
			saveMarkerTicks--;
		}
	}

	public static boolean cutVisible() {
		return restoreFrozen && ClientConfig.get().cutMarker() && ClientStateManager.get().isRestoring();
	}

	public static boolean saveVisible() {
		return saveMarkerTicks > 0 && ClientConfig.get().saveMarker() && !cutVisible();
	}

	public static int width(int guiWidth) {
		return Math.max(1, (int) Math.ceil(guiWidth * WIDTH_FRACTION));
	}

	public static void addFrameListener(FrameListener listener) {
		LISTENERS.add(listener);
	}

	public static void extract(Minecraft minecraft, GuiGraphicsExtractor graphics) {
		boolean cut = cutVisible();
		boolean save = !cut && saveVisible();
		if (cut || save) {
			graphics.nextStratum();
			graphics.fill(0, 0, width(graphics.guiWidth()), graphics.guiHeight(), cut ? CUT_COLOR : SAVE_COLOR);
		}
		frames++;
		for (FrameListener listener : LISTENERS) {
			listener.onFrame(frames, cut, save);
		}
	}

	public interface FrameListener {
		void onFrame(long frame, boolean cutMarker, boolean saveMarker);
	}
}
