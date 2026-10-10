package net.swofty.dreammanhunt.mixin.randomroot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.level.Level;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Level.class, AngerManagement.class, PlayerSpawnFinder.class})
public abstract class ThreadLocalRandomCallsMixin {
	@WrapOperation(method = "<init>*", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;createThreadLocalInstance()Lnet/minecraft/util/RandomSource;"))
	private RandomSource dm$fromStream(Operation<RandomSource> original) {
		return SeedStreams.threadLocal(original::call);
	}
}
