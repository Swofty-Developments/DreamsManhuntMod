package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

public record TimelineScenario(String name, int ticks, int settleTicks, Consumer<TimelineEnv> setup, Inputs inputs) {
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
		});
	}

	public TimelineScenario settle(int ticks) {
		return new TimelineScenario(this.name, this.ticks, ticks, this.setup, this.inputs);
	}

	public TimelineScenario inputs(Inputs inputs) {
		return new TimelineScenario(this.name, this.ticks, this.settleTicks, this.setup, inputs);
	}
}
