package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerTickRateManager.class)
public interface ServerTickRateManagerAccessor {
	@Accessor("remainingSprintTicks")
	long dreammanhunt$getRemainingSprintTicks();

	@Accessor("remainingSprintTicks")
	void dreammanhunt$setRemainingSprintTicks(long ticks);

	@Accessor("scheduledCurrentSprintTicks")
	long dreammanhunt$getScheduledCurrentSprintTicks();

	@Accessor("scheduledCurrentSprintTicks")
	void dreammanhunt$setScheduledCurrentSprintTicks(long ticks);

	@Accessor("previousIsFrozen")
	boolean dreammanhunt$getPreviousIsFrozen();

	@Accessor("previousIsFrozen")
	void dreammanhunt$setPreviousIsFrozen(boolean frozen);

	@Invoker("updateStateToClients")
	void dreammanhunt$updateStateToClients();
}
