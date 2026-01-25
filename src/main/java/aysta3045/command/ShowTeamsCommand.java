package aysta3045.command;

import aysta3045.util.FTBTeamsHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ShowTeamsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("showteams")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ShowTeamsCommand::execute)
                )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        // 检查并发送队伍信息
        if (FTBTeamsHelper.checkAndSendTeamInfo(sender)) {
            source.sendMessage(
                    Text.literal("§a已成功广播队伍分组信息！")
                            .styled(style -> style.withColor(0x55FF55))
            );
            return 1;
        } else {
            source.sendError(Text.literal("§cFTB Teams模组安装有误或未找到队伍信息！"));
            return 0;
        }
    }
}