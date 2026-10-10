package net.swofty.dreammanhunt.mixin.randomroot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.util.RandomSource;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpreadPlayersCommand.class)
public abstract class SpreadPlayersCommandMixin {
	@WrapOperation(method = "spreadPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;createThreadLocalInstance()Lnet/minecraft/util/RandomSource;"))
	private static RandomSource dm$fromStream(Operation<RandomSource> original) {
		return SeedStreams.threadLocal(original::call);
	}
}
