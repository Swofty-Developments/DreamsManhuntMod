package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
	@Accessor("customSpawners")
	List<CustomSpawner> dreammanhunt$getCustomSpawners();

	@Accessor("ENTITY_COUNTER")
	static AtomicInteger dreammanhunt$getEntityCounter() {
		throw new UnsupportedOperationException();
	}

	@Accessor("emptyTime")
	int dreammanhunt$getEmptyTime();

	@Accessor("emptyTime")
	void dreammanhunt$setEmptyTime(int ticks);
}
