package net.swofty.dreammanhunt.server.tracking.mixin;

import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VecDeltaCodec.class)
public interface VecDeltaCodecAccessor {
	@Accessor("base")
	Vec3 dreammanhunt$getBase();
}
