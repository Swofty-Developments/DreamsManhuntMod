package net.swofty.dreammanhunt.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.client.network.SavestateClientNetwork;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class SavestateMenuScreen extends Screen {
	private static final int HEADER_HEIGHT = 44;
	private static final int FOOTER_HEIGHT = 40;
	private static final long NOTICE_MILLIS = 6000L;
	private static final int COLOR_MUTED = 0xFFA0A0A0;
	private static final int COLOR_WARNING = 0xFFFFD45C;
	private static final int COLOR_ERROR = 0xFFFF6B6B;

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private @Nullable SavestateList list;
	private @Nullable Button saveButton;
	private @Nullable Component notice;
	private long noticeUntil;
	private int seenListVersion = -1;

	public SavestateMenuScreen() {
		super(Component.translatable("dreammanhunt.menu.title"));
	}

	@Override
	protected void init() {
		this.list = this.layout.addToContents(new SavestateList(this.minecraft, this, this.width, this.layout.getContentHeight()));
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.saveButton = footer.addChild(Button.builder(Component.translatable("dreammanhunt.menu.save_new"), button -> this.saveNew()).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		if (this.seenListVersion == -1) {
			SavestateClientNetwork.requestList();
		}
		this.seenListVersion = -2;
		this.onStateList();
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
		this.layout.arrangeElements();
	}

	public boolean canManage() {
		return SavestateClientNetwork.canManage();
	}

	public boolean hasList() {
		return SavestateClientNetwork.states() != null;
	}

	public @Nullable SavestateList list() {
		return this.list;
	}

	public void onStateList() {
		int version = SavestateClientNetwork.listVersion();
		if (version == this.seenListVersion || this.list == null) {
			return;
		}
		this.seenListVersion = version;
		List<SavestateInfo> states = SavestateClientNetwork.states();
		this.list.setStates(states == null ? List.of() : states);
		if (this.saveButton != null) {
			this.saveButton.active = this.canManage();
			this.saveButton.setTooltip(this.canManage() || states == null ? null : Tooltip.create(Component.translatable("dreammanhunt.menu.read_only")));
		}
	}

	public void showNotice(Component text) {
		this.notice = text;
		this.noticeUntil = Util.getMillis() + NOTICE_MILLIS;
	}

	@Override
	public void tick() {
		this.onStateList();
	}

	private void saveNew() {
		this.minecraft.gui.setScreen(null);
		SavestateKeys.scheduleSave("");
	}

	public void restore(SavestateInfo info) {
		this.minecraft.gui.setScreen(null);
		SavestateClientNetwork.requestRestore(info.id());
	}

	public void confirmDelete(SavestateInfo info) {
		this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				SavestateClientNetwork.requestDelete(info.id());
			}
			this.minecraft.gui.setScreen(this);
		}, Component.translatable("dreammanhunt.menu.delete.title"), Component.translatable("dreammanhunt.menu.delete.message", info.name()),
				Component.translatable("dreammanhunt.menu.delete"), CommonComponents.GUI_CANCEL));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (SavestateKeys.MENU.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 11, 0xFFFFFFFF);
		int subtitleY = 26;
		Component subtitle;
		int color;
		if (this.notice != null && Util.getMillis() < this.noticeUntil) {
			subtitle = this.notice;
			color = COLOR_ERROR;
		} else if (!this.hasList()) {
			subtitle = Component.translatable("dreammanhunt.menu.loading");
			color = COLOR_MUTED;
		} else if (!this.canManage()) {
			subtitle = Component.translatable("dreammanhunt.menu.read_only");
			color = COLOR_WARNING;
		} else {
			subtitle = Component.translatable("dreammanhunt.menu.hint");
			color = COLOR_MUTED;
		}
		graphics.centeredText(this.font, subtitle, this.width / 2, subtitleY, color);

		if (this.list != null && this.list.isEmpty() && this.hasList()) {
			int centerY = this.layout.getHeaderHeight() + this.layout.getContentHeight() / 2;
			graphics.centeredText(this.font, Component.translatable("dreammanhunt.menu.empty"), this.width / 2, centerY - 10, 0xFFFFFFFF);
			graphics.centeredText(this.font, Component.translatable("dreammanhunt.menu.empty.hint", SavestateKeys.SAVE.getTranslatedKeyMessage()),
					this.width / 2, centerY + 4, COLOR_MUTED);
		}
	}

	@Override
	public void removed() {
		ThumbnailManager.releaseAll();
	}
}
