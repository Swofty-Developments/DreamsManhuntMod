package net.swofty.dreammanhunt.mixin.sync;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
	private static final Comparator<LevelChunk> DM_CHUNK_ORDER = Comparator.comparingInt((LevelChunk chunk) -> chunk.getPos().x()).thenComparingInt(chunk -> chunk.getPos().z());

	@WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;collectSpawningChunks(Ljava/util/List;)V"))
	private void dm$collectInStableOrder(ChunkMap chunkMap, List<LevelChunk> output, Operation<Void> original) {
		original.call(chunkMap, output);
		output.sort(DM_CHUNK_ORDER);
	}

	@WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V"))
	private void dm$tickInStableOrder(ChunkMap chunkMap, Consumer<LevelChunk> consumer, Operation<Void> original) {
		List<LevelChunk> chunks = new ArrayList<>();
		original.call(chunkMap, (Consumer<LevelChunk>) chunks::add);
		chunks.sort(DM_CHUNK_ORDER);
		chunks.forEach(consumer);
	}
}
