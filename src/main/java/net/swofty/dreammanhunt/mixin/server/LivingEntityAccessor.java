package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	int dreammanhunt$getAttackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void dreammanhunt$setAttackStrengthTicker(int ticks);

	@Accessor("noActionTime")
	int dreammanhunt$getNoActionTime();

	@Accessor("noActionTime")
	void dreammanhunt$setNoActionTime(int ticks);

	@Accessor("lastHurt")
	float dreammanhunt$getLastHurt();

	@Accessor("lastHurt")
	void dreammanhunt$setLastHurt(float amount);

	@Accessor("useItem")
	void dreammanhunt$setUseItem(ItemStack stack);

	@Accessor("useItemRemaining")
	int dreammanhunt$getUseItemRemaining();

	@Accessor("useItemRemaining")
	void dreammanhunt$setUseItemRemaining(int ticks);

	@Accessor("recentKineticEnemies")
	void dreammanhunt$setRecentKineticEnemies(Object2LongMap<Entity> enemies);

	@Invoker("setLivingEntityFlag")
	void dreammanhunt$setLivingEntityFlag(int flag, boolean value);

	@Accessor("noJumpDelay")
	int dreammanhunt$getNoJumpDelay();

	@Accessor("noJumpDelay")
	void dreammanhunt$setNoJumpDelay(int ticks);
}
