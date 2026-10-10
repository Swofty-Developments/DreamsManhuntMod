package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
	@Accessor("cooldowns")
	Map<Identifier, Object> dreammanhunt$getCooldowns();

	@Accessor("tickCount")
	int dreammanhunt$getTickCount();

	@Accessor("tickCount")
	void dreammanhunt$setTickCount(int tickCount);
}
