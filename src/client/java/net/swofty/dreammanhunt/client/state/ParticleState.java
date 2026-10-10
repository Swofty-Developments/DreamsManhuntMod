package net.swofty.dreammanhunt.client.state;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.world.entity.Entity;
import net.swofty.dreammanhunt.client.mixin.state.ParticleEngineInvoker;

final class ParticleState {
	private ParticleState() {
	}

	record Entry(ParticleRenderType type, Particle particle) {
	}

	record Snapshot(List<Entry> particles, List<Particle> pending, List<TrackingEmitter> emitters, Map<ParticleLimit, Integer> counts) {
	}

	static Snapshot capture(Minecraft minecraft) {
		ParticleEngine engine = minecraft.particleEngine;
		ObjectCopier copier = new ObjectCopier(null, null);
		List<Entry> particles = new ArrayList<>();
		Map<ParticleRenderType, ParticleGroup<?>> groups = Reflect.get(engine, "particles");
		for (Map.Entry<ParticleRenderType, ParticleGroup<?>> group : groups.entrySet()) {
			Queue<? extends Particle> queue = Reflect.get(group.getValue(), "particles");
			for (Particle particle : queue) {
				particles.add(new Entry(group.getKey(), copier.copyRoot(particle)));
			}
		}
		Queue<Particle> toAdd = Reflect.get(engine, "particlesToAdd");
		Queue<TrackingEmitter> emitters = Reflect.get(engine, "trackingEmitters");
		Object2IntOpenHashMap<ParticleLimit> counts = Reflect.get(engine, "trackedParticleCounts");
		return new Snapshot(particles, ObjectCopier.copyAll(new ArrayList<>(toAdd), copier), ObjectCopier.copyAll(new ArrayList<>(emitters), copier), Map.copyOf(counts));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static void apply(Minecraft minecraft, Snapshot snapshot) {
		ParticleEngine engine = minecraft.particleEngine;
		engine.clearParticles();
		Map<UUID, Entity> entities = ObjectCopier.entitiesByUuid(minecraft.level);
		ObjectCopier copier = new ObjectCopier(minecraft.level, entities);
		Map<ParticleRenderType, ParticleGroup<?>> groups = Reflect.get(engine, "particles");
		for (Entry entry : snapshot.particles()) {
			Particle copy = copier.copyRoot(entry.particle());
			if (copy == null) {
				continue;
			}
			ParticleGroup group = groups.get(entry.type());
			if (group == null) {
				group = ((ParticleEngineInvoker) engine).dm$createParticleGroup(entry.type());
				groups.put(entry.type(), group);
			}
			Queue queue = Reflect.get(group, "particles");
			queue.add(copy);
		}
		Queue<Particle> toAdd = Reflect.get(engine, "particlesToAdd");
		toAdd.addAll(ObjectCopier.copyAll(snapshot.pending(), copier));
		Queue<TrackingEmitter> emitters = Reflect.get(engine, "trackingEmitters");
		emitters.addAll(ObjectCopier.copyAll(snapshot.emitters(), copier));
		Object2IntOpenHashMap<ParticleLimit> counts = Reflect.get(engine, "trackedParticleCounts");
		counts.clear();
		snapshot.counts().forEach(counts::put);
	}

	static int count(Minecraft minecraft) {
		int total = 0;
		Map<ParticleRenderType, ParticleGroup<?>> groups = Reflect.get(minecraft.particleEngine, "particles");
		for (ParticleGroup<?> group : groups.values()) {
			total += group.size();
		}
		return total;
	}
}
