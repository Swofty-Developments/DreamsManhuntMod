package net.swofty.dreammanhunt.client.state;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;

final class ViewReadiness {
	private ViewReadiness() {
	}

	static boolean isReady(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.player.isRemoved()) {
			return false;
		}
		if (minecraft.gui.screen() instanceof LevelLoadingScreen || minecraft.gui.overlay() != null) {
			return false;
		}
		LevelRenderer renderer = minecraft.levelRenderer;
		ViewArea viewArea = renderer.viewArea();
		if (viewArea == null || !renderer.hasRenderedAllSections() || renderer.visibleSections().isEmpty()) {
			return false;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		int distance = Math.min(viewArea.getViewDistance(), minecraft.options.getEffectiveRenderDistance());
		ChunkPos center = ChunkPos.containing(camera.blockPosition());
		for (int dx = -distance; dx <= distance; dx++) {
			for (int dz = -distance; dz <= distance; dz++) {
				int x = center.x() + dx;
				int z = center.z() + dz;
				if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), distance, x, z) && !level.getChunkSource().hasChunk(x, z)) {
					return false;
				}
			}
		}
		if (!occlusionGraphSettled(renderer.sectionOcclusionGraph(), center, distance)) {
			return false;
		}
		long fade = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
		long now = Util.getMillis();
		for (SectionRenderDispatcher.RenderSection section : renderer.visibleSections()) {
			if (section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED || section.getVisibility(now, fade) < 1.0F) {
				return false;
			}
			if (section.getSectionMesh().hasRenderableLayers() && Reflect.<Long>get(section, "uploadedTime") == 0L) {
				return false;
			}
		}
		return true;
	}

	private static boolean occlusionGraphSettled(SectionOcclusionGraph graph, ChunkPos center, int distance) {
		if (Reflect.<Boolean>get(graph, "needsFullUpdate")) {
			return false;
		}
		Future<?> fullUpdate = Reflect.get(graph, "fullUpdateTask");
		if (fullUpdate != null && !fullUpdate.isDone()) {
			return false;
		}
		if (Reflect.<AtomicBoolean>get(graph, "needsFrustumUpdate").get()) {
			return false;
		}
		AtomicReference<Object> current = Reflect.get(graph, "currentGraph");
		Object state = current.get();
		if (state == null) {
			return false;
		}
		Collection<?> pending = Reflect.get(state, "sectionsToPropagateFrom");
		if (!pending.isEmpty()) {
			return false;
		}
		for (long chunkNode : graph.expectedChunks()) {
			int x = ChunkPos.getX(chunkNode);
			int z = ChunkPos.getZ(chunkNode);
			if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), distance, x, z)) {
				return false;
			}
		}
		return true;
	}
}
