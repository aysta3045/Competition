package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EndCompetitionOnDragonKillCommand {
    // 存储监听状态的变量
    private static boolean isMonitoring = false;
    private static ScheduledExecutorService scheduler;
    private static final Set<UUID> dragonKilledPlayers = ConcurrentHashMap.newKeySet();

    // 末影龙击杀成就的ID
    private static final Identifier END_DRAGON_ADVANCEMENT =
            Identifier.of("minecraft", "end/kill_dragon");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("endondragonkill")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndCompetitionOnDragonKillCommand::startMonitoring)
                )
                .then(CommandManager.literal("stopdragonmonitor")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndCompetitionOnDragonKillCommand::stopMonitoring)
                )
                .then(CommandManager.literal("checkdragonstatus")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndCompetitionOnDragonKillCommand::checkStatus)
                )
        );
    }

    private static int startMonitoring(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (isMonitoring) {
            source.sendMessage(
                    Text.literal("§c末影龙击杀监听已经在运行中!")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return 0;
        }

        // 初始化调度器
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }

        isMonitoring = true;
        dragonKilledPlayers.clear();

        // 启动监听任务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForDragonKill(source);
            } catch (Exception e) {
                System.err.println("末影龙击杀监听异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);

        // 广播监听开始消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §a末影龙击杀监听已启动!")
                            .styled(style -> style.withColor(0x55FF55))
            );

            player.sendMessage(
                    Text.literal("§6[比赛系统] §c第一位击杀末影龙的玩家将结束比赛!")
            );
        }

        source.sendMessage(
                Text.literal("§a已启动末影龙击杀监听! 当有玩家击杀末影龙时，比赛将自动结束。")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 启动了末影龙击杀监听")
        );

        return 1;
    }

    private static int stopMonitoring(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        if (!isMonitoring) {
            source.sendMessage(
                    Text.literal("§c末影龙击杀监听未在运行!")
            );
            return 0;
        }

        cleanup();

        // 广播监听停止消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §c末影龙击杀监听已停止!")
            );
        }

        source.sendMessage(
                Text.literal("§a已停止末影龙击杀监听")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 停止了末影龙击杀监听")
        );

        return 1;
    }

    private static int checkStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (isMonitoring) {
            source.sendMessage(
                    Text.literal("§a末影龙击杀监听正在运行中")
                            .styled(style -> style.withColor(0x55FF55))
            );

            // 检查当前是否有玩家已经击杀了末影龙
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                if (hasKilledDragon(player)) {
                    source.sendMessage(
                            Text.literal("§e玩家 §a" + player.getName().getString() +
                                    " §e已击杀末影龙!")
                    );
                }
            }

            if (dragonKilledPlayers.isEmpty()) {
                source.sendMessage(
                        Text.literal("§7目前还没有玩家击杀末影龙")
                );
            }
        } else {
            source.sendMessage(
                    Text.literal("§7末影龙击杀监听未在运行")
            );
        }

        return 1;
    }

    private static void checkForDragonKill(ServerCommandSource source) {
        boolean dragonKilledThisTick = false;
        ServerPlayerEntity killer = null;

        // 检查所有在线玩家
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (hasKilledDragon(player) && !dragonKilledPlayers.contains(player.getUuid())) {
                // 记录击杀了末影龙的玩家
                dragonKilledPlayers.add(player.getUuid());
                dragonKilledThisTick = true;
                killer = player;
                break; // 只处理第一个击杀的玩家
            }
        }

        if (dragonKilledThisTick && killer != null) {
            // 停止倒计时
            if (CountdownCommand.isCountdownRunning()) {
                // 创建一个临时的ServerCommandSource来调用stopCountdown
                ServerCommandSource tempSource = source.getServer().getCommandSource();
                CountdownCommand.stopCountdown();
            }

            // 停止监听
            cleanup();

            // 宣布比赛结束
            announceCompetitionEnd(source, killer);
        }
    }

    private static boolean hasKilledDragon(ServerPlayerEntity player) {
        PlayerAdvancementTracker advancementTracker = player.getAdvancementTracker();

        // 获取服务器注册表中的AdvancementEntry
        AdvancementEntry advancementEntry = player.getServer()
                .getAdvancementLoader()
                .get(END_DRAGON_ADVANCEMENT);

        if (advancementEntry != null) {
            AdvancementProgress progress = advancementTracker.getProgress(advancementEntry);
            return progress.isDone();
        }

        return false;
    }

    private static void announceCompetitionEnd(ServerCommandSource source, ServerPlayerEntity killer) {
        // 广播游戏结束效果
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 显示结束标题
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    Text.literal("§c§l比赛结束!")
            ));
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                    Text.literal("§e末影龙已被击杀!")
            ));

            // 播放庆祝音效
            player.playSound(net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

            // 发送聊天消息
            player.sendMessage(
                    Text.literal("§6=============================================")
            );
            player.sendMessage(
                    Text.literal("§c§l比赛结束！末影龙已被击杀！")
            );
            player.sendMessage(
                    Text.literal("§a击杀者: §e" + killer.getName().getString())
            );
            player.sendMessage(
                    Text.literal("§6恭喜获胜者！")
            );
            player.sendMessage(
                    Text.literal("§6=============================================")
            );

            // 如果是击杀者，给予特殊效果
            if (player.getUuid().equals(killer.getUuid())) {
                player.sendMessage(
                        Text.literal("§6§l恭喜你！你成功击杀了末影龙并赢得了比赛！")
                );

                // 给予击杀者特殊效果
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE,
                        20 * 60 * 5, // 5分钟
                        2,
                        true,
                        true
                ));

                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.GLOWING,
                        20 * 60 * 10, // 10分钟
                        0,
                        true,
                        true
                ));
            }

            // 创建大量粒子效果
            if (player.getServerWorld().getRegistryKey() == World.END) {
                // 如果在末地，使用末地相关粒子
                player.getServerWorld().spawnParticles(
                        player,
                        net.minecraft.particle.ParticleTypes.DRAGON_BREATH,
                        true,
                        player.getX(), player.getY() + 2, player.getZ(),
                        100,
                        1.0, 1.0, 1.0,
                        0.1
                );
            } else {
                // 在其他维度使用烟花粒子
                player.getServerWorld().spawnParticles(
                        player,
                        net.minecraft.particle.ParticleTypes.FIREWORK,
                        true,
                        player.getX(), player.getY() + 2, player.getZ(),
                        100,
                        1.0, 1.0, 1.0,
                        0.1
                );
            }
        }

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] 比赛结束！玩家 " + killer.getName().getString() +
                        " 击杀了末影龙")
        );
    }

    private static void cleanup() {
        isMonitoring = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    // 清理资源的方法
    public static void onServerStop() {
        cleanup();
    }

    // 获取监听状态的方法（供其他类使用）
    public static boolean isMonitoring() {
        return isMonitoring;
    }
}