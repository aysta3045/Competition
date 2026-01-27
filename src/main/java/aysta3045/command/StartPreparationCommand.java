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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class StartPreparationCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("startprep")
                        .requires(source -> source.hasPermissionLevel(2))
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
                // 0. 清理玩家背包（不清空末影箱）
                clearPlayerInventory(target);

                // 1. 设置为冒险模式（不能破坏方块）
                target.changeGameMode(GameMode.ADVENTURE);

                // 2. 传送到指定位置 (0, 100, 0)
                target.teleport(
                        target.getServerWorld(),
                        0, 300, 0,
                        target.getYaw(), target.getPitch()
                );

                // 3. 给予失明效果
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.BLINDNESS,
                        20 * 60 * 60, // 1小时
                        0,
                        true,
                        true
                ));

                // 4. 给予挖掘疲劳效果（禁止挖掘）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.MINING_FATIGUE,
                        20 * 60 * 60, // 1小时
                        255, // 最高等级
                        true,
                        true
                ));

                // 5. 给予虚弱效果（防止攻击）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS,
                        20 * 60 * 60, // 1小时
                        255, // 最高等级
                        true,
                        true
                ));

                // 6. 给予缓慢效果（虽然效果有限，但配合漂浮效果更好）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS,
                        20 * 60 * 60, // 1小时
                        255, // 最高等级
                        true,
                        true
                ));

                // 7. 给予抗性提升效果（防止受到伤害）
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE,
                        20 * 60 * 60, // 1小时
                        255, // 最高等级
                        true,
                        true
                ));

                // 8. 在屏幕上显示文本
                target.networkHandler.sendPacket(new TitleS2CPacket(
                        Text.literal("§c§l准备阶段")
                ));
                target.networkHandler.sendPacket(new SubtitleS2CPacket(
                        Text.literal("§7请等待裁判")
                ));

                // 9. 播放音效
                target.playSound(SoundEvents.BLOCK_BELL_USE, 1.0f, 1.0f);

                // 10. 创建粒子效果
                target.getServerWorld().spawnParticles(
                        target,
                        ParticleTypes.TOTEM_OF_UNDYING,
                        true,
                        target.getX(),
                        target.getY() + 2,
                        target.getZ(),
                        20,
                        0.5,
                        0.5,
                        0.5,
                        0.1
                );

                preparedPlayers.add(target.getName().getString());

                // 11. 记录到控制台
                source.getServer().sendMessage(
                        Text.literal("[比赛系统] " + target.getName().getString() + " 已进入准备阶段")
                );
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

    /**
     * 清理玩家背包（不包括末影箱）
     */
    private static void clearPlayerInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        // 清理主物品栏（36个槽位）
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        // 清理装备栏（头盔、胸甲、护腿、靴子）
        inventory.armor.set(0, ItemStack.EMPTY);
        inventory.armor.set(1, ItemStack.EMPTY);
        inventory.armor.set(2, ItemStack.EMPTY);
        inventory.armor.set(3, ItemStack.EMPTY);

        // 清理副手
        inventory.offHand.set(0, ItemStack.EMPTY);

        // 标记物品栏已更改
        inventory.markDirty();

        // 可选：在控制台记录背包清理
        System.out.println("[比赛系统] 已清理玩家 " + player.getName().getString() + " 的背包");

        // 给玩家发送提示信息
        player.sendMessage(Text.literal("§c你的背包已被清空，准备比赛！"), false);
    }
}