package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleEngine.class)
public interface ParticleEngineInvoker {
	@Invoker("createParticleGroup")
	ParticleGroup<?> dm$createParticleGroup(ParticleRenderType type);
}
