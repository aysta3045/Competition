package aysta3045.screen;

import aysta3045.Competition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

public class CompetitionManagementScreenHandler extends ScreenHandler {
    private final Inventory inventory;

    // 主构造器
    public CompetitionManagementScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }

    // 修改构造器，直接传入类型
    public CompetitionManagementScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        // 这里需要使用正确的ScreenHandlerType
        super(ScreenHandlerType.GENERIC_9X3, syncId); // 或者您可以使用自定义的类型
        this.inventory = inventory;

        // 检查库存大小
        checkSize(inventory, 27);
        inventory.onOpen(playerInventory.player);

        setupSlots(inventory, playerInventory);
    }

    private void setupSlots(Inventory inventory, PlayerInventory playerInventory) {
        int m;
        int l;

        // GUI 物品槽位 (3行9列)
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(inventory, l + m * 9, 8 + l * 18, 18 + m * 18));
            }
        }

        // 玩家物品栏 (3行9列)
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }

        // 玩家快捷栏 (1行9列)
        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 142));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot2 = this.slots.get(slot);

        if (slot2 != null && slot2.hasStack()) {
            ItemStack itemStack2 = slot2.getStack();
            itemStack = itemStack2.copy();

            if (slot < 27) {
                if (!this.insertItem(itemStack2, 27, 63, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(itemStack2, 0, 27, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot2.setStack(ItemStack.EMPTY);
            } else {
                slot2.markDirty();
            }

            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot2.onTakeItem(player, itemStack2);
        }

        return itemStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }
}