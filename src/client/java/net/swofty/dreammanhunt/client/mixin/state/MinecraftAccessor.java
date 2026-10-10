package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("clientTickCount")
	long dm$getClientTickCount();

	@Accessor("clientTickCount")
	void dm$setClientTickCount(long value);

	@Accessor("rightClickDelay")
	int dm$getRightClickDelay();

	@Accessor("rightClickDelay")
	void dm$setRightClickDelay(int value);

	@Accessor("pendingConnection")
	Connection dm$getPendingConnection();

	@Accessor("deltaTracker")
	DeltaTracker.Timer dm$getDeltaTracker();
}
