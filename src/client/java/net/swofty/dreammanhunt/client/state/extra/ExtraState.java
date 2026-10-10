package net.swofty.dreammanhunt.client.state.extra;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.swofty.dreammanhunt.client.mixin.extra.ItemEntityAccessor;
import org.slf4j.Logger;

public final class ExtraState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<CompoundTag, Map<NeedleDirectionHelper.Wobbler, Object[]>> WOBBLERS = Collections.synchronizedMap(new WeakHashMap<>());

	private ExtraState() {
	}

	public static void capture(Minecraft minecraft, CompoundTag root) {
		try {
			CompoundTag tag = new CompoundTag();
			tag.put("blockEntities", BlockEntityAnimations.capture(minecraft));
			tag.put("itemBob", captureItemBob(minecraft));
			tag.put("random", RandomStates.capture(minecraft));
			root.put("extra", tag);
			WOBBLERS.put(root, NeedleWobblers.capture());
		} catch (Throwable t) {
			LOGGER.error("Failed to capture extra client state", t);
		}
	}

	public static void apply(Minecraft minecraft, CompoundTag root) {
		try {
			CompoundTag tag = root.getCompoundOrEmpty("extra");
			BlockEntityAnimations.apply(minecraft, tag.getCompoundOrEmpty("blockEntities"));
			applyItemBob(minecraft, tag.getCompoundOrEmpty("itemBob"));
			RandomStates.apply(minecraft, tag.getCompoundOrEmpty("random"));
			Map<NeedleDirectionHelper.Wobbler, Object[]> wobblers = WOBBLERS.get(root);
			if (wobblers != null) {
				NeedleWobblers.apply(wobblers);
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to apply extra client state", t);
		}
	}

	private static CompoundTag captureItemBob(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof ItemEntity item) {
				tag.putFloat(item.getUUID().toString(), item.bobOffs);
			}
		}
		return tag;
	}

	private static void applyItemBob(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof ItemEntity item) {
				Tag value = tag.get(item.getUUID().toString());
				if (value != null) {
					((ItemEntityAccessor) item).dm$setBobOffs(tag.getFloatOr(item.getUUID().toString(), item.bobOffs));
				}
			}
		}
	}
}
