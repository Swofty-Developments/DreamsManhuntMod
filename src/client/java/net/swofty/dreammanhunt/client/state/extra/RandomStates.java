package net.swofty.dreammanhunt.client.state.extra;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

public final class RandomStates {
	private static final RandomSource ANIMATE = new LegacyRandomSource(RandomSource.create().nextLong());
	private static final RandomSource PARTICLE = new LegacyRandomSource(RandomSource.create().nextLong());

	private RandomStates() {
	}

	public static long nextAnimateSeed() {
		synchronized (ANIMATE) {
			return ANIMATE.nextLong();
		}
	}

	public static long nextParticleSeed() {
		synchronized (PARTICLE) {
			return PARTICLE.nextLong();
		}
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		synchronized (ANIMATE) {
			tag.put("animate", state(ANIMATE));
		}
		synchronized (PARTICLE) {
			tag.put("particle", state(PARTICLE));
		}
		tag.put("spell", state(read(net.minecraft.client.particle.SpellParticle.class, "RANDOM", null)));
		tag.put("level", state(read(Level.class, "random", minecraft.level)));
		tag.put("particles", state(read(minecraft.particleEngine.getClass(), "random", minecraft.particleEngine)));
		CompoundTag entities = new CompoundTag();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			entities.put(entity.getUUID().toString(), state(read(Entity.class, "random", entity)));
		}
		tag.put("entities", entities);
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		synchronized (ANIMATE) {
			restore(ANIMATE, tag.getCompoundOrEmpty("animate"));
		}
		synchronized (PARTICLE) {
			restore(PARTICLE, tag.getCompoundOrEmpty("particle"));
		}
		restore(read(net.minecraft.client.particle.SpellParticle.class, "RANDOM", null), tag.getCompoundOrEmpty("spell"));
		restore(read(Level.class, "random", minecraft.level), tag.getCompoundOrEmpty("level"));
		restore(read(minecraft.particleEngine.getClass(), "random", minecraft.particleEngine), tag.getCompoundOrEmpty("particles"));
		CompoundTag entities = tag.getCompoundOrEmpty("entities");
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			UUID uuid = entity.getUUID();
			CompoundTag state = entities.getCompoundOrEmpty(uuid.toString());
			if (!state.isEmpty()) {
				restore(read(Entity.class, "random", entity), state);
			}
		}
	}

	private static Object read(Class<?> owner, String name, Object target) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static CompoundTag state(Object random) {
		CompoundTag tag = new CompoundTag();
		if (random != null) {
			walk(random, tag, true, 0);
		}
		return tag;
	}

	private static void restore(Object random, CompoundTag tag) {
		if (random != null && !tag.isEmpty()) {
			walk(random, tag, false, 0);
		}
	}

	private static void walk(Object owner, CompoundTag tag, boolean capture, int depth) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				try {
					field.setAccessible(true);
					String key = field.getName();
					Object value = field.get(owner);
					if (field.getType().isPrimitive() && !Modifier.isFinal(field.getModifiers())) {
						if (capture) {
							Primitives.put(tag, key, field.getType(), value);
						} else if (tag.contains(key)) {
							Primitives.set(owner, field, tag, key, field.getType());
						}
					} else if (value instanceof AtomicLong atomic) {
						if (capture) {
							tag.putLong(key, atomic.get());
						} else if (tag.contains(key)) {
							atomic.set(tag.getLongOr(key, atomic.get()));
						}
					} else if (value != null && depth < 2 && value.getClass().getName().startsWith("net.minecraft.world.level.levelgen.")) {
						CompoundTag nested = capture ? new CompoundTag() : tag.getCompoundOrEmpty(key);
						walk(value, nested, capture, depth + 1);
						if (capture) {
							tag.put(key, nested);
						}
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
	}
}
