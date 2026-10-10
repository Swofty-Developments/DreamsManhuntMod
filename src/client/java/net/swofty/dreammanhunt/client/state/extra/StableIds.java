package net.swofty.dreammanhunt.client.state.extra;

import java.util.UUID;
import net.minecraft.world.entity.Entity;

public final class StableIds {
	private StableIds() {
	}

	public static int of(Entity entity) {
		UUID uuid = entity.getUUID();
		long bits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
		return (int) (bits ^ bits >>> 32) & 0xFFFF;
	}
}
