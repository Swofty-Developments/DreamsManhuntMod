package net.swofty.dreammanhunt.client.state;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

final class OverlayState {
	private OverlayState() {
	}

	record Snapshot(List<Object> visibleToasts, BitSet occupiedSlots, List<Toast> queuedToasts, List<Object> subtitles) {
	}

	static Snapshot capture(Minecraft minecraft) {
		ObjectCopier copier = new ObjectCopier(null, null);
		ToastManager toasts = minecraft.gui.toastManager();
		List<Object> visible = Reflect.get(toasts, "visibleToasts");
		BitSet slots = Reflect.get(toasts, "occupiedSlots");
		Deque<Toast> queued = Reflect.get(toasts, "queued");
		List<Object> subtitles = Reflect.get(subtitleOverlay(minecraft), "subtitles");
		return new Snapshot(
			copyAll(visible, copier),
			(BitSet) slots.clone(),
			ObjectCopier.copyAll(new ArrayList<>(queued), copier),
			copyAll(subtitles, copier)
		);
	}

	static void apply(Minecraft minecraft, Snapshot snapshot) {
		ObjectCopier copier = new ObjectCopier(null, null);
		ToastManager toasts = minecraft.gui.toastManager();
		List<Object> visible = Reflect.get(toasts, "visibleToasts");
		visible.clear();
		visible.addAll(copyAll(snapshot.visibleToasts(), copier));
		BitSet slots = Reflect.get(toasts, "occupiedSlots");
		slots.clear();
		slots.or(snapshot.occupiedSlots());
		Deque<Toast> queued = Reflect.get(toasts, "queued");
		queued.clear();
		queued.addAll(ObjectCopier.copyAll(snapshot.queuedToasts(), copier));
		SubtitleOverlay overlay = subtitleOverlay(minecraft);
		List<Object> subtitles = Reflect.get(overlay, "subtitles");
		subtitles.clear();
		subtitles.addAll(copyAll(snapshot.subtitles(), copier));
		List<Object> audible = Reflect.get(overlay, "audibleSubtitles");
		audible.clear();
	}

	private static SubtitleOverlay subtitleOverlay(Minecraft minecraft) {
		return Reflect.get(minecraft.gui.hud, "subtitleOverlay");
	}

	static int visibleToastCount(Minecraft minecraft) {
		List<Object> visible = Reflect.get(minecraft.gui.toastManager(), "visibleToasts");
		return visible.size();
	}

	private static List<Object> copyAll(List<Object> sources, ObjectCopier copier) {
		List<Object> out = new ArrayList<>(sources.size());
		for (Object source : sources) {
			Object copy = copier.copyRoot(source);
			if (copy != null) {
				out.add(copy);
			}
		}
		return out;
	}
}
