package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.NeedleDirectionHelper;
import net.minecraft.client.renderer.item.properties.numeric.Time;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Time.class)
public abstract class TimeItemPropertyMixin {
	@Shadow
	@Final
	private NeedleDirectionHelper.Wobbler wobbler;

	@WrapOperation(
		method = "calculate",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/properties/numeric/Time$TimeSource;get(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ItemOwner;Lnet/minecraft/util/RandomSource;)F")
	)
	private float dm$drawOnlyOnTickChange(Time.TimeSource source, ClientLevel level, ItemStack stack, ItemOwner owner, RandomSource random, Operation<Float> original) {
		if (!this.wobbler.shouldUpdate(level.getGameTime())) {
			return this.wobbler.rotation();
		}
		return original.call(source, level, stack, owner, random);
	}
}
