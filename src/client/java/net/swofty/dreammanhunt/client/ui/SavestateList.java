package net.swofty.dreammanhunt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.swofty.dreammanhunt.api.SavestateInfo;

import java.util.Comparator;
import java.util.List;

public final class SavestateList extends ObjectSelectionList<SavestateList.Entry> {
	public static final int ITEM_HEIGHT = 44;
	public static final int THUMB_WIDTH = 64;
	public static final int THUMB_HEIGHT = 36;
	private static final Identifier JOIN_SPRITE = Identifier.withDefaultNamespace("world_list/join");
	private static final Identifier JOIN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("world_list/join_highlighted");
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFA8A8A8;
	private static final int TEXT_TERTIARY = 0xFF808080;

	private final SavestateMenuScreen screen;

	public SavestateList(Minecraft minecraft, SavestateMenuScreen screen, int width, int height) {
		super(minecraft, width, height, 0, ITEM_HEIGHT);
		this.screen = screen;
	}

	public void setStates(List<SavestateInfo> states) {
		String selectedId = this.getSelected() == null ? null : this.getSelected().info.id();
		double scroll = this.scrollAmount();
		this.clearEntries();
		states.stream()
				.sorted(Comparator.comparingLong(SavestateInfo::createdAtEpochMillis).reversed())
				.forEach(info -> this.addEntry(new Entry(info)));
		this.setScrollAmount(scroll);
		if (selectedId != null) {
			this.children().stream().filter(entry -> entry.info.id().equals(selectedId)).findFirst().ifPresent(this::setSelected);
		}
	}

	public boolean isEmpty() {
		return this.children().isEmpty();
	}

	@Override
	public int getRowWidth() {
		return Math.min(360, this.width - 28);
	}

	@Override
	protected void extractItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Entry entry) {
		if (entry == this.getHovered() && entry != this.getSelected() && this.screen.canManage()) {
			this.extractSelection(graphics, entry, 0xFF606060);
		}
		super.extractItem(graphics, mouseX, mouseY, a, entry);
	}

	public final class Entry extends ObjectSelectionList.Entry<Entry> {
		private final SavestateInfo info;
		private final Button deleteButton;

		Entry(SavestateInfo info) {
			this.info = info;
			this.deleteButton = Button.builder(Component.translatable("dreammanhunt.menu.delete"), button -> SavestateList.this.screen.confirmDelete(info))
					.size(SavestateList.this.minecraft.font.width(Component.translatable("dreammanhunt.menu.delete")) + 12, 20)
					.build();
		}

		public SavestateInfo info() {
			return this.info;
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", Component.translatable("dreammanhunt.menu.narration",
					this.info.name(), SavestateFormatting.relativeTime(this.info.createdAtEpochMillis(), Util.getEpochMillis()),
					SavestateFormatting.inGameTime(this.info), SavestateFormatting.creator(this.info)));
		}

		private boolean showDelete() {
			return SavestateList.this.screen.canManage();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			Font font = SavestateList.this.minecraft.font;
			int x = this.getContentX();
			int y = this.getContentY();
			int thumbY = y + (this.getContentHeight() - THUMB_HEIGHT) / 2;

			graphics.fill(x - 1, thumbY - 1, x + THUMB_WIDTH + 1, thumbY + THUMB_HEIGHT + 1, 0xFF000000);
			Identifier texture = ThumbnailManager.texture(this.info.id());
			if (texture != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, thumbY, 0.0F, 0.0F, THUMB_WIDTH, THUMB_HEIGHT,
						ThumbnailManager.WIDTH, ThumbnailManager.HEIGHT, ThumbnailManager.WIDTH, ThumbnailManager.HEIGHT);
			} else {
				graphics.fill(x, thumbY, x + THUMB_WIDTH, thumbY + THUMB_HEIGHT, 0xFF1E1E1E);
				graphics.centeredText(font, Component.translatable("dreammanhunt.menu.no_preview"), x + THUMB_WIDTH / 2, thumbY + (THUMB_HEIGHT - 8) / 2, TEXT_TERTIARY);
			}

			int right = this.getContentRight();
			if (this.showDelete()) {
				this.deleteButton.setPosition(right - this.deleteButton.getWidth() - 2, y + (this.getContentHeight() - 20) / 2);
				this.deleteButton.extractRenderState(graphics, mouseX, mouseY, a);
				right = this.deleteButton.getX() - 4;
			}

			boolean overDelete = this.showDelete() && this.deleteButton.isMouseOver(mouseX, mouseY);
			if (hovered && SavestateList.this.screen.canManage() && !overDelete) {
				graphics.fill(x, thumbY, x + THUMB_WIDTH, thumbY + THUMB_HEIGHT, 0xA0909090);
				boolean overThumb = mouseX >= x && mouseX < x + THUMB_WIDTH && mouseY >= thumbY && mouseY < thumbY + THUMB_HEIGHT;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overThumb ? JOIN_HIGHLIGHTED_SPRITE : JOIN_SPRITE,
						x + (THUMB_WIDTH - 32) / 2, thumbY + (THUMB_HEIGHT - 32) / 2, 32, 32);
			}

			int textX = x + THUMB_WIDTH + 6;
			int maxWidth = Math.max(10, right - textX);
			int textY = y + (this.getContentHeight() - 32) / 2;
			Component name = Component.literal(this.info.name());
			this.text(graphics, font, name, textX, textY, maxWidth, TEXT_PRIMARY);
			Component when = Component.translatable("dreammanhunt.menu.when",
					SavestateFormatting.relativeTime(this.info.createdAtEpochMillis(), Util.getEpochMillis()),
					SavestateFormatting.inGameTime(this.info));
			this.text(graphics, font, when, textX, textY + 12, maxWidth, TEXT_SECONDARY);
			Component who = Component.translatable("dreammanhunt.menu.who", SavestateFormatting.creator(this.info), SavestateFormatting.dimension(this.info));
			this.text(graphics, font, who, textX, textY + 23, maxWidth, TEXT_TERTIARY);

			if (hovered && font.width(name) > maxWidth && mouseX >= textX && mouseY < textY + 10) {
				graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
			}
		}

		private void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
			if (font.width(text) <= maxWidth) {
				graphics.text(font, text, x, y, color, false);
				return;
			}
			int ellipsis = font.width("...");
			FormattedText clipped = font.substrByWidth(text, Math.max(0, maxWidth - ellipsis));
			graphics.text(font, clipped.getString() + "...", x, y, color, false);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			SavestateList.this.setSelected(this);
			if (!SavestateList.this.screen.canManage()) {
				return true;
			}
			if (this.deleteButton.mouseClicked(event, doubleClick)) {
				return true;
			}
			SavestateList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			SavestateList.this.screen.restore(this.info);
			return true;
		}
	}
}
