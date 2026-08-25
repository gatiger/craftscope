package io.github.gatiger.craftscope.recipe;

import net.minecraft.world.item.ItemStack;

public record CraftScopeResolvedIngredient(
        ItemStack stack,
        int requiredCount
) {
}