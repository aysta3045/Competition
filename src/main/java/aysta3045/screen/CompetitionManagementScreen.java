package aysta3045.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;

public class CompetitionManagementScreen extends HandledScreen<CompetitionManagementScreenHandler> {
    // 使用不同的纹理，以示区分
    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/gui/container/shulker_box.png");

    // 生鸡肉按钮的位置和大小（关闭权限）
    private static final int CHICKEN_BUTTON_X = 8;
    private static final int CHICKEN_BUTTON_Y = 18;
    private static final int CHICKEN_BUTTON_SIZE = 16;

    // 生牛肉按钮的位置和大小（准备阶段）
    private static final int BEEF_BUTTON_X = 26;
    private static final int BEEF_BUTTON_Y = 18;
    private static final int BEEF_BUTTON_SIZE = 16;

    // 红色旗帜按钮的位置和大小（启动比赛）
    private static final int RED_BANNER_BUTTON_X = 44;
    private static final int RED_BANNER_BUTTON_Y = 18;
    private static final int RED_BANNER_BUTTON_SIZE = 16;

    // 时钟按钮的位置和大小（倒计时管理）
    private static final int CLOCK_BUTTON_X = 8;
    private static final int CLOCK_BUTTON_Y = 54;
    private static final int CLOCK_BUTTON_SIZE = 16;

    // 白色旗帜按钮的位置和大小（分组列表）
    private static final int BANNER_BUTTON_X = 26;
    private static final int BANNER_BUTTON_Y = 54;
    private static final int BANNER_BUTTON_SIZE = 16;

    // 生鸡肉物品堆栈
    private final ItemStack chickenStack;
    // 生牛肉物品堆栈
    private final ItemStack beefStack;
    // 红色旗帜物品堆栈
    private final ItemStack redBannerStack;
    // 时钟物品堆栈
    private final ItemStack clockStack;
    // 白色旗帜物品堆栈
    private final ItemStack bannerStack;

    // 冷却时间相关（防止连续点击）
    private long lastChickenClickTime = 0;
    private long lastBeefClickTime = 0;
    private long lastRedBannerClickTime = 0;
    private long lastClockClickTime = 0;
    private long lastBannerClickTime = 0;
    private static final long COOLDOWN_MS = 2000; // 2秒冷却

    public CompetitionManagementScreen(CompetitionManagementScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;

        // 创建生鸡肉物品堆栈并设置自定义名称
        this.chickenStack = Items.CHICKEN.getDefaultStack();
        this.chickenStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("关闭权限").styled(style -> style.withColor(0xFF5555)));

