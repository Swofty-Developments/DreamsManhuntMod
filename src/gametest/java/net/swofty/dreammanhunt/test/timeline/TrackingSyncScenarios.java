package net.swofty.dreammanhunt.test.timeline;

import java.util.List;
import net.swofty.dreammanhunt.client.state.ClientSnapshot;

public final class TrackingSyncScenarios {
	static {
		Probes.addClient((minecraft, area, out) -> {
			out.put("clientclock", "hudTick", ClientSnapshot.counters(minecraft).getCompoundOrEmpty("timing").getIntOr("hudTickCount", -1));
			out.put("clientclock", "playerTick", minecraft.player == null ? -1 : minecraft.player.tickCount);
			java.util.Map<java.util.UUID, java.util.List<String>> byUuid = new java.util.HashMap<>();
			for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
				byUuid.computeIfAbsent(entity.getUUID(), k -> new java.util.ArrayList<>()).add(entity.getId() + (entity.isRemoved() ? "R" : ""));
			}
			java.util.List<String> duplicates = byUuid.entrySet().stream().filter(e -> e.getValue().size() > 1).map(e -> e.getKey().toString().substring(0, 8) + "=" + e.getValue()).sorted().toList();
			out.put("clientclock", "entities", byUuid.size());
			for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity == minecraft.player) {
					continue;
				}
				String key = "clienttrack:" + Probes.entityKey(entity);
				net.minecraft.core.PositionAndRotation target = entity.getInterpolation().target();
				out.put(key, "id", entity.getId());
				out.put(key, "tick", entity.tickCount);
				out.put(key, "codecBase", entity.getPositionCodec().decode(0, 0, 0));
				out.put(key, "target", target == null ? "none" : target.position() + "/" + target.yRot());
			}
			out.put("clientclock", "duplicates", duplicates);
		});
	}

	private TrackingSyncScenarios() {
	}

	static void addTo(List<TimelineScenario> list) {
		list.add(TimelineScenario.of("tracking-spawner", 80, env -> {
			env.cmd("time set 6000");
			env.block(-1, 0, 3, "minecraft:spawner{SpawnData:{entity:{id:\"minecraft:pig\"}},RequiredPlayerRange:16s}");
		}).settle(60));
	}
}
