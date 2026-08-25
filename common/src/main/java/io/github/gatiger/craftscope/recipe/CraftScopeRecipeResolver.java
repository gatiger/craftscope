package io.github.gatiger.craftscope.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftScopeRecipeResolver {

    private static final int MAX_DEPTH = 32;

    private CraftScopeRecipeResolver() {
    }

    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount
    ) {
        return resolveTree(
                target,
                targetCount,
                Map.of()
        );
    }

    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> recipeOverrides
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null
                || target == null
                || target.isEmpty()
                || targetCount <= 0) {

            return null;
        }

        RecipeManager recipeManager =
                minecraft.level.getRecipeManager();

        RegistryAccess registryAccess =
                minecraft.level.registryAccess();

        Set<String> activePath =
                new HashSet<>();

        CraftScopeRecipeNode root =
                resolveNode(
                        target,
                        targetCount,
                        recipeManager,
                        registryAccess,
                        activePath,
                        0,
                        List.of(target),
                        "root",
                        recipeOverrides
                );

        return new CraftScopeRecipeTree(root);
    }

    private static CraftScopeRecipeNode resolveNode(
            ItemStack requestedStack,
            int requestedCount,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            Set<String> activePath,
            int depth,
            List<ItemStack> acceptedVariants,
            String nodePath,
            Map<String, ResourceLocation> recipeOverrides
    ) {
        ItemStack stack =
                requestedStack.copy();

        String key =
                getItemKey(stack);

        if (depth >= MAX_DEPTH
                || activePath.contains(key)) {

            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        List<RecipeCandidate> candidates =
                findRankedCraftingRecipes(
                        stack,
                        recipeManager,
                        registryAccess
                );

        if (candidates.isEmpty()) {

            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        RecipeCandidate selectedCandidate =
                chooseCandidate(
                        candidates,
                        recipeOverrides.get(nodePath)
                );

        RecipeHolder<?> selectedHolder =
                selectedCandidate.holder();

        Recipe<?> recipe =
                selectedHolder.value();

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
                divideRoundUp(
                        requestedCount,
                        outputCount
                );

        List<ResourceLocation> alternativeRecipeIds =
                new ArrayList<>();

        for (RecipeCandidate candidate :
                candidates) {

            ResourceLocation id =
                    candidate.holder().id();

            if (!id.equals(
                    selectedHolder.id()
            )) {

                alternativeRecipeIds.add(id);
            }
        }

        CraftScopeRecipeNode node =
                new CraftScopeRecipeNode(
                        stack,
                        requestedCount,
                        craftsNeeded,
                        true,
                        acceptedVariants,
                        selectedHolder.id(),
                        alternativeRecipeIds
                );

        activePath.add(key);

        Map<String, IngredientGroup> grouped =
                new LinkedHashMap<>();

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient.isEmpty()) {
                continue;
            }

            ItemStack[] possibilities =
                    ingredient.getItems();

            if (possibilities.length == 0) {
                continue;
            }

            List<ItemStack> variants =
                    normalizeVariants(
                            possibilities
                    );

            if (variants.isEmpty()) {
                continue;
            }

            ItemStack representative =
                    chooseRepresentative(
                            variants
                    );

            String ingredientKey =
                    buildVariantGroupKey(
                            variants
                    );

            IngredientGroup existing =
                    grouped.get(ingredientKey);

            if (existing == null) {

                grouped.put(
                        ingredientKey,
                        new IngredientGroup(
                                representative,
                                variants,
                                1
                        )
                );

            } else {

                grouped.put(
                        ingredientKey,
                        new IngredientGroup(
                                existing.stack(),
                                existing.variants(),
                                existing.countPerCraft() + 1
                        )
                );
            }
        }

        int childIndex = 0;

        for (IngredientGroup group :
                grouped.values()) {

            int requiredIngredientCount =
                    group.countPerCraft()
                            * craftsNeeded;

            String childPath =
                    nodePath
                            + "/"
                            + childIndex
                            + ":"
                            + getItemKey(
                                    group.stack()
                            );

            CraftScopeRecipeNode child =
                    resolveNode(
                            group.stack(),
                            requiredIngredientCount,
                            recipeManager,
                            registryAccess,
                            activePath,
                            depth + 1,
                            group.variants(),
                            childPath,
                            recipeOverrides
                    );

            node.addChild(child);

            childIndex++;
        }

        activePath.remove(key);

        return node;
    }

    private static RecipeCandidate chooseCandidate(
            List<RecipeCandidate> candidates,
            ResourceLocation overrideId
    ) {
        if (overrideId != null) {

            for (RecipeCandidate candidate :
                    candidates) {

                if (candidate.holder()
                        .id()
                        .equals(overrideId)) {

                    return candidate;
                }
            }
        }

        return candidates.getFirst();
    }

    private static List<RecipeCandidate> findRankedCraftingRecipes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        List<RecipeCandidate> candidates =
                new ArrayList<>();

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

            if (output.isEmpty()
                    || !ItemStack.isSameItem(
                            output,
                            target
                    )) {

                continue;
            }

            if (looksLikeStorageConversion(
                    target,
                    recipe,
                    recipeManager,
                    registryAccess
            )) {
                continue;
            }

            candidates.add(
                    new RecipeCandidate(
                            holder,
                            scoreRecipe(
                                    target,
                                    recipe,
                                    registryAccess
                            )
                    )
            );
        }

        candidates.sort(
                Comparator
                        .comparingInt(
                                RecipeCandidate::score
                        )
                        .reversed()
                        .thenComparing(
                                candidate ->
                                        candidate.holder()
                                                .id()
                                                .toString()
                        )
        );

        return candidates;
    }

    private static int scoreRecipe(
            ItemStack target,
            Recipe<?> recipe,
            RegistryAccess registryAccess
    ) {
        int score = 0;
        int ingredientSlots = 0;

        Set<String> uniqueItems =
                new HashSet<>();

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient.isEmpty()) {
                continue;
            }

            ingredientSlots++;

            ItemStack[] possibilities =
                    ingredient.getItems();

            int breadthBonus =
                    Math.min(
                            possibilities.length,
                            3
                    );

            score += breadthBonus;

            for (ItemStack possibility :
                    possibilities) {

                if (possibility.isEmpty()) {
                    continue;
                }

                uniqueItems.add(
                        getItemKey(possibility)
                );

                if (ItemStack.isSameItem(
                        possibility,
                        target
                )) {
                    score -= 1000;
                }
            }
        }

        score += ingredientSlots * 5;

        score += Math.min(
                uniqueItems.size(),
                3
        );

        ItemStack output =
                recipe.getResultItem(
                        registryAccess
                );

        if (output.getCount() > 16) {
            score -= output.getCount();
        }

        return score;
    }

    private static boolean looksLikeStorageConversion(
            ItemStack target,
            Recipe<?> recipe,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        ItemStack output =
                recipe.getResultItem(
                        registryAccess
                );

        if (output.getCount() <= 1) {
            return false;
        }

        Ingredient onlyIngredient = null;
        int nonEmptyIngredients = 0;

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient.isEmpty()) {
                continue;
            }

            nonEmptyIngredients++;
            onlyIngredient = ingredient;

            if (nonEmptyIngredients > 1) {
                return false;
            }
        }

        if (nonEmptyIngredients != 1
                || onlyIngredient == null) {

            return false;
        }

        ItemStack[] inputs =
                onlyIngredient.getItems();

        if (inputs.length == 0) {
            return false;
        }

        for (ItemStack input :
                inputs) {

            if (input.isEmpty()) {
                continue;
            }

            if (hasReverseCraftingConversion(
                    target,
                    input,
                    recipeManager,
                    registryAccess
            )) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasReverseCraftingConversion(
            ItemStack decompressedItem,
            ItemStack compressedItem,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        for (RecipeHolder<?> holder :
                recipeManager.getAllRecipesFor(
                        RecipeType.CRAFTING
                )) {

            Recipe<?> reverseRecipe =
                    holder.value();

            ItemStack reverseOutput =
                    reverseRecipe.getResultItem(
                            registryAccess
                    );

            if (reverseOutput.isEmpty()
                    || !ItemStack.isSameItem(
                            reverseOutput,
                            compressedItem
                    )) {

                continue;
            }

            boolean containsOriginal =
                    false;

            for (Ingredient ingredient :
                    reverseRecipe.getIngredients()) {

                if (ingredient.isEmpty()) {
                    continue;
                }

                for (ItemStack possibility :
                        ingredient.getItems()) {

                    if (ItemStack.isSameItem(
                            possibility,
                            decompressedItem
                    )) {

                        containsOriginal = true;
                        break;
                    }
                }

                if (containsOriginal) {
                    break;
                }
            }

            if (containsOriginal) {
                return true;
            }
        }

        return false;
    }

    private static List<ItemStack> normalizeVariants(
            ItemStack[] possibilities
    ) {
        Map<String, ItemStack> unique =
                new LinkedHashMap<>();

        for (ItemStack stack :
                possibilities) {

            if (stack == null
                    || stack.isEmpty()) {

                continue;
            }

            unique.putIfAbsent(
                    getItemKey(stack),
                    stack.copy()
            );
        }

        List<ItemStack> result =
                new ArrayList<>(
                        unique.values()
                );

        result.sort(
                Comparator.comparing(
                        CraftScopeRecipeResolver::getItemKey
                )
        );

        return result;
    }

    private static ItemStack chooseRepresentative(
            List<ItemStack> variants
    ) {
        if (variants.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return variants
                .getFirst()
                .copy();
    }

    private static String buildVariantGroupKey(
            List<ItemStack> variants
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (ItemStack variant :
                variants) {

            if (!builder.isEmpty()) {
                builder.append("|");
            }

            builder.append(
                    getItemKey(variant)
            );
        }

        return builder.toString();
    }

    private static CraftScopeRecipeNode createLeaf(
            ItemStack stack,
            int requiredCount,
            List<ItemStack> variants
    ) {
        return new CraftScopeRecipeNode(
                stack,
                requiredCount,
                0,
                false,
                variants,
                null,
                List.of()
        );
    }

    private static int divideRoundUp(
            int value,
            int divisor
    ) {
        return (value + divisor - 1)
                / divisor;
    }

    private static String getItemKey(
            ItemStack stack
    ) {
        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        return id.toString();
    }

    private record IngredientGroup(
            ItemStack stack,
            List<ItemStack> variants,
            int countPerCraft
    ) {
    }

    private record RecipeCandidate(
            RecipeHolder<?> holder,
            int score
    ) {
    }
}