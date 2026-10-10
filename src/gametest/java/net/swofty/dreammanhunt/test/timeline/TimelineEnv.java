package net.swofty.dreammanhunt.test.timeline;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

public record TimelineEnv(ClientGameTestContext context, TestServerContext server, BlockPos origin) {
	public void cmd(String format, Object... args) {
		this.server.runCommand(String.format(Locale.ROOT, format, args));
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
		this.cmd("fill %d %d %d %d %d %d %s", this.origin.getX() + x0, this.origin.getY() + y0, this.origin.getZ() + z0,
				this.origin.getX() + x1, this.origin.getY() + y1, this.origin.getZ() + z1, state);
	}

	public void block(int dx, int dy, int dz, String state) {
		this.cmd("setblock %d %d %d %s", this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz, state);
	}

	public void summon(String type, double dx, double dy, double dz, String nbt) {
		this.cmd(("summon %s %.2f %.2f %.2f " + nbt).trim(), type, this.origin.getX() + 0.5 + dx, this.origin.getY() + dy, this.origin.getZ() + 0.5 + dz);
	}

	public static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
