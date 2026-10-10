package net.swofty.dreammanhunt.server.tracking.mixin;

import java.util.List;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerEntity.class)
public interface ServerEntityAccessor {
	@Accessor("entity")
	Entity dreammanhunt$getEntity();

	@Accessor("positionCodec")
	VecDeltaCodec dreammanhunt$getPositionCodec();

	@Accessor("lastSentYRot")
	byte dreammanhunt$getLastSentYRot();

	@Accessor("lastSentYRot")
	void dreammanhunt$setLastSentYRot(byte value);

	@Accessor("lastSentXRot")
	byte dreammanhunt$getLastSentXRot();

	@Accessor("lastSentXRot")
	void dreammanhunt$setLastSentXRot(byte value);

	@Accessor("lastSentYHeadRot")
	byte dreammanhunt$getLastSentYHeadRot();

	@Accessor("lastSentYHeadRot")
	void dreammanhunt$setLastSentYHeadRot(byte value);

	@Accessor("lastSentMovement")
	Vec3 dreammanhunt$getLastSentMovement();

	@Accessor("lastSentMovement")
	void dreammanhunt$setLastSentMovement(Vec3 value);

	@Accessor("tickCount")
	int dreammanhunt$getTickCount();

	@Accessor("tickCount")
	void dreammanhunt$setTickCount(int value);

	@Accessor("teleportDelay")
	int dreammanhunt$getTeleportDelay();

	@Accessor("teleportDelay")
	void dreammanhunt$setTeleportDelay(int value);

	@Accessor("lastPassengers")
	List<Entity> dreammanhunt$getLastPassengers();

	@Accessor("lastPassengers")
	void dreammanhunt$setLastPassengers(List<Entity> value);

	@Accessor("wasRiding")
	boolean dreammanhunt$getWasRiding();

	@Accessor("wasRiding")
	void dreammanhunt$setWasRiding(boolean value);

	@Accessor("wasOnGround")
	boolean dreammanhunt$getWasOnGround();

	@Accessor("wasOnGround")
	void dreammanhunt$setWasOnGround(boolean value);
}
