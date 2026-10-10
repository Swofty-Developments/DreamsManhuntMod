package net.swofty.dreammanhunt.client.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

final class LiveStates {
	private static final String KEY = "liveId";
	private static final int MAX_ENTRIES = 32;
	private static final Map<String, Live> LIVE = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Live> eldest) {
			return this.size() > MAX_ENTRIES;
		}
	};

	private LiveStates() {
	}

	static Live create(CompoundTag root) {
		String id = UUID.randomUUID().toString();
		root.putString(KEY, id);
		Live live = new Live();
		synchronized (LIVE) {
			LIVE.put(id, live);
		}
		return live;
	}

	static @Nullable Live get(CompoundTag root) {
		String id = root.getStringOr(KEY, "");
		synchronized (LIVE) {
			return LIVE.get(id);
		}
	}

	static final class Live {
		@Nullable List<GuiMessage> chat;
		ParticleState.@Nullable Snapshot particles;
		OverlayState.@Nullable Snapshot overlays;
		EntityTrackingState.@Nullable Snapshot entityTracking;
	}
}
