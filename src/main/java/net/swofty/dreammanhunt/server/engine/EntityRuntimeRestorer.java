package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.swofty.dreammanhunt.mixin.server.EntityAccessor;
import net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor;
import net.swofty.dreammanhunt.mixin.server.LivingEntityAccessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityRuntimeRestorer {
	private static volatile Map<UUID, CompoundTag> pending = new ConcurrentHashMap<>();

	private EntityRuntimeRestorer() {
	}

	static void arm(Map<UUID, CompoundTag> runtime) {
		pending = new ConcurrentHashMap<>(runtime);
	}

	public static void apply(Entity entity) {
		Map<UUID, CompoundTag> runtime = pending;
		if (runtime.isEmpty()) {
			return;
		}
		CompoundTag tag = runtime.remove(entity.getUUID());
		if (tag == null) {
			return;
		}
		EntityAccessor accessor = (EntityAccessor) entity;
		if (accessor.dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
			tag.getLong("seed").ifPresent(seed -> ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().set(seed));
		}
		tag.getInt("tick_count").ifPresent(ticks -> entity.tickCount = ticks);
		tag.getInt("invulnerable_time").ifPresent(accessor::dreammanhunt$setInvulnerableTime);
		if (entity instanceof LivingEntity) {
			LivingEntityAccessor living = (LivingEntityAccessor) entity;
			tag.getInt("attack_strength").ifPresent(living::dreammanhunt$setAttackStrengthTicker);
			tag.getInt("no_action_time").ifPresent(living::dreammanhunt$setNoActionTime);
		}
		if (entity instanceof Player player) {
			tag.getInt("take_xp_delay").ifPresent(delay -> player.takeXpDelay = delay);
		}
	}

	static CompoundTag capture(Entity entity) {
		CompoundTag tag = new CompoundTag();
		tag.store("uuid", UUIDUtil.CODEC, entity.getUUID());
		EntityAccessor accessor = (EntityAccessor) entity;
		if (accessor.dreammanhunt$getRandom() instanceof LegacyRandomSource random) {
			tag.putLong("seed", ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().get());
		}
		tag.putInt("tick_count", entity.tickCount);
		tag.putInt("invulnerable_time", accessor.dreammanhunt$getInvulnerableTime());
		if (entity instanceof LivingEntity) {
			LivingEntityAccessor living = (LivingEntityAccessor) entity;
			tag.putInt("attack_strength", living.dreammanhunt$getAttackStrengthTicker());
			tag.putInt("no_action_time", living.dreammanhunt$getNoActionTime());
		}
		if (entity instanceof Player player) {
			tag.putInt("take_xp_delay", player.takeXpDelay);
		}
		return tag;
	}
}
