package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.FallLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CombatState {
	private static final String[] COUNTERS = {"lastDamageTime", "combatStartTime", "combatEndTime"};
	private static final String[] FLAGS = {"inCombat", "takingDamage"};

	private CombatState() {
	}

	static CompoundTag capture(LivingEntity entity) {
		CombatTracker tracker = entity.getCombatTracker();
		CompoundTag tag = new CompoundTag();
		for (String name : COUNTERS) {
			tag.putInt(name, (Integer) get(tracker, name));
		}
		for (String name : FLAGS) {
			tag.putBoolean(name, (Boolean) get(tracker, name));
		}
		ListTag entries = new ListTag();
		for (CombatEntry entry : entries(tracker)) {
			DamageSource source = entry.source();
			CompoundTag encoded = new CompoundTag();
			source.typeHolder().unwrapKey().ifPresent(key -> encoded.putString("type", key.identifier().toString()));
			if (source.getDirectEntity() != null) {
				encoded.store("direct", UUIDUtil.CODEC, source.getDirectEntity().getUUID());
			}
			if (source.getEntity() != null) {
				encoded.store("causing", UUIDUtil.CODEC, source.getEntity().getUUID());
			}
			Vec3 position = source.sourcePositionRaw();
			if (position != null) {
				encoded.putDouble("x", position.x);
				encoded.putDouble("y", position.y);
				encoded.putDouble("z", position.z);
			}
			encoded.putFloat("damage", entry.damage());
			encoded.putFloat("fall", entry.fallDistance());
			if (entry.fallLocation() != null) {
				encoded.putString("fall_location", entry.fallLocation().id());
			}
			entries.add(encoded);
		}
		tag.put("entries", entries);
		return tag;
	}

	static void apply(LivingEntity entity, CompoundTag tag, ServerLevel level) {
		CombatTracker tracker = entity.getCombatTracker();
		for (String name : COUNTERS) {
			tag.getInt(name).ifPresent(value -> set(tracker, name, value));
		}
		for (String name : FLAGS) {
			tag.getBoolean(name).ifPresent(value -> set(tracker, name, value));
		}
		ListTag entries = tag.getListOrEmpty("entries");
		if (entries.isEmpty()) {
			entries(tracker).clear();
			return;
		}
		AiState.defer(() -> {
			List<CombatEntry> rebuilt = new ArrayList<>();
			for (Tag element : entries) {
				if (!(element instanceof CompoundTag encoded)) {
					continue;
				}
				Identifier typeId = Identifier.tryParse(encoded.getStringOr("type", ""));
				if (typeId == null) {
					continue;
				}
				Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(ResourceKey.create(Registries.DAMAGE_TYPE, typeId)).orElse(null);
				if (type == null) {
					continue;
				}
				Entity direct = resolve(encoded, "direct", level);
				Entity causing = resolve(encoded, "causing", level);
				if ((encoded.contains("direct") && direct == null) || (encoded.contains("causing") && causing == null)) {
					return false;
				}
				DamageSource source = encoded.contains("x")
						? new DamageSource(type, new Vec3(encoded.getDoubleOr("x", 0.0), encoded.getDoubleOr("y", 0.0), encoded.getDoubleOr("z", 0.0)))
						: new DamageSource(type, direct, causing);
				FallLocation location = encoded.getString("fall_location").map(FallLocation::new).orElse(null);
				rebuilt.add(new CombatEntry(source, encoded.getFloatOr("damage", 0.0F), location, encoded.getFloatOr("fall", 0.0F)));
			}
			List<CombatEntry> live = entries(tracker);
			live.clear();
			live.addAll(rebuilt);
			return true;
		});
	}

	private static Entity resolve(CompoundTag tag, String key, ServerLevel level) {
		UUID uuid = tag.read(key, UUIDUtil.CODEC).orElse(null);
		return uuid == null ? null : level.getEntity(uuid);
	}

	@SuppressWarnings("unchecked")
	private static List<CombatEntry> entries(CombatTracker tracker) {
		return (List<CombatEntry>) get(tracker, "entries");
	}

	private static Object get(Object owner, String name) {
		try {
			Field field = CombatTracker.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void set(Object owner, String name, Object value) {
		try {
			Field field = CombatTracker.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(owner, value);
		} catch (ReflectiveOperationException ignored) {
		}
	}
}
