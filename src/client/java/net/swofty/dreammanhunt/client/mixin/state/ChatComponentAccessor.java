package net.swofty.dreammanhunt.client.mixin.state;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("chatScrollbarPos")
	int dm$getChatScrollbarPos();

	@Accessor("chatScrollbarPos")
	void dm$setChatScrollbarPos(int value);

	@Accessor("newMessageSinceScroll")
	boolean dm$getNewMessageSinceScroll();

	@Accessor("newMessageSinceScroll")
	void dm$setNewMessageSinceScroll(boolean value);

	@Accessor("allMessages")
	List<GuiMessage> dm$getAllMessages();
}
