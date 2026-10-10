package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(SavedDataStorage.class)
public interface SavedDataStorageAccessor {
	@Accessor("cache")
	Map<SavedDataType<?>, Optional<SavedData>> dreammanhunt$getCache();

	@Accessor("pendingWriteFuture")
	CompletableFuture<?> dreammanhunt$getPendingWriteFuture();
}
