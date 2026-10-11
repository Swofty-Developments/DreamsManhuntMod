package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuSyncAccessor {
	@Accessor("stateId")
	void dreammanhunt$sync$setStateId(int stateId);
}
