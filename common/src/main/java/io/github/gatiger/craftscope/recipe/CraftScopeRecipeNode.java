package io.github.gatiger.craftscope.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CraftScopeRecipeNode {

    private final ItemStack stack;
    private final int requiredCount;
    private final int craftsNeeded;
    private final boolean craftable;

    private final List<ItemStack> acceptedVariants =
            new ArrayList<>();

    private final List<CraftScopeRecipeNode> children =
            new ArrayList<>();

    public CraftScopeRecipeNode(
            ItemStack stack,
            int requiredCount,
            int craftsNeeded,
            boolean craftable
    ) {
        this(
                stack,
                requiredCount,
                craftsNeeded,
                craftable,
                List.of(stack)
        );
    }

    public CraftScopeRecipeNode(
            ItemStack stack,
            int requiredCount,
            int craftsNeeded,
            boolean craftable,
            List<ItemStack> acceptedVariants
    ) {
        this.stack = stack.copy();
        this.requiredCount = requiredCount;
        this.craftsNeeded = craftsNeeded;
        this.craftable = craftable;

        for (ItemStack variant : acceptedVariants) {
            if (variant != null && !variant.isEmpty()) {
                this.acceptedVariants.add(
                        variant.copy()
                );
            }
        }

        if (this.acceptedVariants.isEmpty()
                && !stack.isEmpty()) {

            this.acceptedVariants.add(
                    stack.copy()
            );
        }
    }

    public ItemStack getStack() {
        return stack.copy();
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public int getCraftsNeeded() {
        return craftsNeeded;
    }

    public boolean isCraftable() {
        return craftable;
    }

    public void addChild(
            CraftScopeRecipeNode child
    ) {
        children.add(child);
    }

    public List<CraftScopeRecipeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean hasVariants() {
        return acceptedVariants.size() > 1;
    }

    public List<ItemStack> getAcceptedVariants() {

        List<ItemStack> result =
                new ArrayList<>();

        for (ItemStack stack : acceptedVariants) {
            result.add(stack.copy());
        }

        return Collections.unmodifiableList(result);
    }
}