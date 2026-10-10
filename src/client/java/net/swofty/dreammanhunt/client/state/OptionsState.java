package net.swofty.dreammanhunt.client.state;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.swofty.dreammanhunt.client.mixin.state.OptionsAccessor;
import org.slf4j.Logger;

final class OptionsState {
	private static final Logger LOGGER = LogUtils.getLogger();

	private OptionsState() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		Options options = minecraft.options;
		options.save();
		ListTag lines = new ListTag();
		try {
			for (String line : Files.readAllLines(file(options), StandardCharsets.UTF_8)) {
				if (!isKeyMapping(line)) {
					lines.add(StringTag.valueOf(line));
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to read options for client state", e);
			return tag;
		}
		tag.put("lines", lines);
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		ListTag saved = tag.getListOrEmpty("lines");
		if (saved.isEmpty()) {
			return;
		}
		Options options = minecraft.options;
		Path path = file(options);
		try {
			options.save();
			List<String> current = Files.readAllLines(path, StandardCharsets.UTF_8);
			List<String> merged = new ArrayList<>();
			for (Tag raw : saved) {
				if (raw instanceof StringTag(String line) && !isKeyMapping(line)) {
					merged.add(line);
				}
			}
			for (String line : current) {
				if (isKeyMapping(line)) {
					merged.add(line);
				}
			}
			if (merged.equals(current)) {
				return;
			}
			List<String> packsBefore = List.copyOf(options.resourcePacks);
			String languageBefore = options.languageCode;
			Files.write(path, merged, StandardCharsets.UTF_8);
			options.load();
			options.broadcastOptions();
			boolean packsChanged = !packsBefore.equals(options.resourcePacks);
			boolean languageChanged = !languageBefore.equals(options.languageCode);
			if (languageChanged) {
				minecraft.getLanguageManager().setSelected(options.languageCode);
			}
			if (packsChanged) {
				options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
			}
			if (packsChanged || languageChanged) {
				minecraft.reloadResourcePacks();
			}
		} catch (IOException e) {
			LOGGER.error("Failed to restore options from client state", e);
		}
	}

	private static Path file(Options options) {
		return ((OptionsAccessor) options).dm$getOptionsFile().toPath();
	}

	private static boolean isKeyMapping(String line) {
		return line.startsWith("key_");
	}
}
