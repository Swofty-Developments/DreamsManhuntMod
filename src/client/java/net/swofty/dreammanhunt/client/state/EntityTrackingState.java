package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class EntityTrackingState {
	private static final Set<Class<?>> NESTED_HOLDERS = Set.of(net.minecraft.world.entity.ai.control.BodyRotationControl.class);
	private static final Set<String> EXCLUDED = Set.of("Entity.id", "Entity.removalReason");

	private EntityTrackingState() {
	}

	record Nested(Field holder, Field field, Object value) {
	}

	record EntitySnapshot(Entity source, Class<?> type, Map<Field, Object> primitives, List<Nested> nested, Vec3 position, Vec3 delta, Vec3 codecBase, @Nullable InterpolationHandler handler) {
	}

	record Snapshot(Map<UUID, EntitySnapshot> entities) {
	}

	static Snapshot capture(Minecraft minecraft) {
		Map<UUID, EntitySnapshot> entities = new HashMap<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity == minecraft.player) {
				continue;
			}
			Map<Field, Object> primitives = new HashMap<>();
			List<Nested> nested = new ArrayList<>();
			try {
				for (Field field : Reflect.instanceFields(entity.getClass())) {
					if (restorable(field)) {
						primitives.put(field, field.get(entity));
					} else if (NESTED_HOLDERS.contains(field.getType())) {
						Object holder = field.get(entity);
						if (holder != null) {
							for (Field inner : Reflect.instanceFields(holder.getClass())) {
								if (inner.getType().isPrimitive() && !Modifier.isFinal(inner.getModifiers())) {
									nested.add(new Nested(field, inner, inner.get(holder)));
								}
							}
						}
					}
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			InterpolationHandler handler = entity.getInterpolation();
			InterpolationHandler handlerCopy = handler == InterpolationHandler.NO_OP ? null : (InterpolationHandler) new Copier(minecraft, entity, entity).copy(handler);
			entities.put(entity.getUUID(), new EntitySnapshot(entity, entity.getClass(), primitives, nested, entity.position(), entity.getDeltaMovement(),
				codecBase(entity.getPositionCodec()), handlerCopy));
		}
		return new Snapshot(entities);
	}

	private static final Set<Entity> APPLIED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	static void apply(Minecraft minecraft, Snapshot snapshot) {
		APPLIED.clear();
		applyTo(minecraft, snapshot);
	}

	static void applyLateArrivals(Minecraft minecraft, Snapshot snapshot) {
		applyTo(minecraft, snapshot);
		APPLIED.clear();
	}

	private static void applyTo(Minecraft minecraft, Snapshot snapshot) {
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity == minecraft.player || APPLIED.contains(entity)) {
				continue;
			}
			EntitySnapshot saved = snapshot.entities().get(entity.getUUID());
			if (saved == null || saved.type() != entity.getClass()) {
				continue;
			}
			APPLIED.add(entity);
			entity.setPos(saved.position());
			try {
				for (Map.Entry<Field, Object> entry : saved.primitives().entrySet()) {
					entry.getKey().set(entity, entry.getValue());
				}
				for (Nested entry : saved.nested()) {
					Object holder = entry.holder().get(entity);
					if (holder != null) {
						entry.field().set(holder, entry.value());
					}
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			entity.setDeltaMovement(saved.delta());
			entity.getPositionCodec().setBase(saved.codecBase());
			if (saved.handler() != null && entity.getInterpolation().getClass() == saved.handler().getClass()) {
				Object copy = new Copier(minecraft, saved.source(), entity).copy(saved.handler());
				try {
					Reflect.field(Entity.class, "interpolationHandler").set(entity, copy);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	static List<String> describe(Minecraft minecraft) {
		List<String> out = new ArrayList<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity != minecraft.player) {
				out.add(String.format(java.util.Locale.ROOT, "%s %s pos=%.6f,%.6f,%.6f rot=%.3f,%.3f head=%.3f old=%.6f,%.6f,%.6f", entity.getType().toShortString(),
					entity.getUUID().toString().substring(0, 8), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot(), entity.getYHeadRot(),
					entity.xo, entity.yo, entity.zo));
			}
		}
		out.sort(null);
		return out;
	}

	private static boolean restorable(Field field) {
		int modifiers = field.getModifiers();
		boolean valueLike = field.getType().isPrimitive() || field.getType().isEnum() || field.getType() == Vec3.class;
		return valueLike && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers) && !field.isSynthetic()
			&& !EXCLUDED.contains(field.getDeclaringClass().getSimpleName() + "." + field.getName());
	}

	private static Vec3 codecBase(VecDeltaCodec codec) {
		return Reflect.get(codec, "base");
	}

	private static final class Copier {
		private final Map<Object, Object> copies = new IdentityHashMap<>();
		private final Map<UUID, Entity> entities;

		Copier(Minecraft minecraft, Entity from, Entity to) {
			this.copies.put(from, to);
			this.entities = ObjectCopier.entitiesByUuid(minecraft.level);
		}

		Object copy(Object source) {
			return this.copyValue(source);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private @Nullable Object copyValue(@Nullable Object value) {
			if (value == null) {
				return null;
			}
			Object existing = this.copies.get(value);
			if (existing != null) {
				return existing;
			}
			Class<?> type = value.getClass();
			if (value instanceof Entity entity) {
				Entity current = this.entities.get(entity.getUUID());
				return current != null ? current : entity;
			}
			if (type.isRecord() || type.isEnum() || value instanceof Vec3 || value instanceof String || value instanceof Number || value instanceof Boolean) {
				return value;
			}
			if (type.isArray()) {
				int length = Array.getLength(value);
				Object array = Array.newInstance(type.getComponentType(), length);
				this.copies.put(value, array);
				for (int i = 0; i < length; i++) {
					Array.set(array, i, type.getComponentType().isPrimitive() ? Array.get(value, i) : this.copyValue(Array.get(value, i)));
				}
				return array;
			}
			if (value instanceof LinkedList<?> || value instanceof ArrayList<?> || value instanceof ArrayDeque<?>) {
				Collection out = value instanceof LinkedList<?> ? new LinkedList<>() : value instanceof ArrayList<?> ? new ArrayList<>() : new ArrayDeque<>();
				this.copies.put(value, out);
				for (Object element : (Collection<?>) value) {
					out.add(this.copyValue(element));
				}
				return out;
			}
			if (!type.getName().startsWith("net.minecraft.world.entity.") && !type.getName().startsWith("net.minecraft.client.")
				&& !type.getName().startsWith("net.minecraft.core.PositionAndRotation")) {
				return value;
			}
			Object copy = Reflect.allocate(type);
			this.copies.put(value, copy);
			try {
				for (Field field : Reflect.instanceFields(type)) {
					field.set(copy, field.getType().isPrimitive() ? field.get(value) : this.copyValue(field.get(value)));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			return copy;
		}
	}
}
