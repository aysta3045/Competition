package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;

import java.util.HashSet;
import java.util.Set;

public class StartPreparationCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("startprep")
                        .requires(source -> source.hasPermissionLevel(2)) // 需要OP权限(等级2)
                        .executes(StartPreparationCommand::execute)
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

        Set<String> preparedPlayers = new HashSet<>();

        // 准备阶段操作
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            if (target != sender) {
                // 1. 设置为旁观者模式
                target.changeGameMode(GameMode.SPECTATOR);

                // 2. 传送到指定位置 (0, 100, 0) - 修复传送方法
                target.teleport(
                        target.getServerWorld(),
                        0, 100, 0,
                        target.getYaw(), target.getPitch()
                );

                // 3. 给予黑暗效果（失明）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.BLINDNESS, // 失明效果
                        20 * 60 * 60, // 1小时（游戏刻）
                        0, // 等级0
                        true, // 环境效果
                        true // 显示粒子
                ));

                // 4. 给予缓慢效果（禁止移动）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, // 缓慢效果
                        20 * 60 * 60, // 1小时（游戏刻）
                        255, // 最高等级（几乎无法移动）
                        true, // 环境效果
                        true // 显示粒子
                ));

                // 5. 给予挖掘疲劳效果（禁止挖掘）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.MINING_FATIGUE, // 挖掘疲劳
                        20 * 60 * 60, // 1小时（游戏刻）
                        255, // 最高等级
                        true, // 环境效果
                        true // 显示粒子
                ));

                // 6. 在屏幕上显示文本
                target.networkHandler.sendPacket(new TitleS2CPacket(
                        Text.literal("§c§l准备阶段")
                ));
                target.networkHandler.sendPacket(new SubtitleS2CPacket(
                        Text.literal("§7请等待裁判")
                ));


                preparedPlayers.add(target.getName().getString());
            }
        }

        if (!preparedPlayers.isEmpty()) {
            // 创建玩家名字列表
            StringBuilder playersList = new StringBuilder();
            int index = 0;
            for (String playerName : preparedPlayers) {
                if (index > 0) {
                    if (index == preparedPlayers.size() - 1) {
                        playersList.append(" 和 ");
                    } else {
                        playersList.append(", ");
                    }
                }
                playersList.append(playerName);
                index++;
            }

            // 给发送者反馈
            source.sendMessage(
                    Text.literal("§a已成功将以下玩家设为准备状态：§e" + playersList.toString())
            );

            // 广播消息
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                player.sendMessage(
                        Text.literal("§6管理员 " + sender.getName().getString() +
                                " 启动了比赛准备阶段")
                );
            }

            return 1;
        } else {
            // 如果没有其他玩家在线
            source.sendMessage(
                    Text.literal("§7当前没有其他在线玩家，无需设置准备状态")
            );
            return 0;
        }
    }
}