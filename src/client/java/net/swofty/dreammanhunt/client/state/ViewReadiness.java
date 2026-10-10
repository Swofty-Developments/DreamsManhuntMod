package net.swofty.dreammanhunt.client.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;

final class ViewReadiness {
	private static final int MAX_CHECK_RADIUS = 8;

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
		ChunkPos center = minecraft.player.chunkPosition();
		int radius = Math.max(1, Math.min(minecraft.options.getEffectiveRenderDistance(), MAX_CHECK_RADIUS) - 1);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius && !level.getChunkSource().hasChunk(center.x() + dx, center.z() + dz)) {
					return false;
				}
			}
		}
		LevelRenderer renderer = minecraft.levelRenderer;
		if (!renderer.hasRenderedAllSections() || renderer.visibleSections().isEmpty()) {
			return false;
		}
		long fade = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
		long now = Util.getMillis();
		for (SectionRenderDispatcher.RenderSection section : renderer.visibleSections()) {
			if (section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED || section.getVisibility(now, fade) < 1.0F) {
				return false;
			}
		}
		return true;
	}
}
