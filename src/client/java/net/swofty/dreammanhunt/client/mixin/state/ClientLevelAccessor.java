package net.swofty.dreammanhunt.client.mixin.state;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
	@Accessor("skyFlashTime")
	int dm$getSkyFlashTime();

	@Accessor("skyFlashTime")
	void dm$setSkyFlashTime(int value);

	@Accessor("rainSoundTime")
	int dm$getRainSoundTime();

	@Accessor("rainSoundTime")
	void dm$setRainSoundTime(int value);

	@Accessor("destroyingBlocks")
	Int2ObjectMap<BlockDestructionProgress> dm$getDestroyingBlocks();

	@Accessor("destructionProgress")
	Long2ObjectMap<SortedSet<BlockDestructionProgress>> dm$getDestructionProgress();
}
