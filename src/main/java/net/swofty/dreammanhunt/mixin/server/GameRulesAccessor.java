package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRules.class)
public interface GameRulesAccessor {
	@Accessor("rules")
	GameRuleMap dreammanhunt$getRules();
}
