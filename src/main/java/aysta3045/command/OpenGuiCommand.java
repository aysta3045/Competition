package aysta3045.command;

import aysta3045.screen.CompetitionScreenHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;

public class OpenGuiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("gui")
                        .executes(OpenGuiCommand::openGui)
                )
        );
    }

    private static int openGui(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        // 打开 GUI
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, playerEntity) -> new CompetitionScreenHandler(syncId, inventory),
                Text.literal("比赛管理界面")  // 直接使用文本，避免翻译问题
        ));

        return 1;
    }
}