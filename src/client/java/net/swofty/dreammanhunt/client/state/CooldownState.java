package net.swofty.dreammanhunt.client.state;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;

final class CooldownState {
	private CooldownState() {
	}

	static CompoundTag capture(LocalPlayer player) {
		ItemCooldowns cooldowns = player.getCooldowns();
		CompoundTag tag = new CompoundTag();
		tag.putInt("tickCount", Reflect.<Integer>get(cooldowns, "tickCount"));
		CompoundTag entries = new CompoundTag();
		Map<Identifier, Object> map = Reflect.get(cooldowns, "cooldowns");
		try {
			for (Map.Entry<Identifier, Object> entry : map.entrySet()) {
				RecordComponent[] components = entry.getValue().getClass().getRecordComponents();
				int[] times = new int[components.length];
				for (int i = 0; i < components.length; i++) {
					components[i].getAccessor().setAccessible(true);
					times[i] = (Integer) components[i].getAccessor().invoke(entry.getValue());
				}
				entries.putIntArray(entry.getKey().toString(), times);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		tag.put("entries", entries);
		return tag;
	}

	static void apply(LocalPlayer player, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		ItemCooldowns cooldowns = player.getCooldowns();
		Reflect.set(cooldowns, "tickCount", tag.getIntOr("tickCount", 0));
		Map<Identifier, Object> map = Reflect.get(cooldowns, "cooldowns");
		map.clear();
		CompoundTag entries = tag.getCompoundOrEmpty("entries");
		try {
			Class<?> instanceType = Class.forName(ItemCooldowns.class.getName() + "$CooldownInstance");
			Constructor<?> constructor = instanceType.getDeclaredConstructor(int.class, int.class);
			constructor.setAccessible(true);
			for (String key : entries.keySet()) {
				int[] times = entries.getIntArray(key).orElse(new int[0]);
				Identifier group = Identifier.tryParse(key);
				if (times.length == 2 && group != null) {
					map.put(group, constructor.newInstance(times[0], times[1]));
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
