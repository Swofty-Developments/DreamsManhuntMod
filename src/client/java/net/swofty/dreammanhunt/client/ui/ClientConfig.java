package net.swofty.dreammanhunt.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.swofty.dreammanhunt.DreamManhunt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ClientConfig(boolean cutMarker, boolean saveMarker) {
	public static final ClientConfig DEFAULT = new ClientConfig(true, false);

	private static final Codec<ClientConfig> READ_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("cutMarker", DEFAULT.cutMarker).forGetter(ClientConfig::cutMarker),
			Codec.BOOL.optionalFieldOf("saveMarker", DEFAULT.saveMarker).forGetter(ClientConfig::saveMarker)
	).apply(instance, ClientConfig::new));

	private static final Codec<ClientConfig> WRITE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("cutMarker").forGetter(ClientConfig::cutMarker),
			Codec.BOOL.fieldOf("saveMarker").forGetter(ClientConfig::saveMarker)
	).apply(instance, ClientConfig::new));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile ClientConfig current = DEFAULT;

	public static ClientConfig get() {
		return current;
	}

	public static void set(ClientConfig config) {
		current = config;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(DreamManhunt.MOD_ID + "-client.json");
	}

	public static ClientConfig load() {
		Path path = path();
		ClientConfig config = DEFAULT;
		if (Files.isRegularFile(path)) {
			try {
				JsonElement json = JsonParser.parseString(Files.readString(path));
				config = READ_CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(error -> DreamManhunt.LOGGER.warn("Invalid {}: {}", path, error))
						.orElse(DEFAULT);
			} catch (IOException | RuntimeException e) {
				DreamManhunt.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(WRITE_CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow()));
		} catch (IOException | RuntimeException e) {
			DreamManhunt.LOGGER.warn("Could not write {}", path, e);
		}
		current = config;
		return config;
	}
}
