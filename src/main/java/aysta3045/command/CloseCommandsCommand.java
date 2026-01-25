package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CloseCommandsCommand {
    // 存储被关闭权限的玩家UUID
    private static final ConcurrentHashMap<UUID, Boolean> disabledPlayers = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 关闭权限命令
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("closecmds")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CloseCommandsCommand::executeClose)
                )
        );

        // 恢复单个玩家权限命令
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("restorecmds")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(CloseCommandsCommand::executeRestore)
                        )
                )
        );

        // 恢复所有玩家权限命令
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("restoreallcmds")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CloseCommandsCommand::executeRestoreAll)
                )
        );

        // 查看被禁用玩家列表命令
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("listdisabled")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CloseCommandsCommand::executeListDisabled)
                )
        );
    }

    // 检查玩家是否被禁用了指令
    public static boolean isPlayerDisabled(ServerPlayerEntity player) {
        return player != null && disabledPlayers.containsKey(player.getUuid());
    }

    // 重置所有被禁用的玩家
    public static void resetAll() {
        disabledPlayers.clear();
    }

    // 恢复指定玩家的权限
    public static boolean restorePlayer(ServerPlayerEntity player) {
        if (player != null && disabledPlayers.containsKey(player.getUuid())) {
            disabledPlayers.remove(player.getUuid());
            return true;
        }
        return false;
    }

    private static int executeClose(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        List<String> closedPlayers = new ArrayList<>();

        // 关闭其他玩家的指令权限
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            if (target != sender && !disabledPlayers.containsKey(target.getUuid())) {
                // 1. 添加到禁用列表
                disabledPlayers.put(target.getUuid(), true);

                // 2. 从OP列表中移除玩家
                source.getServer().getPlayerManager().removeFromOperators(target.getGameProfile());

                // 3. 强制重新发送命令树
                source.getServer().getPlayerManager().sendCommandTree(target);

                // 4. 发送权限更新包
                target.networkHandler.syncWithPlayerPosition();

                // 5. 给目标玩家发送通知
                target.sendMessage(
                        Text.literal("§c你的指令权限已被管理员永久关闭！")
                                .styled(style -> style.withColor(0xFF5555))
                );

                // 6. 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[权限系统] " + target.getName().getString() + " 的命令权限已被关闭")
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
                    Text.literal("§a已成功永久关闭以下玩家的指令权限：§e" + playersList)
                            .styled(style -> style.withColor(0x55FF55))
            );

            source.sendMessage(
                    Text.literal("§6这些玩家需要重新获得OP权限才能使用命令")
                            .styled(style -> style.withColor(0xFFAA00))
            );

            // 记录操作日志
            source.getServer().sendMessage(
                    Text.literal("[权限系统] " + sender.getName().getString() +
                            " 已关闭 " + playersList + " 的命令权限")
            );

            return closedPlayers.size();
        } else {
            // 如果没有其他玩家在线
            source.sendMessage(
                    Text.literal("§7当前没有其他在线玩家需要关闭权限")
                            .styled(style -> style.withColor(0xAAAAAA))
            );
            return 0;
        }
    }

    private static int executeRestore(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            // 获取目标玩家
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            // 检查不能恢复自己的权限（如果自己在禁用列表中）
            if (target == sender) {
                source.sendError(Text.literal("§c你不能恢复自己的权限！"));
                return 0;
            }

            // 检查玩家是否在禁用列表中
            if (disabledPlayers.containsKey(target.getUuid())) {
                // 1. 从禁用列表中移除
                disabledPlayers.remove(target.getUuid());

                // 2. 重新添加为OP
                source.getServer().getPlayerManager().addToOperators(target.getGameProfile());

                // 3. 强制重新发送命令树
                source.getServer().getPlayerManager().sendCommandTree(target);

                // 4. 给目标玩家发送通知
                target.sendMessage(
                        Text.literal("§a你的指令权限已被管理员恢复！")
                                .styled(style -> style.withColor(0x55FF55))
                );

                // 5. 给发送者反馈
                source.sendMessage(
                        Text.literal("§a已成功恢复玩家 §e" + target.getName().getString() + " §a的指令权限")
                                .styled(style -> style.withColor(0x55FF55))
                );

                // 6. 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[权限系统] " + sender.getName().getString() +
                                " 已恢复 " + target.getName().getString() + " 的命令权限")
                );

                return 1;
            } else {
                source.sendError(Text.literal("§c该玩家的指令权限未被关闭！"));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("§c找不到指定的玩家！"));
            return 0;
        }
    }

    private static int executeRestoreAll(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        List<String> restoredPlayers = new ArrayList<>();

        // 恢复所有在线玩家的权限
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            // 跳过执行命令的玩家自己
            if (target == sender) {
                continue;
            }

            // 检查玩家是否在禁用列表中
            if (disabledPlayers.containsKey(target.getUuid())) {
                // 1. 从禁用列表中移除
                disabledPlayers.remove(target.getUuid());

                // 2. 重新添加为OP
                source.getServer().getPlayerManager().addToOperators(target.getGameProfile());

                // 3. 强制重新发送命令树
                source.getServer().getPlayerManager().sendCommandTree(target);

                // 4. 给目标玩家发送通知
                target.sendMessage(
                        Text.literal("§a你的指令权限已被管理员恢复！")
                                .styled(style -> style.withColor(0x55FF55))
                );

                restoredPlayers.add(target.getName().getString());
            }
        }

        if (!restoredPlayers.isEmpty()) {
            // 创建玩家名字列表
            StringBuilder playersList = new StringBuilder();
            for (int i = 0; i < restoredPlayers.size(); i++) {
                if (i > 0) {
                    if (i == restoredPlayers.size() - 1) {
                        playersList.append(" 和 ");
                    } else {
                        playersList.append(", ");
                    }
                }
                playersList.append(restoredPlayers.get(i));
            }

            // 给发送者反馈
            source.sendMessage(
                    Text.literal("§a已成功恢复以下玩家的指令权限：§e" + playersList)
                            .styled(style -> style.withColor(0x55FF55))
            );

            // 记录到控制台
            source.getServer().sendMessage(
                    Text.literal("[权限系统] " + sender.getName().getString() +
                            " 已恢复 " + playersList + " 的命令权限")
            );

            return restoredPlayers.size();
        } else {
            source.sendMessage(
                    Text.literal("§7当前没有玩家被禁用指令权限")
                            .styled(style -> style.withColor(0xAAAAAA))
            );
            return 0;
        }
    }

    private static int executeListDisabled(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        // 获取所有在线玩家
        List<ServerPlayerEntity> onlinePlayers = source.getServer().getPlayerManager().getPlayerList();

        List<String> disabledOnlinePlayers = new ArrayList<>();
        List<String> disabledOfflinePlayers = new ArrayList<>();

        // 统计在线和离线的被禁用玩家
        for (UUID playerId : disabledPlayers.keySet()) {
            boolean found = false;
            for (ServerPlayerEntity player : onlinePlayers) {
                if (player.getUuid().equals(playerId)) {
                    disabledOnlinePlayers.add(player.getName().getString());
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 离线玩家，我们无法获取名字，使用UUID代替
                disabledOfflinePlayers.add(playerId.toString().substring(0, 8) + "...");
            }
        }

        if (disabledOnlinePlayers.isEmpty() && disabledOfflinePlayers.isEmpty()) {
            source.sendMessage(
                    Text.literal("§7当前没有被禁用指令权限的玩家")
                            .styled(style -> style.withColor(0xAAAAAA))
            );
            return 0;
        }

        // 显示在线被禁用玩家
        if (!disabledOnlinePlayers.isEmpty()) {
            StringBuilder onlineList = new StringBuilder();
            for (int i = 0; i < disabledOnlinePlayers.size(); i++) {
                if (i > 0) {
                    onlineList.append(", ");
                }
                onlineList.append(disabledOnlinePlayers.get(i));
            }

            source.sendMessage(
                    Text.literal("§6在线被禁用玩家 (§e" + disabledOnlinePlayers.size() + "§6): §a" + onlineList)
                            .styled(style -> style.withColor(0xFFAA00))
            );
        }

        // 显示离线被禁用玩家
        if (!disabledOfflinePlayers.isEmpty()) {
            source.sendMessage(
                    Text.literal("§6离线被禁用玩家 (§e" + disabledOfflinePlayers.size() + "§6)")
                            .styled(style -> style.withColor(0xFFAA00))
            );

            // 如果离线玩家数量不多，显示完整UUID
            if (disabledOfflinePlayers.size() <= 5) {
                for (String uuid : disabledOfflinePlayers) {
                    source.sendMessage(
                            Text.literal("  §7- " + uuid)
                                    .styled(style -> style.withColor(0x888888))
                    );
                }
            } else {
                source.sendMessage(
                        Text.literal("  §7(太多离线玩家)")
                                .styled(style -> style.withColor(0x888888))
                );
            }
        }

        source.sendMessage(
                Text.literal("§6使用 §e/competition restorecmds <玩家名> §6恢复单个玩家权限")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        source.sendMessage(
                Text.literal("§6使用 §e/competition restoreallcmds §6恢复所有玩家权限")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        return disabledOnlinePlayers.size() + disabledOfflinePlayers.size();
    }
}