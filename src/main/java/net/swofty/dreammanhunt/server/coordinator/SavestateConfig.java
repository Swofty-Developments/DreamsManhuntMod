package net.swofty.dreammanhunt.server.coordinator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.permissions.PermissionLevel;
import net.swofty.dreammanhunt.DreamManhunt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record SavestateConfig(boolean allowEveryone, PermissionLevel permissionLevel, int readyTimeoutSeconds) {
	public static final SavestateConfig DEFAULT = new SavestateConfig(false, PermissionLevel.GAMEMASTERS, 20);

	public static final Codec<SavestateConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("allowEveryone", DEFAULT.allowEveryone).forGetter(SavestateConfig::allowEveryone),
			PermissionLevel.CODEC.optionalFieldOf("permissionLevel", DEFAULT.permissionLevel).forGetter(SavestateConfig::permissionLevel),
			Codec.intRange(1, 600).optionalFieldOf("readyTimeoutSeconds", DEFAULT.readyTimeoutSeconds).forGetter(SavestateConfig::readyTimeoutSeconds)
	).apply(instance, SavestateConfig::new));

	private static final Codec<SavestateConfig> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("allowEveryone").forGetter(SavestateConfig::allowEveryone),
			PermissionLevel.CODEC.fieldOf("permissionLevel").forGetter(SavestateConfig::permissionLevel),
			Codec.INT.fieldOf("readyTimeoutSeconds").forGetter(SavestateConfig::readyTimeoutSeconds)
	).apply(instance, SavestateConfig::new));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile SavestateConfig current = DEFAULT;

	public static SavestateConfig get() {
		return current;
	}

	public static void set(SavestateConfig config) {
		current = config;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(DreamManhunt.MOD_ID + ".json");
	}

	public static SavestateConfig load() {
		Path path = path();
		SavestateConfig config = DEFAULT;
		if (Files.isRegularFile(path)) {
			try {
				JsonElement json = JsonParser.parseString(Files.readString(path));
				config = CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(error -> DreamManhunt.LOGGER.warn("Invalid {}: {}", path, error))
						.orElse(DEFAULT);
			} catch (IOException | RuntimeException e) {
				DreamManhunt.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		write(path, config);
		current = config;
		return config;
	}

	private static void write(Path path, SavestateConfig config) {
		try {
			JsonElement json = FULL_CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException | RuntimeException e) {
			DreamManhunt.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
