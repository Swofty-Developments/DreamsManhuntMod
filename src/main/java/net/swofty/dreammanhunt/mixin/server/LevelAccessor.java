package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Level.class)
public interface LevelAccessor {
	@Accessor("blockEntityTickers")
	List<TickingBlockEntity> dreammanhunt$getBlockEntityTickers();

	@Accessor("random")
	RandomSource dreammanhunt$getRandom();

	@Accessor("soundSeedGenerator")
	RandomSource dreammanhunt$getSoundSeedGenerator();

	@Accessor("randValue")
	int dreammanhunt$getRandValue();

	@Accessor("randValue")
	void dreammanhunt$setRandValue(int randValue);

	@Accessor("oRainLevel")
	float dreammanhunt$getORainLevel();

	@Accessor("oRainLevel")
	void dreammanhunt$setORainLevel(float level);

	@Accessor("rainLevel")
	float dreammanhunt$getRainLevel();

	@Accessor("rainLevel")
	void dreammanhunt$setRainLevel(float level);

	@Accessor("oThunderLevel")
	float dreammanhunt$getOThunderLevel();

	@Accessor("oThunderLevel")
	void dreammanhunt$setOThunderLevel(float level);

	@Accessor("thunderLevel")
	float dreammanhunt$getThunderLevel();

	@Accessor("thunderLevel")
	void dreammanhunt$setThunderLevel(float level);
}
