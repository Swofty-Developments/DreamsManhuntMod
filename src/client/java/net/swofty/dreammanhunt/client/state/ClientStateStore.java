package net.swofty.dreammanhunt.client.state;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ClientStateStore {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	private ClientStateStore() {
	}

	static Path directory() {
		return FabricLoader.getInstance().getGameDir().resolve("dreammanhunt").resolve("client_states");
	}

	static @Nullable Path file(String id) {
		if (!SAFE_ID.matcher(id).matches() || id.startsWith(".")) {
			return null;
		}
		return directory().resolve(id + ".nbt");
	}

	static boolean exists(String id) {
		Path file = file(id);
		return file != null && Files.isRegularFile(file);
	}

	static void write(String id, CompoundTag tag) {
		Path file = file(id);
		if (file == null) {
			LOGGER.warn("Refusing to store client state with unsafe id {}", id);
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(id + ".nbt.tmp");
			NbtIo.writeCompressed(tag, temp);
			Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.error("Failed to write client state {}", id, e);
		}
	}

	static @Nullable CompoundTag read(String id) {
		Path file = file(id);
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}
		try {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			LOGGER.error("Failed to read client state {}", id, e);
			return null;
		}
	}

	static void delete(String id) {
		Path file = file(id);
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			LOGGER.error("Failed to delete client state {}", id, e);
		}
	}
}
