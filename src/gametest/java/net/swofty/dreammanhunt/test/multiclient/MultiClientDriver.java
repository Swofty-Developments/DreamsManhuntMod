package net.swofty.dreammanhunt.test.multiclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import net.swofty.dreammanhunt.client.ui.CutMarker;
import net.swofty.dreammanhunt.client.ui.SavestateKeys;
import net.swofty.dreammanhunt.client.ui.SavestateList;
import net.swofty.dreammanhunt.client.ui.SavestateMenuScreen;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLKeyboard;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.TreeMap;

public final class MultiClientDriver implements ClientModInitializer {
	private static final int[] SHOT_OFFSETS = {10, 30};
	private static final int WINDOW_TICKS = 40;
	private static final int READY_DELAY_TICKS = 100;

	private final String name = System.getProperty("dreammanhunt.mc.name", "");
	private final String role = System.getProperty("dreammanhunt.mc.role", "");
	private final Path dir = Path.of(System.getProperty("dreammanhunt.mc.dir", "."));
	private final String server = System.getProperty("dreammanhunt.mc.server", "");
	private final int moveTicks = Integer.getInteger("dreammanhunt.mc.moveTicks", 20);
	private final int turnDegrees = Integer.getInteger("dreammanhunt.mc.turnDegrees", 30);

	private Field hudTickField;
	private boolean connectRequested;
	private int inWorldTicks;
	private boolean ready;
	private boolean saveSent;
	private int saveKeyRelease = -1;
	private @Nullable Window captureWindow;
	private @Nullable Window resumeWindow;
	private boolean mutateStarted;
	private int mutateTicks;
	private boolean mutated;
	private int restoreStage;
	private int restoreWait;
	private @Nullable String stateId;
	private int pendingHudAtSave = -1;
	private boolean resumePending;
	private boolean done;
	private int cutFrames;
	private boolean cutRun;

	private static final class Window {
		final String phase;
		final int base;
		final TreeMap<Integer, Boolean> shots = new TreeMap<>();

		Window(String phase, int base) {
			this.phase = phase;
			this.base = base;
			for (int offset : SHOT_OFFSETS) {
				this.shots.put(offset, false);
			}
		}
	}

