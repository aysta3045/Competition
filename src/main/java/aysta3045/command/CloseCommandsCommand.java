package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class CloseCommandsCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("closecmds")
                        .requires(source -> source.hasPermissionLevel(2)) // 需要OP权限(等级2)
                        .executes(CloseCommandsCommand::execute)
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

        List<String> closedPlayers = new ArrayList<>();

        // 关闭其他玩家的指令权限
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            if (target != sender) {
                // 重新发送命令树，这会使玩家需要重新获得OP权限才能使用命令
                source.getServer().getPlayerManager().sendCommandTree(target);

                // 给目标玩家发送通知
                target.sendMessage(
                        Text.literal("§c你的指令权限已被管理员关闭！")
                                .styled(style -> style.withColor(0xFF5555))
                );

                closedPlayers.add(target.getName().getString());
            }
        }

        if (!closedPlayers.isEmpty()) {
            // 创建玩家名字列表
            StringBuilder playersList = new StringBuilder();
            for (int i = 0; i < closedPlayers.size(); i++) {
                if (i > 0) {
                    if (i == closedPlayers.size() - 1) {
                        playersList.append(" 和 ");
                    } else {
                        playersList.append(", ");
                    }
                }
                playersList.append(closedPlayers.get(i));
            }

            // 给发送者反馈
            source.sendMessage(
                    Text.literal("§a已成功关闭以下玩家的指令权限：§e" + playersList.toString())
                            .styled(style -> style.withColor(0x55FF55))
            );


            return 1;
        } else {
            // 如果没有其他玩家在线
            source.sendMessage(
                    Text.literal("§7当前没有其他在线玩家，无需关闭权限")
                            .styled(style -> style.withColor(0xAAAAAA))
            );
            return 0;
        }
    }
}