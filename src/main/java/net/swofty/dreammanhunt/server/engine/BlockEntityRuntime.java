package net.swofty.dreammanhunt.server.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.swofty.dreammanhunt.mixin.server.BeaconBlockEntityAccessor;
import net.swofty.dreammanhunt.mixin.server.LevelAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockEntityRuntime {
	private static volatile Map<String, CompoundTag> pending = new ConcurrentHashMap<>();

	private BlockEntityRuntime() {
	}

	static ListTag capture(MinecraftServer server) {
		ListTag list = new ListTag();
		for (ServerLevel level : server.getAllLevels()) {
			for (TickingBlockEntity ticker : new ArrayList<>(((LevelAccessor) level).dreammanhunt$getBlockEntityTickers())) {
				if (ticker.isRemoved() || !(level.getBlockEntity(ticker.getPos()) instanceof BeaconBlockEntity beacon)) {
					continue;
				}
				BeaconBlockEntityAccessor accessor = (BeaconBlockEntityAccessor) beacon;
				CompoundTag tag = new CompoundTag();
				tag.putString("key", key(level, beacon.getBlockPos()));
				tag.putInt("levels", accessor.dreammanhunt$getLevels());
				tag.putInt("last_check_y", accessor.dreammanhunt$getLastCheckY());
				tag.putIntArray("beam", sections(accessor.dreammanhunt$getBeamSections()));
				tag.putIntArray("checking", sections(accessor.dreammanhunt$getCheckingBeamSections()));
				tag.putBoolean("shared", accessor.dreammanhunt$getBeamSections() == accessor.dreammanhunt$getCheckingBeamSections());
				list.add(tag);
			}
		}
		return list;
	}

	static void arm(ListTag list) {
		Map<String, CompoundTag> map = new ConcurrentHashMap<>();
		for (Tag element : list) {
			if (element instanceof CompoundTag tag) {
				map.put(tag.getStringOr("key", ""), tag);
			}
		}
		pending = map;
	}

	public static void apply(BlockEntity blockEntity, Level level) {
		if (!(level instanceof ServerLevel) || !(blockEntity instanceof BeaconBlockEntity beacon)) {
			return;
		}
		Map<String, CompoundTag> map = pending;
		CompoundTag tag = map.isEmpty() ? null : map.remove(key(level, beacon.getBlockPos()));
		if (tag == null) {
			return;
		}
		BeaconBlockEntityAccessor accessor = (BeaconBlockEntityAccessor) beacon;
		accessor.dreammanhunt$setLevels(tag.getIntOr("levels", 0));
		accessor.dreammanhunt$setLastCheckY(tag.getIntOr("last_check_y", level.getMinY() - 1));
		List<BeaconBeamOwner.Section> beam = sections(tag.getIntArray("beam").orElse(new int[0]));
		accessor.dreammanhunt$setBeamSections(beam);
		accessor.dreammanhunt$setCheckingBeamSections(tag.getBooleanOr("shared", false) ? beam : sections(tag.getIntArray("checking").orElse(new int[0])));
	}

	private static String key(Level level, BlockPos pos) {
		return level.dimension().identifier() + "@" + pos.asLong();
	}

	private static int[] sections(List<BeaconBeamOwner.Section> sections) {
		int[] values = new int[sections.size() * 2];
		for (int i = 0; i < sections.size(); i++) {
			values[i * 2] = sections.get(i).getColor();
			values[i * 2 + 1] = sections.get(i).getHeight();
		}
		return values;
	}

	private static List<BeaconBeamOwner.Section> sections(int[] values) {
		List<BeaconBeamOwner.Section> sections = new ArrayList<>();
		for (int i = 0; i + 1 < values.length; i += 2) {
			BeaconBeamOwner.Section section = new BeaconBeamOwner.Section(values[i]);
			for (int height = 1; height < values[i + 1]; height++) {
				section.increaseHeight();
			}
			sections.add(section);
		}
		return sections;
	}
}
