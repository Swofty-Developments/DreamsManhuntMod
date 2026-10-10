package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalProcessor.class)
public interface PortalProcessorAccessor {
	@Accessor("portal")
	Portal dreammanhunt$getPortal();

	@Accessor("portalTime")
	void dreammanhunt$setPortalTime(int ticks);
}
