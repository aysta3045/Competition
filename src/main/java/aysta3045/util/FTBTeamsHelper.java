package aysta3045.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * FTB Teams 助手类 - 支持直接API调用和反射回退
 */
public class FTBTeamsHelper {

    // 队伍颜色列表（彩虹顺序）
    private static final String[] TEAM_COLORS = {
            "§c红队", "§6橙队", "§e黄队", "§a绿队", "§b青队", "§9蓝队", "§5紫队"
    };

    // 队伍颜色格式化代码
    private static final Formatting[] TEAM_FORMATTINGS = {
            Formatting.RED,      // 红色
            Formatting.GOLD,     // 橙色
            Formatting.YELLOW,   // 黄色
            Formatting.GREEN,    // 绿色
            Formatting.AQUA,     // 青色
            Formatting.BLUE,     // 蓝色
            Formatting.DARK_PURPLE // 紫色
    };

    /**
     * 检查FTB Teams模组是否可用
     * @return true如果FTB Teams可用
     */
    public static boolean isFTBTeamsAvailable() {
        // 首先尝试检查类是否存在
        try {
            Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查FTB Teams API是否可用（直接API调用）
     * @return true如果API可以直接调用
     */
    public static boolean isDirectAPIAvailable() {
        try {
            // 尝试获取API实例
            Class<?> ftbTeamsClass = Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            Object api = ftbTeamsClass.getMethod("api").invoke(null);
            return api != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有队伍信息（使用最佳可用方法）
     * @param server Minecraft服务器实例
     * @return 队伍名称->成员列表的映射
     */
    public static Map<String, List<String>> getAllTeams(MinecraftServer server) {
        if (server == null) {
            return Collections.emptyMap();
        }

        // 先尝试直接API调用（如果可用）
        Map<String, List<String>> teams = getAllTeamsViaDirectAPI(server);
        if (!teams.isEmpty()) {
            System.out.println("[比赛系统] 使用直接API获取FTB Teams队伍信息");
            return teams;
        }

        // 如果直接API失败，尝试反射方式
        teams = getAllTeamsViaReflection(server);
        if (!teams.isEmpty()) {
            System.out.println("[比赛系统] 使用反射获取FTB Teams队伍信息");
            return teams;
        }

        // 两种方法都失败，返回空Map
        System.err.println("[比赛系统] 无法获取FTB Teams队伍信息");
        return Collections.emptyMap();
    }

    /**
     * 使用直接API调用获取队伍信息（需要编译时依赖）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getAllTeamsViaDirectAPI(MinecraftServer server) {
        Map<String, List<String>> teams = new LinkedHashMap<>();

        try {
            // 导入直接API类（编译时需要依赖）
            Class<?> ftbTeamsClass = Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            Object api = ftbTeamsClass.getMethod("api").invoke(null);

            // 获取队伍管理器
            Object teamManager = api.getClass().getMethod("getManager").invoke(api);

            // 获取所有队伍
            Collection<Object> allTeams = (Collection<Object>) teamManager.getClass()
                    .getMethod("getAllTeams")
                    .invoke(teamManager);

            // 遍历所有队伍
            for (Object team : allTeams) {
                try {
                    // 获取队伍名称
                    String teamName = getTeamName(team);

                    // 获取队伍成员
                    List<String> members = getTeamMembers(team, server);

                    // 只添加有成员的队伍
                    if (!members.isEmpty()) {
                        teams.put(teamName, members);
                    }
                } catch (Exception e) {
                    System.err.println("[比赛系统] 处理队伍时出错: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[比赛系统] 直接API调用失败: " + e.getMessage());
        }

        return teams;
    }

    /**
     * 使用反射获取队伍信息（兼容性方法）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getAllTeamsViaReflection(MinecraftServer server) {
        Map<String, List<String>> teams = new LinkedHashMap<>();

        try {
            // 使用反射获取FTB Teams API
            Class<?> ftbTeamsClass = Class.forName("dev.ftb.mods.ftbteams.FTBTeams");
            Object api = ftbTeamsClass.getMethod("api").invoke(null);

            // 获取队伍管理器
            Object teamManager = api.getClass().getMethod("getManager").invoke(api);

            // 获取所有队伍
            Collection<Object> allTeams = (Collection<Object>) teamManager.getClass()
                    .getMethod("getAllTeams")
                    .invoke(teamManager);

            // 遍历所有队伍
            for (Object team : allTeams) {
                try {
                    // 获取队伍名称
                    String teamName = getTeamNameViaReflection(team);

                    // 获取队伍成员
                    List<String> members = getTeamMembersViaReflection(team, server);

                    // 只添加有成员的队伍
                    if (!members.isEmpty()) {
                        teams.put(teamName, members);
                    }
                } catch (Exception e) {
                    System.err.println("[比赛系统] 反射处理队伍时出错: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[比赛系统] 反射调用失败: " + e.getMessage());
            // 调试信息
            e.printStackTrace();
        }

        return teams;
    }

    /**
     * 获取队伍名称（直接API方式）
     */
    private static String getTeamName(Object team) throws Exception {
        try {
            // 尝试获取短名称
            String shortName = (String) team.getClass()
                    .getMethod("getShortName")
                    .invoke(team);

            if (shortName != null && !shortName.trim().isEmpty()) {
                return shortName;
            }

            // 尝试获取完整名称
            String fullName = (String) team.getClass()
                    .getMethod("getName")
                    .invoke(team);

            return fullName != null ? fullName : "未命名队伍";
        } catch (Exception e) {
            // 如果获取名称失败，返回默认名称
            return "队伍-" + team.hashCode();
        }
    }

    /**
     * 获取队伍成员（直接API方式）
     */
    @SuppressWarnings("unchecked")
    private static List<String> getTeamMembers(Object team, MinecraftServer server) throws Exception {
        List<String> members = new ArrayList<>();

        // 获取队伍成员
        Collection<Object> teamMembers = (Collection<Object>) team.getClass()
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
                    continue;
                }
            } catch (Exception e) {
                // 如果无法获取名称，尝试获取UUID
            }

            try {
                // 尝试获取UUID
                UUID uuid = (UUID) member.getClass()
                        .getMethod("getId")
                        .invoke(member);

                if (uuid != null) {
                    // 通过UUID查找玩家
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    if (player != null) {
                        members.add(player.getName().getString());
                    } else {
                        members.add("玩家-" + uuid.toString().substring(0, 8));
                    }
                }
            } catch (Exception e) {
                // 如果获取UUID也失败，添加默认名称
                members.add("未知玩家");
            }
        }

        return members;
    }

    /**
     * 获取队伍名称（反射方式）
     */
    private static String getTeamNameViaReflection(Object team) {
        try {
            // 尝试多种可能的方法名
            String[] methodNames = {"getShortName", "getDisplayName", "getName", "getTeamName"};

            for (String methodName : methodNames) {
                try {
                    Object result = team.getClass().getMethod(methodName).invoke(team);
                    if (result != null) {
                        String name = result.toString();
                        if (!name.trim().isEmpty()) {
                            return name;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // 方法不存在，继续尝试下一个
                }
            }

            // 所有方法都失败，尝试通过属性获取
            try {
                Object properties = team.getClass().getMethod("getProperties").invoke(team);
                Object displayName = properties.getClass().getMethod("getDisplayName").invoke(properties);
                if (displayName != null) {
                    return displayName.toString();
                }
            } catch (Exception e) {
                // 忽略异常
            }
        } catch (Exception e) {
            System.err.println("[比赛系统] 获取队伍名称失败: " + e.getMessage());
        }

        return "未命名队伍";
    }

    /**
     * 获取队伍成员（反射方式）
     */
    @SuppressWarnings("unchecked")
    private static List<String> getTeamMembersViaReflection(Object team, MinecraftServer server) {
        List<String> members = new ArrayList<>();

        try {
            // 尝试多种获取成员的方法
            Object memberCollection = null;

            try {
                memberCollection = team.getClass().getMethod("getMembers").invoke(team);
            } catch (NoSuchMethodException e) {
                try {
                    memberCollection = team.getClass().getMethod("getOnlineMembers").invoke(team);
                } catch (NoSuchMethodException e2) {
                    try {
                        memberCollection = team.getClass().getMethod("getPlayerIds").invoke(team);
                    } catch (NoSuchMethodException e3) {
                        return members; // 所有方法都不存在，返回空列表
                    }
                }
            }

            if (memberCollection instanceof Collection) {
                Collection<Object> memberObjects = (Collection<Object>) memberCollection;

                for (Object member : memberObjects) {
                    try {
                        // 尝试获取玩家名称
                        if (member instanceof UUID) {
                            UUID uuid = (UUID) member;
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            if (player != null) {
                                members.add(player.getName().getString());
                            } else {
                                members.add("玩家-" + uuid.toString().substring(0, 8));
                            }
                        } else {
                            // 尝试调用getName方法
                            try {
                                String name = (String) member.getClass().getMethod("getName").invoke(member);
                                if (name != null) {
                                    members.add(name);
                                }
                            } catch (NoSuchMethodException e) {
                                // 如果对象有toString方法，使用它
                                members.add(member.toString());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[比赛系统] 处理队伍成员时出错: " + e.getMessage());
                        members.add("未知玩家");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[比赛系统] 获取队伍成员失败: " + e.getMessage());
        }

        return members;
    }

    /**
     * 格式化队伍信息为Text列表
     * @param teams 队伍信息映射
     * @return 格式化的Text消息列表
     */
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
            // 获取队伍颜色
            String colorPrefix;
            int colorValue;

            if (teamIndex < TEAM_COLORS.length) {
                colorPrefix = TEAM_COLORS[teamIndex];
                colorValue = getColorFromFormatting(teamIndex);
            } else {
                colorPrefix = "§7队伍" + (teamIndex + 1);
                colorValue = 0xAAAAAA;
            }

            // 添加队伍标题
            messages.add(Text.literal(colorPrefix + " §7(" + entry.getKey() + ")")
                    .styled(style -> style.withColor(colorValue)));

            // 添加队员列表
            List<String> members = entry.getValue();
            if (members.isEmpty()) {
                messages.add(Text.literal("  §7成员: §c无队员")
                        .styled(style -> style.withColor(0xAAAAAA)));
            } else {
                StringBuilder membersText = new StringBuilder("  §7成员: ");
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) {
                        membersText.append("§7, ");
                    }
                    membersText.append("§f").append(members.get(i));
                }
                messages.add(Text.literal(membersText.toString())
                        .styled(style -> style.withColor(0xAAAAAA)));
            }

            // 添加队员数量
            messages.add(Text.literal("  §7人数: §e" + members.size() + "人")
                    .styled(style -> style.withColor(0x888888)));

            teamIndex++;
        }

        // 统计总玩家数
        int totalPlayers = teams.values().stream()
                .mapToInt(List::size)
                .sum();

        // 添加总计信息
        messages.add(Text.literal("§6总计: §e" + teams.size() + "§6个队伍, §e" +
                        totalPlayers + "§6名玩家")
                .styled(style -> style.withColor(0xFFAA00)));
        messages.add(Text.literal("§6=================================")
                .styled(style -> style.withColor(0xFFAA00)));

        return messages;
    }

    /**
     * 检查并发送队伍信息（主入口方法）
     * @param sender 发送命令的玩家
     * @return true如果成功发送队伍信息
     */
    public static boolean checkAndSendTeamInfo(ServerPlayerEntity sender) {
        if (sender == null) {
            return false;
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return false;
        }

        // 检查FTB Teams是否可用
        if (!isFTBTeamsAvailable()) {
            sender.sendMessage(
                    Text.literal("§c错误: FTB Teams模组未安装或版本不兼容！")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return false;
        }

        // 获取队伍信息
        Map<String, List<String>> teams = getAllTeams(server);

        if (teams.isEmpty()) {
            sender.sendMessage(
                    Text.literal("§c未找到任何队伍信息，请检查FTB Teams配置！")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return false;
        }

        // 向所有在线玩家广播队伍信息
        broadcastTeamInfo(server, teams, sender);

        // 记录到控制台
        int totalPlayers = teams.values().stream()
                .mapToInt(List::size)
                .sum();

        server.sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 广播了队伍分组信息 (" + teams.size() + "个队伍, " +
                        totalPlayers + "名玩家)")
        );

        return true;
    }

    /**
     * 向所有在线玩家广播队伍信息
     */
    private static void broadcastTeamInfo(MinecraftServer server, Map<String, List<String>> teams, ServerPlayerEntity sender) {
        int totalPlayers = teams.values().stream()
                .mapToInt(List::size)
                .sum();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // 发送标题
            player.sendMessage(
                    Text.literal("§6========== 队伍分组信息 ==========")
                            .styled(style -> style.withColor(0xFFAA00))
            );

            player.sendMessage(
                    Text.literal("§7由 " + sender.getName().getString() + " 广播")
                            .styled(style -> style.withColor(0x888888))
            );

            // 发送队伍信息
            int teamIndex = 0;
            for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
                String colorPrefix;
                if (teamIndex < TEAM_COLORS.length) {
                    colorPrefix = TEAM_COLORS[teamIndex];
                } else {
                    colorPrefix = "§7队伍" + (teamIndex + 1);
                }

                // 队伍标题
                player.sendMessage(
                        Text.literal(colorPrefix + " §7(" + entry.getKey() + ")")
                );

                // 队员列表
                List<String> members = entry.getValue();
                if (members.isEmpty()) {
                    player.sendMessage(
                            Text.literal("  §7成员: §c无队员")
                    );
                } else {
                    StringBuilder membersText = new StringBuilder("  §7成员: ");
                    for (int i = 0; i < members.size(); i++) {
                        if (i > 0) {
                            membersText.append("§7, ");
                        }
                        membersText.append("§f").append(members.get(i));
                    }
                    player.sendMessage(
                            Text.literal(membersText.toString())
                    );
                }

                // 队员数量
                player.sendMessage(
                        Text.literal("  §7人数: §e" + members.size() + "人")
                );

                teamIndex++;
            }

            // 统计信息
            player.sendMessage(
                    Text.literal("§6总计: §e" + teams.size() + "§6个队伍, §e" +
                            totalPlayers + "§6名玩家")
            );
            player.sendMessage(
                    Text.literal("§6=================================")
            );
        }
    }

    /**
     * 根据索引获取颜色值
     */
    private static int getColorFromFormatting(int index) {
        if (index < 0 || index >= TEAM_FORMATTINGS.length) {
            return 0xAAAAAA; // 默认灰色
        }

        Formatting formatting = TEAM_FORMATTINGS[index];
        switch (formatting) {
            case RED: return 0xFF5555;
            case GOLD: return 0xFFAA00;
            case YELLOW: return 0xFFFF55;
            case GREEN: return 0x55FF55;
            case AQUA: return 0x55FFFF;
            case BLUE: return 0x5555FF;
            case DARK_PURPLE: return 0xFF55FF;
            default: return 0xAAAAAA;
        }
    }

    /**
     * 获取队伍颜色代码（用于GUI等）
     */
    public static String getTeamColorCode(int teamIndex) {
        if (teamIndex >= 0 && teamIndex < TEAM_COLORS.length) {
            // 提取颜色代码（前2个字符）
            return TEAM_COLORS[teamIndex].substring(0, 2);
        }
        return "§7"; // 默认灰色
    }

    /**
     * 获取队伍显示名称（带颜色）
     */
    public static String getTeamDisplayName(int teamIndex, String teamName) {
        String colorCode = getTeamColorCode(teamIndex);
        return colorCode + teamName;
    }
}