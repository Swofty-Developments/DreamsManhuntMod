package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAudioAccessor {
	@Accessor("wasUnderwater")
	boolean dma$getWasUnderwater();

	@Accessor("wasUnderwater")
	void dma$setWasUnderwater(boolean wasUnderwater);
}
