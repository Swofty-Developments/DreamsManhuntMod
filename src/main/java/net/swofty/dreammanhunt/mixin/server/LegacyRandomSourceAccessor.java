package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public interface LegacyRandomSourceAccessor {
	@Accessor("seed")
	AtomicLong dreammanhunt$getSeed();
}
