package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.swofty.dreammanhunt.server.coordinator.PlayerSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin {
	@Shadow
	private int openCount;
	@Shadow
	private ShulkerBoxBlockEntity.AnimationStatus animationStatus;
	@Shadow
	private float progress;
	@Shadow
	private float progressOld;

	@Inject(method = "startOpen", at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$silentReopen(ContainerUser user, CallbackInfo ci) {
		if (!PlayerSync.isReopening()) {
			return;
		}
		ci.cancel();
		ShulkerBoxBlockEntity self = (ShulkerBoxBlockEntity) (Object) this;
		Level level = self.getLevel();
		if (self.isRemoved() || level == null || user.getLivingEntity().isSpectator()) {
			return;
		}
		this.openCount = Math.max(this.openCount, 0) + 1;
		this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENED;
		this.progress = 1.0F;
		this.progressOld = 1.0F;
		BlockPos pos = self.getBlockPos();
		level.blockEvent(pos, self.getBlockState().getBlock(), 1, this.openCount);
	}
}
