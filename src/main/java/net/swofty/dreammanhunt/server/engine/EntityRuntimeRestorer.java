package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.UUIDUtil;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.swofty.dreammanhunt.mixin.server.EntityAccessor;
import net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor;
import net.swofty.dreammanhunt.mixin.server.LivingEntityAccessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityRuntimeRestorer {
	private static volatile Map<UUID, CompoundTag> pending = new ConcurrentHashMap<>();
	private static final Map<Entity, float[]> RIDE_ROTATIONS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private EntityRuntimeRestorer() {
	}

	static void arm(Map<UUID, CompoundTag> runtime) {
		pending = new ConcurrentHashMap<>(runtime);
	}

	public static void apply(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Map<UUID, CompoundTag> runtime = pending;
		CompoundTag tag = runtime.isEmpty() ? null : runtime.remove(entity.getUUID());
		if (tag != null) {
			applyTag(entity, tag);
		}
		if (((EntityAccessor) entity).dreammanhunt$getRawId() < 0) {
			int saved = tag == null ? 0 : tag.getIntOr("id", 0);
			entity.setId(saved > 0 && !EntityIds.isLive(level.getServer(), saved) ? saved : EntityIds.allocate(level.getServer()));
		}
		if (tag != null) {
			AiState.resolveReferences();
		}
	}

	static void applyTag(Entity entity, CompoundTag tag) {
		if (entity.level() instanceof ServerLevel level) {
			tag.getCompound("ai").ifPresent(ai -> AiState.apply(entity, ai, level));
			RIDE_ROTATIONS.put(entity, rotations(entity));
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
			tag.getInt("no_jump_delay").ifPresent(living::dreammanhunt$setNoJumpDelay);
			tag.getFloat("last_hurt").ifPresent(living::dreammanhunt$setLastHurt);
			tag.getInt("hurt_duration").ifPresent(ticks -> ((LivingEntity) entity).hurtDuration = ticks);
			applyUse((LivingEntity) entity, living, tag);
		}
		if (entity instanceof Mob mob) {
			tag.getInt("ambient_sound_time").ifPresent(ticks -> mob.ambientSoundTime = ticks);
		}
		if (entity instanceof Player player) {
			tag.getInt("take_xp_delay").ifPresent(delay -> player.takeXpDelay = delay);
		}
	}

	private static void applyUse(LivingEntity entity, LivingEntityAccessor accessor, CompoundTag tag) {
		tag.getCompound("use").ifPresent(use -> {
			InteractionHand hand = use.getBooleanOr("off_hand", false) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack stack = entity.getItemInHand(hand);
			if (stack.isEmpty()) {
				return;
			}
			accessor.dreammanhunt$setUseItem(stack);
			accessor.dreammanhunt$setUseItemRemaining(use.getIntOr("remaining", stack.getUseDuration(entity)));
			accessor.dreammanhunt$setLivingEntityFlag(1, true);
			accessor.dreammanhunt$setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
			if (stack.has(DataComponents.KINETIC_WEAPON)) {
				accessor.dreammanhunt$setRecentKineticEnemies(new Object2LongOpenHashMap<>());
			}
		});
	}

	public static void afterStartRiding(Entity entity) {
		float[] saved = RIDE_ROTATIONS.remove(entity);
		if (saved == null) {
			return;
		}
		entity.setYRot(saved[0]);
		entity.setXRot(saved[1]);
		entity.yRotO = saved[2];
		entity.xRotO = saved[3];
		if (entity instanceof LivingEntity living) {
			living.yHeadRot = saved[4];
			living.yHeadRotO = saved[5];
			living.yBodyRot = saved[6];
			living.yBodyRotO = saved[7];
		}
	}

	private static float[] rotations(Entity entity) {
		float[] values = new float[8];
		values[0] = entity.getYRot();
		values[1] = entity.getXRot();
		values[2] = entity.yRotO;
		values[3] = entity.xRotO;
		if (entity instanceof LivingEntity living) {
			values[4] = living.yHeadRot;
			values[5] = living.yHeadRotO;
			values[6] = living.yBodyRot;
			values[7] = living.yBodyRotO;
		}
		return values;
	}

	static CompoundTag capture(Entity entity) {
		CompoundTag tag = new CompoundTag();
		tag.store("uuid", UUIDUtil.CODEC, entity.getUUID());
		tag.putInt("id", entity.getId());
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
			tag.putInt("no_jump_delay", living.dreammanhunt$getNoJumpDelay());
			tag.putFloat("last_hurt", living.dreammanhunt$getLastHurt());
			tag.putInt("hurt_duration", ((LivingEntity) entity).hurtDuration);
			LivingEntity livingEntity = (LivingEntity) entity;
			if (livingEntity.isUsingItem()) {
				CompoundTag use = new CompoundTag();
				use.putBoolean("off_hand", livingEntity.getUsedItemHand() == InteractionHand.OFF_HAND);
				use.putInt("remaining", living.dreammanhunt$getUseItemRemaining());
				tag.put("use", use);
			}
		}
		if (entity instanceof Mob mob) {
			tag.putInt("ambient_sound_time", mob.ambientSoundTime);
		}
		if (entity instanceof Player player) {
			tag.putInt("take_xp_delay", player.takeXpDelay);
		} else {
			tag.put("ai", AiState.capture(entity));
		}
		return tag;
	}
}
