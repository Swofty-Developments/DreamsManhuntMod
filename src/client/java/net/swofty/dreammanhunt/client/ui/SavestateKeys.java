package net.swofty.dreammanhunt.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import org.jspecify.annotations.Nullable;

public final class SavestateKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DreamManhunt.MOD_ID, "main"));
	public static final KeyMapping SAVE = new KeyMapping("key.dreammanhunt.save", InputConstants.KEY_K, CATEGORY);
	public static final KeyMapping MENU = new KeyMapping("key.dreammanhunt.menu", InputConstants.KEY_J, CATEGORY);

	private static @Nullable String scheduledName;
	private static int scheduledDelay;

	private SavestateKeys() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(SAVE);
		KeyMappingHelper.registerKeyMapping(MENU);
	}

	public static void scheduleSave(String name) {
		scheduledName = name;
		scheduledDelay = 2;
	}

	public static void tick(Minecraft minecraft) {
		ThumbnailManager.tick();
		if (minecraft.player == null) {
			scheduledName = null;
			while (SAVE.consumeClick()) {
			}
			while (MENU.consumeClick()) {
			}
			return;
		}
		if (scheduledName != null && --scheduledDelay <= 0) {
			String name = scheduledName;
			scheduledName = null;
			if (minecraft.gui.screen() == null) {
				SavestateClientNetwork.requestSave(name);
			}
		}
		boolean save = false;
		while (SAVE.consumeClick()) {
			save = true;
		}
		if (save) {
			SavestateClientNetwork.requestSave("");
		}
		boolean menu = false;
		while (MENU.consumeClick()) {
			menu = true;
		}
		if (menu && minecraft.gui.screen() == null) {
			openMenu(minecraft);
		}
	}

	public static void openMenu(Minecraft minecraft) {
		if (!SavestateClientNetwork.serverHasMod()) {
			SavestateClientNetwork.showNotice(Component.translatable("dreammanhunt.notice.server_missing_mod"));
			return;
		}
		minecraft.gui.setScreen(new SavestateMenuScreen());
	}
}
