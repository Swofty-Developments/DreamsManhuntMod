package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientAvatarState.class)
public interface ClientAvatarStateAccessor {
	@Accessor("deltaMovementOnPreviousTick")
	Vec3 dm$getDeltaMovementOnPreviousTick();

	@Accessor("deltaMovementOnPreviousTick")
	void dm$setDeltaMovementOnPreviousTick(Vec3 value);

	@Accessor("walkDist")
	float dm$getWalkDist();

	@Accessor("walkDist")
	void dm$setWalkDist(float value);

	@Accessor("walkDistO")
	float dm$getWalkDistO();

	@Accessor("walkDistO")
	void dm$setWalkDistO(float value);

	@Accessor("xCloak")
	double dm$getXCloak();

	@Accessor("xCloak")
	void dm$setXCloak(double value);

	@Accessor("yCloak")
	double dm$getYCloak();

	@Accessor("yCloak")
	void dm$setYCloak(double value);

	@Accessor("zCloak")
	double dm$getZCloak();

	@Accessor("zCloak")
	void dm$setZCloak(double value);

	@Accessor("xCloakO")
	double dm$getXCloakO();

	@Accessor("xCloakO")
	void dm$setXCloakO(double value);

	@Accessor("yCloakO")
	double dm$getYCloakO();

	@Accessor("yCloakO")
	void dm$setYCloakO(double value);

	@Accessor("zCloakO")
	double dm$getZCloakO();

	@Accessor("zCloakO")
	void dm$setZCloakO(double value);

	@Accessor("bob")
	float dm$getBob();

	@Accessor("bob")
	void dm$setBob(float value);

	@Accessor("bobO")
	float dm$getBobO();

	@Accessor("bobO")
	void dm$setBobO(float value);

}
