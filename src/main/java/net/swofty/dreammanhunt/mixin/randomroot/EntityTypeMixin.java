package net.swofty.dreammanhunt.mixin.randomroot;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
	@WrapMethod(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;")
	private Entity dm$loadWithoutStream(Level level, EntitySpawnRequest request, Operation<Entity> original) {
		if (request.reason() != EntitySpawnReason.LOAD) {
			return original.call(level, request);
		}
		SeedStreams.beginLoad();
		try {
			return original.call(level, request);
		} finally {
			SeedStreams.endLoad();
		}
	}
}
