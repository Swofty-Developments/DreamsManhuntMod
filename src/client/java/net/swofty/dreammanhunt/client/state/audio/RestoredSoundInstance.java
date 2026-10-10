package net.swofty.dreammanhunt.client.state.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

final class RestoredSoundInstance extends AbstractSoundInstance {
	RestoredSoundInstance(Identifier identifier, SoundSource source, SoundInstance.Attenuation attenuation, boolean relative) {
		super(identifier, source, SoundInstance.createUnseededRandom());
		this.attenuation = attenuation;
		this.relative = relative;
	}
}
