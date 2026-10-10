package net.swofty.dreammanhunt.client.state;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import org.lwjgl.sdl.SDLMouse;

final class PhysicalKeys {
	private static final String TEST_INPUT = "net.fabricmc.fabric.impl.client.gametest.TestInputImpl";

	private PhysicalKeys() {
	}

	static void sync(Minecraft minecraft) {
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping instanceof ToggleKeyMapping toggle && Reflect.<BooleanSupplier>get(toggle, "needsToggle").getAsBoolean()) {
				continue;
			}
			InputConstants.Key key = Reflect.get(mapping, "key");
			if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
				mapping.setDown(false);
				continue;
			}
			switch (key.getType()) {
				case KEYBOARD -> mapping.setDown(InputConstants.isKeyDown(key.getValue()));
				case MOUSE -> mapping.setDown(isMouseButtonDown(key));
				default -> {
				}
			}
		}
	}

	private static boolean isMouseButtonDown(InputConstants.Key key) {
		Set<?> testKeys = testKeysDown();
		if (testKeys != null) {
			return testKeys.contains(key);
		}
		int buttons = SDLMouse.SDL_GetMouseState(null, null);
		int button = key.getValue();
		return button >= 1 && button <= 32 && (buttons & (1 << (button - 1))) != 0;
	}

	private static Set<?> testKeysDown() {
		try {
			Class<?> type = Class.forName(TEST_INPUT, false, PhysicalKeys.class.getClassLoader());
			Field field = type.getDeclaredField("KEYS_DOWN");
			field.setAccessible(true);
			return (Set<?>) field.get(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}
}
