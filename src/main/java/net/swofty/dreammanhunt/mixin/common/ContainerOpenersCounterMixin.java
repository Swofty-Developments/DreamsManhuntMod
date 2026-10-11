package net.swofty.dreammanhunt.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.swofty.dreammanhunt.server.coordinator.PlayerSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ContainerOpenersCounter.class)
public abstract class ContainerOpenersCounterMixin {
	@WrapOperation(method = "incrementOpeners", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/ContainerOpenersCounter;onOpen(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private void dreammanhunt$silentReopen(ContainerOpenersCounter counter, Level level, BlockPos pos, BlockState state, Operation<Void> original) {
		if (!PlayerSync.isReopening()) {
			original.call(counter, level, pos, state);
		}
	}

	@WrapOperation(method = "incrementOpeners", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)V"))
	private void dreammanhunt$noGameEventOnReopen(Level level, Entity entity, Holder<GameEvent> event, BlockPos pos, Operation<Void> original) {
		if (!PlayerSync.isReopening()) {
			original.call(level, entity, event, pos);
		}
	}

	@WrapOperation(method = "incrementOpeners", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/ContainerOpenersCounter;openerCountChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)V"))
	private void dreammanhunt$noBlockEventOnReopen(ContainerOpenersCounter counter, Level level, BlockPos pos, BlockState state, int previous, int current,
			Operation<Void> original) {
		if (!PlayerSync.isReopening()) {
			original.call(counter, level, pos, state, previous, current);
		}
	}
}
