package io.github.gatiger.craftscope.client;

import net.minecraft.world.item.ItemStack;

public interface CraftScopeTargetItemReceiver {

    void craftscope$setTargetItem(ItemStack stack);

    int craftscope$getTargetSlotX();

    int craftscope$getTargetSlotY();

    int craftscope$getTargetSlotWidth();

    int craftscope$getTargetSlotHeight();
}