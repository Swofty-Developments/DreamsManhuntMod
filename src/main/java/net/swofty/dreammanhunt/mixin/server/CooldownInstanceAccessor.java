package net.swofty.dreammanhunt.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("startTime")
	int dreammanhunt$getStartTime();

	@Accessor("endTime")
	int dreammanhunt$getEndTime();
}
