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

    // 存储原版队伍名称映射
    private static final Map<String, String> VANILLA_TEAM_COLOR_MAP = new HashMap<>();

    // 定义7个组的颜色和显示名称
    public static final Map<String, TeamInfo> TEAM_COLORS = new LinkedHashMap<>();

    static {
        // 初始化7个组的颜色
        TEAM_COLORS.put("red", new TeamInfo("红色", "red", "§c"));
        TEAM_COLORS.put("orange", new TeamInfo("橙色", "gold", "§6"));
        TEAM_COLORS.put("yellow", new TeamInfo("黄色", "yellow", "§e"));
        TEAM_COLORS.put("green", new TeamInfo("绿色", "green", "§a"));
        TEAM_COLORS.put("cyan", new TeamInfo("青色", "aqua", "§b"));
        TEAM_COLORS.put("blue", new TeamInfo("蓝色", "blue", "§9"));
        TEAM_COLORS.put("purple", new TeamInfo("紫色", "light_purple", "§d"));

        // 初始化原版颜色映射
        VANILLA_TEAM_COLOR_MAP.put("red", "red");
        VANILLA_TEAM_COLOR_MAP.put("orange", "gold");
        VANILLA_TEAM_COLOR_MAP.put("yellow", "yellow");
        VANILLA_TEAM_COLOR_MAP.put("green", "green");
        VANILLA_TEAM_COLOR_MAP.put("cyan", "aqua");
        VANILLA_TEAM_COLOR_MAP.put("blue", "blue");
        VANILLA_TEAM_COLOR_MAP.put("purple", "light_purple");
    }

    public static class TeamInfo {
        private final String displayName;
        private final String vanillaColor;
        private final String colorCode;

        public TeamInfo(String displayName, String vanillaColor, String colorCode) {
            this.displayName = displayName;
            this.vanillaColor = vanillaColor;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getVanillaColor() {
            return vanillaColor;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 设置玩家分组命令
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
                                        .executes(TeamSetCommand::executeTeamSet)
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
                // 从分组中移除特定玩家
                .then(CommandManager.literal("teamremove")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(TeamSetCommand::executeTeamRemove)
                        )
                )
                // 查看特定玩家所在分组
                .then(CommandManager.literal("teamcheck")
                        .requires(source -> source.hasPermissionLevel(0))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(TeamSetCommand::executeTeamCheck)
                        )
                )
                // 初始化所有原版队伍（一次性创建所有队伍）
                .then(CommandManager.literal("teaminit")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(TeamSetCommand::executeTeamInit)
                )
        );
    }

    private static int executeTeamSet(CommandContext<ServerCommandSource> context) {
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
            String teamName = "team_" + color; // 原版队伍名称
            String displayName = teamInfo.getDisplayName() + "组"; // 显示名称

            // 检查玩家是否已经在其他组
            String previousTeam = playerTeams.get(target.getUuid());

            // 如果玩家之前有分组，先将其从原版队伍中移除
            if (previousTeam != null) {
                String previousTeamName = "team_" + previousTeam;
                String removeCommand = String.format("team leave %s", target.getName().getString());
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        removeCommand
                );
            }

            // 创建队伍（如果不存在）
            String createTeamCommand = String.format("team add %s \"%s\"", teamName, displayName);
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    createTeamCommand
            );

            // 设置队伍颜色
            String colorCommand = String.format("team modify %s color %s", teamName, teamInfo.getVanillaColor());
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    colorCommand
            );

            // 设置队伍显示名称颜色（可选）
            String displayNameCommand = String.format("team modify %s displayName {\"text\":\"%s组\",\"color\":\"%s\"}",
                    teamName, teamInfo.getDisplayName(), teamInfo.getVanillaColor());
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    displayNameCommand
            );

            // 设置队伍成员前缀（可选）
            String prefixCommand = String.format("team modify %s prefix {\"text\":\"[%s组] \",\"color\":\"%s\"}",
                    teamName, teamInfo.getDisplayName(), teamInfo.getVanillaColor());
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    prefixCommand
            );

            // 将玩家加入队伍
            String joinCommand = String.format("team join %s %s", teamName, target.getName().getString());
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    joinCommand
            );

            // 更新内存映射
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

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("§c执行命令时出错: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
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
                Text.literal("§6使用 §e/competition teamset <颜色> <玩家> §6设置分组")
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

        // 将所有玩家从原版队伍中移除
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            String leaveCommand = String.format("team leave %s", player.getName().getString());
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    leaveCommand
            );
        }

        // 删除所有我们创建的队伍
        for (String color : TEAM_COLORS.keySet()) {
            String teamName = "team_" + color;
            String removeTeamCommand = String.format("team remove %s", teamName);
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    removeTeamCommand
            );
        }

        // 清除内存映射
        playerTeams.clear();

        // 通知所有玩家
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("§6所有分组已被管理员清除"));
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

    private static int executeTeamRemove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            ServerPlayerEntity sender = source.getPlayer();

            if (sender == null) {
                source.sendError(Text.literal("只有玩家可以执行此命令!"));
                return 0;
            }

            // 检查玩家是否有分组
            String previousTeam = playerTeams.remove(target.getUuid());

            if (previousTeam != null) {
                // 将玩家从原版队伍中移除
                String leaveCommand = String.format("team leave %s", target.getName().getString());
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        leaveCommand
                );

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
        } catch (Exception e) {
            source.sendError(Text.literal("§c找不到指定的玩家！"));
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

    private static int executeTeamInit(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        try {
            // 创建所有原版队伍
            for (Map.Entry<String, TeamInfo> entry : TEAM_COLORS.entrySet()) {
                String color = entry.getKey();
                TeamInfo teamInfo = entry.getValue();
                String teamName = "team_" + color;
                String displayName = teamInfo.getDisplayName() + "组";

                // 创建队伍
                String createTeamCommand = String.format("team add %s \"%s\"", teamName, displayName);
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        createTeamCommand
                );

                // 设置队伍颜色
                String colorCommand = String.format("team modify %s color %s", teamName, teamInfo.getVanillaColor());
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        colorCommand
                );

                // 设置队伍显示名称颜色
                String displayNameCommand = String.format("team modify %s displayName {\"text\":\"%s组\",\"color\":\"%s\"}",
                        teamName, teamInfo.getDisplayName(), teamInfo.getVanillaColor());
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        displayNameCommand
                );

                // 设置队伍成员前缀
                String prefixCommand = String.format("team modify %s prefix {\"text\":\"[%s组] \",\"color\":\"%s\"}",
                        teamName, teamInfo.getDisplayName(), teamInfo.getVanillaColor());
                source.getServer().getCommandManager().executeWithPrefix(
                        source.getServer().getCommandSource(),
                        prefixCommand
                );

                // 等待一下避免命令执行过快
                Thread.sleep(50);
            }

            source.sendMessage(Text.literal("§a所有原版队伍已初始化完成！"));
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("§c初始化队伍时出错: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
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