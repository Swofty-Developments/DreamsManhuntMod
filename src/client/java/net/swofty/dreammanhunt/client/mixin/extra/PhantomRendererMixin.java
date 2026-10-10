package net.swofty.dreammanhunt.client.mixin.extra;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.world.entity.monster.Phantom;
import net.swofty.dreammanhunt.client.state.extra.StableIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhantomRenderer.class)
public abstract class PhantomRendererMixin {
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Phantom;Lnet/minecraft/client/renderer/entity/state/PhantomRenderState;F)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Phantom;getUniqueFlapTickOffset()I"))
	private int dm$stableFlapOffset(Phantom phantom, Operation<Integer> original) {
		return StableIds.of(phantom) * 3;
	}
}
