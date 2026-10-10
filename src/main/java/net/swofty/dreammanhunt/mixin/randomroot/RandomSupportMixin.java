package net.swofty.dreammanhunt.mixin.randomroot;

import net.minecraft.world.level.levelgen.RandomSupport;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomSupport.class)
public abstract class RandomSupportMixin {
	@Inject(method = "generateUniqueSeed", at = @At("HEAD"), cancellable = true)
	private static void dm$seedFromStream(CallbackInfoReturnable<Long> cir) {
		Long seed = SeedStreams.nextSeed();
		if (seed != null) {
			cir.setReturnValue(seed);
		}
	}
}
