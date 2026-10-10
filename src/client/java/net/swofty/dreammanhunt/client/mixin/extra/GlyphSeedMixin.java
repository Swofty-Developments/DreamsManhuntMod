package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.swofty.dreammanhunt.client.mixin.state.MinecraftAccessor;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GlyphSeedMixin {
	@Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
	private void dm$seedGlyphs(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		long tick = ((MinecraftAccessor) minecraft).dm$getClientTickCount();
		int partial = Float.floatToIntBits(deltaTracker.getGameTimeDeltaPartialTick(true));
		long seed = SeedStreams.Stream.mix(SeedStreams.CLIENT.salt() ^ SeedStreams.Stream.mix(tick * 0x9E3779B97F4A7C15L + partial));
		((FontAccessor) minecraft.font).dm$getRandom().setSeed(seed);
	}
}
