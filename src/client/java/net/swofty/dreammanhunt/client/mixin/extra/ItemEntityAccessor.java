package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
	@Mutable
	@Accessor("bobOffs")
	void dm$setBobOffs(float bobOffs);
}
