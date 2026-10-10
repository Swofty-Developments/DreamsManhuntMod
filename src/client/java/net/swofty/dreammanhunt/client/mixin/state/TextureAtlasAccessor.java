package net.swofty.dreammanhunt.client.mixin.state;

import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
	@Accessor("animatedTexturesStates")
	List<SpriteContents.AnimationState> dm$getAnimatedTexturesStates();
}
