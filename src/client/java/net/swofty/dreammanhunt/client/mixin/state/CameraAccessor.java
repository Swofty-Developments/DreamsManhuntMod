package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
	@Accessor("eyeHeight")
	float dm$getEyeHeight();

	@Accessor("eyeHeight")
	void dm$setEyeHeight(float value);

	@Accessor("eyeHeightOld")
	float dm$getEyeHeightOld();

	@Accessor("eyeHeightOld")
	void dm$setEyeHeightOld(float value);

	@Accessor("fovModifier")
	float dm$getFovModifier();

	@Accessor("fovModifier")
	void dm$setFovModifier(float value);

	@Accessor("oldFovModifier")
	float dm$getOldFovModifier();

	@Accessor("oldFovModifier")
	void dm$setOldFovModifier(float value);

}
