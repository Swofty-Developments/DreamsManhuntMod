package net.swofty.dreammanhunt.server.coordinator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.swofty.dreammanhunt.api.SavestateInfo;

import java.util.List;
import java.util.stream.Stream;

import static net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator.tr;

public final class SavestateCommands {
	private static final SuggestionProvider<CommandSourceStack> STATES = (context, builder) -> {
		List<SavestateInfo> states = SavestateCoordinator.get(context.getSource().getServer()).listStates();
		return SharedSuggestionProvider.suggest(states.stream().flatMap(info -> Stream.of(info.name(), info.id())), builder);
	};

	private SavestateCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("savestate")
				.then(Commands.literal("save")
						.requires(SavestatePermissions::canManage)
						.executes(context -> save(context, ""))
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(context -> save(context, StringArgumentType.getString(context, "name")))))
				.then(Commands.literal("load")
						.requires(SavestatePermissions::canManage)
						.then(Commands.argument("state", StringArgumentType.greedyString())
								.suggests(STATES)
								.executes(context -> load(context, StringArgumentType.getString(context, "state")))))
				.then(Commands.literal("delete")
						.requires(SavestatePermissions::canManage)
						.then(Commands.argument("state", StringArgumentType.greedyString())
								.suggests(STATES)
								.executes(context -> delete(context, StringArgumentType.getString(context, "state")))))
				.then(Commands.literal("list")
						.executes(SavestateCommands::list)));
	}

	private static SavestateCoordinator coordinator(CommandContext<CommandSourceStack> context) {
		return SavestateCoordinator.get(context.getSource().getServer());
	}

	private static int save(CommandContext<CommandSourceStack> context, String name) {
		return coordinator(context).save(Requester.of(context.getSource()), name).isPresent() ? 1 : 0;
	}

	private static int load(CommandContext<CommandSourceStack> context, String state) {
		return coordinator(context).restore(Requester.of(context.getSource()), state) ? 1 : 0;
	}

	private static int delete(CommandContext<CommandSourceStack> context, String state) {
		return coordinator(context).delete(Requester.of(context.getSource()), state) ? 1 : 0;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		List<SavestateInfo> states = coordinator(context).listStates();
		if (states.isEmpty()) {
			source.sendSuccess(() -> tr("dreammanhunt.command.list.empty"), false);
			return 0;
		}
		source.sendSuccess(() -> tr("dreammanhunt.command.list.header", states.size()), false);
		for (SavestateInfo info : states.reversed()) {
			Component line = Component.literal(" - ")
					.append(Component.literal(info.name()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + info.id() + ") ").withStyle(ChatFormatting.DARK_GRAY))
					.append(tr("dreammanhunt.command.list.entry", info.creatorName().isEmpty() ? "-" : info.creatorName(),
							info.dayTime() / 24000L + 1).copy().withStyle(ChatFormatting.GRAY));
			source.sendSuccess(() -> line, false);
		}
		return states.size();
	}
}
