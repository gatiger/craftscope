package io.github.gatiger.craftscope.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

public final class CraftScopeRecipeResolver {

    private CraftScopeRecipeResolver() {
    }

    public static List<CraftScopeResolvedIngredient> resolveImmediateIngredients(
            ItemStack target,
            int targetCount
    ) {
        List<CraftScopeResolvedIngredient> result =
                new ArrayList<>();

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null
                || target == null
                || target.isEmpty()
                || targetCount <= 0) {

            return result;
        }

        RecipeManager recipeManager =
                minecraft.level.getRecipeManager();

        RegistryAccess registryAccess =
                minecraft.level.registryAccess();

        RecipeHolder<?> matchingRecipe = null;

        for (RecipeHolder<?> holder :
                recipeManager.getAllRecipesFor(
                        RecipeType.CRAFTING
                )) {

            Recipe<?> recipe =
                    holder.value();

            ItemStack output =
                    recipe.getResultItem(
                            registryAccess
                    );

            if (ItemStack.isSameItem(
                    output,
                    target
            )) {
                matchingRecipe = holder;
                break;
            }
        }

        if (matchingRecipe == null) {
            return result;
        }

        Recipe<?> recipe =
                matchingRecipe.value();

        ItemStack output =
                recipe.getResultItem(
                        registryAccess
                );

        int outputCount =
                Math.max(
                        1,
                        output.getCount()
                );

        int craftsNeeded =
                (int) Math.ceil(
                        (double) targetCount
                                / outputCount
                );

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient.isEmpty()) {
                continue;
            }

            ItemStack[] possibleStacks =
                    ingredient.getItems();

            if (possibleStacks.length == 0) {
                continue;
            }

            /*
             * For this first resolver, use the first valid stack
             * from the ingredient/tag.
             *
             * Later we will expose alternate ingredient/recipe
             * choices instead of silently picking the first one.
             */
            ItemStack chosen =
                    possibleStacks[0].copy();

            result.add(
                    new CraftScopeResolvedIngredient(
                            chosen,
                            craftsNeeded
                    )
            );
        }

        return result;
    }
}