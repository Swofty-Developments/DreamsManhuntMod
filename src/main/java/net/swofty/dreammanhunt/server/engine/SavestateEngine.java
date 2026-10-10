package net.swofty.dreammanhunt.server.engine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.api.SavestateInfo;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class SavestateEngine {
	private final MinecraftServer server;

	private SavestateEngine(MinecraftServer server) {
		this.server = server;
	}

	public static SavestateEngine get(MinecraftServer server) {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}

	public SavestateInfo save(String id, String name, @Nullable ServerPlayer creator) throws SavestateException {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}

	public List<SavestateInfo> list() {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}

	public Optional<SavestateInfo> find(String idOrName) {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}

	public void restore(String id) throws SavestateException {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}

	public boolean delete(String id) {
		throw new UnsupportedOperationException("TODO(server-engine)");
	}
}