        // 创建生牛肉物品堆栈并设置自定义名称
        this.beefStack = Items.BEEF.getDefaultStack();
        this.beefStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("准备阶段").styled(style -> style.withColor(0xFFAA00)));

        // 创建红色旗帜物品堆栈并设置自定义名称
        this.redBannerStack = Items.RED_BANNER.getDefaultStack();
        this.redBannerStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("启动比赛").styled(style -> style.withColor(0xFF5555)));

        // 创建时钟物品堆栈并设置自定义名称
        this.clockStack = Items.CLOCK.getDefaultStack();
        this.clockStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("倒计时管理").styled(style -> style.withColor(0x55FF55)));

        // 创建白色旗帜物品堆栈并设置自定义名称
        this.bannerStack = Items.WHITE_BANNER.getDefaultStack();
        this.bannerStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("分组列表").styled(style -> style.withColor(0xFFFFFF)));
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 6;
        playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // 绘制背景纹理
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

        // 绘制五个按钮
        drawChickenButton(context);
        drawBeefButton(context);
        drawRedBannerButton(context);
        drawClockButton(context);
        drawBannerButton(context);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        // 绘制悬停提示
        drawChickenTooltip(context, mouseX, mouseY);
        drawBeefTooltip(context, mouseX, mouseY);
        drawRedBannerTooltip(context, mouseX, mouseY);
        drawClockTooltip(context, mouseX, mouseY);
        drawBannerTooltip(context, mouseX, mouseY);
    }

    /**
     * 绘制生鸡肉物品按钮
     */
    private void drawChickenButton(DrawContext context) {
        int x = this.x + CHICKEN_BUTTON_X;
        int y = this.y + CHICKEN_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastChickenClickTime < COOLDOWN_MS;

        // 绘制生鸡肉物品
        context.drawItem(chickenStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + CHICKEN_BUTTON_SIZE, y + CHICKEN_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastChickenClickTime);
            double progress = (double) remainingMs / COOLDOWN_MS;
            int height = (int) (CHICKEN_BUTTON_SIZE * progress);

            // 绘制冷却进度条
            context.fill(x, y + (CHICKEN_BUTTON_SIZE - height),
                    x + CHICKEN_BUTTON_SIZE, y + CHICKEN_BUTTON_SIZE,
                    0x66FF5555);
        }

        // 绘制物品数量
        context.drawItemInSlot(this.textRenderer, chickenStack, x, y);

        // 绘制红色边框提示（当鼠标悬停且不在冷却中时）
        if (isMouseOverChicken(this.client.mouse.getX() / this.client.getWindow().getScaleFactor(),
                this.client.mouse.getY() / this.client.getWindow().getScaleFactor()) &&
                !isCoolingDown) {
            context.drawBorder(x, y, CHICKEN_BUTTON_SIZE, CHICKEN_BUTTON_SIZE, 0xFFFF5555);
        }
    }

    /**
     * 绘制生牛肉物品按钮
     */
    private void drawBeefButton(DrawContext context) {
        int x = this.x + BEEF_BUTTON_X;
        int y = this.y + BEEF_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastBeefClickTime < COOLDOWN_MS;

        // 绘制生牛肉物品
        context.drawItem(beefStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + BEEF_BUTTON_SIZE, y + BEEF_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastBeefClickTime);
            double progress = (double) remainingMs / COOLDOWN_MS;
            int height = (int) (BEEF_BUTTON_SIZE * progress);

            // 绘制冷却进度条
            context.fill(x, y + (BEEF_BUTTON_SIZE - height),
                    x + BEEF_BUTTON_SIZE, y + BEEF_BUTTON_SIZE,
                    0x66FFAA00);
        }

        // 绘制物品数量
        context.drawItemInSlot(this.textRenderer, beefStack, x, y);

        // 绘制橙色边框提示（当鼠标悬停且不在冷却中时）
        if (isMouseOverBeef(this.client.mouse.getX() / this.client.getWindow().getScaleFactor(),
                this.client.mouse.getY() / this.client.getWindow().getScaleFactor()) &&
                !isCoolingDown) {
            context.drawBorder(x, y, BEEF_BUTTON_SIZE, BEEF_BUTTON_SIZE, 0xFFFFAA00);
        }
    }

    /**
     * 绘制红色旗帜按钮
     */
    private void drawRedBannerButton(DrawContext context) {
        int x = this.x + RED_BANNER_BUTTON_X;
        int y = this.y + RED_BANNER_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastRedBannerClickTime < COOLDOWN_MS;

        // 绘制红色旗帜物品
        context.drawItem(redBannerStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + RED_BANNER_BUTTON_SIZE, y + RED_BANNER_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastRedBannerClickTime);
            double progress = (double) remainingMs / COOLDOWN_MS;
            int height = (int) (RED_BANNER_BUTTON_SIZE * progress);

            // 绘制冷却进度条
            context.fill(x, y + (RED_BANNER_BUTTON_SIZE - height),
                    x + RED_BANNER_BUTTON_SIZE, y + RED_BANNER_BUTTON_SIZE,
                    0x66FF5555);
        }

        // 绘制物品数量
        context.drawItemInSlot(this.textRenderer, redBannerStack, x, y);

        // 绘制红色边框提示（当鼠标悬停且不在冷却中时）
        if (isMouseOverRedBanner(this.client.mouse.getX() / this.client.getWindow().getScaleFactor(),
                this.client.mouse.getY() / this.client.getWindow().getScaleFactor()) &&
                !isCoolingDown) {
            context.drawBorder(x, y, RED_BANNER_BUTTON_SIZE, RED_BANNER_BUTTON_SIZE, 0xFFFF5555);
        }
    }

    /**
     * 绘制时钟按钮
     */
    private void drawClockButton(DrawContext context) {
        int x = this.x + CLOCK_BUTTON_X;
        int y = this.y + CLOCK_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastClockClickTime < COOLDOWN_MS;

        // 绘制时钟物品
        context.drawItem(clockStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + CLOCK_BUTTON_SIZE, y + CLOCK_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastClockClickTime);
            double progress = (double) remainingMs / COOLDOWN_MS;
            int height = (int) (CLOCK_BUTTON_SIZE * progress);

            // 绘制冷却进度条
            context.fill(x, y + (CLOCK_BUTTON_SIZE - height),
                    x + CLOCK_BUTTON_SIZE, y + CLOCK_BUTTON_SIZE,
                    0x6655FF55);
        }

        // 绘制物品数量
        context.drawItemInSlot(this.textRenderer, clockStack, x, y);

        // 绘制绿色边框提示（当鼠标悬停且不在冷却中时）
        if (isMouseOverClock(this.client.mouse.getX() / this.client.getWindow().getScaleFactor(),
                this.client.mouse.getY() / this.client.getWindow().getScaleFactor()) &&
                !isCoolingDown) {
            context.drawBorder(x, y, CLOCK_BUTTON_SIZE, CLOCK_BUTTON_SIZE, 0xFF55FF55);
        }
    }

    /**
     * 绘制白色旗帜按钮
     */
    private void drawBannerButton(DrawContext context) {
        int x = this.x + BANNER_BUTTON_X;
        int y = this.y + BANNER_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastBannerClickTime < COOLDOWN_MS;

        // 绘制白色旗帜物品
        context.drawItem(bannerStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + BANNER_BUTTON_SIZE, y + BANNER_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastBannerClickTime);
            double progress = (double) remainingMs / COOLDOWN_MS;
            int height = (int) (BANNER_BUTTON_SIZE * progress);

            // 绘制冷却进度条
            context.fill(x, y + (BANNER_BUTTON_SIZE - height),
                    x + BANNER_BUTTON_SIZE, y + BANNER_BUTTON_SIZE,
                    0x66FFFFFF);
        }

        // 绘制物品数量
        context.drawItemInSlot(this.textRenderer, bannerStack, x, y);

        // 绘制白色边框提示（当鼠标悬停且不在冷却中时）
        if (isMouseOverBanner(this.client.mouse.getX() / this.client.getWindow().getScaleFactor(),
                this.client.mouse.getY() / this.client.getWindow().getScaleFactor()) &&
                !isCoolingDown) {
            context.drawBorder(x, y, BANNER_BUTTON_SIZE, BANNER_BUTTON_SIZE, 0xFFFFFFFF);
        }
    }

    /**
     * 绘制生鸡肉按钮的悬停提示
     */
    private void drawChickenTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在生鸡肉按钮上
        if (isMouseOverChicken(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastChickenClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastChickenClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§c左键点击：准备阶段-关闭其他玩家权限"),
                        mouseX, mouseY);
            }
        }
    }

    /**
     * 绘制生牛肉按钮的悬停提示
     */
    private void drawBeefTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在生牛肉按钮上
        if (isMouseOverBeef(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastBeefClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastBeefClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§6左键点击：准备阶段（包含倒计时）"),
                        mouseX, mouseY);
            }
        }
    }

    /**
     * 绘制红色旗帜按钮的悬停提示
     */
    private void drawRedBannerTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在红色旗帜按钮上
        if (isMouseOverRedBanner(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastRedBannerClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastRedBannerClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§c§l左键点击：启动比赛"),
                        mouseX, mouseY);
                context.drawTooltip(this.textRenderer,
                        Text.literal("§a启动60秒出生点选择，然后开始比赛"),
                        mouseX + 10, mouseY + 15);
                context.drawTooltip(this.textRenderer,
                        Text.literal("§e右键点击：取消比赛启动流程"),
                        mouseX + 10, mouseY + 30);
            }
        }
    }

    /**
     * 绘制时钟按钮的悬停提示
     */
    private void drawClockTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在时钟按钮上
        if (isMouseOverClock(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastClockClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastClockClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§a左键点击：开始倒计时（3小时30分钟）"),
                        mouseX, mouseY);
                context.drawTooltip(this.textRenderer,
                        Text.literal("§e右键点击：停止倒计时"),
                        mouseX + 10, mouseY + 15);
                context.drawTooltip(this.textRenderer,
                        Text.literal("§7Shift+左键：检查倒计时状态"),
                        mouseX + 10, mouseY + 30);
            }
        }
    }

    /**
     * 绘制白色旗帜按钮的悬停提示
     */
    private void drawBannerTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在白色旗帜按钮上
        if (isMouseOverBanner(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastBannerClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastBannerClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§f左键点击：查看分组列表"),
                        mouseX, mouseY);
                context.drawTooltip(this.textRenderer,
                        Text.literal("§7显示所有队伍及其成员"),
                        mouseX + 10, mouseY + 15);
            }
        }
    }

    /**
     * 检查鼠标是否悬停在生鸡肉按钮上
     */
    private boolean isMouseOverChicken(double mouseX, double mouseY) {
        int screenX = this.x + CHICKEN_BUTTON_X;
        int screenY = this.y + CHICKEN_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + CHICKEN_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + CHICKEN_BUTTON_SIZE;
    }

    /**
     * 检查鼠标是否悬停在生牛肉按钮上
     */
    private boolean isMouseOverBeef(double mouseX, double mouseY) {
        int screenX = this.x + BEEF_BUTTON_X;
        int screenY = this.y + BEEF_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + BEEF_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + BEEF_BUTTON_SIZE;
    }

    /**
     * 检查鼠标是否悬停在红色旗帜按钮上
     */
    private boolean isMouseOverRedBanner(double mouseX, double mouseY) {
        int screenX = this.x + RED_BANNER_BUTTON_X;
        int screenY = this.y + RED_BANNER_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + RED_BANNER_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + RED_BANNER_BUTTON_SIZE;
    }

    /**
     * 检查鼠标是否悬停在时钟按钮上
     */
    private boolean isMouseOverClock(double mouseX, double mouseY) {
        int screenX = this.x + CLOCK_BUTTON_X;
        int screenY = this.y + CLOCK_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + CLOCK_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + CLOCK_BUTTON_SIZE;
    }

    /**
     * 检查鼠标是否悬停在白色旗帜按钮上
     */
    private boolean isMouseOverBanner(double mouseX, double mouseY) {
        int screenX = this.x + BANNER_BUTTON_X;
        int screenY = this.y + BANNER_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + BANNER_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + BANNER_BUTTON_SIZE;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // 绘制标题
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        // 绘制玩家库存标签
        context.drawText(this.textRenderer, this.playerInventoryTitle,
                this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否左键点击了生鸡肉按钮
        if (button == 0 && isMouseOverChicken(mouseX, mouseY)) {
            return handleChickenButtonClick();
        }

        // 检查是否左键点击了生牛肉按钮
        if (button == 0 && isMouseOverBeef(mouseX, mouseY)) {
            return handleBeefButtonClick();
        }

        // 检查是否点击了红色旗帜按钮
        if (isMouseOverRedBanner(mouseX, mouseY)) {
            return handleRedBannerButtonClick(button);
        }

        // 检查是否点击了时钟按钮
        if (isMouseOverClock(mouseX, mouseY)) {
            return handleClockButtonClick(button);
        }

        // 检查是否左键点击了白色旗帜按钮
        if (button == 0 && isMouseOverBanner(mouseX, mouseY)) {
            return handleBannerButtonClick();
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 处理生鸡肉按钮点击
     */
    private boolean handleChickenButtonClick() {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastChickenClickTime < COOLDOWN_MS) {
            // 仍在冷却中
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        0.5f, 0.5f
                );
            }
            return true;
        }

        if (this.client != null && this.client.player != null) {
            // 使用命令方式来执行
            this.client.player.networkHandler.sendCommand("competition closecmds");

            // 播放点击声音
            this.client.player.playSound(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    0.8f, 1.0f
            );

            // 更新最后点击时间
            lastChickenClickTime = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * 处理生牛肉按钮点击
     */
    private boolean handleBeefButtonClick() {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastBeefClickTime < COOLDOWN_MS) {
            // 仍在冷却中
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        0.5f, 0.5f
                );
            }
            return true;
        }

        if (this.client != null && this.client.player != null) {
            // 使用命令方式来执行
            this.client.player.networkHandler.sendCommand("competition startprep");

            // 播放点击声音
            this.client.player.playSound(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    0.8f, 1.0f
            );

            // 更新最后点击时间
            lastBeefClickTime = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * 处理红色旗帜按钮点击
     */
    private boolean handleRedBannerButtonClick(int button) {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastRedBannerClickTime < COOLDOWN_MS) {
            // 仍在冷却中
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        0.5f, 0.5f
                );
            }
            return true;
        }

        if (this.client != null && this.client.player != null) {
            // 左键：启动比赛
            if (button == 0) {
                this.client.player.networkHandler.sendCommand("competition start");
            }
            // 右键：取消比赛启动
            else if (button == 1) {
                this.client.player.networkHandler.sendCommand("competition cancelstart");
            }

            // 播放点击声音
            this.client.player.playSound(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    0.8f, 1.0f
            );

            // 更新最后点击时间
            lastRedBannerClickTime = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * 处理时钟按钮点击
     */
    private boolean handleClockButtonClick(int button) {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastClockClickTime < COOLDOWN_MS) {
            // 仍在冷却中
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        0.5f, 0.5f
                );
            }
            return true;
        }

        if (this.client != null && this.client.player != null) {
            // 左键：开始倒计时
            if (button == 0) {
                // 检查是否按下了Shift键
                boolean hasShiftDown = hasShiftDown();

                if (hasShiftDown) {
                    // Shift+左键：检查倒计时状态
                    this.client.player.networkHandler.sendCommand("competition checkcountdown");
                } else {
                    // 普通左键：开始倒计时
                    this.client.player.networkHandler.sendCommand("competition countdown");
                }
            }
            // 右键：停止倒计时
            else if (button == 1) {
                this.client.player.networkHandler.sendCommand("competition stopcountdown");
            }

            // 播放点击声音
            this.client.player.playSound(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    0.8f, 1.0f
            );

            // 更新最后点击时间
            lastClockClickTime = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * 处理白色旗帜按钮点击
     */
    private boolean handleBannerButtonClick() {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastBannerClickTime < COOLDOWN_MS) {
            // 仍在冷却中
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        0.5f, 0.5f
                );
            }
            return true;
        }

        if (this.client != null && this.client.player != null) {
            // 发送teamlist命令
            this.client.player.networkHandler.sendCommand("competition teamlist");

            // 播放点击声音
            this.client.player.playSound(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    0.8f, 1.0f
            );

            // 更新最后点击时间
            lastBannerClickTime = System.currentTimeMillis();
        }
        return true;
    }
}