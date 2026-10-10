package net.swofty.dreammanhunt.client.state;

import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

final class FadeState {
	private FadeState() {
	}

	static CompoundTag capture(Minecraft minecraft) {
		CompoundTag tag = new CompoundTag();
		AtmosphericFogEnvironment atmosphere = atmosphere();
		if (atmosphere != null) {
			tag.putFloat("rainFogMultiplier", Reflect.<Float>get(atmosphere, "rainFogMultiplier"));
		}
		CompoundTag effects = new CompoundTag();
		for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : minecraft.player.getActiveEffectsMap().entrySet()) {
			String id = effectId(entry.getKey());
			if (id != null) {
				Object blend = Reflect.get(entry.getValue(), "blendState");
				CompoundTag blendTag = new CompoundTag();
				blendTag.putFloat("factor", Reflect.<Float>get(blend, "factor"));
				blendTag.putFloat("factorPreviousFrame", Reflect.<Float>get(blend, "factorPreviousFrame"));
				effects.put(id, blendTag);
			}
		}
		tag.put("effects", effects);
		tag.putLong("spectatorSelection", Reflect.<Long>get(minecraft.gui.hud.getSpectatorGui(), "lastSelectionTime"));
		return tag;
	}

	static void apply(Minecraft minecraft, CompoundTag tag) {
		if (tag.isEmpty()) {
			return;
		}
		AtmosphericFogEnvironment atmosphere = atmosphere();
		if (atmosphere != null && tag.contains("rainFogMultiplier")) {
			Reflect.set(atmosphere, "rainFogMultiplier", tag.getFloatOr("rainFogMultiplier", 0.0F));
		}
		CompoundTag effects = tag.getCompoundOrEmpty("effects");
		for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : minecraft.player.getActiveEffectsMap().entrySet()) {
			String id = effectId(entry.getKey());
			if (id != null && effects.contains(id)) {
				CompoundTag blendTag = effects.getCompoundOrEmpty(id);
				Object blend = Reflect.get(entry.getValue(), "blendState");
				Reflect.set(blend, "factor", blendTag.getFloatOr("factor", 1.0F));
				Reflect.set(blend, "factorPreviousFrame", blendTag.getFloatOr("factorPreviousFrame", 1.0F));
			}
		}
		Reflect.set(minecraft.gui.hud.getSpectatorGui(), "lastSelectionTime", tag.getLongOr("spectatorSelection", 0L));
	}

	private static String effectId(Holder<MobEffect> holder) {
		return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
	}

	private static AtmosphericFogEnvironment atmosphere() {
		try {
			List<?> environments = (List<?>) Reflect.field(FogRenderer.class, "FOG_ENVIRONMENTS").get(null);
			for (Object environment : environments) {
				if (environment instanceof AtmosphericFogEnvironment atmospheric) {
					return atmospheric;
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return null;
	}
}
