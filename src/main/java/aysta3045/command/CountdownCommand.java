// [file name]: CountdownCommand.java
package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CountdownCommand {
    private static ScheduledExecutorService scheduler;
    private static boolean isCountingDown = false;
    private static AtomicInteger remainingSeconds = new AtomicInteger(0);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("countdown")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> startCountdown(context, 12600)) // 默认3小时30分钟
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(context -> {
                                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                    return startCountdown(context, seconds);
                                })
                        )
                )
                .then(CommandManager.literal("stopcountdown")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CountdownCommand::stopCountdown)
                )
                .then(CommandManager.literal("checkcountdown")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CountdownCommand::checkCountdown)
                )
        );
    }

    private static int startCountdown(CommandContext<ServerCommandSource> context, int totalSeconds) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (isCountingDown) {
            source.sendError(Text.literal("§c倒计时已经在运行中！使用 /competition stopcountdown 停止当前倒计时"));
            return 0;
        }

        // 初始化调度器
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }

        isCountingDown = true;
        remainingSeconds.set(totalSeconds);

        // 广播倒计时开始
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §a倒计时已开始: §e" + formatTime(totalSeconds))
                            .styled(style -> style.withColor(0xFFAA00))
            );

            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§6§l比赛开始!")
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§a倒计时: " + formatTime(totalSeconds))
            ));

            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 启动了倒计时: " + formatTime(totalSeconds))
        );

        // 启动定时任务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int currentSeconds = remainingSeconds.decrementAndGet();

                if (currentSeconds <= 0) {
                    // 倒计时结束
                    endCountdown(source);
                    stopCountdown();
                    return;
                }

                // 特殊时间点提示
                if (currentSeconds == 3600) { // 1小时
                    broadcastMessage(source, "§6[比赛系统] §a剩余时间: §e1小时");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.8f);
                } else if (currentSeconds == 1800) { // 30分钟
                    broadcastMessage(source, "§6[比赛系统] §a剩余时间: §e30分钟");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.8f);
                } else if (currentSeconds == 600) { // 10分钟
                    broadcastMessage(source, "§6[比赛系统] §a剩余时间: §e10分钟");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.0f);
                } else if (currentSeconds == 300) { // 5分钟
                    broadcastMessage(source, "§6[比赛系统] §c剩余时间: §e5分钟");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.0f);
                } else if (currentSeconds == 60) { // 1分钟
                    broadcastMessage(source, "§6[比赛系统] §c剩余时间: §e1分钟");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f);
                } else if (currentSeconds == 30) { // 30秒
                    broadcastMessage(source, "§6[比赛系统] §c剩余时间: §e30秒");
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f);
                } else if (currentSeconds == 10) { // 只在第10秒提示一次
                    // 显示剩余10秒提示
                    broadcastTitleForAll(source,
                            Text.literal("§c§l最后10秒"),
                            Text.literal("§7即将结束"));
                    playSoundForAll(source, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f);
                }

                // 每半小时更新一次显示（除了最后10秒）
                if (currentSeconds % 1800 == 0 && currentSeconds > 0 && currentSeconds > 10) {
                    updateHalfHourForAll(source, currentSeconds);
                }

            } catch (Exception e) {
                System.err.println("倒计时任务异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);

        source.sendMessage(
                Text.literal("§a倒计时已启动: §e" + formatTime(totalSeconds))
        );

        return 1;
    }

    private static void endCountdown(ServerCommandSource source) {
        // 游戏结束效果
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 显示结束标题
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§c§l游戏结束!")
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§a比赛时间到!")
            ));

            // 播放烟花音效
            player.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);

            // 发送聊天消息
            player.sendMessage(
                    Text.literal("§6[比赛系统] §c§l游戏结束！比赛时间到！")
                            .styled(style -> style.withColor(0xFF5555))
            );

            // 创建粒子效果
            player.getServerWorld().spawnParticles(
                    player,
                    ParticleTypes.FIREWORK,
                    true,
                    player.getX(),
                    player.getY() + 2,
                    player.getZ(),
                    50,
                    0.5,
                    0.5,
                    0.5,
                    0.2
            );
        }

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] 比赛倒计时结束，游戏已结束")
        );
    }

    private static int stopCountdown(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (!isCountingDown) {
            source.sendError(Text.literal("§c没有正在运行的倒计时！"));
            return 0;
        }

        stopCountdown();

        source.sendMessage(
                Text.literal("§a已停止当前倒计时")
        );

        // 广播消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §c倒计时已被管理员停止")
            );
        }

        return 1;
    }

    private static void stopCountdown() {
        isCountingDown = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static int checkCountdown(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (isCountingDown) {
            int seconds = remainingSeconds.get();
            source.sendMessage(
                    Text.literal("§a当前倒计时剩余: §e" + formatTime(seconds))
            );

            // 计算小时、分钟、秒
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            int secs = seconds % 60;

            source.sendMessage(
                    Text.literal(String.format("§7精确时间: §e%d小时 %d分钟 %d秒", hours, minutes, secs))
            );

            return 1;
        } else {
            source.sendMessage(
                    Text.literal("§7当前没有正在运行的倒计时")
            );
            return 0;
        }
    }

    private static void broadcastMessage(ServerCommandSource source, String message) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(message));
        }
    }

    private static void playSoundForAll(ServerCommandSource source, SoundEvent sound, float pitch) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.playSound(sound, 1.0f, pitch);
        }
    }

    private static void broadcastTitleForAll(ServerCommandSource source, Text title, Text subtitle) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        }
    }

    private static void updateHalfHourForAll(ServerCommandSource source, int seconds) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 将秒转换为小时和分钟
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;

            String timeText;
            if (hours > 0) {
                if (minutes == 0) {
                    timeText = hours + "小时";
                } else {
                    timeText = hours + "小时" + minutes + "分钟";
                }
            } else {
                timeText = minutes + "分钟";
            }

            player.sendMessage(
                    Text.literal("§6[比赛系统] §a剩余时间: §e" + timeText)
            );
        }
    }

    private static String formatTime(int totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "秒";
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            if (minutes > 0) {
                if (seconds > 0) {
                    return String.format("%d小时%d分%d秒", hours, minutes, seconds);
                } else {
                    return String.format("%d小时%d分", hours, minutes);
                }
            } else {
                return String.format("%d小时", hours);
            }
        } else {
            if (seconds > 0) {
                return String.format("%d分%d秒", minutes, seconds);
            } else {
                return String.format("%d分", minutes);
            }
        }
    }

    // 检查是否有倒计时正在运行
    public static boolean isCountdownRunning() {
        return isCountingDown;
    }

    // 获取剩余时间
    public static int getRemainingTime() {
        return remainingSeconds.get();
    }

    // 清理资源
    public static void cleanup() {
        stopCountdown();
    }
}