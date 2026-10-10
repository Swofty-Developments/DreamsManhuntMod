package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.swofty.dreammanhunt.server.engine.EntityRuntimeRestorer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRuntimeMixin {
	@Inject(method = "load", at = @At("TAIL"))
	private void dreammanhunt$restoreRuntime(ValueInput input, CallbackInfo ci) {
		EntityRuntimeRestorer.apply((Entity) (Object) this);
	}
}
