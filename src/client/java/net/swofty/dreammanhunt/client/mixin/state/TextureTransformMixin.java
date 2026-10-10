package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextureTransform.class)
public abstract class TextureTransformMixin {
	@WrapOperation(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private static long dm$visualGlintClock(Operation<Long> original) {
		return VisualClock.millis();
	}
}
