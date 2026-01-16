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

    // 生鸡肉按钮的位置和大小
    private static final int CHICKEN_BUTTON_X = 8; // 右上角，距离右边缘8像素
    private static final int CHICKEN_BUTTON_Y = 18;
    private static final int CHICKEN_BUTTON_SIZE = 16;

    // 生鸡肉物品堆栈
    private final ItemStack chickenStack;
    // 冷却时间相关（防止连续点击）
    private long lastClickTime = 0;
    private static final long COOLDOWN_MS = 2000; // 2秒冷却

    public CompetitionManagementScreen(CompetitionManagementScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;

        // 创建生鸡肉物品堆栈并设置自定义名称
        this.chickenStack = Items.CHICKEN.getDefaultStack();
        this.chickenStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("关闭指令权限").styled(style -> style.withColor(0xFF5555)));
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

        // 绘制生鸡肉物品按钮
        drawChickenButton(context);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        // 绘制悬停提示
        drawChickenTooltip(context, mouseX, mouseY);
    }

    /**
     * 绘制生鸡肉物品按钮
     */
    private void drawChickenButton(DrawContext context) {
        int x = this.x + CHICKEN_BUTTON_X;
        int y = this.y + CHICKEN_BUTTON_Y;

        // 检查是否在冷却中
        boolean isCoolingDown = System.currentTimeMillis() - lastClickTime < COOLDOWN_MS;

        // 绘制生鸡肉物品
        context.drawItem(chickenStack, x, y);

        // 如果在冷却中，添加灰色覆盖层
        if (isCoolingDown) {
            context.fill(x, y, x + CHICKEN_BUTTON_SIZE, y + CHICKEN_BUTTON_SIZE, 0x88000000);

            // 计算剩余冷却时间
            long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastClickTime);
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
     * 绘制生鸡肉按钮的悬停提示
     */
    private void drawChickenTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在生鸡肉按钮上
        if (isMouseOverChicken(mouseX, mouseY)) {
            boolean isCoolingDown = System.currentTimeMillis() - lastClickTime < COOLDOWN_MS;

            if (isCoolingDown) {
                long remainingMs = COOLDOWN_MS - (System.currentTimeMillis() - lastClickTime);
                double remainingSeconds = remainingMs / 1000.0;

                context.drawTooltip(this.textRenderer,
                        Text.literal("§c冷却中... (" + String.format("%.1f", remainingSeconds) + "秒后可用)"),
                        mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer,
                        Text.literal("§c左键点击：关闭除自己外所有玩家的指令权限\n§7§o需要管理员权限(OP)\n§7§o冷却时间: 2秒"),
                        mouseX, mouseY);
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
            // 检查冷却时间
            if (System.currentTimeMillis() - lastClickTime < COOLDOWN_MS) {
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
                lastClickTime = System.currentTimeMillis();

                // 关闭GUI
                this.client.setScreen(null);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}