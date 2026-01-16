package aysta3045.command;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class CommandBlocker {

    public static void initialize() {
        // 监听玩家加入事件
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (CloseCommandsCommand.isPlayerDisabled(player)) {
                server.execute(() -> {
                    // 确保被禁用玩家没有OP权限
                    server.getPlayerManager().removeFromOperators(player.getGameProfile());

                    // 发送命令树更新
                    server.getPlayerManager().sendCommandTree(player);

                    // 发送警告消息
                    player.sendMessage(
                            Text.literal("§c警告：你的指令权限已被管理员关闭！")
                                    .styled(style -> style.withColor(0xFF5555)),
                            false
                    );
                });
            } else {
                // 如果玩家不在禁用列表中，确保他们有OP权限（如果他们是OP的话）
                server.execute(() -> {
                    // 这里我们可以检查玩家是否是OP，如果是则确保他们被添加到OP列表
                    // 注意：这个逻辑取决于你的服务器配置，你可能不需要这个
                });
            }
        });

        // 定期检查被禁用玩家的权限
        ServerTickEvents.START_SERVER_TICK.register(CommandBlocker::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        // 每5秒（100tick）检查一次
        if (server.getTicks() % 100 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (CloseCommandsCommand.isPlayerDisabled(player)) {
                    // 确保被禁用玩家不是OP
                    if (server.getPlayerManager().isOperator(player.getGameProfile())) {
                        server.getPlayerManager().removeFromOperators(player.getGameProfile());
                        server.getPlayerManager().sendCommandTree(player);
                    }

                }
            }
        }
    }
}