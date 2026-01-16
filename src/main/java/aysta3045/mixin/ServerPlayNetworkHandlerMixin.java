package aysta3045.mixin;

import aysta3045.command.CloseCommandsCommand;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "onCommandExecution",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        String command = packet.command();

        // 检查玩家是否被禁用了指令
        if (CloseCommandsCommand.isPlayerDisabled(player)) {
            // 检查是否允许的命令
            if (!isAllowedCommand(command)) {
                // 阻止命令执行
                player.sendMessage(
                        Text.literal("§c错误：你的指令权限已被管理员关闭！")
                                .styled(style -> style.withColor(0xFF5555)),
                        false
                );
                ci.cancel();
            }
        }
    }

    private boolean isAllowedCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        String lowerCommand = command.toLowerCase().trim();

        // 允许聊天相关命令
        return lowerCommand.startsWith("msg ") ||
                lowerCommand.startsWith("tell ") ||
                lowerCommand.startsWith("whisper ") ||
                lowerCommand.startsWith("w ") ||
                lowerCommand.startsWith("me ") ||
                lowerCommand.startsWith("say ") ||
                lowerCommand.equals("help") ||
                lowerCommand.equals("list") ||
                lowerCommand.equals("online");
    }
}