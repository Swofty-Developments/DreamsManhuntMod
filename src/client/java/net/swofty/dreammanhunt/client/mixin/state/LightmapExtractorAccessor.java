package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface LightmapExtractorAccessor {
	@Accessor("blockLightFlicker")
	float dm$getBlockLightFlicker();

	@Accessor("blockLightFlicker")
	void dm$setBlockLightFlicker(float value);

	@Accessor("needsUpdate")
	boolean dm$getNeedsUpdate();

	@Accessor("needsUpdate")
	void dm$setNeedsUpdate(boolean value);

	@Accessor("randomSource")
	RandomSource dm$getRandomSource();
}
