package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;
import net.swofty.dreammanhunt.client.state.extra.NeedleWobblers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NeedleDirectionHelper.class)
public abstract class NeedleDirectionHelperMixin {
	@Inject(method = "standardWobbler", at = @At("RETURN"))
	private static void dm$track(float factor, CallbackInfoReturnable<NeedleDirectionHelper.Wobbler> cir) {
		NeedleWobblers.register(cir.getReturnValue());
	}
}
