package net.swofty.dreammanhunt.server.tracking;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationTracker;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.SteppedInterpolationTracker;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.server.tracking.mixin.ChunkMapAccessor;
import net.swofty.dreammanhunt.server.tracking.mixin.ServerEntityAccessor;
import net.swofty.dreammanhunt.server.tracking.mixin.TrackedEntityAccessor;
import net.swofty.dreammanhunt.server.tracking.mixin.VecDeltaCodecAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class TrackingState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FILE = "tracking.nbt";
	private static final int ARM_TICKS = 1200;
	private static final Map<String, CompoundTag> CAPTURED = new ConcurrentHashMap<>();
	private static volatile Map<UUID, CompoundTag> pending = Map.of();
	private static volatile int pendingUntilTick;
	private static volatile @Nullable MinecraftServer pendingServer;

	private TrackingState() {
	}

	public static void capture(MinecraftServer server, String id) {
		CompoundTag root = new CompoundTag();
		ListTag entries = new ListTag();
		for (ServerLevel level : server.getAllLevels()) {
			for (Object tracked : ((ChunkMapAccessor) level.getChunkSource().chunkMap).dreammanhunt$getEntityMap().values()) {
				ServerEntity serverEntity = ((TrackedEntityAccessor) tracked).dreammanhunt$getServerEntity();
				entries.add(captureEntity(serverEntity));
			}
		}
		root.put("entities", entries);
		CAPTURED.put(id, root);
	}

	public static void write(String id, Path dir) {
		CompoundTag root = CAPTURED.remove(id);
		if (root == null || !Files.isDirectory(dir)) {
			return;
		}
		try {
			NbtIo.writeCompressed(root, dir.resolve(FILE));
		} catch (IOException e) {
			LOGGER.error("Failed to write entity tracking state for {}", id, e);
		}
	}

	public static void arm(MinecraftServer server, Path dir) {
		Path file = dir.resolve(FILE);
		Map<UUID, CompoundTag> byUuid = new ConcurrentHashMap<>();
		if (!Files.isRegularFile(file)) {
			LOGGER.warn("Savestate {} has no entity tracking state; entity trackers start fresh and clients will see entities snap", dir.getFileName());
		} else {
			try {
				CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
				for (Tag raw : root.getListOrEmpty("entities")) {
					if (raw instanceof CompoundTag tag) {
						try {
							byUuid.put(UUID.fromString(tag.getStringOr("uuid", "")), tag);
						} catch (IllegalArgumentException ignored) {
						}
					}
				}
			} catch (IOException e) {
				LOGGER.error("Failed to read entity tracking state from {}", file, e);
			}
		}
		pending = byUuid;
		pendingServer = server;
		pendingUntilTick = server.getTickCount() + ARM_TICKS;
	}

	public static void onTrackerCreated(ServerEntity serverEntity) {
		Map<UUID, CompoundTag> armed = pending;
		if (armed.isEmpty()) {
			return;
		}
		MinecraftServer server = pendingServer;
		if (server == null || server.getTickCount() > pendingUntilTick) {
			pending = Map.of();
			return;
		}
		Entity entity = ((ServerEntityAccessor) serverEntity).dreammanhunt$getEntity();
		CompoundTag tag = armed.get(entity.getUUID());
		if (tag != null && entity.getType().toString().equals(tag.getStringOr("type", ""))) {
			applyEntity(serverEntity, entity, tag);
		}
	}

	public static void onBarrierLifted() {
		if (!pending.isEmpty()) {
			pending = Map.of();
		}
	}

	private static CompoundTag captureEntity(ServerEntity serverEntity) {
		ServerEntityAccessor accessor = (ServerEntityAccessor) serverEntity;
		Entity entity = accessor.dreammanhunt$getEntity();
		CompoundTag tag = new CompoundTag();
		tag.putString("uuid", entity.getUUID().toString());
		tag.putString("type", entity.getType().toString());
		putVec(tag, "base", ((VecDeltaCodecAccessor) accessor.dreammanhunt$getPositionCodec()).dreammanhunt$getBase());
		tag.putByte("yRot", accessor.dreammanhunt$getLastSentYRot());
		tag.putByte("xRot", accessor.dreammanhunt$getLastSentXRot());
		tag.putByte("yHeadRot", accessor.dreammanhunt$getLastSentYHeadRot());
		putVec(tag, "movement", accessor.dreammanhunt$getLastSentMovement());
		tag.putInt("tickCount", accessor.dreammanhunt$getTickCount());
		tag.putInt("teleportDelay", accessor.dreammanhunt$getTeleportDelay());
		tag.putBoolean("wasRiding", accessor.dreammanhunt$getWasRiding());
		tag.putBoolean("wasOnGround", accessor.dreammanhunt$getWasOnGround());
		ListTag passengers = new ListTag();
		for (Entity passenger : accessor.dreammanhunt$getLastPassengers()) {
			passengers.add(net.minecraft.nbt.StringTag.valueOf(passenger.getUUID().toString()));
		}
		tag.put("passengers", passengers);
		tag.putBoolean("needsSync", entity.needsSync);
		tag.putBoolean("syncPosition", entity.syncPosition);
		tag.putBoolean("syncVelocity", entity.syncVelocity);
		tag.putBoolean("dataDirty", entity.getEntityData().isDirty());
		InterpolationTracker tracker = entity.getInterpolation().interpolationTracker();
		if (tracker instanceof SteppedInterpolationTracker stepped) {
			tag.put("interpolation", captureStepped(stepped));
		}
		return tag;
	}

	private static void applyEntity(ServerEntity serverEntity, Entity entity, CompoundTag tag) {
		ServerEntityAccessor accessor = (ServerEntityAccessor) serverEntity;
		Vec3 base = getVec(tag, "base");
		if (base != null) {
			accessor.dreammanhunt$getPositionCodec().setBase(base);
		}
		accessor.dreammanhunt$setLastSentYRot(tag.getByteOr("yRot", accessor.dreammanhunt$getLastSentYRot()));
		accessor.dreammanhunt$setLastSentXRot(tag.getByteOr("xRot", accessor.dreammanhunt$getLastSentXRot()));
		accessor.dreammanhunt$setLastSentYHeadRot(tag.getByteOr("yHeadRot", accessor.dreammanhunt$getLastSentYHeadRot()));
		Vec3 movement = getVec(tag, "movement");
		if (movement != null) {
			accessor.dreammanhunt$setLastSentMovement(movement);
		}
		accessor.dreammanhunt$setTickCount(tag.getIntOr("tickCount", 0));
		accessor.dreammanhunt$setTeleportDelay(tag.getIntOr("teleportDelay", 0));
		accessor.dreammanhunt$setWasRiding(tag.getBooleanOr("wasRiding", false));
		accessor.dreammanhunt$setWasOnGround(tag.getBooleanOr("wasOnGround", entity.onGround()));
		List<String> saved = new ArrayList<>();
		for (Tag raw : tag.getListOrEmpty("passengers")) {
			saved.add(raw.asString().orElse(""));
		}
		List<String> current = entity.getPassengers().stream().map(passenger -> passenger.getUUID().toString()).toList();
		if (saved.equals(current)) {
			accessor.dreammanhunt$setLastPassengers(List.copyOf(entity.getPassengers()));
		}
		entity.needsSync = tag.getBooleanOr("needsSync", entity.needsSync);
		entity.syncPosition = tag.getBooleanOr("syncPosition", entity.syncPosition);
		entity.syncVelocity = tag.getBooleanOr("syncVelocity", entity.syncVelocity);
		if (!(entity instanceof net.minecraft.world.entity.player.Player) && !tag.getBooleanOr("dataDirty", true)) {
			entity.getEntityData().packDirty();
		}
		InterpolationTracker tracker = entity.getInterpolation().interpolationTracker();
		if (tracker instanceof SteppedInterpolationTracker stepped && tag.contains("interpolation")) {
			applyStepped(stepped, tag.getCompoundOrEmpty("interpolation"));
		}
	}

	private static CompoundTag captureStepped(SteppedInterpolationTracker tracker) {
		CompoundTag tag = new CompoundTag();
		List<PositionStep> steps = get(tracker, "trackedSteps");
		ListTag list = new ListTag();
		for (PositionStep step : steps) {
			CompoundTag entry = new CompoundTag();
			putVec(entry, "pos", step.position());
			entry.putInt("ticks", step.tickOffset());
			list.add(entry);
		}
		tag.put("steps", list);
		tag.putInt("ticksSinceLastStep", get(tracker, "ticksSinceLastStep"));
		putVec(tag, "predicted", get(tracker, "predictedDelta"));
		return tag;
	}

	private static void applyStepped(SteppedInterpolationTracker tracker, CompoundTag tag) {
		List<PositionStep> steps = get(tracker, "trackedSteps");
		steps.clear();
		for (Tag raw : tag.getListOrEmpty("steps")) {
			if (raw instanceof CompoundTag entry) {
				Vec3 pos = getVec(entry, "pos");
				if (pos != null) {
					steps.add(new PositionStep(pos, entry.getIntOr("ticks", 1)));
				}
			}
		}
		set(tracker, "ticksSinceLastStep", tag.getIntOr("ticksSinceLastStep", 0));
		Vec3 predicted = getVec(tag, "predicted");
		set(tracker, "predictedDelta", predicted == null ? Vec3.ZERO : predicted);
	}

	private static void putVec(CompoundTag tag, String key, @Nullable Vec3 vec) {
		if (vec == null) {
			return;
		}
		ListTag list = new ListTag();
		list.add(DoubleTag.valueOf(vec.x));
		list.add(DoubleTag.valueOf(vec.y));
		list.add(DoubleTag.valueOf(vec.z));
		tag.put(key, list);
	}

	private static @Nullable Vec3 getVec(CompoundTag tag, String key) {
		ListTag list = tag.getListOrEmpty(key);
		if (list.size() != 3) {
			return null;
		}
		return new Vec3(list.getDoubleOr(0, 0.0), list.getDoubleOr(1, 0.0), list.getDoubleOr(2, 0.0));
	}

	@SuppressWarnings("unchecked")
	private static <T> T get(Object owner, String name) {
		try {
			Field field = owner.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void set(Object owner, String name, Object value) {
		try {
			Field field = owner.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(owner, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
