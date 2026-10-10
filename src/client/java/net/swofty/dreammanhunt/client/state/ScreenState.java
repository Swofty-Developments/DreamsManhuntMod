package net.swofty.dreammanhunt.client.state;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ScreenState {
	private static final Logger LOGGER = LogUtils.getLogger();

	private ScreenState() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		Screen screen = minecraft.gui.screen();
		tag.putDouble("mouseX", minecraft.mouseHandler.xpos());
		tag.putDouble("mouseY", minecraft.mouseHandler.ypos());
		if (screen == null) {
			tag.putString("kind", "none");
		} else if (screen instanceof ChatScreen chat) {
			tag.putString("kind", "chat");
			EditBox input = Reflect.get(chat, "input");
			tag.putString("text", input.getValue());
			tag.putInt("cursorPos", Reflect.<Integer>get(input, "cursorPos"));
			tag.putInt("highlightPos", Reflect.<Integer>get(input, "highlightPos"));
			tag.putInt("displayPos", Reflect.<Integer>get(input, "displayPos"));
			tag.putLong("focusedTime", Reflect.<Long>get(input, "focusedTime"));
		} else if (screen instanceof CreativeModeInventoryScreen creative) {
			tag.putString("kind", "creative");
			CreativeModeTab selected = staticSelectedTab();
			Identifier tabId = selected == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selected);
			if (tabId != null) {
				tag.putString("tab", tabId.toString());
			}
			tag.putFloat("scroll", Reflect.<Float>get(creative, "scrollOffs"));
		} else if (screen instanceof InventoryScreen inventory) {
			tag.putString("kind", "inventory");
			tag.putBoolean("recipeBook", recipeBook(inventory).isVisible());
		} else if (screen instanceof AbstractContainerScreen<?>) {
			tag.putString("kind", "container");
			tag.putString("class", screen.getClass().getName());
		} else if (screen instanceof PauseScreen pause) {
			tag.putString("kind", "pause");
			tag.putBoolean("showMenu", pause.showsPauseMenu());
		} else {
			tag.putString("kind", "other");
			tag.putString("class", screen.getClass().getName());
		}
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty() || minecraft.player == null) {
			return;
		}
		String kind = tag.getStringOr("kind", "other");
		Screen current = minecraft.gui.screen();
		switch (kind) {
			case "none" -> {
				if (current != null) {
					minecraft.gui.setScreen(null);
				}
			}
			case "chat" -> {
				ChatScreen chat = current instanceof ChatScreen existing ? existing : new ChatScreen(tag.getStringOr("text", ""), false);
				if (current != chat) {
					minecraft.gui.setScreen(chat);
				}
				EditBox input = Reflect.get(chat, "input");
				input.setValue(tag.getStringOr("text", ""));
				Reflect.set(input, "displayPos", tag.getIntOr("displayPos", 0));
				Reflect.set(input, "cursorPos", tag.getIntOr("cursorPos", 0));
				Reflect.set(input, "highlightPos", tag.getIntOr("highlightPos", 0));
				Reflect.set(input, "focusedTime", tag.getLongOr("focusedTime", VisualClock.millis()));
			}
			case "inventory", "container" -> {
				if (kind.equals("container")) {
					LOGGER.info("Container screen {} was open at the save; reopening the player inventory (the server owns container menus)", tag.getStringOr("class", "?"));
				}
				InventoryScreen inventory = current instanceof InventoryScreen existing ? existing : new InventoryScreen(minecraft.player);
				if (current != inventory) {
					minecraft.gui.setScreen(inventory);
				}
				RecipeBookComponent<?> book = recipeBook(inventory);
				if (book.isVisible() != tag.getBooleanOr("recipeBook", book.isVisible())) {
					book.toggleVisibility();
				}
			}
			case "creative" -> {
				if (!(current instanceof CreativeModeInventoryScreen)) {
					minecraft.gui.setScreen(new CreativeModeInventoryScreen(minecraft.player, minecraft.player.connection.enabledFeatures(), minecraft.options.operatorItemsTab().get()));
				}
				Screen screen = minecraft.gui.screen();
				CreativeModeTab tab = tag.contains("tab") ? BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.tryParse(tag.getStringOr("tab", ""))) : null;
				if (screen instanceof CreativeModeInventoryScreen creative && tab != null) {
					try {
						java.lang.reflect.Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
						select.setAccessible(true);
						select.invoke(creative, tab);
					} catch (ReflectiveOperationException e) {
						LOGGER.warn("Could not restore creative tab", e);
					}
					Reflect.set(creative, "scrollOffs", tag.getFloatOr("scroll", 0.0F));
				}
			}
			case "pause" -> {
				if (!(current instanceof PauseScreen)) {
					minecraft.gui.setScreen(new PauseScreen(tag.getBooleanOr("showMenu", true)));
				}
			}
			default -> {
			}
		}
		restoreMouse(minecraft, tag);
	}

	private static void restoreMouse(Minecraft minecraft, CompoundTag tag) {
		double x = tag.getDoubleOr("mouseX", minecraft.mouseHandler.xpos());
		double y = tag.getDoubleOr("mouseY", minecraft.mouseHandler.ypos());
		Reflect.set(minecraft.mouseHandler, "xpos", x);
		Reflect.set(minecraft.mouseHandler, "ypos", y);
		if (!minecraft.mouseHandler.isMouseGrabbed()) {
			InputConstants.releaseMouse(minecraft.getWindow(), x, y);
		}
	}

	private static RecipeBookComponent<?> recipeBook(AbstractRecipeBookScreen<?> screen) {
		return Reflect.get(screen, "recipeBookComponent");
	}

	private static @Nullable CreativeModeTab staticSelectedTab() {
		try {
			return (CreativeModeTab) Reflect.field(CreativeModeInventoryScreen.class, "selectedTab").get(null);
		} catch (IllegalAccessException e) {
			return null;
		}
	}
}
