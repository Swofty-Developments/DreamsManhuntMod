package net.swofty.dreammanhunt.client.mixin.state;

import java.io.File;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
	@Accessor("optionsFile")
	File dm$getOptionsFile();
}
