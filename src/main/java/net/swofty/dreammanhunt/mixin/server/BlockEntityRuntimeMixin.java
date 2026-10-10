package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.swofty.dreammanhunt.server.engine.BlockEntityRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityRuntimeMixin {
	@Inject(method = "setLevel", at = @At("TAIL"))
	private void dreammanhunt$restoreRuntime(Level level, CallbackInfo ci) {
		BlockEntityRuntime.apply((BlockEntity) (Object) this, level);
	}
}
