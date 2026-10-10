package net.swofty.dreammanhunt.client.mixin.state;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacyRandomSource.class)
public interface LegacyRandomSourceAccessor {
	@Accessor("seed")
	AtomicLong dm$getSeed();
}
