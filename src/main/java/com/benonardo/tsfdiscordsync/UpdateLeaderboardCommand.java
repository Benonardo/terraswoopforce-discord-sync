package com.benonardo.tsfdiscordsync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class UpdateLeaderboardCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager
                .literal("updateleaderboard")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .executes(UpdateLeaderboardCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        TSFDiscordSync.updateMessage(context.getSource().getMinecraftServer().getScoreboard());

        return 1;
    }

}
