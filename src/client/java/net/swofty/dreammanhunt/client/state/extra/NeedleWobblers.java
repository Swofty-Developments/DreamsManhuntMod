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

	private NeedleWobblers() {
	}

	public static void register(NeedleDirectionHelper.Wobbler wobbler) {
		LIVE.add(wobbler);
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
