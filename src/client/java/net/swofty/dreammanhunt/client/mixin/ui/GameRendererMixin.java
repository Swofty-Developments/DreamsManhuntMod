package net.swofty.dreammanhunt.client.mixin.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.swofty.dreammanhunt.client.ui.ThumbnailManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V"))
	private void dreammanhunt$captureThumbnail(CallbackInfo ci) {
		ThumbnailManager.onLevelRendered(this.mainRenderTarget);
	}
}
