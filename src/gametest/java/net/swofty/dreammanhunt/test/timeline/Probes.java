package net.swofty.dreammanhunt.test.timeline;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Probes {
	public interface ServerProbe {
		void capture(MinecraftServer server, TimelineScenario.Area area, Fingerprint out);
	}

	public interface ClientProbe {
		void capture(Minecraft minecraft, TimelineScenario.Area area, Fingerprint out);
	}

	private static final List<ServerProbe> SERVER = new CopyOnWriteArrayList<>();
	private static final List<ClientProbe> CLIENT = new CopyOnWriteArrayList<>();

	static {
		SERVER.add(Probes::levels);
		SERVER.add(Probes::entities);
		SERVER.add(Probes::blockEntities);
		CLIENT.add(Probes::clientEntities);
	}

	private Probes() {
	}

	public static void addServer(ServerProbe probe) {
		SERVER.add(probe);
	}

	public static void addClient(ClientProbe probe) {
		CLIENT.add(probe);
	}

	static Fingerprint captureServer(MinecraftServer server, TimelineScenario.Area area) {
		Fingerprint out = new Fingerprint();
		for (ServerProbe probe : SERVER) {
			probe.capture(server, area, out);
		}
		return out;
	}

	static Fingerprint captureClient(Minecraft minecraft, TimelineScenario.Area area) {
		Fingerprint out = new Fingerprint();
		for (ClientProbe probe : CLIENT) {
			probe.capture(minecraft, area, out);
		}
		return out;
	}

	public static String entityKey(Entity entity) {
		return entity.getType().builtInRegistryHolder().key().identifier().getPath() + "#" + entity.getUUID().toString().substring(0, 8);
	}

	private static void levels(MinecraftServer server, TimelineScenario.Area area, Fingerprint out) {
		for (ServerLevel level : server.getAllLevels()) {
			String key = "level:" + level.dimension().identifier().getPath();
			out.put(key, "gameTime", level.getGameTime());
			out.put(key, "random", Reflect.describe(level.getRandom(), 0));
			out.put(key, "rain", level.getRainLevel(1.0F) + "/" + level.getThunderLevel(1.0F));
			out.put("chunks:" + level.dimension().identifier().getPath(), "loaded", level.getChunkSource().getLoadedChunksCount());
			out.put(key, "blockTicks", level.getBlockTicks().count());
			out.put(key, "fluidTicks", level.getFluidTicks().count());
			Object blockEvents = Reflect.get(level, "blockEvents");
			out.put(key, "blockEvents", blockEvents instanceof java.util.Collection<?> c ? c.size() : -1);
			Object tickList = Reflect.get(level, "entityTickList");
			Object active = tickList == null ? null : Reflect.get(tickList, "active");
			if (active instanceof Map<?, ?> map) {
				List<String> order = new ArrayList<>();
				for (Object value : map.values()) {
					if (value instanceof Entity entity && area.contains(entity)) {
						order.add(entityKey(entity));
					}
				}
				out.put(key, "tickOrder", order);
			}
		}
	}

	private static void entities(MinecraftServer server, TimelineScenario.Area area, Fingerprint out) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!area.contains(entity)) {
					continue;
				}
				Map<String, String> fields = out.subject(entityKey(entity));
				base(entity, fields);
				if (entity instanceof Mob mob) {
					mob(mob, fields);
				}
				if (entity instanceof LivingEntity living) {
					brain(living.getBrain(), fields);
				}
			}
		}
	}

	private static void base(Entity entity, Map<String, String> fields) {
		fields.put("id", Integer.toString(entity.getId()));
		fields.put("pos", Reflect.describe(entity.position(), 0));
		fields.put("motion", Reflect.describe(entity.getDeltaMovement(), 0));
		fields.put("rot", entity.getYRot() + "/" + entity.getXRot() + "/" + entity.getYHeadRot());
		fields.put("onGround", Boolean.toString(entity.onGround()));
		fields.put("pose", entity.getPose().name());
		fields.put("tickCount", Integer.toString(entity.tickCount));
		fields.put("fallDistance", Double.toString(entity.fallDistance));
		fields.put("fire", Integer.toString(entity.getRemainingFireTicks()));
		fields.put("random", Reflect.describe(Reflect.get(entity, "random"), 0));
		fields.put("vehicle", entity.getVehicle() == null ? "none" : entity.getVehicle().getUUID().toString());
		if (entity instanceof LivingEntity living) {
			fields.put("health", Float.toString(living.getHealth()));
			fields.put("bodyRot", Float.toString(living.yBodyRot));
			fields.put("hurtTime", Integer.toString(living.hurtTime));
			fields.put("invulnerable", Integer.toString(living.getInvulnerableTime()));
			fields.put("using", living.isUsingItem() + ":" + living.getUseItemRemainingTicks());
			fields.put("attackAnim", Reflect.describe(Reflect.get(living, "attackAnim"), 0));
			fields.put("effects", Reflect.describe(living.getActiveEffects(), 1));
		}
		fields.put("data", entity.getEntityData().getNonDefaultValues() == null ? "[]" : Reflect.describe(entity.getEntityData().getNonDefaultValues(), 1));
	}

	private static void mob(Mob mob, Map<String, String> fields) {
		fields.put("target", mob.getTarget() == null ? "none" : mob.getTarget().getUUID().toString());
		fields.put("aggressive", Boolean.toString(mob.isAggressive()));
		goals("goal", (GoalSelector) Reflect.get(mob, "goalSelector"), fields);
		goals("targetGoal", (GoalSelector) Reflect.get(mob, "targetSelector"), fields);
		Object navigation = Reflect.get(mob, "navigation");
		Path path = navigation instanceof net.minecraft.world.entity.ai.navigation.PathNavigation own ? own.getPath() : null;
		fields.put("nav.path", path == null ? "none" : Reflect.describePath(path));
		Reflect.dumpFields(navigation, "nav", fields, 0);
		Reflect.dumpFields(Reflect.get(mob, "moveControl"), "move", fields, 0);
		Reflect.dumpFields(Reflect.get(mob, "lookControl"), "look", fields, 0);
		Reflect.dumpFields(Reflect.get(mob, "jumpControl"), "jump", fields, 0);
	}

	private static void goals(String prefix, GoalSelector selector, Map<String, String> fields) {
		if (selector == null) {
			return;
		}
		List<String> running = new ArrayList<>();
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			if (wrapped.isRunning()) {
				running.add(wrapped.getPriority() + ":" + wrapped.getGoal().getClass().getSimpleName());
			}
		}
		running.sort(String::compareTo);
		fields.put(prefix + ".running", running.toString());
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			Reflect.dumpFields(wrapped.getGoal(), prefix + "." + wrapped.getPriority() + "." + wrapped.getGoal().getClass().getSimpleName(), fields, 0);
		}
	}

	private static void brain(Brain<?> brain, Map<String, String> fields) {
		Object memories = Reflect.get(brain, "memories");
		if (memories instanceof Map<?, ?> map && !map.isEmpty()) {
			Map<String, String> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				Map<String, String> slot = new java.util.LinkedHashMap<>();
				Reflect.dumpFields(entry.getValue(), "", slot, 1);
				sorted.put(String.valueOf(entry.getKey()), slot.toString());
			}
			sorted.forEach((k, v) -> fields.put("brain.memory." + k, v));
			fields.put("brain.activities", Reflect.describe(brain.getActiveActivities(), 1));
			List<String> behaviors = new ArrayList<>();
			for (Object behavior : brain.getRunningBehaviors()) {
				behaviors.add(behavior.getClass().getSimpleName());
			}
			behaviors.sort(String::compareTo);
			fields.put("brain.running", behaviors.toString());
		}
	}

	private static void blockEntities(MinecraftServer server, TimelineScenario.Area area, Fingerprint out) {
		ServerLevel level = server.getLevel(area.dimension());
		if (level == null) {
			return;
		}
		for (BlockPos pos : BlockPos.betweenClosed(area.min(), area.max())) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockEntity blockEntity = level.getChunkAt(pos).getBlockEntity(pos);
			if (blockEntity != null) {
				Map<String, String> fields = out.subject("be:" + pos.toShortString());
				fields.put("type", blockEntity.getClass().getSimpleName());
				fields.put("nbt", Integer.toHexString(blockEntity.saveWithoutMetadata(level.registryAccess()).hashCode()));
				Reflect.dumpFields(blockEntity, "f", fields, 0);
			}
		}
	}

	private static void clientEntities(Minecraft minecraft, TimelineScenario.Area area, Fingerprint out) {
		if (minecraft.level == null) {
			return;
		}
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!area.contains(entity)) {
				continue;
			}
			Map<String, String> fields = out.subject("client:" + entityKey(entity));
			fields.put("id", Integer.toString(entity.getId()));
			fields.put("pos", Reflect.describe(entity.position(), 0));
			fields.put("rot", entity.getYRot() + "/" + entity.getXRot() + "/" + entity.getYHeadRot());
			fields.put("pose", entity.getPose().name());
			if (entity instanceof LivingEntity living) {
				fields.put("health", Float.toString(living.getHealth()));
				fields.put("bodyRot", Float.toString(living.yBodyRot));
			}
		}
	}
}
