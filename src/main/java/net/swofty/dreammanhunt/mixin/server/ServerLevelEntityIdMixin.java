package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.swofty.dreammanhunt.server.engine.EntityIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelEntityIdMixin {
	@Inject(method = "getNextEntityId", at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$nextEntityId(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(EntityIds.next((ServerLevel) (Object) this));
	}
}
