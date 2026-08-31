package io.github.gatiger.craftscope.recipe;

import net.minecraft.resources.ResourceLocation;
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

    private final ResourceLocation preferredRecipeId;

    private final List<ResourceLocation> alternativeRecipeIds =
            new ArrayList<>();

    /*
     * Ingredient alternatives are different from equivalent families.
     *
     * Equivalent family:
     *   Any Log / Any Planks / Red or Brown Mushroom
     *
     * Selectable alternative:
     *   Leather OR Cardboard
     *
     * A selectable alternative keeps every accepted stack for
     * inventory matching, but only the explicitly selected stack
     * is allowed to drive recursive production expansion.
     */
    private final boolean selectableIngredientAlternatives;

    /*
     * false:
     *   CraftScope is waiting for the player to choose an alternative.
     *
     * true:
     *   stack is the persisted active alternative and the other
     *   alternatives are hidden from the active production branch.
     */
    private final boolean explicitIngredientVariantSelection;

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
                List.of(stack),
                null,
                List.of(),
                false,
                false
        );
    }

    public CraftScopeRecipeNode(
            ItemStack stack,
            int requiredCount,
            int craftsNeeded,
            boolean craftable,
            List<ItemStack> acceptedVariants
    ) {
        this(
                stack,
                requiredCount,
                craftsNeeded,
                craftable,
                acceptedVariants,
                null,
                List.of(),
                false,
                false
        );
    }

    public CraftScopeRecipeNode(
            ItemStack stack,
            int requiredCount,
            int craftsNeeded,
            boolean craftable,
            List<ItemStack> acceptedVariants,
            ResourceLocation preferredRecipeId,
            List<ResourceLocation> alternativeRecipeIds
    ) {
        this(
                stack,
                requiredCount,
                craftsNeeded,
                craftable,
                acceptedVariants,
                preferredRecipeId,
                alternativeRecipeIds,
                false,
                false
        );
    }

    public CraftScopeRecipeNode(
            ItemStack stack,
            int requiredCount,
            int craftsNeeded,
            boolean craftable,
            List<ItemStack> acceptedVariants,
            ResourceLocation preferredRecipeId,
            List<ResourceLocation> alternativeRecipeIds,
            boolean selectableIngredientAlternatives,
            boolean explicitIngredientVariantSelection
    ) {
        this.stack =
                stack == null
                        ? ItemStack.EMPTY
                        : stack.copy();

        this.requiredCount =
                requiredCount;

        this.craftsNeeded =
                craftsNeeded;

        this.craftable =
                craftable;

        this.preferredRecipeId =
                preferredRecipeId;

        if (acceptedVariants != null) {
            for (ItemStack variant :
                    acceptedVariants) {

                if (variant != null
                        && !variant.isEmpty()) {

                    this.acceptedVariants.add(
                            variant.copy()
                    );
                }
            }
        }

        if (this.acceptedVariants.isEmpty()
                && !this.stack.isEmpty()) {

            this.acceptedVariants.add(
                    this.stack.copy()
            );
        }

        if (alternativeRecipeIds != null) {
            for (ResourceLocation id :
                    alternativeRecipeIds) {

                if (id != null) {
                    this.alternativeRecipeIds.add(
                            id
                    );
                }
            }
        }

        this.selectableIngredientAlternatives =
                selectableIngredientAlternatives
                        && this.acceptedVariants.size() > 1;

        this.explicitIngredientVariantSelection =
                this.selectableIngredientAlternatives
                        && explicitIngredientVariantSelection;
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
        if (child != null) {
            children.add(
                    child
            );
        }
    }

    public List<CraftScopeRecipeNode> getChildren() {
        return Collections.unmodifiableList(
                children
        );
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

        for (ItemStack variant :
                acceptedVariants) {

            result.add(
                    variant.copy()
            );
        }

        return Collections.unmodifiableList(
                result
        );
    }

    public boolean hasSelectableIngredientAlternatives() {
        return selectableIngredientAlternatives;
    }

    public boolean hasExplicitIngredientVariantSelection() {
        return explicitIngredientVariantSelection;
    }

    public ResourceLocation getPreferredRecipeId() {
        return preferredRecipeId;
    }

    public List<ResourceLocation> getAlternativeRecipeIds() {
        return Collections.unmodifiableList(
                alternativeRecipeIds
        );
    }

    public boolean hasAlternativeRecipes() {
        return !alternativeRecipeIds.isEmpty();
    }

    public int getAlternativeRecipeCount() {
        return alternativeRecipeIds.size();
    }

    public int getTotalRecipeCount() {
        if (!craftable
                || preferredRecipeId == null) {

            return 0;
        }

        return 1
                + alternativeRecipeIds.size();
    }
}