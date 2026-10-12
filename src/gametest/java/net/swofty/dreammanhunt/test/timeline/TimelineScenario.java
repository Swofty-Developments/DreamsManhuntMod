package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

public record TimelineScenario(String name, int ticks, int settleTicks, Consumer<TimelineEnv> setup, Inputs inputs, ResourceKey<Level> dimension, BlockPos center, int radius) {
	public interface Inputs {
		void apply(MinecraftServer server, TimelineEnv env, int tick);
	}

	public record Area(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
		public boolean contains(Entity entity) {
			return entity.level().dimension() == this.dimension && AABB.encapsulatingFullBlocks(this.min, this.max).contains(entity.position());
		}
	}

	public static TimelineScenario of(String name, int ticks, Consumer<TimelineEnv> setup) {
		return new TimelineScenario(name, ticks, 20, setup, (server, env, tick) -> {
		}, null, null, 24);
	}

	public TimelineScenario settle(int ticks) {
		return new TimelineScenario(this.name, this.ticks, ticks, this.setup, this.inputs, this.dimension, this.center, this.radius);
	}

	public TimelineScenario inputs(Inputs inputs) {
		return new TimelineScenario(this.name, this.ticks, this.settleTicks, this.setup, inputs, this.dimension, this.center, this.radius);
	}

	public TimelineScenario area(ResourceKey<Level> dimension, BlockPos center, int radius) {
		return new TimelineScenario(this.name, this.ticks, this.settleTicks, this.setup, this.inputs, dimension, center, radius);
	}

	public TimelineScenario renamed(String name) {
		return new TimelineScenario(name, this.ticks, this.settleTicks, this.setup, this.inputs, this.dimension, this.center, this.radius);
	}

	public TimelineScenario radius(int radius) {
		return new TimelineScenario(this.name, this.ticks, this.settleTicks, this.setup, this.inputs, this.dimension, this.center, radius);
	}
}
