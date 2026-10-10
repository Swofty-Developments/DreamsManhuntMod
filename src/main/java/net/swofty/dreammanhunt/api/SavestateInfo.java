package net.swofty.dreammanhunt.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SavestateInfo(
		String id,
		String name,
		long createdAtEpochMillis,
		long gameTime,
		long dayTime,
		String creatorName,
		String dimension
) {
	public static final Codec<SavestateInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(SavestateInfo::id),
			Codec.STRING.fieldOf("name").forGetter(SavestateInfo::name),
			Codec.LONG.fieldOf("created_at").forGetter(SavestateInfo::createdAtEpochMillis),
			Codec.LONG.fieldOf("game_time").forGetter(SavestateInfo::gameTime),
			Codec.LONG.fieldOf("day_time").forGetter(SavestateInfo::dayTime),
			Codec.STRING.fieldOf("creator").forGetter(SavestateInfo::creatorName),
			Codec.STRING.fieldOf("dimension").forGetter(SavestateInfo::dimension)
	).apply(instance, SavestateInfo::new));

	public static final StreamCodec<ByteBuf, SavestateInfo> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SavestateInfo::id,
			ByteBufCodecs.STRING_UTF8, SavestateInfo::name,
			ByteBufCodecs.VAR_LONG, SavestateInfo::createdAtEpochMillis,
			ByteBufCodecs.VAR_LONG, SavestateInfo::gameTime,
			ByteBufCodecs.VAR_LONG, SavestateInfo::dayTime,
			ByteBufCodecs.STRING_UTF8, SavestateInfo::creatorName,
			ByteBufCodecs.STRING_UTF8, SavestateInfo::dimension,
			SavestateInfo::new
	);
}
