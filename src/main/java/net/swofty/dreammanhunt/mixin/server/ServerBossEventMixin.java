package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.server.level.ServerBossEvent;
import net.swofty.dreammanhunt.server.engine.BossBarRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void dreammanhunt$register(CallbackInfo ci) {
		BossBarRegistry.register((ServerBossEvent) (Object) this);
	}
}
