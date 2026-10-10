package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerSyncAccessor {
	@Accessor("clientIsFloating")
	boolean dreammanhunt$sync$getClientIsFloating();

	@Accessor("clientIsFloating")
	void dreammanhunt$sync$setClientIsFloating(boolean floating);

	@Accessor("aboveGroundTickCount")
	int dreammanhunt$sync$getAboveGroundTickCount();

	@Accessor("aboveGroundTickCount")
	void dreammanhunt$sync$setAboveGroundTickCount(int ticks);

	@Accessor("clientVehicleIsFloating")
	boolean dreammanhunt$sync$getClientVehicleIsFloating();

	@Accessor("clientVehicleIsFloating")
	void dreammanhunt$sync$setClientVehicleIsFloating(boolean floating);

	@Accessor("aboveGroundVehicleTickCount")
	int dreammanhunt$sync$getAboveGroundVehicleTickCount();

	@Accessor("aboveGroundVehicleTickCount")
	void dreammanhunt$sync$setAboveGroundVehicleTickCount(int ticks);
}
