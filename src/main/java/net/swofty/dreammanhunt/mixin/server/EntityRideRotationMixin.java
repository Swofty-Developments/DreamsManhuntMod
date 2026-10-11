package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.Entity;
import net.swofty.dreammanhunt.server.engine.EntityRuntimeRestorer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityRideRotationMixin {
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("RETURN"))
	private void dreammanhunt$restoreRiderRotation(Entity vehicle, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> cir) {
		EntityRuntimeRestorer.afterStartRiding((Entity) (Object) this);
	}
}
