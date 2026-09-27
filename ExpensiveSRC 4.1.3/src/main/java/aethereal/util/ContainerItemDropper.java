package aethereal.util;

import net.minecraft.item.Item;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class ContainerItemDropper implements ItemDropper {
    @Override
    public void dropItems(ScreenHandler screenHandler, int i, Item item) {
        if (screenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler genericContainerScreenHandler = (GenericContainerScreenHandler) screenHandler;
            int size = genericContainerScreenHandler.getInventory().size();
            for (int i2 = 0; i2 < size; i2++) {
                Slot slot = (Slot) genericContainerScreenHandler.slots.get(i2);
                if (!slot.getStack().isEmpty() && slot.getStack().getItem() == item) {
                    PlayerActionUtil.INSTANCE.windowClick(SlotActionType.THROW, i2, 1);
                }
            }
        }
    }
}
