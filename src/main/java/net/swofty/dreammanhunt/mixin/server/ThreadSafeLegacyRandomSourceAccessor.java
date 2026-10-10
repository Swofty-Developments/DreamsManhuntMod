package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(ThreadSafeLegacyRandomSource.class)
public interface ThreadSafeLegacyRandomSourceAccessor {
	@Accessor("seed")
	AtomicLong dreammanhunt$getSeed();
}
