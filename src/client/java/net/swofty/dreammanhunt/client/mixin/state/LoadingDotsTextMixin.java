package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.gui.screens.LoadingDotsText;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LoadingDotsText.class)
public abstract class LoadingDotsTextMixin {
	@ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
	private static long dm$visualDotsClock(long timeMs) {
		return VisualClock.millis();
	}
}
