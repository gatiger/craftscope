package io.github.gatiger.craftscope.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
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

        return new CraftScopeRecipeTree(
                root
        );
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
                findRankedProductionRecipes(
                        stack,
                        recipeManager,
                        registryAccess
                );

        /*
         * Prevent indirect reverse loops.
         *
         * Example:
         *
         * Iron Ingot
         *   -> Iron Nuggets
         *      -> Iron Ingot
         *
         * If a candidate requires something that is already
         * higher in the active branch, do not follow it.
         */
        candidates.removeIf(
                candidate ->
                        candidateUsesActivePathItem(
                                candidate,
                                activePath
                        )
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
                    candidate.primaryRecipeId();

            if (!id.equals(
                    selectedCandidate.primaryRecipeId()
            )) {

                alternativeRecipeIds.add(
                        id
                );
            }
        }

        CraftScopeRecipeNode node =
                new CraftScopeRecipeNode(
                        stack,
                        requestedCount,
                        craftsNeeded,
                        true,
                        acceptedVariants,
                        selectedCandidate.primaryRecipeId(),
                        alternativeRecipeIds
                );

        activePath.add(
                key
        );

        /*
         * Use the selected route's normalized ingredient groups.
         *
         * This lets equivalent inputs such as:
         *
         * Iron Ore
         * Deepslate Iron Ore
         *
         * become one accepted-variant group.
         */
        for (IngredientGroup group :
                selectedCandidate.ingredientGroups()) {

            int requiredIngredientCount =
                    group.countPerCraft()
                            * craftsNeeded;

            int childIndex =
                    node.getChildren().size();

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

            node.addChild(
                    child
            );
        }

        activePath.remove(
                key
        );

        return node;
    }

    private static boolean candidateUsesActivePathItem(
            RecipeCandidate candidate,
            Set<String> activePath
    ) {
        if (activePath.isEmpty()) {
            return false;
        }

        for (IngredientGroup group :
                candidate.ingredientGroups()) {

            for (ItemStack variant :
                    group.variants()) {

                if (activePath.contains(
                        getItemKey(variant)
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    private static RecipeCandidate chooseCandidate(
            List<RecipeCandidate> candidates,
            ResourceLocation overrideId
    ) {
        if (overrideId != null) {

            for (RecipeCandidate candidate :
                    candidates) {

                if (candidate.matchesRecipeId(
                        overrideId
                )) {

                    return candidate;
                }
            }
        }

        return candidates.getFirst();
    }

    private static List<RecipeCandidate> findRankedProductionRecipes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        List<RecipeCandidate> rawCandidates =
                new ArrayList<>();

        collectCandidates(
                target,
                recipeManager,
                registryAccess,
                RecipeType.CRAFTING,
                ProductionType.CRAFTING,
                rawCandidates
        );

        collectCandidates(
                target,
                recipeManager,
                registryAccess,
                RecipeType.SMELTING,
                ProductionType.SMELTING,
                rawCandidates
        );

        collectCandidates(
                target,
                recipeManager,
                registryAccess,
                RecipeType.BLASTING,
                ProductionType.BLASTING,
                rawCandidates
        );

        List<RecipeCandidate> candidates =
                mergeEquivalentCookingRoutes(
                        rawCandidates
                );

        candidates.sort(
                Comparator
                        .comparingInt(
                                RecipeCandidate::score
                        )
                        .reversed()
                        .thenComparing(
                                candidate ->
                                        candidate
                                                .primaryRecipeId()
                                                .toString()
                        )
        );

        return candidates;
    }

    private static <I extends RecipeInput, T extends Recipe<I>>
    void collectCandidates(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            RecipeType<T> recipeType,
            ProductionType productionType,
            List<RecipeCandidate> candidates
    ) {
        for (RecipeHolder<T> holder :
                recipeManager.getAllRecipesFor(
                        recipeType
                )) {

            T recipe =
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

            /*
             * Storage-conversion filtering applies to
             * crafting-table recipes.
             *
             * Furnace and blast-furnace recipes are forward
             * processing routes and should not be filtered here.
             */
            if (productionType
                    == ProductionType.CRAFTING
                    && looksLikeStorageConversion(
                            target,
                            recipe,
                            recipeManager,
                            registryAccess
                    )) {

                continue;
            }

            List<IngredientGroup> ingredientGroups =
                    buildIngredientGroups(
                            recipe
                    );

            int score =
                    scoreRecipe(
                            target,
                            recipe,
                            registryAccess,
                            productionType
                    );

            candidates.add(
                    new RecipeCandidate(
                            holder,
                            productionType,
                            score,
                            ingredientGroups,
                            List.of(
                                    holder.id()
                            )
                    )
            );
        }
    }

    private static List<IngredientGroup> buildIngredientGroups(
            Recipe<?> recipe
    ) {
        Map<String, IngredientGroup> grouped =
                new LinkedHashMap<>();

        /*
         * Only explicit recipe ingredients are included.
         *
         * Furnace/blast-furnace fuel is NOT part of the recipe
         * ingredient list, so CraftScope will not invent a coal,
         * charcoal, lava, etc. requirement.
         */
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
                    grouped.get(
                            ingredientKey
                    );

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

        return new ArrayList<>(
                grouped.values()
        );
    }

    /*
     * Collapse equivalent furnace/blast-furnace material routes.
     *
     * Example:
     *
     * Iron Ore -> Iron Ingot
     * Deepslate Iron Ore -> Iron Ingot
     *
     * become one:
     *
     * Any Iron Ore -> Iron Ingot
     *
     * Smelting and blasting versions are also collapsed because
     * they require the same actual material.
     *
     * Raw Iron remains a separate route.
     * Iron Nuggets remain a separate crafting route.
     */
    private static List<RecipeCandidate> mergeEquivalentCookingRoutes(
            List<RecipeCandidate> rawCandidates
    ) {
        List<RecipeCandidate> result =
                new ArrayList<>();

        Map<String, List<RecipeCandidate>> cookingGroups =
                new LinkedHashMap<>();

        for (RecipeCandidate candidate :
                rawCandidates) {

            if (candidate.productionType()
                    == ProductionType.CRAFTING) {

                result.add(
                        candidate
                );

                continue;
            }

            String routeKey =
                    buildCookingRouteKey(
                            candidate
                    );

            cookingGroups
                    .computeIfAbsent(
                            routeKey,
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            candidate
                    );
        }

        for (List<RecipeCandidate> group :
                cookingGroups.values()) {

            result.add(
                    mergeCookingGroup(
                            group
                    )
            );
        }

        return result;
    }

    private static String buildCookingRouteKey(
            RecipeCandidate candidate
    ) {
        if (candidate
                .ingredientGroups()
                .size() != 1) {

            return "recipe:"
                    + candidate
                    .primaryRecipeId();
        }

        IngredientGroup group =
                candidate
                        .ingredientGroups()
                        .getFirst();

        Set<String> familyKeys =
                new HashSet<>();

        for (ItemStack variant :
                group.variants()) {

            familyKeys.add(
                    getCookingFamilyKey(
                            variant
                    )
            );
        }

        List<String> sorted =
                new ArrayList<>(
                        familyKeys
                );

        sorted.sort(
                String::compareTo
        );

        return String.join(
                "|",
                sorted
        );
    }

    /*
     * Iron Ore and Deepslate Iron Ore share the same cooking
     * family.
     *
     * This also works for similarly named modded ores.
     */
    private static String getCookingFamilyKey(
            ItemStack stack
    ) {
        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        String path =
                id.getPath();

        if (path.startsWith(
                "deepslate_"
        )
                && path.endsWith(
                "_ore"
        )) {

            path =
                    path.substring(
                            "deepslate_".length()
                    );
        }

        return id.getNamespace()
                + ":"
                + path;
    }

    private static RecipeCandidate mergeCookingGroup(
            List<RecipeCandidate> candidates
    ) {
        /*
         * Explicit generic type is important here.
         *
         * Without <RecipeCandidate>, Java can infer Object for
         * the chained comparator in this environment.
         */
        RecipeCandidate representative =
                candidates.stream()
                        .min(
                                Comparator
                                        .<RecipeCandidate>comparingInt(
                                                candidate ->
                                                        productionMethodOrder(
                                                                candidate.productionType()
                                                        )
                                        )
                                        .thenComparing(
                                                candidate ->
                                                        candidate
                                                                .primaryRecipeId()
                                                                .toString()
                                        )
                        )
                        .orElseThrow();

        List<ItemStack> combinedVariants =
                new ArrayList<>();

        List<ResourceLocation> equivalentRecipeIds =
                new ArrayList<>();

        int bestScore =
                Integer.MIN_VALUE;

        int countPerCraft =
                1;

        for (RecipeCandidate candidate :
                candidates) {

            bestScore =
                    Math.max(
                            bestScore,
                            candidate.score()
                    );

            for (ResourceLocation id :
                    candidate.equivalentRecipeIds()) {

                if (!equivalentRecipeIds.contains(
                        id
                )) {

                    equivalentRecipeIds.add(
                            id
                    );
                }
            }

            if (!candidate
                    .ingredientGroups()
                    .isEmpty()) {

                IngredientGroup group =
                        candidate
                                .ingredientGroups()
                                .getFirst();

                combinedVariants.addAll(
                        group.variants()
                );

                countPerCraft =
                        Math.max(
                                countPerCraft,
                                group.countPerCraft()
                        );
            }
        }

        List<ItemStack> normalizedVariants =
                normalizeVariants(
                        combinedVariants
                );

        ItemStack representativeStack =
                chooseRepresentative(
                        normalizedVariants
                );

        List<IngredientGroup> mergedIngredients =
                List.of(
                        new IngredientGroup(
                                representativeStack,
                                normalizedVariants,
                                countPerCraft
                        )
                );

        /*
         * Give routes that accept several equivalent materials
         * a tiny bonus.
         *
         * It is intentionally small so flexibility does not
         * override the main production ranking.
         */
        int flexibilityBonus =
                Math.min(
                        Math.max(
                                0,
                                normalizedVariants.size() - 1
                        ),
                        3
                );

        return new RecipeCandidate(
                representative.holder(),
                representative.productionType(),
                bestScore + flexibilityBonus,
                mergedIngredients,
                equivalentRecipeIds
        );
    }

    private static int productionMethodOrder(
            ProductionType type
    ) {
        return switch (type) {

            case SMELTING ->
                    0;

            case BLASTING ->
                    1;

            case CRAFTING ->
                    2;
        };
    }

    private static int scoreRecipe(
            ItemStack target,
            Recipe<?> recipe,
            RegistryAccess registryAccess,
            ProductionType productionType
    ) {
        int score =
                getProductionTypeScore(
                        productionType
                );

        int ingredientSlots =
                0;

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

            score +=
                    Math.min(
                            possibilities.length,
                            3
                    );

            for (ItemStack possibility :
                    possibilities) {

                if (possibility.isEmpty()) {
                    continue;
                }

                uniqueItems.add(
                        getItemKey(
                                possibility
                        )
                );

                score +=
                        scoreInputPreference(
                                possibility
                        );

                if (ItemStack.isSameItem(
                        possibility,
                        target
                )) {

                    score -=
                            1000;
                }
            }
        }

        score +=
                ingredientSlots * 5;

        score +=
                Math.min(
                        uniqueItems.size(),
                        3
                );

        ItemStack output =
                recipe.getResultItem(
                        registryAccess
                );

        if (output.getCount() > 16) {

            score -=
                    output.getCount();
        }

        return score;
    }

    /*
     * Normal forward processing should beat crafting together
     * smaller pieces.
     *
     * Normal furnace smelting is placed slightly ahead of
     * blasting as the baseline processing method.
     */
    private static int getProductionTypeScore(
            ProductionType productionType
    ) {
        return switch (productionType) {

            case SMELTING ->
                    3000;

            case BLASTING ->
                    2900;

            case CRAFTING ->
                    1000;
        };
    }

    private static int scoreInputPreference(
            ItemStack stack
    ) {
        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        String path =
                id.getPath();

        int score =
                0;

        /*
         * Prefer ordinary ore processing when available.
         */
        if (path.endsWith(
                "_ore"
        )) {

            score +=
                    50;
        }

        /*
         * Raw materials are also a normal processing route,
         * but sit just behind ores for the automatic default.
         */
        if (path.startsWith(
                "raw_"
        )) {

            score +=
                    30;
        }

        /*
         * Prefer the normal ore as the representative over its
         * deepslate equivalent.
         *
         * They will still be merged into one variant group.
         */
        if (path.startsWith(
                "deepslate_"
        )) {

            score -=
                    5;
        }

        /*
         * Recombining nuggets remains available but should not
         * normally become the preferred production route.
         */
        if (path.endsWith(
                "_nugget"
        )) {

            score -=
                    75;
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

        Ingredient onlyIngredient =
                null;

        int nonEmptyIngredients =
                0;

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient.isEmpty()) {
                continue;
            }

            nonEmptyIngredients++;

            onlyIngredient =
                    ingredient;

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

                        containsOriginal =
                                true;

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
        List<ItemStack> list =
                new ArrayList<>();

        for (ItemStack stack :
                possibilities) {

            list.add(
                    stack
            );
        }

        return normalizeVariants(
                list
        );
    }

    private static List<ItemStack> normalizeVariants(
            List<ItemStack> possibilities
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
                    getItemKey(
                            stack
                    ),
                    stack.copy()
            );
        }

        List<ItemStack> result =
                new ArrayList<>(
                        unique.values()
                );

        result.sort(
                Comparator.comparing(
                        CraftScopeRecipeResolver
                                ::getItemKey
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

                builder.append(
                        "|"
                );
            }

            builder.append(
                    getItemKey(
                            variant
                    )
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

    private enum ProductionType {

        CRAFTING,
        SMELTING,
        BLASTING
    }

    private record IngredientGroup(
            ItemStack stack,
            List<ItemStack> variants,
            int countPerCraft
    ) {
    }

    private record RecipeCandidate(
            RecipeHolder<?> holder,
            ProductionType productionType,
            int score,
            List<IngredientGroup> ingredientGroups,
            List<ResourceLocation> equivalentRecipeIds
    ) {

        private ResourceLocation primaryRecipeId() {

            return holder.id();
        }

        private boolean matchesRecipeId(
                ResourceLocation id
        ) {
            if (primaryRecipeId().equals(
                    id
            )) {

                return true;
            }

            return equivalentRecipeIds.contains(
                    id
            );
        }
    }
}