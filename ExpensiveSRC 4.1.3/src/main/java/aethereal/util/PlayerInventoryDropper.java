package aethereal.util;

import net.minecraft.item.Item;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;

public class PlayerInventoryDropper implements ItemDropper {
    @Override
    public void dropItems(ScreenHandler screenHandler, int i, Item item) {
        int size = screenHandler instanceof GenericContainerScreenHandler ? ((GenericContainerScreenHandler) screenHandler).getInventory().size() : 0;
        DefaultedList defaultedList = screenHandler.slots;
        for (int i2 = size; i2 < defaultedList.size(); i2++) {
            if (!(screenHandler instanceof PlayerScreenHandler) || i2 < 5 || i2 > 8) {
                Slot slot = (Slot) defaultedList.get(i2);
                if (!slot.getStack().isEmpty() && slot.getStack().getItem() == item) {
                    PlayerActionUtil.INSTANCE.windowClick(SlotActionType.THROW, i2, 1);
                }
            }
        }
    }
}