	@Override
	public void onInitializeClient() {
		if (this.role.isEmpty()) {
			return;
		}
		try {
			this.hudTickField = Hud.class.getDeclaredField("tickCount");
			this.hudTickField.setAccessible(true);
			Files.createDirectories(this.dir.resolve("shots"));
		} catch (ReflectiveOperationException | IOException e) {
			throw new IllegalStateException(e);
		}
		ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.event("disconnected"));
		ClientTickEvents.END_CLIENT_TICK.register(this::endTick);
		CutMarker.addFrameListener((frame, cut, save) -> this.onFrame(frame, cut));
		if (!this.role.equals("vanilla")) {
			SavestateClientNetwork.addObserver(new SavestateClientNetwork.Observer() {
				@Override
				public void onSaveRequested() {
					MultiClientDriver.this.pendingHudAtSave = MultiClientDriver.this.hud();
					MultiClientDriver.this.event("save_requested");
				}

				@Override
				public void onCapture(String id) {
					MultiClientDriver.this.onCapture(id);
				}

				@Override
				public void onDiscard(String id) {
					MultiClientDriver.this.event("discard", "id", id);
				}

				@Override
				public void onRestoreBegin(String id) {
					MultiClientDriver.this.event("begin", "id", id);
				}

				@Override
				public void onRestoreApply(String id) {
					MultiClientDriver.this.event("apply", "id", id);
				}

				@Override
				public void onReadySent(String id) {
					MultiClientDriver.this.event("ready_sent", "id", id);
				}

				@Override
				public void onRestoreResume(String id) {
					MultiClientDriver.this.stateId = id;
					MultiClientDriver.this.resumePending = true;
					MultiClientDriver.this.event("resume_packet", "id", id);
				}
			});
		}
		this.event("boot", "role", this.role);
	}

	private int hud() {
		try {
			return this.hudTickField.getInt(Minecraft.getInstance().gui.hud);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean flag(String file) {
		return Files.exists(this.dir.resolve(file));
	}

	private void touch(String file) {
		try {
			Files.writeString(this.dir.resolve(file), this.name + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void event(String kind, Object... pairs) {
		Minecraft minecraft = Minecraft.getInstance();
		StringBuilder line = new StringBuilder("MCT|").append(this.name).append('|').append(kind)
				.append("|ms=").append(System.currentTimeMillis());
		if (minecraft.gui != null && minecraft.gui.hud != null) {
			line.append("|hud=").append(this.hud());
		}
		LocalPlayer player = minecraft.player;
		if (player != null) {
			line.append("|player_tick=").append(player.tickCount)
					.append("|game_time=").append(player.level().getGameTime())
					.append(String.format(Locale.ROOT, "|pos=%.4f,%.4f,%.4f|yaw=%.3f|slot=%d", player.getX(), player.getY(), player.getZ(),
							player.getYRot(), player.getInventory().getSelectedSlot()));
			StringBuilder others = new StringBuilder();
			for (AbstractClientPlayer other : minecraft.level.players()) {
				if (other != player) {
					others.append(others.isEmpty() ? "" : ",").append(other.getPlainTextName()).append(':').append(other.getId())
							.append(':').append(other.tickCount);
				}
			}
			line.append("|others=").append(others);
		}
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			line.append('|').append(pairs[i]).append('=').append(pairs[i + 1]);
		}
		line.append('\n');
		try {
			Files.writeString(this.dir.resolve("events-" + this.name + ".log"), line.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void onFrame(long frame, boolean cut) {
		Minecraft minecraft = Minecraft.getInstance();
		if (cut) {
			if (!this.cutRun) {
				this.cutRun = true;
				this.cutFrames = 0;
				this.event("cut_marker_on", "frame", frame, "restores", SavestateClientNetwork.restoresCompleted());
			}
			this.cutFrames++;
			if (this.cutFrames == 3) {
				this.takeShot(minecraft, "frozen", 0);
			}
		} else if (this.cutRun) {
			this.cutRun = false;
			this.event("cut_marker_off", "frame", frame, "frozen_frames", this.cutFrames, "restores", SavestateClientNetwork.restoresCompleted());
		}
	}

	private void onCapture(String id) {
		this.stateId = id;
		int base = this.pendingHudAtSave >= 0 ? this.pendingHudAtSave : this.hud();
		this.pendingHudAtSave = -1;
		this.captureWindow = new Window("cap", base);
		Minecraft.getInstance().gui.toastManager().clear();
		this.event("capture", "id", id, "base", base);
	}

	private void startTick(Minecraft minecraft) {
		if (this.resumePending) {
			this.resumePending = false;
			int base = this.hud();
			this.resumeWindow = new Window("res", base);
			minecraft.gui.toastManager().clear();
			this.event("resume", "id", this.stateId, "base", base);
		}
	}

	private void endTick(Minecraft minecraft) {
		if (this.saveKeyRelease >= 0 && --this.saveKeyRelease < 0) {
			this.pressKey(minecraft, SavestateKeys.SAVE, false);
		}
		this.connectIfNeeded(minecraft);
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		if (!this.ready) {
			if (minecraft.gui.screen() == null && ++this.inWorldTicks >= READY_DELAY_TICKS) {
				this.ready = true;
				minecraft.gui.toastManager().clear();
				this.event("ready");
				this.touch("ready-" + this.name);
			}
			return;
		}
		this.screenshots(minecraft, this.captureWindow);
		this.screenshots(minecraft, this.resumeWindow);

		if (this.role.equals("runner") && !this.saveSent && this.flag("cmd-save")) {
			this.saveSent = true;
			this.event("press_save_key");
			this.pressKey(minecraft, SavestateKeys.SAVE, true);
			this.saveKeyRelease = 1;
		}

		if (!this.mutateStarted && this.flag("cmd-mutate")) {
			this.mutateStarted = true;
			this.event("mutate_start", "move_ticks", this.moveTicks);
			player.setYRot(player.getYRot() + this.turnDegrees);
			player.getInventory().setSelectedSlot((player.getInventory().getSelectedSlot() + 1 + this.moveTicks / 20) % 9);
			minecraft.options.keyUp.setDown(true);
		}
		if (this.mutateStarted && !this.mutated) {
			this.mutateTicks++;
			if (this.mutateTicks == this.moveTicks) {
				minecraft.options.keyUp.setDown(false);
			}
			if (this.mutateTicks >= this.moveTicks + 20) {
				this.mutated = true;
				this.takeShot(minecraft, "mut", 0);
				this.event("mutated");
				this.touch("mutated-" + this.name);
			}
		}

		if (this.role.equals("runner") && this.flag("cmd-restore")) {
			this.driveRestore(minecraft);
		}

		Window resume = this.resumeWindow;
		if (!this.done && resume != null && this.hud() >= resume.base + WINDOW_TICKS) {
			this.done = true;
			this.event("done");
			this.touch("done-" + this.name);
		}
	}

	private void driveRestore(Minecraft minecraft) {
		switch (this.restoreStage) {
			case 0 -> {
				this.event("press_menu_key");
				this.pressKey(minecraft, SavestateKeys.MENU, true);
				this.restoreStage = 1;
			}
			case 1 -> {
				this.pressKey(minecraft, SavestateKeys.MENU, false);
				this.restoreStage = 2;
			}
			case 2 -> {
				if (minecraft.gui.screen() instanceof SavestateMenuScreen menu && menu.list() != null && this.stateId != null) {
					SavestateList.Entry entry = menu.list().children().stream()
							.filter(candidate -> candidate.info().id().equals(this.stateId))
							.findFirst().orElse(null);
					if (entry != null && ++this.restoreWait >= 10) {
						double x = entry.getContentX() + SavestateList.THUMB_WIDTH / 2.0;
						double y = entry.getContentY() + entry.getContentHeight() / 2.0;
						MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
						this.event("click_entry", "id", this.stateId);
						menu.mouseClicked(click, false);
						menu.mouseReleased(click);
						this.restoreStage = 3;
					}
				}
			}
			default -> {
			}
		}
	}

	private void pressKey(Minecraft minecraft, KeyMapping mapping, boolean down) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		int keycode = SDLKeyboard.SDL_GetKeyFromScancode(key.getValue(), (short) 0, false);
		minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), down ? InputConstants.PRESS : InputConstants.RELEASE,
				new KeyEvent(key.getValue(), keycode, 0));
	}

	private void connectIfNeeded(Minecraft minecraft) {
		if (this.connectRequested || this.server.isEmpty() || minecraft.level != null) {
			return;
		}
		if (!(minecraft.gui.screen() instanceof TitleScreen)) {
			return;
		}
		if (this.role.equals("late") && !this.flag("cmd-join-late")) {
			return;
		}
		this.connectRequested = true;
		this.event("connect", "server", this.server);
		ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(this.server),
				new ServerData(this.name, this.server, ServerData.Type.OTHER), false, null);
	}

	private void screenshots(Minecraft minecraft, @Nullable Window window) {
		if (window == null) {
			return;
		}
		int offset = this.hud() - window.base;
		Boolean taken = window.shots.get(offset);
		if (taken == null || taken) {
			return;
		}
		window.shots.put(offset, true);
		this.takeShot(minecraft, window.phase, offset);
	}

	private void takeShot(Minecraft minecraft, String phase, int offset) {
		Path file = this.dir.resolve("shots").resolve(this.name + "-" + phase + "-" + offset + ".png");
		this.event("screenshot", "phase", phase, "offset", offset, "file", file.getFileName());
		Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
			try (image) {
				image.writeToFile(file);
			} catch (IOException e) {
				this.event("screenshot_failed", "file", file.getFileName(), "error", e.getMessage());
			}
		}));
	}
}
