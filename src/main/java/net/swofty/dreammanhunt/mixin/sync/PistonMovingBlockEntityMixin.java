package net.swofty.dreammanhunt.mixin.sync;

import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin {
	private static final String DM_PROGRESS = "dreammanhunt_progress";

	@Shadow
	private float progress;

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void dm$saveExactProgress(ValueOutput output, CallbackInfo ci) {
		output.putFloat(DM_PROGRESS, this.progress);
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void dm$loadExactProgress(ValueInput input, CallbackInfo ci) {
		this.progress = input.getFloatOr(DM_PROGRESS, this.progress);
	}
}
