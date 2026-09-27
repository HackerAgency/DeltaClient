package aethereal.type;
import aethereal.model.DisplayNamed;
import aethereal.Lang;
import aethereal.model.Translation;

import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum AutoSwapItemType implements DisplayNamed {
    SHIELD(Lang.COMBAT_AUTOSWAP_VALUE_SHIELD, Items.SHIELD, itemStack -> {
        return itemStack.getItem() == Items.SHIELD;
    }),
    GAPPLE(Lang.COMBAT_AUTOSWAP_VALUE_GAPPLE, Items.GOLDEN_APPLE, itemStack2 -> {
        return itemStack2.getItem() == Items.GOLDEN_APPLE || itemStack2.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }),
    SPHERE(Lang.COMBAT_AUTOSWAP_VALUE_SPHERE, Items.PLAYER_HEAD, itemStack3 -> {
        return itemStack3.getItem() == Items.PLAYER_HEAD;
    }),
    FIREWORK(Lang.COMBAT_AUTOSWAP_VALUE_FIREWORK, Items.FIREWORK_ROCKET, itemStack4 -> {
        return itemStack4.getItem() == Items.FIREWORK_ROCKET;
    }),
    TOTEM(Lang.COMBAT_AUTOSWAP_VALUE_TOTEM, Items.TOTEM_OF_UNDYING, itemStack5 -> {
        return itemStack5.getItem() == Items.TOTEM_OF_UNDYING;
    }),
    FOOD(Lang.COMBAT_AUTOSWAP_VALUE_FOOD, Items.COOKED_BEEF, itemStack6 -> {
        return itemStack6.get(DataComponentTypes.FOOD) != null;
    });

    public final Translation displayName;
    public final Item displayItem;
    public final Predicate<ItemStack> filter;

    @Override
    public Translation getDisplayName() {
        return this.displayName;
    }

    public Translation displayName() {
        return this.displayName;
    }

    public Item displayItem() {
        return this.displayItem;
    }

    public Predicate<ItemStack> filter() {
        return this.filter;
    }

    AutoSwapItemType(Translation class254Var, Item item, Predicate<ItemStack> predicate) {
        this.displayName = class254Var;
        this.displayItem = item;
        this.filter = predicate;
    }
}
