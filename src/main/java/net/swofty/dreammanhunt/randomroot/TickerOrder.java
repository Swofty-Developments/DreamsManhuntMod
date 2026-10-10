package net.swofty.dreammanhunt.randomroot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.Level;
import net.swofty.dreammanhunt.mixin.randomroot.LevelTickersAccessor;

public final class TickerOrder {
	private TickerOrder() {
	}

	public static CompoundTag capture(Level level) {
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", level.dimension().identifier().toString());
		List<TickingBlockEntity> tickers = new ArrayList<>(((LevelTickersAccessor) level).dm$getBlockEntityTickers());
		tickers.addAll(((LevelTickersAccessor) level).dm$getPendingBlockEntityTickers());
		long[] order = new long[tickers.size()];
		for (int i = 0; i < tickers.size(); i++) {
			order[i] = tickers.get(i).getPos().asLong();
		}
		tag.putLongArray("order", order);
		return tag;
	}

	public static void apply(Level level, CompoundTag tag) {
		if (tag.isEmpty() || !level.dimension().identifier().toString().equals(tag.getStringOr("dimension", ""))) {
			return;
		}
		long[] order = tag.getLongArray("order").orElse(new long[0]);
		Map<Long, Integer> rank = new HashMap<>();
		for (int i = 0; i < order.length; i++) {
			rank.putIfAbsent(order[i], i);
		}
		LevelTickersAccessor accessor = (LevelTickersAccessor) level;
		List<TickingBlockEntity> tickers = accessor.dm$getBlockEntityTickers();
		tickers.addAll(accessor.dm$getPendingBlockEntityTickers());
		accessor.dm$getPendingBlockEntityTickers().clear();
		List<TickingBlockEntity> sorted = new ArrayList<>(tickers);
		sorted.sort(Comparator.comparingInt(ticker -> rank.getOrDefault(ticker.getPos().asLong(), Integer.MAX_VALUE)));
		tickers.clear();
		tickers.addAll(sorted);
	}
}
