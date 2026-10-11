package net.swofty.dreammanhunt.test.timeline.mixin;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.swofty.dreammanhunt.test.timeline.RngTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LegacyRandomSource.class)
public abstract class LegacyRandomSourceTraceMixin {
	@Inject(method = "next", at = @At("RETURN"))
	private void dreammanhuntTest$trace(int bits, CallbackInfoReturnable<Integer> cir) {
		if (RngTrace.active()) {
			RngTrace.record(this, bits, cir.getReturnValueI());
		}
	}
}
