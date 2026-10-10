package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TickRateManager.class)
public abstract class TickRateManagerMixin {
	@Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$freezeEverythingDuringRestore(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SavestateCoordinator.isFrozen((TickRateManager) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
