package net.swofty.dreammanhunt.server.coordinator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.swofty.dreammanhunt.mixin.server.LegacyRandomSourceAccessor;
import net.swofty.dreammanhunt.mixin.common.EntityRandomAccessor;
import net.swofty.dreammanhunt.mixin.common.FishingHookAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.mixin.common.AbstractContainerMenuSyncAccessor;
import net.swofty.dreammanhunt.mixin.common.CompoundContainerAccessor;
import net.swofty.dreammanhunt.mixin.common.LivingEntitySyncAccessor;
import net.swofty.dreammanhunt.mixin.common.PortalProcessorAccessor;
import net.swofty.dreammanhunt.mixin.common.ServerGamePacketListenerSyncAccessor;
import net.swofty.dreammanhunt.mixin.common.ServerPlayerSyncAccessor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PlayerSync {
	private static final String FILE = "player_sync.nbt";
	private static final int SPIN_ATTACK_FLAG = 4;
	private static boolean reopening;
	private static final Map<ServerPlayer, Component> TITLES = new WeakHashMap<>();

	private PlayerSync() {
	}

	public static boolean isReopening() {
		return reopening;
	}

	static Map<UUID, CompoundTag> captureAll(MinecraftServer server) {
		Map<UUID, CompoundTag> states = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				states.put(player.getUUID(), capture(player));
			} catch (RuntimeException e) {
				DreamManhunt.LOGGER.error("Could not capture protocol state for {}", player.getPlainTextName(), e);
			}
		}
		return states;
	}

	public static CompoundTag capture(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.put("fields", PlayerFields.capture(player));
		Input input = player.getLastClientInput();
		CompoundTag inputTag = new CompoundTag();
		inputTag.putBoolean("forward", input.forward());
		inputTag.putBoolean("backward", input.backward());
		inputTag.putBoolean("left", input.left());
		inputTag.putBoolean("right", input.right());
		inputTag.putBoolean("jump", input.jump());
		inputTag.putBoolean("shift", input.shift());
		inputTag.putBoolean("sprint", input.sprint());
		tag.put("input", inputTag);
		tag.putBoolean("sprinting", player.isSprinting());
		tag.putBoolean("shift_key_down", player.isShiftKeyDown());
		tag.putBoolean("swimming", player.isSwimming());
		tag.putString("pose", player.getPose().name());

		LivingEntitySyncAccessor living = (LivingEntitySyncAccessor) player;
		tag.putInt("fall_fly_ticks", living.dreammanhunt$sync$getFallFlyTicks());
		int spinTicks = living.dreammanhunt$sync$getAutoSpinAttackTicks();
		if (spinTicks > 0) {
			CompoundTag spin = new CompoundTag();
			spin.putInt("ticks", spinTicks);
			spin.putFloat("damage", living.dreammanhunt$sync$getAutoSpinAttackDmg());
			ItemStack stack = living.dreammanhunt$sync$getAutoSpinAttackItemStack();
			if (stack != null && !stack.isEmpty()) {
				RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
				spin.store("item", ItemStack.OPTIONAL_CODEC, ops, stack.copy());
			}
			tag.put("spin_attack", spin);
		}

		PortalProcessor portal = player.portalProcess;
		if (portal != null) {
			Portal block = ((PortalProcessorAccessor) portal).dreammanhunt$getPortal();
			if (block instanceof Block portalBlock) {
				CompoundTag portalTag = new CompoundTag();
				portalTag.putString("block", BuiltInRegistries.BLOCK.getKey(portalBlock).toString());
				portalTag.store("entry", BlockPos.CODEC, portal.getEntryPosition());
				portalTag.putInt("time", portal.getPortalTime());
				portalTag.putBoolean("inside", portal.isInsidePortalThisTick());
				tag.put("portal", portalTag);
			}
		}

		FishingHook fishing = player.fishing;
		if (fishing != null && !fishing.isRemoved()) {
			CompoundTag hook = new CompoundTag();
			hook.store("uuid", UUIDUtil.CODEC, fishing.getUUID());
			hook.putInt("id", fishing.getId());
			hook.store("pos", Vec3.CODEC, fishing.position());
			hook.putFloat("yaw", fishing.getYRot());
			hook.putFloat("pitch", fishing.getXRot());
			hook.putInt("luck", ((FishingHookAccessor) fishing).dreammanhunt$getLuck());
			hook.putInt("lure", ((FishingHookAccessor) fishing).dreammanhunt$getLureSpeed());
			Entity hooked = fishing.getHookedIn();
			if (hooked != null) {
				hook.store("hooked", UUIDUtil.CODEC, hooked.getUUID());
			}
			if (((EntityRandomAccessor) fishing).dreammanhunt$sync$getRandom() instanceof LegacyRandomSource random) {
				hook.putLong("seed", ((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().get());
			}
			hook.put("fields", PlayerFields.capture(fishing));
			tag.put("fishing", hook);
		}

		Entity camera = player.getCamera();
		if (camera != player) {
			tag.store("camera", UUIDUtil.CODEC, camera.getUUID());
		}

		tag.putInt("container_counter", ((ServerPlayerSyncAccessor) player).dreammanhunt$sync$getContainerCounter());
		tag.putInt("inventory_state", player.inventoryMenu.getStateId());
		BlockPos container = containerPos(player.containerMenu, player);
		if (container != null) {
			tag.store("container", BlockPos.CODEC, container);
			tag.putInt("container_id", player.containerMenu.containerId);
			tag.putInt("container_state", player.containerMenu.getStateId());
		}

		ServerGamePacketListenerSyncAccessor connection = (ServerGamePacketListenerSyncAccessor) player.connection;
		tag.putBoolean("client_floating", connection.dreammanhunt$sync$getClientIsFloating());
		tag.putInt("above_ground_ticks", connection.dreammanhunt$sync$getAboveGroundTickCount());
		tag.putBoolean("client_vehicle_floating", connection.dreammanhunt$sync$getClientVehicleIsFloating());
		tag.putInt("above_ground_vehicle_ticks", connection.dreammanhunt$sync$getAboveGroundVehicleTickCount());
		return tag;
	}

	private static @Nullable BlockPos containerPos(AbstractContainerMenu menu, ServerPlayer player) {
		if (menu == player.inventoryMenu) {
			return null;
		}
		for (Slot slot : menu.slots) {
			Container container = slot.container;
			if (container instanceof BlockEntity blockEntity) {
				return blockEntity.getBlockPos();
			}
			if (container instanceof CompoundContainer compound
					&& ((CompoundContainerAccessor) compound).dreammanhunt$getContainer1() instanceof BlockEntity first) {
				return first.getBlockPos();
			}
		}
		return null;
	}

	public static void apply(ServerPlayer player, CompoundTag tag) {
		tag.getCompound("fields").ifPresent(fields -> PlayerFields.apply(player, fields));
		tag.getCompound("input").ifPresent(input -> player.setLastClientInput(new Input(
				input.getBooleanOr("forward", false),
				input.getBooleanOr("backward", false),
				input.getBooleanOr("left", false),
				input.getBooleanOr("right", false),
				input.getBooleanOr("jump", false),
				input.getBooleanOr("shift", false),
				input.getBooleanOr("sprint", false))));
		player.setShiftKeyDown(tag.getBooleanOr("shift_key_down", false));
		player.setSprinting(tag.getBooleanOr("sprinting", false));
		player.setSwimming(tag.getBooleanOr("swimming", false));
		try {
			player.setPose(Pose.valueOf(tag.getStringOr("pose", Pose.STANDING.name())));
		} catch (IllegalArgumentException ignored) {
		}

		LivingEntitySyncAccessor living = (LivingEntitySyncAccessor) player;
		living.dreammanhunt$sync$setFallFlyTicks(tag.getIntOr("fall_fly_ticks", 0));
		tag.getCompound("spin_attack").ifPresent(spin -> {
			RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			living.dreammanhunt$sync$setAutoSpinAttackTicks(spin.getIntOr("ticks", 0));
			living.dreammanhunt$sync$setAutoSpinAttackDmg(spin.getFloatOr("damage", 0.0F));
			living.dreammanhunt$sync$setAutoSpinAttackItemStack(spin.read("item", ItemStack.OPTIONAL_CODEC, ops).orElse(null));
			living.dreammanhunt$sync$setLivingEntityFlag(SPIN_ATTACK_FLAG, true);
		});

		player.portalProcess = null;
		tag.getCompound("portal").ifPresent(portalTag -> {
			Identifier id = Identifier.tryParse(portalTag.getStringOr("block", ""));
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
			BlockPos entry = portalTag.read("entry", BlockPos.CODEC).orElse(null);
			if (block instanceof Portal portal && entry != null) {
				PortalProcessor processor = new PortalProcessor(portal, entry);
				((PortalProcessorAccessor) processor).dreammanhunt$setPortalTime(portalTag.getIntOr("time", 0));
				processor.setAsInsidePortalThisTick(portalTag.getBooleanOr("inside", false));
				player.portalProcess = processor;
			}
		});

		((ServerPlayerSyncAccessor) player).dreammanhunt$sync$setContainerCounter(tag.getIntOr("container_counter", 0));
		int inventoryState = tag.getIntOr("inventory_state", -1);
		if (inventoryState > 0) {
			((AbstractContainerMenuSyncAccessor) player.inventoryMenu).dreammanhunt$sync$setStateId(inventoryState - 1);
			player.inventoryMenu.sendAllDataToRemote();
		}

		ServerGamePacketListenerSyncAccessor connection = (ServerGamePacketListenerSyncAccessor) player.connection;
		connection.dreammanhunt$sync$setClientIsFloating(tag.getBooleanOr("client_floating", false));
		connection.dreammanhunt$sync$setAboveGroundTickCount(tag.getIntOr("above_ground_ticks", 0));
		connection.dreammanhunt$sync$setClientVehicleIsFloating(tag.getBooleanOr("client_vehicle_floating", false));
		connection.dreammanhunt$sync$setAboveGroundVehicleTickCount(tag.getIntOr("above_ground_vehicle_ticks", 0));
	}

	static void respawnFishingHook(ServerPlayer player, CompoundTag tag) {
		tag.getCompound("fishing").ifPresent(hook -> {
			UUID uuid = hook.read("uuid", UUIDUtil.CODEC).orElse(null);
			Vec3 pos = hook.read("pos", Vec3.CODEC).orElse(null);
			ServerLevel level = player.level();
			if (uuid == null || pos == null || level.getEntity(uuid) != null) {
				return;
			}
			FishingHook fishing = new FishingHook(EntityTypes.FISHING_BOBBER, level, hook.getIntOr("luck", 0), hook.getIntOr("lure", 0));
			fishing.setUUID(uuid);
			int id = hook.getIntOr("id", -1);
			if (id > 0 && !idInUse(player.level().getServer(), id)) {
				fishing.setId(id);
			}
			fishing.snapTo(pos.x, pos.y, pos.z, hook.getFloatOr("yaw", 0.0F), hook.getFloatOr("pitch", 0.0F));
			hook.getCompound("fields").ifPresent(fields -> PlayerFields.apply(fishing, fields));
			hook.getLong("seed").ifPresent(seed -> {
				if (((EntityRandomAccessor) fishing).dreammanhunt$sync$getRandom() instanceof LegacyRandomSource random) {
					((LegacyRandomSourceAccessor) random).dreammanhunt$getSeed().set(seed);
				}
			});
			fishing.setOwner(player);
			hook.read("hooked", UUIDUtil.CODEC).map(level::getEntity).ifPresent(hooked -> ((FishingHookAccessor) fishing).dreammanhunt$setHookedIn(hooked));
			level.addFreshEntity(fishing);
		});
	}

	private static boolean idInUse(MinecraftServer server, int id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) != null) {
				return true;
			}
		}
		return false;
	}

	static void linkFishingHook(ServerPlayer player) {
		if (player.fishing != null && !player.fishing.isRemoved()) {
			return;
		}
		List<? extends FishingHook> hooks = player.level().getEntities(EntityTypeTest.<Entity, FishingHook>forClass(FishingHook.class),
				hook -> !hook.isRemoved() && hook.getPlayerOwner() == player);
		if (!hooks.isEmpty()) {
			player.fishing = hooks.getFirst();
		}
	}

	static void applyCamera(ServerPlayer player, CompoundTag tag) {
		tag.read("camera", UUIDUtil.CODEC).ifPresent(uuid -> {
			Entity camera = player.level().getEntity(uuid);
			if (camera != null) {
				player.setCamera(camera);
			}
		});
	}

	static void resendContainer(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		Component title = TITLES.remove(player);
		if (menu == player.inventoryMenu || title == null) {
			return;
		}
		player.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), title));
		((AbstractContainerMenuSyncAccessor) menu).dreammanhunt$sync$setStateId(menu.getStateId() - 1);
		menu.sendAllDataToRemote();
	}

	static void reopenContainer(ServerPlayer player, CompoundTag tag) {
		tag.read("container", BlockPos.CODEC).ifPresent(pos -> {
			ServerLevel level = player.level();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
			if (provider == null) {
				return;
			}
			TITLES.put(player, provider.getDisplayName());
			ServerPlayerSyncAccessor counter = (ServerPlayerSyncAccessor) player;
			int next = counter.dreammanhunt$sync$getContainerCounter();
			int savedId = tag.getIntOr("container_id", -1);
			if (savedId > 0) {
				counter.dreammanhunt$sync$setContainerCounter(savedId - 1);
			}
			reopening = true;
			try {
				player.openMenu(provider);
			} finally {
				reopening = false;
			}
			if (savedId > 0) {
				counter.dreammanhunt$sync$setContainerCounter(next);
			}
			int savedState = tag.getIntOr("container_state", -1);
			if (savedState > 0 && player.containerMenu != player.inventoryMenu) {
				((AbstractContainerMenuSyncAccessor) player.containerMenu).dreammanhunt$sync$setStateId(savedState - 1);
				player.containerMenu.sendAllDataToRemote();
			}
		});
	}

	static Path file(MinecraftServer server, String id) {
		return server.getWorldPath(LevelResource.ROOT).resolve(DreamManhunt.MOD_ID).resolve("savestates").resolve(id).resolve(FILE);
	}

	static void write(MinecraftServer server, String id, Map<UUID, CompoundTag> states) {
		CompoundTag root = new CompoundTag();
		states.forEach((uuid, tag) -> root.put(uuid.toString(), tag));
		Path path = file(server, id);
		try {
			if (Files.isDirectory(path.getParent())) {
				NbtIo.writeCompressed(root, path);
			}
		} catch (IOException e) {
			DreamManhunt.LOGGER.error("Could not write {}", path, e);
		}
	}

	static Map<UUID, CompoundTag> read(MinecraftServer server, String id) {
		Map<UUID, CompoundTag> states = new HashMap<>();
		Path path = file(server, id);
		if (!Files.isRegularFile(path)) {
			return states;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			for (String key : root.keySet()) {
				try {
					root.getCompound(key).ifPresent(tag -> states.put(UUID.fromString(key), tag));
				} catch (IllegalArgumentException ignored) {
				}
			}
		} catch (IOException e) {
			DreamManhunt.LOGGER.error("Could not read {}", path, e);
		}
		return states;
	}
}
