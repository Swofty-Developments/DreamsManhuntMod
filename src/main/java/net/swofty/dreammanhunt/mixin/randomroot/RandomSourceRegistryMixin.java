package net.swofty.dreammanhunt.mixin.randomroot;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LegacyRandomSource.class, SingleThreadedRandomSource.class, ThreadSafeLegacyRandomSource.class, XoroshiroRandomSource.class})
public abstract class RandomSourceRegistryMixin {
	@Inject(method = "<init>*", at = @At("RETURN"))
	private void dm$register(CallbackInfo ci) {
		SeedStreams.register((RandomSource) (Object) this);
	}
}
