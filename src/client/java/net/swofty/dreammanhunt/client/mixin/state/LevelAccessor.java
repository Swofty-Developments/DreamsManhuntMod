package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelAccessor {
	@Accessor("rainLevel")
	float dm$getRainLevel();

	@Accessor("rainLevel")
	void dm$setRainLevel(float value);

	@Accessor("oRainLevel")
	float dm$getORainLevel();

	@Accessor("oRainLevel")
	void dm$setORainLevel(float value);

	@Accessor("thunderLevel")
	float dm$getThunderLevel();

	@Accessor("thunderLevel")
	void dm$setThunderLevel(float value);

	@Accessor("oThunderLevel")
	float dm$getOThunderLevel();

	@Accessor("oThunderLevel")
	void dm$setOThunderLevel(float value);

}
