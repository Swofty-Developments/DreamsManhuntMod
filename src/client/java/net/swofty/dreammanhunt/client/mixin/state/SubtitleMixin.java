package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.SubtitleOverlay$Subtitle")
public abstract class SubtitleMixin {
	@WrapOperation(method = {"<init>", "getBrightness", "refresh", "purgeOldInstances"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long dm$visualClock(Operation<Long> original) {
		return VisualClock.millis();
	}
}
