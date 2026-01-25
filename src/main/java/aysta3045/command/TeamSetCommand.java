package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamSetCommand {

    // 存储玩家所在组的映射: UUID -> 组颜色
    private static final ConcurrentHashMap<UUID, String> playerTeams = new ConcurrentHashMap<>();

    // 定义7个组的颜色和显示名称
    public static final Map<String, TeamInfo> TEAM_COLORS = new LinkedHashMap<>();

    static {
        // 初始化7个组的颜色
        TEAM_COLORS.put("red", new TeamInfo("红色", 0xFF5555, "§c"));
        TEAM_COLORS.put("orange", new TeamInfo("橙色", 0xFFAA00, "§6"));
        TEAM_COLORS.put("yellow", new TeamInfo("黄色", 0xFFFF55, "§e"));
        TEAM_COLORS.put("green", new TeamInfo("绿色", 0x55FF55, "§a"));
        TEAM_COLORS.put("cyan", new TeamInfo("青色", 0x55FFFF, "§b"));
        TEAM_COLORS.put("blue", new TeamInfo("蓝色", 0x5555FF, "§9"));
        TEAM_COLORS.put("purple", new TeamInfo("紫色", 0xFF55FF, "§d"));
    }

    // 团队信息类
    private static class TeamInfo {
        private final String displayName;
        private final int color;
        private final String colorCode;

        public TeamInfo(String displayName, int color, String colorCode) {
            this.displayName = displayName;
            this.color = color;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 设置单个玩家分组命令（保持向后兼容）
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("teamset")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("color", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    // 提供颜色建议
                                    for (String color : TEAM_COLORS.keySet()) {
                                        builder.suggest(color);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(TeamSetCommand::executeSingleTeamSet)
                                )
                                // 添加多玩家支持
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(TeamSetCommand::executeMultiTeamSet)
                                )
                        )
                )
                // 查看分组列表命令
                .then(CommandManager.literal("teamlist")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(TeamSetCommand::executeTeamList)
                )
                // 清除所有分组命令
                .then(CommandManager.literal("teamclear")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(TeamSetCommand::executeTeamClear)
                )
                // 从分组中移除单个玩家
                .then(CommandManager.literal("teamremove")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(TeamSetCommand::executeSingleTeamRemove)
                        )
                        // 添加多玩家移除支持
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(TeamSetCommand::executeMultiTeamRemove)
                        )
                )
                // 查看特定玩家所在分组
                .then(CommandManager.literal("teamcheck")
                        .requires(source -> source.hasPermissionLevel(0))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(TeamSetCommand::executeTeamCheck)
                        )
                )
        );
    }

    // 为单个玩家设置分组（保持向后兼容）
    private static int executeSingleTeamSet(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            // 获取参数
            String color = StringArgumentType.getString(context, "color").toLowerCase();
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            return setPlayerTeam(source, sender, target, color);
        } catch (Exception e) {
            source.sendError(Text.literal("§c执行命令时出错: " + e.getMessage()));
            return 0;
        }
    }

    // 为多个玩家设置分组
    private static int executeMultiTeamSet(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            // 获取参数
            String color = StringArgumentType.getString(context, "color").toLowerCase();
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "players");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            // 检查颜色是否有效
            if (!TEAM_COLORS.containsKey(color)) {
                StringBuilder validColors = new StringBuilder();
                for (String c : TEAM_COLORS.keySet()) {
                    if (!validColors.isEmpty()) {
                        validColors.append(", ");
                    }
                    validColors.append(c);
                }
                source.sendError(Text.literal("§c无效的颜色！有效颜色: " + validColors));
                return 0;
            }

            // 获取队伍信息
            TeamInfo teamInfo = TEAM_COLORS.get(color);

            // 统计信息
            List<String> successfullySet = new ArrayList<>();
            List<String> alreadyInTeam = new ArrayList<>();
            List<String> movedFromOtherTeam = new ArrayList<>();

            // 遍历所有目标玩家
            for (ServerPlayerEntity target : targets) {
                // 检查玩家是否已经在当前组
                String previousTeam = playerTeams.get(target.getUuid());

                if (color.equals(previousTeam)) {
                    // 玩家已经在当前组
                    alreadyInTeam.add(target.getName().getString());
                    continue;
                }

                // 更新分组
                playerTeams.put(target.getUuid(), color);

                // 给目标玩家发送通知
                String teamColorCode = teamInfo.getColorCode();
                target.sendMessage(
                        Text.literal(teamColorCode + "你已被分配到" + teamInfo.getDisplayName() + "组！")
                );

                // 记录信息
                successfullySet.add(target.getName().getString());

                if (previousTeam != null) {
                    // 如果之前有分组，记录从哪个组移动过来
                    TeamInfo prevTeamInfo = TEAM_COLORS.get(previousTeam);
                    movedFromOtherTeam.add(target.getName().getString() + " (" + prevTeamInfo.getDisplayName() + "→" + teamInfo.getDisplayName() + ")");
                }

                // 更新玩家显示名称
                updatePlayerDisplayName(target, teamInfo);
            }

            // 给发送者反馈
            if (!successfullySet.isEmpty()) {
                // 创建玩家名字列表
                StringBuilder playersList = new StringBuilder();
                for (int i = 0; i < successfullySet.size(); i++) {
                    if (i > 0) {
                        playersList.append(", ");
                    }
                    playersList.append(successfullySet.get(i));
                }

                String colorCode = teamInfo.getColorCode();
                source.sendMessage(
                        Text.literal("§a已将 " + successfullySet.size() + " 名玩家分配到" + colorCode + teamInfo.getDisplayName() + "§a组: §e" + playersList)
                );

                // 如果有玩家从其他组移动过来
                if (!movedFromOtherTeam.isEmpty()) {
                    source.sendMessage(
                            Text.literal("§6其中 " + movedFromOtherTeam.size() + " 名玩家从其他组移动过来")
                    );
                }

                // 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[分组系统] " + sender.getName().getString() +
                                " 将 " + successfullySet.size() + " 名玩家分配到" +
                                teamInfo.getDisplayName() + "组: " + playersList)
                );
            }

            // 如果有玩家已经在当前组
            if (!alreadyInTeam.isEmpty()) {
                StringBuilder alreadyList = new StringBuilder();
                for (int i = 0; i < alreadyInTeam.size(); i++) {
                    if (i > 0) {
                        alreadyList.append(", ");
                    }
                    alreadyList.append(alreadyInTeam.get(i));
                }

                source.sendMessage(
                        Text.literal("§7以下玩家已在" + teamInfo.getDisplayName() + "组: §e" + alreadyList)
                );
            }

            // 返回成功设置的数量
            return successfullySet.size();
        } catch (Exception e) {
            source.sendError(Text.literal("§c执行命令时出错: " + e.getMessage()));
            return 0;
        }
    }

    // 为单个玩家设置分组的辅助方法
    private static int setPlayerTeam(ServerCommandSource source, ServerPlayerEntity sender, ServerPlayerEntity target, String color) {
        // 检查颜色是否有效
        if (!TEAM_COLORS.containsKey(color)) {
            StringBuilder validColors = new StringBuilder();
            for (String c : TEAM_COLORS.keySet()) {
                if (!validColors.isEmpty()) {
                    validColors.append(", ");
                }
                validColors.append(c);
            }
            source.sendError(Text.literal("§c无效的颜色！有效颜色: " + validColors));
            return 0;
        }

        // 获取队伍信息
        TeamInfo teamInfo = TEAM_COLORS.get(color);

        // 检查玩家是否已经在当前组
        String previousTeam = playerTeams.get(target.getUuid());

        if (color.equals(previousTeam)) {
            source.sendMessage(
                    Text.literal("§7玩家 " + target.getName().getString() + " 已经在" + teamInfo.getDisplayName() + "组")
            );
            return 0;
        }

        // 更新分组
        playerTeams.put(target.getUuid(), color);

        // 给目标玩家发送通知
        String teamColorCode = teamInfo.getColorCode();
        target.sendMessage(
                Text.literal(teamColorCode + "你已被分配到" + teamInfo.getDisplayName() + "组！")
        );

        // 给发送者反馈
        if (previousTeam != null) {
            // 如果之前有分组
            TeamInfo prevTeamInfo = TEAM_COLORS.get(previousTeam);
            source.sendMessage(
                    Text.literal("§a已将玩家 §e" + target.getName().getString() +
                            " §a从" + prevTeamInfo.getDisplayName() +
                            "组移动到" + teamInfo.getDisplayName() + "组")
            );

            // 记录到控制台
            source.getServer().sendMessage(
                    Text.literal("[分组系统] " + sender.getName().getString() +
                            " 将 " + target.getName().getString() + " 从" +
                            prevTeamInfo.getDisplayName() + "组移动到" +
                            teamInfo.getDisplayName() + "组")
            );
        } else {
            // 如果之前没有分组
            source.sendMessage(
                    Text.literal("§a已将玩家 §e" + target.getName().getString() +
                            " §a分配到" + teamInfo.getDisplayName() + "组")
            );

            // 记录到控制台
            source.getServer().sendMessage(
                    Text.literal("[分组系统] " + sender.getName().getString() +
                            " 将 " + target.getName().getString() + " 分配到" +
                            teamInfo.getDisplayName() + "组")
            );
        }

        // 更新玩家显示名称
        updatePlayerDisplayName(target, teamInfo);

        return 1;
    }

    private static int executeTeamList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        // 统计每个组的人数
        Map<String, List<String>> teamPlayers = new LinkedHashMap<>();

        // 初始化所有组
        for (String color : TEAM_COLORS.keySet()) {
            teamPlayers.put(color, new ArrayList<>());
        }
        teamPlayers.put("未分组", new ArrayList<>()); // 添加未分组类别

        // 遍历所有在线玩家
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            String team = playerTeams.get(player.getUuid());
            if (team != null && TEAM_COLORS.containsKey(team)) {
                teamPlayers.get(team).add(player.getName().getString());
            } else {
                teamPlayers.get("未分组").add(player.getName().getString());
            }
        }

        // 显示分组列表
        source.sendMessage(
                Text.literal("§6========== 分组列表 ==========")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        boolean hasPlayers = false;
        for (Map.Entry<String, TeamInfo> entry : TEAM_COLORS.entrySet()) {
            String color = entry.getKey();
            TeamInfo teamInfo = entry.getValue();
            List<String> players = teamPlayers.get(color);

            if (!players.isEmpty()) {
                hasPlayers = true;
                // 创建玩家名字列表
                StringBuilder playersList = new StringBuilder();
                for (int i = 0; i < players.size(); i++) {
                    if (i > 0) {
                        playersList.append(", ");
                    }
                    playersList.append(players.get(i));
                }

                String colorCode = teamInfo.getColorCode();
                source.sendMessage(
                        Text.literal(colorCode + teamInfo.getDisplayName() +
                                "组 (§e" + players.size() + colorCode + "): §f" + playersList)
                );
            }
        }

        // 显示未分组玩家
        List<String> ungroupedPlayers = teamPlayers.get("未分组");
        if (!ungroupedPlayers.isEmpty()) {
            hasPlayers = true;
            StringBuilder playersList = new StringBuilder();
            for (int i = 0; i < ungroupedPlayers.size(); i++) {
                if (i > 0) {
                    playersList.append(", ");
                }
                playersList.append(ungroupedPlayers.get(i));
            }

            source.sendMessage(
                    Text.literal("§7未分组 (§e" + ungroupedPlayers.size() + "§7): §f" + playersList)
            );
        }

        if (!hasPlayers) {
            source.sendMessage(Text.literal("§7当前没有在线玩家"));
        }

        // 显示总人数
        int totalPlayers = source.getServer().getPlayerManager().getCurrentPlayerCount();
        source.sendMessage(
                Text.literal("§6总计: §e" + totalPlayers + " §6名在线玩家")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        source.sendMessage(
                Text.literal("§6使用 §e/competition teamset <颜色> <玩家> §6设置单个玩家分组")
                        .styled(style -> style.withColor(0xFFAA00))
        );
        source.sendMessage(
                Text.literal("§6使用 §e/competition teamset <颜色> @p @a @r §6设置多个玩家分组")
                        .styled(style -> style.withColor(0xFFAA00))
        );
        source.sendMessage(
                Text.literal("§6使用 §e/competition teamremove <玩家> §6从分组中移除")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        return 1;
    }

    private static int executeTeamClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        // 获取清除前的分组数量
        int clearedCount = playerTeams.size();

        if (clearedCount == 0) {
            source.sendMessage(Text.literal("§7当前没有设置任何分组"));
            return 0;
        }

        // 清除所有分组
        playerTeams.clear();

        // 通知所有玩家
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("§6你的分组已被管理员清除"));
            // 恢复玩家默认显示名称
            resetPlayerDisplayName(player);
        }

        // 给发送者反馈
        source.sendMessage(
                Text.literal("§a已清除所有分组，共 §e" + clearedCount + " §a名玩家")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[分组系统] " + sender.getName().getString() +
                        " 清除了所有分组，共 " + clearedCount + " 名玩家")
        );

        return clearedCount;
    }

    // 从分组中移除单个玩家
    private static int executeSingleTeamRemove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            return removePlayerFromTeam(source, sender, target);
        } catch (Exception e) {
            source.sendError(Text.literal("§c找不到指定的玩家！"));
            return 0;
        }
    }

    // 从分组中移除多个玩家
    private static int executeMultiTeamRemove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "players");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            // 统计信息
            List<String> successfullyRemoved = new ArrayList<>();
            List<String> notInTeam = new ArrayList<>();

            // 遍历所有目标玩家
            for (ServerPlayerEntity target : targets) {
                // 检查玩家是否有分组
                String previousTeam = playerTeams.remove(target.getUuid());

                if (previousTeam != null) {
                    // 恢复玩家默认显示名称
                    resetPlayerDisplayName(target);

                    // 获取之前的队伍信息
                    TeamInfo prevTeamInfo = TEAM_COLORS.get(previousTeam);

                    // 给目标玩家发送通知
                    target.sendMessage(Text.literal("§6你已从" + prevTeamInfo.getDisplayName() + "组中移除"));

                    // 记录信息
                    successfullyRemoved.add(target.getName().getString());
                } else {
                    // 玩家没有分组
                    notInTeam.add(target.getName().getString());
                }
            }

            // 给发送者反馈
            if (!successfullyRemoved.isEmpty()) {
                // 创建玩家名字列表
                StringBuilder playersList = new StringBuilder();
                for (int i = 0; i < successfullyRemoved.size(); i++) {
                    if (i > 0) {
                        playersList.append(", ");
                    }
                    playersList.append(successfullyRemoved.get(i));
                }

                source.sendMessage(
                        Text.literal("§a已从分组中移除 §e" + successfullyRemoved.size() + " §a名玩家: " + playersList)
                );

                // 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[分组系统] " + sender.getName().getString() +
                                " 从分组中移除了 " + successfullyRemoved.size() + " 名玩家: " + playersList)
                );
            }

            // 如果有玩家没有分组
            if (!notInTeam.isEmpty()) {
                StringBuilder notInTeamList = new StringBuilder();
                for (int i = 0; i < notInTeam.size(); i++) {
                    if (i > 0) {
                        notInTeamList.append(", ");
                    }
                    notInTeamList.append(notInTeam.get(i));
                }

                source.sendMessage(
                        Text.literal("§7以下玩家没有分组: §e" + notInTeamList)
                );
            }

            // 返回成功移除的数量
            return successfullyRemoved.size();
        } catch (Exception e) {
            source.sendError(Text.literal("§c执行命令时出错: " + e.getMessage()));
            return 0;
        }
    }

    // 从分组中移除玩家的辅助方法
    private static int removePlayerFromTeam(ServerCommandSource source, ServerPlayerEntity sender, ServerPlayerEntity target) {
        // 检查玩家是否有分组
        String previousTeam = playerTeams.remove(target.getUuid());

        if (previousTeam != null) {
            // 恢复玩家默认显示名称
            resetPlayerDisplayName(target);

            // 获取之前的队伍信息
            TeamInfo prevTeamInfo = TEAM_COLORS.get(previousTeam);

            // 给目标玩家发送通知
            target.sendMessage(Text.literal("§6你已从" + prevTeamInfo.getDisplayName() + "组中移除"));

            // 给发送者反馈
            source.sendMessage(
                    Text.literal("§a已将玩家 §e" + target.getName().getString() +
                            " §a从" + prevTeamInfo.getDisplayName() + "组中移除")
            );

            // 记录到控制台
            source.getServer().sendMessage(
                    Text.literal("[分组系统] " + sender.getName().getString() +
                            " 将 " + target.getName().getString() + " 从" +
                            prevTeamInfo.getDisplayName() + "组中移除")
            );

            return 1;
        } else {
            source.sendError(Text.literal("§c该玩家没有分组！"));
            return 0;
        }
    }

    private static int executeTeamCheck(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            // 检查玩家分组
            String team = playerTeams.get(target.getUuid());

            if (team != null) {
                TeamInfo teamInfo = TEAM_COLORS.get(team);
                String colorCode = teamInfo.getColorCode();

                if (sender == target) {
                    // 查看自己的分组
                    source.sendMessage(
                            Text.literal(colorCode + "你当前在" + teamInfo.getDisplayName() + "组")
                    );
                } else {
                    // 查看别人的分组
                    source.sendMessage(
                            Text.literal(colorCode + "玩家 " + target.getName().getString() +
                                    " 当前在" + teamInfo.getDisplayName() + "组")
                    );
                }
            } else {
                if (sender == target) {
                    source.sendMessage(Text.literal("§7你当前没有分组"));
                } else {
                    source.sendMessage(
                            Text.literal("§7玩家 " + target.getName().getString() + " 当前没有分组")
                    );
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("§c找不到指定的玩家！"));
            return 0;
        }
    }

    /**
     * 更新玩家显示名称，添加组颜色前缀
     */
    private static void updatePlayerDisplayName(ServerPlayerEntity player, TeamInfo teamInfo) {
        // 注意：这只是一个示例，实际修改玩家显示名称可能需要更多处理
        // 在Minecraft中，通常通过计分板或数据包来修改玩家显示名称
        String currentName = player.getName().getString();

        // 我们可以在这里添加逻辑来更新玩家的显示名称
        // 例如，使用计分板团队功能

        // 暂时先发送消息通知
        player.sendMessage(
                Text.literal(teamInfo.getColorCode() + "你的组颜色已更新为" + teamInfo.getDisplayName())
        );
    }

    /**
     * 恢复玩家默认显示名称
     */
    private static void resetPlayerDisplayName(ServerPlayerEntity player) {
        // 恢复玩家显示名称的逻辑
        player.sendMessage(Text.literal("§7你的组颜色已被移除"));
    }

    /**
     * 获取玩家所在分组
     */
    public static String getPlayerTeam(ServerPlayerEntity player) {
        if (player == null) return null;
        return playerTeams.get(player.getUuid());
    }

    /**
     * 获取玩家所在分组的显示信息
     */
    public static TeamInfo getPlayerTeamInfo(ServerPlayerEntity player) {
        String team = getPlayerTeam(player);
        if (team == null) return null;
        return TEAM_COLORS.get(team);
    }

    /**
     * 检查玩家是否在特定分组
     */
    public static boolean isPlayerInTeam(ServerPlayerEntity player, String teamColor) {
        String playerTeam = getPlayerTeam(player);
        return teamColor.equalsIgnoreCase(playerTeam);
    }

    /**
     * 获取分组中的所有玩家
     */
    public static List<ServerPlayerEntity> getPlayersInTeam(ServerCommandSource source, String teamColor) {
        List<ServerPlayerEntity> players = new ArrayList<>();

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (isPlayerInTeam(player, teamColor)) {
                players.add(player);
            }
        }

        return players;
    }

    /**
     * 重置所有分组
     */
    public static void resetAllTeams() {
        playerTeams.clear();
    }

    /**
     * 获取分组统计信息
     */
    public static Map<String, Integer> getTeamStatistics(ServerCommandSource source) {
        Map<String, Integer> stats = new LinkedHashMap<>();

        // 初始化所有组
        for (String color : TEAM_COLORS.keySet()) {
            stats.put(color, 0);
        }
        stats.put("ungrouped", 0);

        // 统计人数
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            String team = playerTeams.get(player.getUuid());
            if (team != null && TEAM_COLORS.containsKey(team)) {
                stats.put(team, stats.get(team) + 1);
            } else {
                stats.put("ungrouped", stats.get("ungrouped") + 1);
            }
        }

        return stats;
    }
}