package net.swofty.dreammanhunt.client.mixin.extra;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.monster.Phantom;
import net.swofty.dreammanhunt.client.state.extra.StableIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Phantom.class)
public abstract class PhantomMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Phantom;getUniqueFlapTickOffset()I"))
	private int dm$stableFlapOffset(Phantom phantom, Operation<Integer> original) {
		return StableIds.of(phantom) * 3;
	}
}
