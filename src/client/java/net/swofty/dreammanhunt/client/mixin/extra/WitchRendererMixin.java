package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.world.entity.monster.Witch;
import net.swofty.dreammanhunt.client.state.extra.StableIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitchRenderer.class)
public abstract class WitchRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Witch;Lnet/minecraft/client/renderer/entity/state/WitchRenderState;F)V", at = @At("TAIL"))
	private void dm$stableNoseSeed(Witch entity, WitchRenderState state, float partialTicks, CallbackInfo ci) {
		state.entityId = StableIds.of(entity);
	}
}
