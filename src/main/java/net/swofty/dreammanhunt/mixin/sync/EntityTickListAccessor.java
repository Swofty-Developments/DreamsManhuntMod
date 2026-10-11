package net.swofty.dreammanhunt.mixin.sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityTickList.class)
public interface EntityTickListAccessor {
	@Accessor("active")
	Int2ObjectMap<Entity> dm$getActive();

	@Accessor("iterated")
	@Nullable Int2ObjectMap<Entity> dm$getIterated();
}
