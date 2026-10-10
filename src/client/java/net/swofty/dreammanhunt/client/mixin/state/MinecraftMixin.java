package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.state.FrozenTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void dm$tickHead(CallbackInfo ci) {
		ClientStateManager manager = ClientStateManager.get();
		if (manager.isRestoring()) {
			FrozenTick.run((Minecraft) (Object) this);
			ci.cancel();
			return;
		}
		manager.onClientTickStart();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void dm$tickTail(CallbackInfo ci) {
		ClientStateManager.get().onClientTickEnd();
	}

	@Inject(method = "runTick", at = @At("HEAD"))
	private void dm$frame(boolean advanceGameTime, CallbackInfo ci) {
		ClientStateManager.get().onFrame((Minecraft) (Object) this);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void dm$disconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
		ClientStateManager.get().onDisconnect();
	}
}
