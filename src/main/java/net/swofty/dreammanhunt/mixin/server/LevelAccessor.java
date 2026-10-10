package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelAccessor {
	@Accessor("random")
	RandomSource dreammanhunt$getRandom();

	@Accessor("randValue")
	int dreammanhunt$getRandValue();

	@Accessor("randValue")
	void dreammanhunt$setRandValue(int randValue);
}
