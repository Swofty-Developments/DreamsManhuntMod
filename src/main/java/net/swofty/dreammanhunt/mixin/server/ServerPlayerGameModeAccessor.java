package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
	@Accessor("isDestroyingBlock")
	boolean dreammanhunt$isDestroyingBlock();

	@Accessor("isDestroyingBlock")
	void dreammanhunt$setDestroyingBlock(boolean destroying);

	@Accessor("destroyProgressStart")
	int dreammanhunt$getDestroyProgressStart();

	@Accessor("destroyProgressStart")
	void dreammanhunt$setDestroyProgressStart(int start);

	@Accessor("destroyPos")
	BlockPos dreammanhunt$getDestroyPos();

	@Accessor("destroyPos")
	void dreammanhunt$setDestroyPos(BlockPos pos);

	@Accessor("destroyDirection")
	Direction dreammanhunt$getDestroyDirection();

	@Accessor("destroyDirection")
	void dreammanhunt$setDestroyDirection(Direction direction);

	@Accessor("gameTicks")
	int dreammanhunt$getGameTicks();

	@Accessor("gameTicks")
	void dreammanhunt$setGameTicks(int ticks);

	@Accessor("hasDelayedDestroy")
	boolean dreammanhunt$hasDelayedDestroy();

	@Accessor("hasDelayedDestroy")
	void dreammanhunt$setDelayedDestroy(boolean delayed);

	@Accessor("delayedDestroyPos")
	BlockPos dreammanhunt$getDelayedDestroyPos();

	@Accessor("delayedDestroyPos")
	void dreammanhunt$setDelayedDestroyPos(BlockPos pos);

	@Accessor("delayedTickStart")
	int dreammanhunt$getDelayedTickStart();

	@Accessor("delayedTickStart")
	void dreammanhunt$setDelayedTickStart(int start);

	@Accessor("lastSentState")
	int dreammanhunt$getLastSentState();

	@Accessor("lastSentState")
	void dreammanhunt$setLastSentState(int state);
}
