package net.swofty.dreammanhunt.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.swofty.dreammanhunt.api.SavestateInfo;

import java.util.Locale;

public final class SavestateFormatting {
	private static final long TICKS_PER_DAY = 24000L;

	private SavestateFormatting() {
	}

	public static Component relativeTime(long createdAtMillis, long nowMillis) {
		long seconds = Math.max(0L, (nowMillis - createdAtMillis) / 1000L);
		if (seconds < 10L) {
			return Component.translatable("dreammanhunt.time.just_now");
		}
		if (seconds < 60L) {
			return Component.translatable("dreammanhunt.time.seconds", seconds);
		}
		long minutes = seconds / 60L;
		if (minutes < 60L) {
			return minutes == 1L ? Component.translatable("dreammanhunt.time.minute") : Component.translatable("dreammanhunt.time.minutes", minutes);
		}
		long hours = minutes / 60L;
		if (hours < 24L) {
			return hours == 1L ? Component.translatable("dreammanhunt.time.hour") : Component.translatable("dreammanhunt.time.hours", hours);
		}
		long days = hours / 24L;
		return days == 1L ? Component.translatable("dreammanhunt.time.day") : Component.translatable("dreammanhunt.time.days", days);
	}

	public static Component inGameTime(SavestateInfo info) {
		long dayTime = Math.max(0L, info.dayTime());
		long day = dayTime / TICKS_PER_DAY + 1L;
		long timeOfDay = dayTime % TICKS_PER_DAY;
		long hours = (timeOfDay / 1000L + 6L) % 24L;
		long minutes = (timeOfDay % 1000L) * 60L / 1000L;
		return Component.translatable("dreammanhunt.time.ingame", day, String.format(Locale.ROOT, "%02d:%02d", hours, minutes));
	}

	public static Component creator(SavestateInfo info) {
		return info.creatorName().isBlank()
				? Component.translatable("dreammanhunt.menu.creator_server")
				: Component.translatable("dreammanhunt.menu.creator", info.creatorName());
	}

	public static Component dimension(SavestateInfo info) {
		Identifier id = Identifier.tryParse(info.dimension());
		if (id == null) {
			return Component.literal(info.dimension());
		}
		String path = id.getPath().replace('_', ' ');
		String fallback = path.isEmpty() ? info.dimension() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
		return Component.translatableWithFallback("dreammanhunt.dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'), fallback);
	}
}
