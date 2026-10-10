package net.swofty.dreammanhunt.client.mixin.extra;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
public abstract class AddEntitySeedMixin {
	@WrapMethod(method = "handleAddEntity")
	private void dm$seedFromUuid(ClientboundAddEntityPacket packet, Operation<Void> original) {
		SeedStreams.beginEntity(packet.getUUID());
		try {
			original.call(packet);
		} finally {
			SeedStreams.endEntity();
		}
	}
}
