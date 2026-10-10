package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.gui.Font;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Font.class)
public interface FontAccessor {
	@Accessor("random")
	RandomSource dm$getRandom();
}
