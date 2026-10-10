package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(
		method = "handleItemCooldown",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
		cancellable = true
	)
	private void dm$keepRestoredCooldowns(ClientboundCooldownPacket packet, CallbackInfo ci) {
		if (ClientStateManager.get().isRestoring()) {
			ci.cancel();
		}
	}
}
