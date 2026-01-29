package aysta3045.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EndPortalMonitorCommand {

    // 存储监听状态
    private static boolean isMonitoring = false;
    private static ScheduledExecutorService scheduler;

    // 存储已发现的末地门框架位置（用于去重）
    private static final Set<String> detectedFrames = ConcurrentHashMap.newKeySet();

    // 存储框架位置和发现时间（用于清理旧记录）
    private static final Map<String, Long> frameDetectionTime = new ConcurrentHashMap<>();
    private static final long FRAME_EXPIRE_TIME = 30 * 60 * 1000; // 30分钟后重新播报

    // 存储所有已知框架的详细信息
    private static final Map<String, FrameInfo> frameInfoMap = new ConcurrentHashMap<>();

    // 框架去重距离（格）
    private static final int FRAME_MIN_DISTANCE = 10; // 两个框架之间的最小距离，小于此距离视为同一个框架

    // 框架信息类
    public static class FrameInfo {
        public final BlockPos framePosition;
        public final BlockPos discovererPosition;
        public final World dimension;
        public final long discoveryTime;
        public final String discoveredBy;

        public FrameInfo(BlockPos framePosition, BlockPos discovererPosition, World dimension, String discoveredBy) {
            this.framePosition = framePosition;
            this.discovererPosition = discovererPosition;
            this.dimension = dimension;
            this.discoveryTime = System.currentTimeMillis();
            this.discoveredBy = discoveredBy;
        }

        @Override
        public String toString() {
            return String.format("(%d, %d, %d)", framePosition.getX(), framePosition.getY(), framePosition.getZ());
        }

        public String getDiscovererPositionString() {
            return String.format("(%d, %d, %d)", discovererPosition.getX(), discovererPosition.getY(), discovererPosition.getZ());
        }

        // 计算与另一个框架的距离
        public double distanceTo(BlockPos otherPos) {
            return Math.sqrt(
                    Math.pow(framePosition.getX() - otherPos.getX(), 2) +
                            Math.pow(framePosition.getY() - otherPos.getY(), 2) +
                            Math.pow(framePosition.getZ() - otherPos.getZ(), 2)
            );
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("competition")
                .then(CommandManager.literal("endportalmonitor")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndPortalMonitorCommand::startMonitoring)
                )
                .then(CommandManager.literal("stopendportalmonitor")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndPortalMonitorCommand::stopMonitoring)
                )
                .then(CommandManager.literal("listportals")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndPortalMonitorCommand::listPortals)
                )
                .then(CommandManager.literal("clearportals")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndPortalMonitorCommand::clearPortals)
                )
                .then(CommandManager.literal("testportal")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(EndPortalMonitorCommand::testPortalBroadcast)
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
                    Text.literal("§c末地门框架监听已经在运行中!")
                            .styled(style -> style.withColor(0xFF5555))
            );
            return 0;
        }

        // 初始化调度器
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }

        isMonitoring = true;

        // 启动监听任务（每秒检查一次）
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForEndPortalFrames(source);
            } catch (Exception e) {
                System.err.println("末地门框架监听异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);

        // 清理过期框架记录（每分钟检查一次）
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupOldFrames();
            } catch (Exception e) {
                System.err.println("清理框架记录异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 5, 60, TimeUnit.SECONDS);

        // 广播监听开始消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §a末地门框架监听已启动!")
                            .styled(style -> style.withColor(0x55FF55))
            );

            player.sendMessage(
                    Text.literal("§6[比赛系统] §e新发现的末地门框架将被全局播报!")
            );
        }

        source.sendMessage(
                Text.literal("§a已启动末地门框架监听! 新发现的框架将被自动播报。")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() + " 启动了末地门框架监听")
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
                    Text.literal("§c末地门框架监听未在运行!")
            );
            return 0;
        }

        cleanup();

        // 广播监听停止消息
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("§6[比赛系统] §c末地门框架监听已停止!")
            );
        }

        source.sendMessage(
                Text.literal("§a已停止末地门框架监听")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() + " 停止了末地门框架监听")
        );

        return 1;
    }

    private static int listPortals(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (frameInfoMap.isEmpty()) {
            source.sendMessage(
                    Text.literal("§7当前没有发现任何末地门框架")
            );
            return 0;
        }

        // 显示已发现框架列表
        source.sendMessage(
                Text.literal("§6========== 已发现的末地门框架 ==========")
                        .styled(style -> style.withColor(0xFFAA00))
        );

        int index = 1;
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, FrameInfo> entry : frameInfoMap.entrySet()) {
            FrameInfo info = entry.getValue();
            String dimensionName = getDimensionName(info.dimension);

            // 计算发现时间（分钟前）
            long minutesAgo = (currentTime - info.discoveryTime) / (60 * 1000);

            source.sendMessage(
                    Text.literal("§e" + index + ". §a框架坐标: §e" + info + " §7(" + dimensionName + ")")
            );

            source.sendMessage(
                    Text.literal("  §7发现者: §f" + info.discoveredBy + " §7| §7发现时间: §f" +
                            minutesAgo + "分钟前")
            );

            source.sendMessage(
                    Text.literal("  §7玩家位置: §f" + info.getDiscovererPositionString())
            );

            // 显示框架是否仍在活动（检查方块是否存在且是否为末地门框架）
            boolean isActive = isFrameStillActive(info.dimension, info.framePosition);
            if (isActive) {
                source.sendMessage(
                        Text.literal("  §a方块状态: §2§l存在")
                );
            } else {
                source.sendMessage(
                        Text.literal("  §c方块状态: §4§l已移除")
                );
            }

            source.sendMessage(Text.literal(""));
            index++;
        }

        source.sendMessage(
                Text.literal("§6使用 §e/competition clearportals §6清除所有记录")
        );

        return frameInfoMap.size();
    }

    private static int clearPortals(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        int clearedCount = frameInfoMap.size();

        // 清除所有记录
        detectedFrames.clear();
        frameDetectionTime.clear();
        frameInfoMap.clear();

        source.sendMessage(
                Text.literal("§a已清除 §e" + clearedCount + " §a个框架记录")
        );

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] " + sender.getName().getString() +
                        " 清除了所有框架记录 (" + clearedCount + "个)")
        );

        return clearedCount;
    }

    private static int testPortalBroadcast(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity sender = source.getPlayer();

        if (sender == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令!"));
            return 0;
        }

        // 模拟发现一个末地门框架
        BlockPos testFramePos = sender.getBlockPos();
        World world = sender.getServerWorld();

        // 测试播报（使用玩家当前位置作为发现者位置）
        broadcastFrameDiscovery(source, testFramePos, sender.getBlockPos(), world, "测试玩家");

        source.sendMessage(
                Text.literal("§a已发送测试末地门框架播报!")
        );

        return 1;
    }

    private static void checkForEndPortalFrames(ServerCommandSource source) {
        // 检查所有在线玩家周围区域的末地门框架
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            checkPlayerAreaForFrames(player, source);
        }
    }

    private static void checkPlayerAreaForFrames(ServerPlayerEntity player, ServerCommandSource source) {
        World world = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();

        int radius = 10;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    BlockPos checkPos = playerPos.add(dx, dy, dz);

                    // 检查是否是末地门框架方块
                    if (isEndPortalFrameBlock(world, checkPos)) {
                        // 检查是否已有框架在附近
                        if (!isFrameNearExistingFrames(world, checkPos)) {
                            // 生成唯一标识符
                            String frameKey = generateFrameKey(world, checkPos);

                            // 检查是否已发现过且未过期
                            if (!isFrameRecentlyDetected(frameKey)) {
                                // 记录新的框架
                                detectedFrames.add(frameKey);
                                frameDetectionTime.put(frameKey, System.currentTimeMillis());

                                // 创建框架信息（记录玩家当前位置）
                                FrameInfo frameInfo = new FrameInfo(checkPos, playerPos, world, player.getName().getString());
                                frameInfoMap.put(frameKey, frameInfo);

                                // 全局播报
                                broadcastFrameDiscovery(source, checkPos, playerPos, world, player.getName().getString());
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isFrameNearExistingFrames(World world, BlockPos pos) {
        String currentDimension = world.getRegistryKey().getValue().toString();

        // 检查当前维度中是否有已记录的框架距离太近
        for (Map.Entry<String, FrameInfo> entry : frameInfoMap.entrySet()) {
            FrameInfo existingFrame = entry.getValue();

            // 检查是否在同一维度
            if (existingFrame.dimension.getRegistryKey().getValue().toString().equals(currentDimension)) {
                // 计算距离
                double distance = existingFrame.distanceTo(pos);

                // 如果距离小于最小距离阈值，认为是同一个框架区域
                if (distance < FRAME_MIN_DISTANCE) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isEndPortalFrameBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        // 检查是否是末地门框架方块
        return state.getBlock() instanceof EndPortalFrameBlock || state.isOf(Blocks.END_PORTAL_FRAME);
    }

    private static String generateFrameKey(World world, BlockPos pos) {
        // 使用维度ID和坐标创建唯一键
        return world.getRegistryKey().getValue().toString() + ":" +
                pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static boolean isFrameRecentlyDetected(String frameKey) {
        Long detectionTime = frameDetectionTime.get(frameKey);
        if (detectionTime == null) {
            return false;
        }

        // 检查是否在过期时间内
        return (System.currentTimeMillis() - detectionTime) < FRAME_EXPIRE_TIME;
    }

    private static void broadcastFrameDiscovery(ServerCommandSource source, BlockPos framePos, BlockPos playerPos, World world, String discoverer) {
        String dimensionName = getDimensionName(world);
        String playerCoordinates = formatCoordinates(playerPos);
        String frameCoordinates = formatCoordinates(framePos);

        // 创建广播消息 - 主要播报玩家位置
        Text broadcastTitle = Text.literal("§6[末地门框架] §e发现末地门框架!");
        Text playerPositionMessage = Text.literal("§6发现者: §a" + discoverer + " §6位置: §e" + playerCoordinates);
        Text frameInfoMessage = Text.literal("§6框架位置: §e" + frameCoordinates + " §6(" + dimensionName + ")");

        // 向所有玩家广播
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            // 发送聊天消息
            player.sendMessage(Text.literal("§6============================================="));
            player.sendMessage(broadcastTitle);
            player.sendMessage(playerPositionMessage);
            player.sendMessage(frameInfoMessage);
            player.sendMessage(Text.literal("§6============================================="));
        }

        // 记录到控制台
        source.getServer().sendMessage(
                Text.literal("[比赛系统] 末地门框架发现: 发现者 " + discoverer +
                        " 在 " + playerCoordinates + " 发现了框架 (框架位置: " +
                        frameCoordinates + " " + dimensionName + ")")
        );
    }

    private static String getDimensionName(World world) {
        net.minecraft.registry.RegistryKey<World> key = world.getRegistryKey();

        if (key == World.OVERWORLD) {
            return "主世界";
        } else if (key == World.NETHER) {
            return "下界";
        } else if (key == World.END) {
            return "末地";
        } else {
            return key.getValue().getPath();
        }
    }

    private static String formatCoordinates(BlockPos pos) {
        return String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean isFrameStillActive(World world, BlockPos pos) {
        // 检查框架方块是否仍然存在且是末地门框架
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof EndPortalFrameBlock || state.isOf(Blocks.END_PORTAL_FRAME);
    }

    private static void cleanupOldFrames() {
        long currentTime = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        // 找出过期的框架记录
        for (Map.Entry<String, Long> entry : frameDetectionTime.entrySet()) {
            if ((currentTime - entry.getValue()) > FRAME_EXPIRE_TIME) {
                toRemove.add(entry.getKey());
            }
        }

        // 移除过期记录
        for (String key : toRemove) {
            detectedFrames.remove(key);
            frameDetectionTime.remove(key);
            frameInfoMap.remove(key);
        }

        if (!toRemove.isEmpty()) {
            System.out.println("[比赛系统] 清理了 " + toRemove.size() + " 个过期的框架记录");
        }
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
        detectedFrames.clear();
        frameDetectionTime.clear();
        frameInfoMap.clear();
    }

    // 获取监听状态的方法（供其他类使用）
    public static boolean isMonitoring() {
        return isMonitoring;
    }

    // 获取已发现的框架数量
    public static int getDetectedFrameCount() {
        return frameInfoMap.size();
    }
}