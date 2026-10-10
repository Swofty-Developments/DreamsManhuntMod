package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.swofty.dreammanhunt.server.engine.EntityRngRestorer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRngMixin {
	@Inject(method = "load", at = @At("TAIL"))
	private void dreammanhunt$restoreRandom(ValueInput input, CallbackInfo ci) {
		EntityRngRestorer.apply((Entity) (Object) this);
	}
}
