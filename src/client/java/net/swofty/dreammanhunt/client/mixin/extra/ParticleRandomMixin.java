package net.swofty.dreammanhunt.client.mixin.extra;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import net.swofty.dreammanhunt.client.state.extra.RandomStates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Particle.class)
public abstract class ParticleRandomMixin {
	@WrapOperation(method = "<init>*", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
	private RandomSource dm$restorableRandom(Operation<RandomSource> original) {
		return RandomSource.create(RandomStates.nextParticleSeed());
	}
}
