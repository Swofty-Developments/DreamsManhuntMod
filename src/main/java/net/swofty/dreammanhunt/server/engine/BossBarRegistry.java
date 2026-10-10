package net.swofty.dreammanhunt.server.engine;

import net.minecraft.server.level.ServerBossEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class BossBarRegistry {
	private static final Set<ServerBossEvent> EVENTS = Collections.newSetFromMap(new WeakHashMap<>());

	private BossBarRegistry() {
	}

	public static void register(ServerBossEvent event) {
		synchronized (EVENTS) {
			EVENTS.add(event);
		}
	}

	static void detachEveryone() {
		List<ServerBossEvent> events;
		synchronized (EVENTS) {
			events = new ArrayList<>(EVENTS);
		}
		for (ServerBossEvent event : events) {
			if (!event.getPlayers().isEmpty()) {
				event.removeAllPlayers();
			}
		}
	}
}
