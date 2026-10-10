package net.swofty.dreammanhunt.client.state.audio;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.BeeAggressiveSoundInstance;
import net.minecraft.client.resources.sounds.BeeFlyingSoundInstance;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.GuardianAttackSoundInstance;
import net.minecraft.client.resources.sounds.MinecartSoundInstance;
import net.minecraft.client.resources.sounds.RidingEntitySoundInstance;
import net.minecraft.client.resources.sounds.RidingMinecartSoundInstance;
import net.minecraft.client.resources.sounds.SnifferSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.UnderLiquidAmbientSoundInstance;
import net.minecraft.client.resources.sounds.UnderLiquidSubSound;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.swofty.dreammanhunt.client.mixin.audio.AbstractSoundInstanceAccessor;
import org.jspecify.annotations.Nullable;

final class SoundRecords {
	private SoundRecords() {
	}

	static CompoundTag describe(SoundInstance instance) {
		CompoundTag tag = new CompoundTag();
		tag.putString("class", instance.getClass().getName());
		tag.putString("event", instance.getIdentifier().toString());
		tag.putString("source", instance.getSource().getName());
		tag.putString("attenuation", instance.getAttenuation().name());
		tag.putBoolean("relative", instance.isRelative());
		tag.putBoolean("looping", instance.isLooping());
		tag.putInt("delay", instance.getDelay());
		tag.putDouble("x", instance.getX());
		tag.putDouble("y", instance.getY());
		tag.putDouble("z", instance.getZ());
		if (instance instanceof AbstractSoundInstanceAccessor fields) {
			tag.putFloat("volume", fields.dma$getVolume());
			tag.putFloat("pitch", fields.dma$getPitch());
		}
		Sound sound = instance.getSound();
		if (sound != null) {
			RandomSource probe = RandomSource.create(0L);
			CompoundTag variant = new CompoundTag();
			variant.putString("location", sound.getLocation().toString());
			variant.putFloat("volume", sound.getVolume().sample(probe));
			variant.putFloat("pitch", sound.getPitch().sample(probe));
			variant.putInt("weight", sound.getWeight());
			variant.putString("type", sound.getType().name());
			variant.putBoolean("stream", sound.shouldStream());
			variant.putBoolean("preload", sound.shouldPreload());
			variant.putInt("attenuationDistance", sound.getAttenuationDistance());
			tag.put("sound", variant);
		}
		CompoundTag fields = new CompoundTag();
		CompoundTag entities = new CompoundTag();
		for (Class<?> type = instance.getClass(); type != null && type != AbstractSoundInstance.class && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				String key = type.getName() + "#" + field.getName();
				try {
					field.setAccessible(true);
					Class<?> fieldType = field.getType();
					if (fieldType == int.class) {
						fields.putInt(key, field.getInt(instance));
					} else if (fieldType == float.class) {
						fields.putFloat(key, field.getFloat(instance));
					} else if (fieldType == double.class) {
						fields.putDouble(key, field.getDouble(instance));
					} else if (fieldType == boolean.class) {
						fields.putBoolean(key, field.getBoolean(instance));
					} else if (fieldType == long.class) {
						fields.putLong(key, field.getLong(instance));
					} else if (Entity.class.isAssignableFrom(fieldType) && field.get(instance) instanceof Entity entity) {
						entities.putString(field.getName(), entity.getUUID().toString());
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
		tag.put("fields", fields);
		tag.put("entities", entities);
		return tag;
	}

	static @Nullable Sound variant(CompoundTag tag) {
		CompoundTag variant = tag.getCompoundOrEmpty("sound");
		Identifier location = Identifier.tryParse(variant.getStringOr("location", ""));
		if (location == null) {
			return null;
		}
		Sound.Type type = Sound.Type.FILE;
		try {
			type = Sound.Type.valueOf(variant.getStringOr("type", "FILE"));
		} catch (IllegalArgumentException ignored) {
		}
		return new Sound(location, ConstantFloat.of(variant.getFloatOr("volume", 1.0F)), ConstantFloat.of(variant.getFloatOr("pitch", 1.0F)),
			variant.getIntOr("weight", 1), type, variant.getBooleanOr("stream", false), variant.getBooleanOr("preload", false),
			variant.getIntOr("attenuationDistance", 16));
	}

	static @Nullable SoundInstance recreate(Minecraft minecraft, CompoundTag tag) {
		Identifier event = Identifier.tryParse(tag.getStringOr("event", ""));
		if (event == null) {
			return null;
		}
		SoundSource source = source(tag.getStringOr("source", "master"));
		SoundInstance.Attenuation attenuation = SoundInstance.Attenuation.LINEAR;
		try {
			attenuation = SoundInstance.Attenuation.valueOf(tag.getStringOr("attenuation", "LINEAR"));
		} catch (IllegalArgumentException ignored) {
		}
		SoundInstance instance = null;
		try {
			instance = bind(minecraft, tag, event, source);
		} catch (RuntimeException ignored) {
		}
		if (instance == null) {
			if (!tag.getCompoundOrEmpty("entities").isEmpty() && tag.getBooleanOr("looping", false)) {
				return null;
			}
			instance = new RestoredSoundInstance(event, source, attenuation, tag.getBooleanOr("relative", false));
		} else {
			writeFields(instance, tag.getCompoundOrEmpty("fields"));
		}
		if (instance instanceof AbstractSoundInstanceAccessor fields) {
			fields.dma$setVolume(tag.getFloatOr("volume", 1.0F));
			fields.dma$setPitch(tag.getFloatOr("pitch", 1.0F));
			fields.dma$setX(tag.getDoubleOr("x", 0.0));
			fields.dma$setY(tag.getDoubleOr("y", 0.0));
			fields.dma$setZ(tag.getDoubleOr("z", 0.0));
			fields.dma$setLooping(tag.getBooleanOr("looping", false));
			fields.dma$setDelay(tag.getIntOr("delay", 0));
		}
		return instance;
	}

	private static @Nullable SoundInstance bind(Minecraft minecraft, CompoundTag tag, Identifier event, SoundSource source) {
		String type = tag.getStringOr("class", "");
		SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(event);
		CompoundTag entities = tag.getCompoundOrEmpty("entities");
		CompoundTag fields = tag.getCompoundOrEmpty("fields");
		LocalPlayer player = minecraft.player;
		if (type.equals(BiomeAmbientSoundsHandler.LoopSoundInstance.class.getName())) {
			return new BiomeAmbientSoundsHandler.LoopSoundInstance(soundEvent);
		}
		if (type.equals(UnderLiquidAmbientSoundInstance.class.getName())) {
			return new UnderLiquidAmbientSoundInstance(soundEvent, player, LocalPlayer::isUnderWater);
		}
		if (type.equals(UnderLiquidSubSound.class.getName())) {
			try {
				Constructor<UnderLiquidSubSound> constructor = UnderLiquidSubSound.class.getDeclaredConstructor(LocalPlayer.class, SoundEvent.class);
				constructor.setAccessible(true);
				return constructor.newInstance(player, soundEvent);
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}
		if (type.equals(ElytraOnPlayerSoundInstance.class.getName())) {
			return new ElytraOnPlayerSoundInstance(player);
		}
		if (type.equals(MinecartSoundInstance.class.getName())) {
			return entity(minecraft, entities, "minecart") instanceof AbstractMinecart minecart ? new MinecartSoundInstance(minecart) : null;
		}
		String riding = RidingEntitySoundInstance.class.getName() + "#";
		boolean underwater = fields.getBooleanOr(riding + "underwaterSound", false);
		float volumeMin = fields.getFloatOr(riding + "volumeMin", 0.0F);
		float volumeMax = fields.getFloatOr(riding + "volumeMax", 1.0F);
		float amplifier = fields.getFloatOr(riding + "volumeAmplifier", 1.0F);
		if (type.equals(RidingMinecartSoundInstance.class.getName())) {
			return entity(minecraft, entities, "minecart") instanceof AbstractMinecart minecart
				? new RidingMinecartSoundInstance(player, minecart, underwater, soundEvent, volumeMin, volumeMax, amplifier)
				: null;
		}
		if (type.equals(RidingEntitySoundInstance.class.getName())) {
			Entity ridden = entity(minecraft, entities, "entity");
			return ridden != null ? new RidingEntitySoundInstance(player, ridden, underwater, soundEvent, source, volumeMin, volumeMax, amplifier) : null;
		}
		if (type.equals(BeeFlyingSoundInstance.class.getName())) {
			return entity(minecraft, entities, "bee") instanceof Bee bee ? new BeeFlyingSoundInstance(bee) : null;
		}
		if (type.equals(BeeAggressiveSoundInstance.class.getName())) {
			return entity(minecraft, entities, "bee") instanceof Bee bee ? new BeeAggressiveSoundInstance(bee) : null;
		}
		if (type.equals(GuardianAttackSoundInstance.class.getName())) {
			return entity(minecraft, entities, "guardian") instanceof Guardian guardian ? new GuardianAttackSoundInstance(guardian) : null;
		}
		if (type.equals(SnifferSoundInstance.class.getName())) {
			return entity(minecraft, entities, "sniffer") instanceof Sniffer sniffer ? new SnifferSoundInstance(sniffer) : null;
		}
		if (type.equals(EntityBoundSoundInstance.class.getName())) {
			Entity bound = entity(minecraft, entities, "entity");
			return bound != null ? new EntityBoundSoundInstance(soundEvent, source, tag.getFloatOr("volume", 1.0F), tag.getFloatOr("pitch", 1.0F), bound, 0L) : null;
		}
		return null;
	}

	private static @Nullable Entity entity(Minecraft minecraft, CompoundTag entities, String field) {
		String raw = entities.getStringOr(field, "");
		if (raw.isEmpty() || minecraft.level == null) {
			return null;
		}
		UUID uuid;
		try {
			uuid = UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (minecraft.player != null && minecraft.player.getUUID().equals(uuid)) {
			return minecraft.player;
		}
		return minecraft.level.getEntity(uuid);
	}

	private static void writeFields(SoundInstance instance, CompoundTag fields) {
		for (Class<?> type = instance.getClass(); type != null && type != AbstractSoundInstance.class && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				String key = type.getName() + "#" + field.getName();
				Tag value = fields.get(key);
				if (value == null) {
					continue;
				}
				try {
					field.setAccessible(true);
					Class<?> fieldType = field.getType();
					if (fieldType == int.class) {
						field.setInt(instance, fields.getIntOr(key, 0));
					} else if (fieldType == float.class) {
						field.setFloat(instance, fields.getFloatOr(key, 0.0F));
					} else if (fieldType == double.class) {
						field.setDouble(instance, fields.getDoubleOr(key, 0.0));
					} else if (fieldType == boolean.class) {
						field.setBoolean(instance, fields.getBooleanOr(key, false));
					} else if (fieldType == long.class) {
						field.setLong(instance, fields.getLongOr(key, 0L));
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
	}

	static SoundSource source(String name) {
		for (SoundSource source : SoundSource.values()) {
			if (source.getName().equals(name)) {
				return source;
			}
		}
		return SoundSource.MASTER;
	}
}
