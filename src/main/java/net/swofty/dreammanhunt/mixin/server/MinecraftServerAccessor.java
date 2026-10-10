package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("levels")
	Map<ResourceKey<Level>, ServerLevel> dreammanhunt$getLevels();

	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess dreammanhunt$getStorageSource();

	@Accessor("executor")
	Executor dreammanhunt$getExecutor();

	@Accessor("tickCount")
	int dreammanhunt$getTickCount();

	@Accessor("tickCount")
	void dreammanhunt$setTickCount(int tickCount);

	@Mutable
	@Accessor("randomSequences")
	void dreammanhunt$setRandomSequences(RandomSequences randomSequences);

	@Mutable
	@Accessor("weatherData")
	void dreammanhunt$setWeatherData(WeatherData weatherData);

	@Mutable
	@Accessor("clockManager")
	void dreammanhunt$setClockManager(ServerClockManager clockManager);

	@Mutable
	@Accessor("customBossEvents")
	void dreammanhunt$setCustomBossEvents(CustomBossEvents customBossEvents);

	@Mutable
	@Accessor("scheduledEvents")
	void dreammanhunt$setScheduledEvents(TimerQueue<MinecraftServer> scheduledEvents);

	@Accessor("stopwatches")
	void dreammanhunt$setStopwatches(Stopwatches stopwatches);

	@Accessor("commandStorage")
	void dreammanhunt$setCommandStorage(CommandStorage commandStorage);
}
