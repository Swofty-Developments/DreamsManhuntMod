package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.AnimationState.class)
public interface SpriteAnimationStateAccessor {
	@Accessor("frame")
	int dm$getFrame();

	@Accessor("frame")
	void dm$setFrame(int value);

	@Accessor("subFrame")
	int dm$getSubFrame();

	@Accessor("subFrame")
	void dm$setSubFrame(int value);

	@Accessor("isDirty")
	void dm$setDirty(boolean value);
}
