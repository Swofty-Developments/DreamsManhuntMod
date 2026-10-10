package net.swofty.dreammanhunt.client.state;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.swofty.dreammanhunt.client.mixin.state.MinecraftAccessor;

public final class FrozenTick {
	private FrozenTick() {
	}

	public static void run(Minecraft minecraft) {
		if (minecraft.level != null && minecraft.gameMode != null) {
			minecraft.gameMode.tick();
		}
		minecraft.gui.tick();
		Connection pending = ((MinecraftAccessor) minecraft).dm$getPendingConnection();
		if (minecraft.level == null && pending != null) {
			pending.tick();
		}
		minecraft.keyboardHandler.tick();
	}
}
