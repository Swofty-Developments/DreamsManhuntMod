package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LerpingBossEvent.class)
public abstract class LerpingBossEventMixin {
	@WrapOperation(method = {"<init>", "setProgress", "getProgress"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long dm$visualBossClock(Operation<Long> original) {
		return VisualClock.millis();
	}
}
