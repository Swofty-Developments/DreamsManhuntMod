package net.swofty.dreammanhunt.client.state.extra;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;

public final class NeedleWobblers {
	private static final Set<NeedleDirectionHelper.Wobbler> LIVE = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private static final Set<Object> RANDOMS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private NeedleWobblers() {
	}

	public static void register(NeedleDirectionHelper.Wobbler wobbler) {
		LIVE.add(wobbler);
	}

	public static void registerOwner(Object owner) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (net.minecraft.util.RandomSource.class.isAssignableFrom(field.getType())) {
					try {
						field.setAccessible(true);
						Object random = field.get(owner);
						if (random != null) {
							RANDOMS.add(random);
						}
					} catch (ReflectiveOperationException | RuntimeException ignored) {
					}
				}
			}
		}
	}

	static Map<Object, Long> captureRandoms() {
		Map<Object, Long> out = new IdentityHashMap<>();
		List<Object> randoms;
		synchronized (RANDOMS) {
			randoms = new ArrayList<>(RANDOMS);
		}
		for (Object random : randoms) {
			java.util.concurrent.atomic.AtomicLong seed = seedOf(random);
			if (seed != null) {
				out.put(random, seed.get());
			}
		}
		return out;
	}

	static void applyRandoms(Map<Object, Long> states) {
		states.forEach((random, value) -> {
			java.util.concurrent.atomic.AtomicLong seed = seedOf(random);
			if (seed != null) {
				seed.set(value);
			}
		});
	}

	private static java.util.concurrent.atomic.@org.jspecify.annotations.Nullable AtomicLong seedOf(Object random) {
		for (Class<?> type = random.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField("seed");
				field.setAccessible(true);
				return field.get(random) instanceof java.util.concurrent.atomic.AtomicLong atomic ? atomic : null;
			} catch (NoSuchFieldException ignored) {
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	static Map<NeedleDirectionHelper.Wobbler, Object[]> capture() {
		Map<NeedleDirectionHelper.Wobbler, Object[]> out = new IdentityHashMap<>();
		List<NeedleDirectionHelper.Wobbler> wobblers;
		synchronized (LIVE) {
			wobblers = new ArrayList<>(LIVE);
		}
		for (NeedleDirectionHelper.Wobbler wobbler : wobblers) {
			try {
				out.put(wobbler, new Object[]{field(wobbler, "rotation").getFloat(wobbler), field(wobbler, "deltaRotation").getFloat(wobbler),
					field(wobbler, "lastUpdateTick").getLong(wobbler)});
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		return out;
	}

	static void apply(Map<NeedleDirectionHelper.Wobbler, Object[]> states) {
		states.forEach((wobbler, values) -> {
			try {
				field(wobbler, "rotation").setFloat(wobbler, (Float) values[0]);
				field(wobbler, "deltaRotation").setFloat(wobbler, (Float) values[1]);
				field(wobbler, "lastUpdateTick").setLong(wobbler, (Long) values[2]);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		});
	}

	private static Field field(Object owner, String name) throws NoSuchFieldException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
