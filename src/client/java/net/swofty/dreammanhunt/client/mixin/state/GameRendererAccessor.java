package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("bossOverlayWorldDarkening")
	float dm$getBossOverlayWorldDarkening();

	@Accessor("bossOverlayWorldDarkening")
	void dm$setBossOverlayWorldDarkening(float value);

	@Accessor("bossOverlayWorldDarkeningO")
	float dm$getBossOverlayWorldDarkeningO();

	@Accessor("bossOverlayWorldDarkeningO")
	void dm$setBossOverlayWorldDarkeningO(float value);

	@Accessor("lightmapRenderStateExtractor")
	LightmapRenderStateExtractor dm$getLightmapExtractor();
}
