package aysta3045.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class FTBTeamsHelper {

    // 队伍颜色列表
    private static final String[] TEAM_COLORS = {
            "§c红队", "§6橙队", "§e黄队", "§a绿队", "§b青队", "§9蓝队", "§5紫队"
    };

    // 检查FTB Teams模组是否存在
    public static boolean isFTBTeamsLoaded(MinecraftServer server) {
        try {
            Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // 获取所有队伍信息
    public static Map<String, List<String>> getAllTeams(MinecraftServer server) {
        Map<String, List<String>> teams = new LinkedHashMap<>();

        if (!isFTBTeamsLoaded(server)) {
            return teams;
        }

        try {
            // 使用反射获取FTB Teams的API
            Class<?> ftbTeamsClass = Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            Object api = ftbTeamsClass.getMethod("getAPI").invoke(null);

            // 获取队伍管理器
            Object teamManager = api.getClass().getMethod("getManager").invoke(api);

            // 获取所有队伍
            Collection<?> allTeams = (Collection<?>) teamManager.getClass()
                    .getMethod("getAllTeams")
                    .invoke(teamManager);

            // 遍历队伍
            for (Object team : allTeams) {
                // 获取队伍名称
                String teamName = (String) team.getClass()
                        .getMethod("getShortName")
                        .invoke(team);

                if (teamName == null || teamName.trim().isEmpty()) {
                    teamName = (String) team.getClass()
                            .getMethod("getName")
                            .invoke(team);
                }

                // 获取队伍成员
                List<String> members = new ArrayList<>();
                Collection<?> teamMembers = (Collection<?>) team.getClass()
                        .getMethod("getMembers")
                        .invoke(team);

                for (Object member : teamMembers) {
                    try {
                        // 尝试获取玩家名称
                        String playerName = (String) member.getClass()
                                .getMethod("getName")
                                .invoke(member);

                        if (playerName != null) {
                            members.add(playerName);
                        }
                    } catch (Exception e) {
                        // 如果无法获取名称，尝试获取UUID
                        try {
                            UUID uuid = (UUID) member.getClass()
                                    .getMethod("getUUID")
                                    .invoke(member);

                            // 通过UUID查找玩家名称
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            if (player != null) {
                                members.add(player.getName().getString());
                            } else {
                                members.add(uuid.toString().substring(0, 8) + "...");
                            }
                        } catch (Exception ex) {
                            members.add("未知玩家");
                        }
                    }
                }

                teams.put(teamName, members);
            }

        } catch (Exception e) {
            System.err.println("[比赛系统] 获取FTB Teams队伍信息失败: " + e.getMessage());
            e.printStackTrace();
        }

        return teams;
    }

    // 格式化并输出队伍信息
    public static List<Text> formatTeamInfo(Map<String, List<String>> teams) {
        List<Text> messages = new ArrayList<>();

        if (teams.isEmpty()) {
            messages.add(Text.literal("§c未找到任何队伍信息！")
                    .styled(style -> style.withColor(0xFF5555)));
            return messages;
        }

        // 添加标题
        messages.add(Text.literal("§6========== 队伍分组信息 ==========")
                .styled(style -> style.withColor(0xFFAA00)));

        int teamIndex = 0;

        // 遍历队伍，按顺序分配颜色
        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            final int currentTeamIndex = teamIndex; // 创建final变量用于lambda

            if (currentTeamIndex >= TEAM_COLORS.length) {
                // 如果队伍数量超过颜色数量，使用默认颜色
                messages.add(Text.literal("§7队伍: " + entry.getKey())
                        .styled(style -> style.withColor(0xAAAAAA)));
            } else {
                // 使用预定义的颜色
                messages.add(Text.literal(TEAM_COLORS[currentTeamIndex] + " §7(" + entry.getKey() + ")")
                        .styled(style -> style.withColor(getColorFromFormatting(currentTeamIndex))));
            }

            // 添加队员列表
            StringBuilder membersList = new StringBuilder("  §7成员: ");
            List<String> members = entry.getValue();

            if (members.isEmpty()) {
                membersList.append("§c无队员");
            } else {
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) {
                        membersList.append("§7, ");
                    }
                    membersList.append("§f").append(members.get(i));
                }
            }

            messages.add(Text.literal(membersList.toString())
                    .styled(style -> style.withColor(0xAAAAAA)));

            // 添加队员数量统计
            messages.add(Text.literal("  §7人数: §e" + members.size() + "人")
                    .styled(style -> style.withColor(0x888888)));

            teamIndex++;
        }

        // 添加总计信息
        int totalPlayers = teams.values().stream()
                .mapToInt(List::size)
                .sum();

        messages.add(Text.literal("§6总计: §e" + teams.size() + "§6个队伍, §e" +
                        totalPlayers + "§6名玩家")
                .styled(style -> style.withColor(0xFFAA00)));
        messages.add(Text.literal("§6=================================")
                .styled(style -> style.withColor(0xFFAA00)));

        return messages;
    }

    // 根据索引获取颜色值
    private static int getColorFromFormatting(int index) {
        switch (index) {
            case 0: return 0xFF5555; // 红色
            case 1: return 0xFFAA00; // 橙色
            case 2: return 0xFFFF55; // 黄色
            case 3: return 0x55FF55; // 绿色
            case 4: return 0x55FFFF; // 青色
            case 5: return 0x5555FF; // 蓝色
            case 6: return 0xFF55FF; // 紫色
            default: return 0xAAAAAA; // 灰色
        }
    }

    // 检查并发送队伍信息
    public static boolean checkAndSendTeamInfo(ServerPlayerEntity sender) {
        MinecraftServer server = sender.getServer();

        if (server == null) {
            return false;
        }

        if (!isFTBTeamsLoaded(server)) {
            sender.sendMessage(
                    Text.literal("§c错误: FTB Teams模组未安装或版本不兼容！")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return false;
        }

        Map<String, List<String>> teams = getAllTeams(server);

        if (teams.isEmpty()) {
            sender.sendMessage(
                    Text.literal("§c未找到任何队伍信息，请检查FTB Teams配置！")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return false;
        }

        // 向所有玩家广播队伍信息
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6========== 队伍分组信息 ==========")
                            .styled(style -> style.withColor(0xFFAA00))
            );

            final int[] teamIndexWrapper = {0}; // 使用数组包装器来绕过final限制

            for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
                String colorPrefix = teamIndexWrapper[0] < TEAM_COLORS.length ?
                        TEAM_COLORS[teamIndexWrapper[0]] : "§7队伍";

                player.sendMessage(
                        Text.literal(colorPrefix + " §7(" + entry.getKey() + ")")
                );

                // 显示队员
                StringBuilder members = new StringBuilder("  §7成员: ");
                List<String> memberList = entry.getValue();

                if (memberList.isEmpty()) {
                    members.append("§c无队员");
                } else {
                    for (int i = 0; i < memberList.size(); i++) {
                        if (i > 0) members.append("§7, ");
                        members.append("§f").append(memberList.get(i));
                    }
                }

                player.sendMessage(Text.literal(members.toString()));
                player.sendMessage(
                        Text.literal("  §7人数: §e" + memberList.size() + "人")
                );

                teamIndexWrapper[0]++;
            }

            // 统计信息 - 使用传统循环避免lambda中的final限制
            int totalPlayers = 0;
            for (List<String> memberList : teams.values()) {
                totalPlayers += memberList.size();
            }

            player.sendMessage(
                    Text.literal("§6总计: §e" + teams.size() + "§6个队伍, §e" +
                            totalPlayers + "§6名玩家")
            );
            player.sendMessage(
                    Text.literal("§6=================================")
            );
        }

        // 记录到控制台 - 使用传统循环计算总玩家数
        int totalPlayers = 0;
        for (List<String> memberList : teams.values()) {
            totalPlayers += memberList.size();
        }

        server.sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 广播了队伍分组信息 (" + teams.size() + "个队伍, " +
                        totalPlayers + "名玩家)")
        );

        return true;
    }
}