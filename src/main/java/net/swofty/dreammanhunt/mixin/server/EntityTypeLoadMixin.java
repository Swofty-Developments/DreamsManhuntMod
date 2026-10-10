package net.swofty.dreammanhunt.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.swofty.dreammanhunt.server.engine.EntityIds;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(EntityType.class)
public abstract class EntityTypeLoadMixin {
	@WrapMethod(method = "create(Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Ljava/util/Optional;")
	private static Optional<Entity> dreammanhunt$deferIdForLoad(ValueInput input, Level level, EntitySpawnRequest request, Operation<Optional<Entity>> original) {
		if (!(level instanceof ServerLevel)) {
			return original.call(input, level, request);
		}
		return EntityIds.deferred(() -> original.call(input, level, request));
	}

	@WrapMethod(method = "create(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Ljava/util/Optional;")
	private static Optional<Entity> dreammanhunt$deferIdForTypedLoad(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason, Operation<Optional<Entity>> original) {
		if (!(level instanceof ServerLevel)) {
			return original.call(type, input, level, reason);
		}
		return EntityIds.deferred(() -> original.call(type, input, level, reason));
	}
}
