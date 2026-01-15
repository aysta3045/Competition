package aysta3045.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;

public class CompetitionScreen extends HandledScreen<CompetitionScreenHandler> {
    // 使用原版箱子纹理
    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/gui/container/shulker_box.png");

    // 指南针按钮的位置和大小
    private static final int COMPASS_BUTTON_X = 8;
    private static final int COMPASS_BUTTON_Y = 18;
    private static final int COMPASS_BUTTON_SIZE = 16;

    // 指南针物品堆栈
    private final ItemStack compassStack;

    public CompetitionScreen(CompetitionScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;

        // 创建指南针物品堆栈并设置自定义名称
        this.compassStack = Items.COMPASS.getDefaultStack();
        this.compassStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("比赛管理"));
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
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 绘制指南针物品
        drawCompassItem(context);

        drawMouseoverTooltip(context, mouseX, mouseY);

        // 绘制悬停提示
        drawCompassTooltip(context, mouseX, mouseY);
    }

    /**
     * 绘制指南针物品
     */
    private void drawCompassItem(DrawContext context) {
        int x = this.x + COMPASS_BUTTON_X;
        int y = this.y + COMPASS_BUTTON_Y;

        // 绘制指南针物品
        context.drawItem(compassStack, x, y);
        // 绘制物品数量（1个）
        context.drawItemInSlot(this.textRenderer, compassStack, x, y);
    }

    /**
     * 绘制指南针的悬停提示
     */
    private void drawCompassTooltip(DrawContext context, int mouseX, int mouseY) {
        // 检查鼠标是否悬停在指南针上
        if (isMouseOverCompass(mouseX, mouseY)) {
            // 绘制工具提示
            context.drawTooltip(this.textRenderer, Text.literal("左键点击打开比赛管理"), mouseX, mouseY);
        }
    }

    /**
     * 检查鼠标是否悬停在指南针上
     */
    private boolean isMouseOverCompass(double mouseX, double mouseY) {
        int screenX = this.x + COMPASS_BUTTON_X;
        int screenY = this.y + COMPASS_BUTTON_Y;
        return mouseX >= screenX && mouseX < screenX + COMPASS_BUTTON_SIZE &&
                mouseY >= screenY && mouseY < screenY + COMPASS_BUTTON_SIZE;
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
        // 检查是否左键点击了指南针
        if (button == 0 && isMouseOverCompass(mouseX, mouseY)) {
            // 打开新的比赛管理GUI
            openCompetitionManagementScreen();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 打开比赛管理GUI
     */
    private void openCompetitionManagementScreen() {
        if (this.client != null && this.client.player != null) {
            // 创建新的管理界面屏幕处理器
            CompetitionManagementScreenHandler newHandler = new CompetitionManagementScreenHandler(
                    this.handler.syncId + 1,  // 使用新的syncId
                    this.client.player.getInventory()
            );

            // 打开新的管理GUI，标题为"比赛管理"
            this.client.setScreen(new CompetitionManagementScreen(
                    newHandler,
                    this.client.player.getInventory(),
                    Text.literal("比赛管理")
            ));
        }
    }
}