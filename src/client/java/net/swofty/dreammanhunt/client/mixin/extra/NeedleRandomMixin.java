package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.client.renderer.item.properties.numeric.Time;
import net.swofty.dreammanhunt.client.state.extra.NeedleWobblers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CompassAngleState.class, Time.class})
public abstract class NeedleRandomMixin {
	@Inject(method = "<init>*", at = @At("RETURN"))
	private void dm$trackRandom(CallbackInfo ci) {
		NeedleWobblers.registerOwner(this);
	}
}
