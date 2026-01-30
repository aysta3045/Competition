package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StartCompetitionCommand {

    private static ScheduledExecutorService scheduler;
    private static boolean isRecordingPositions = false;
    private static Map<UUID, Vec3d> playerPositions = new ConcurrentHashMap<>();
    private static Map<String, List<ServerPlayerEntity>> teamPlayers = new HashMap<>();
    private static Map<String, Vec3d> teamAveragePositions = new HashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("start")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(StartCompetitionCommand::executeStartCompetition)
                )
                .then(CommandManager.literal("cancelstart")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(StartCompetitionCommand::executeCancelStart)
                )
        );
    }

    private static int executeStartCompetition(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (isRecordingPositions) {
            source.sendError(Text.literal("§c比赛启动流程已经在进行中！"));
            return 0;
        }

        // 初始化调度器
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }

        isRecordingPositions = true;
        playerPositions.clear();
        teamPlayers.clear();
        teamAveragePositions.clear();

        // 步骤1: 清除所有玩家（除了执行者）身上的所有效果
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player != sender) {
                // 清除所有状态效果
                for (StatusEffectInstance effect : new ArrayList<>(player.getStatusEffects())) {
                    player.removeStatusEffect(effect.getEffectType());
                }

                // 设置旁观者模式
                player.changeGameMode(GameMode.SPECTATOR);

                // 传送到指定位置 (0, 100, 0)
                player.teleport(
                        player.getServerWorld(),
                        0, 100, 0,
                        player.getYaw(), player.getPitch()
                );

                // 发送通知
                player.sendMessage(
                        Text.literal("§c你已被设置为旁观者模式，请选定队伍出生点！")
                );

                // 播放音效
                player.playSound(SoundEvents.BLOCK_BELL_USE, 1.0f, 1.0f);
            }
        }

        // 步骤2: 发送全局消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6=============================================")
            );
            player.sendMessage(
                    Text.literal("§e§l比赛启动流程开始！")
            );
            player.sendMessage(
                    Text.literal("§a请选定队伍出生点，一分钟后记录所有玩家位置")
            );
            player.sendMessage(
                    Text.literal("§a并以队伍所有玩家坐标平均值作为起点")
            );
            player.sendMessage(
                    Text.literal("§6=============================================")
            );

            // 发送标题
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§c§l选定出生点")
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§a一分钟后记录位置")
            ));

            // 播放重要音效
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        // 步骤3: 启动60秒倒计时
        AtomicInteger remainingSeconds = new AtomicInteger(60);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int currentSeconds = remainingSeconds.decrementAndGet();

                if (currentSeconds <= 0) {
                    // 倒计时结束，记录位置
                    recordAndCalculatePositions(source);
                    teleportTeamsToAveragePositions(source);
                    announceCompetitionStart(source);
                    cleanup();
                    return;
                }

                // 特殊时间点提示
                if (currentSeconds == 30) {
                    broadcastMessage(source, "§6[比赛系统] §a剩余时间: §e30秒");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.0f);
                } else if (currentSeconds == 10) {
                    broadcastMessage(source, "§6[比赛系统] §c§l最后10秒！");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.2f);
                } else if (currentSeconds <= 5 && currentSeconds > 0) {
                    broadcastMessage(source, "§6[比赛系统] §c§l" + currentSeconds);
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f + (5 - currentSeconds) * 0.1f);
                }

            } catch (Exception e) {
                System.err.println("比赛启动流程异常: " + e.getMessage());
                e.printStackTrace();
                cleanup();
            }
        }, 1, 1, TimeUnit.SECONDS);

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() + " 启动了比赛流程")
        );

        source.sendMessage(
                Text.literal("§a比赛启动流程已开始！60秒后将记录位置并传送。")
        );
        source.sendMessage(
                Text.literal("§7使用 §c/competition cancelstart §7取消流程")
        );

        return 1;
    }

    private static void recordAndCalculatePositions(ServerCommandSource source) {
        // 记录所有玩家的位置（除了执行命令的玩家）
        ServerPlayerEntity sender = source.getPlayer();

        // 首先清空之前的队伍分组
        teamPlayers.clear();
        teamAveragePositions.clear();

        // 根据teamset分组信息组织玩家，并记录位置
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player == sender) continue; // 跳过执行者

            // 记录玩家当前位置
            playerPositions.put(player.getUuid(), player.getPos());

            // 根据队伍分组
            String teamColor = TeamSetCommand.getPlayerTeam(player);
            if (teamColor != null) {
                teamPlayers.computeIfAbsent(teamColor, k -> new ArrayList<>()).add(player);
            } else {
                // 未分组玩家，单独处理
                teamPlayers.computeIfAbsent("ungrouped", k -> new ArrayList<>()).add(player);
            }
        }

        // 计算每个队伍的平均坐标
        for (Map.Entry<String, List<ServerPlayerEntity>> entry : teamPlayers.entrySet()) {
            String teamColor = entry.getKey();
            List<ServerPlayerEntity> players = entry.getValue();

            if (players.isEmpty()) continue;

            double totalX = 0;
            double totalY = 0;
            double totalZ = 0;
            int count = 0;

            // 只计算当前队伍玩家的坐标
            for (ServerPlayerEntity player : players) {
                Vec3d pos = playerPositions.get(player.getUuid());
                if (pos != null) {
                    totalX += pos.x;
                    totalY += pos.y;
                    totalZ += pos.z;
                    count++;
                }
            }

            if (count > 0) {
                Vec3d averagePos = new Vec3d(totalX / count, totalY / count, totalZ / count);
                teamAveragePositions.put(teamColor, averagePos);

                // 获取队伍信息
                TeamSetCommand.TeamInfo teamInfo = TeamSetCommand.TEAM_COLORS.get(teamColor);
                String teamName = teamInfo != null ? teamInfo.getDisplayName() : "未分组";

                // 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[比赛系统] 队伍 " + teamName + " 平均坐标: " +
                                String.format("%.1f, %.1f, %.1f", averagePos.x, averagePos.y, averagePos.z))
                );
            }
        }
    }

    private static void teleportTeamsToAveragePositions(ServerCommandSource source) {
        // 将所有玩家传送回生存模式并传送到平均位置
        for (Map.Entry<String, List<ServerPlayerEntity>> entry : teamPlayers.entrySet()) {
            String teamColor = entry.getKey();
            List<ServerPlayerEntity> players = entry.getValue();
            Vec3d averagePos = teamAveragePositions.get(teamColor);

            if (averagePos == null || players.isEmpty()) continue;

            // 获取队伍信息
            TeamSetCommand.TeamInfo teamInfo = TeamSetCommand.TEAM_COLORS.get(teamColor);
            String teamName = teamInfo != null ? teamInfo.getDisplayName() : "未分组";

            for (ServerPlayerEntity player : players) {
                // 设置为生存模式
                player.changeGameMode(GameMode.SURVIVAL);

                // 传送到平均位置
                player.teleport(
                        player.getServerWorld(),
                        averagePos.x, averagePos.y, averagePos.z,
                        player.getYaw(), player.getPitch()
                );

                // 清除效果（再次确保）
                for (StatusEffectInstance effect : new ArrayList<>(player.getStatusEffects())) {
                    player.removeStatusEffect(effect.getEffectType());
                }

                // 发送通知
                player.sendMessage(
                        Text.literal("§a你已被传送到" + teamName + "的出生点！")
                );

                // 创建粒子效果
                player.getServerWorld().spawnParticles(
                        player,
                        ParticleTypes.HAPPY_VILLAGER,
                        true,
                        averagePos.x, averagePos.y + 1, averagePos.z,
                        20,
                        0.5, 0.5, 0.5,
                        0.1
                );
            }
        }
    }

    private static void announceCompetitionStart(ServerCommandSource source) {
        // 广播比赛开始消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 显示比赛开始标题
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§a§l比赛开始!")
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§e祝你好运！")
            ));

            // 发送聊天消息
            player.sendMessage(
                    Text.literal("§6=============================================")
            );
            player.sendMessage(
                    Text.literal("§a§l比赛正式开始！")
            );
            player.sendMessage(
                    Text.literal("§e所有玩家已根据队伍分组传送到出生点")
            );
            player.sendMessage(
                    Text.literal("§6=============================================")
            );

            // 播放庆祝音效
            player.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            // 创建大量粒子效果
            player.getServerWorld().spawnParticles(
                    player,
                    ParticleTypes.FIREWORK,
                    true,
                    player.getX(), player.getY() + 2, player.getZ(),
                    100,
                    1.0, 1.0, 1.0,
                    0.1
            );
        }

        // 启动倒计时
        source.getServer().getCommandManager().executeWithPrefix(source, "competition countdown");

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] 比赛正式开始！所有玩家已传送至队伍出生点")
        );
    }

    private static int executeCancelStart(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (!isRecordingPositions) {
            source.sendMessage(Text.literal("§7没有正在进行的比赛启动流程"));
            return 0;
        }

        cleanup();

        // 将除自己以外的所有玩家设置为旁观者模式
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 跳过执行命令的玩家自己
            if (player == sender) {
                continue;
            }

            // 设置为旁观者模式
            player.changeGameMode(GameMode.SPECTATOR);

            // 清除效果
            for (StatusEffectInstance effect : new ArrayList<>(player.getStatusEffects())) {
                player.removeStatusEffect(effect.getEffectType());
            }

            // 发送通知消息
            player.sendMessage(
                    Text.literal("§c比赛启动流程已被管理员取消，你已被设置为旁观者模式")
            );
        }

        // 给执行者发送反馈
        source.sendMessage(
                Text.literal("§a已取消比赛启动流程，其他玩家已被设置为旁观者模式")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() + " 取消了比赛启动流程")
        );

        return 1;
    }

    private static void cleanup() {
        isRecordingPositions = false;
        playerPositions.clear();
        teamPlayers.clear();
        teamAveragePositions.clear();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void broadcastMessage(ServerCommandSource source, String message) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(message));
        }
    }

    private static void playSoundForAll(ServerCommandSource source, net.minecraft.sound.SoundEvent sound, float pitch) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.playSound(sound, 1.0f, pitch);
        }
    }

    // 清理资源的方法
    public static void onServerStop() {
        cleanup();
    }
}