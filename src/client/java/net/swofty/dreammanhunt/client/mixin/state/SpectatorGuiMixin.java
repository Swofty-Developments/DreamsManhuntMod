package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.gui.components.spectator.SpectatorGui.class)
public abstract class SpectatorGuiMixin {
	@WrapOperation(method = {"onHotbarSelected", "getHotbarAlpha", "onMouseScrolled", "onHotbarActionKeyPressed"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long dm$visualClock(Operation<Long> original) {
		return VisualClock.millis();
	}
}
