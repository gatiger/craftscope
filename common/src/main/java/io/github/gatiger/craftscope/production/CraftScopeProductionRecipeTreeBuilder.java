package io.github.gatiger.craftscope.recipe;

import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Builds the Recipe Tree from the same generalized production
 * routes that power Process Diagram and Setup.
 *
 * This replaces the old split where Recipe Tree understood only
 * vanilla crafting/smelting/blasting while Process Diagram used
 * the production-provider system.
 *
 * Result:
 *
 * - Create pressing/crushing/etc. can appear in Recipe Tree.
 * - The recipe selected in Recipe Tree can drive Process Diagram.
 * - Future providers (Mekanism, Thermal, etc.) automatically gain
 *   Recipe Tree support when they expose production routes.
 *
 * The tree currently expands direct, one-step routes recursively.
 * Multi-step routes are synthesized later by the Process Diagram
 * route expander from those same selected direct recipes.
 */
public final class CraftScopeProductionRecipeTreeBuilder {

    private static final int MAX_DEPTH = 32;

    private CraftScopeProductionRecipeTreeBuilder() {
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
        if (target == null
                || target.isEmpty()
                || targetCount <= 0) {

            return null;
        }

        Map<String, ResourceLocation> overrides =
                recipeOverrides == null
                        ? Map.of()
                        : recipeOverrides;

        CraftScopeRecipeNode root =
                resolveNode(
                        target,
                        targetCount,
                        List.of(target),
                        "root",
                        overrides,
                        new HashSet<>(),
                        0
                );

        return new CraftScopeRecipeTree(
                root
        );
    }

    private static CraftScopeRecipeNode resolveNode(
            ItemStack requestedStack,
            int requestedCount,
            List<ItemStack> acceptedVariants,
            String nodePath,
            Map<String, ResourceLocation> recipeOverrides,
            Set<String> activePath,
            int depth
    ) {
        ItemStack stack =
                requestedStack.copy();

        String itemKey =
                getItemKey(
                        stack
                );

        if (depth >= MAX_DEPTH
                || activePath.contains(itemKey)) {

            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        List<CraftScopeProductionRoute> candidates =
                findCandidateRoutes(
                        stack,
                        activePath
                );

        if (candidates.isEmpty()) {
            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        ResourceLocation overrideId =
                recipeOverrides.get(
                        nodePath
                );

        CraftScopeProductionRoute selectedRoute =
                chooseRoute(
                        candidates,
                        overrideId
                );

        if (selectedRoute == null
                || selectedRoute.steps().isEmpty()) {

            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        long outputPerRun =
                Math.max(
                        1L,
                        selectedRoute
                                .targetOutput()
                                .amount()
                );

        long runs =
                ceilDiv(
                        Math.max(
                                1L,
                                requestedCount
                        ),
                        outputPerRun
                );

        ResourceLocation preferredChoiceId =
                getRouteChoiceId(
                        selectedRoute
                );

        List<ResourceLocation> alternativeChoiceIds =
                new ArrayList<>();

        for (CraftScopeProductionRoute candidate :
                candidates) {

            if (candidate == selectedRoute) {
                continue;
            }

            ResourceLocation choiceId =
                    getRouteChoiceId(
                            candidate
                    );

            if (choiceId != null
                    && !choiceId.equals(preferredChoiceId)
                    && !alternativeChoiceIds.contains(choiceId)) {

                alternativeChoiceIds.add(
                        choiceId
                );
            }
        }

        CraftScopeRecipeNode node =
                new CraftScopeRecipeNode(
                        stack,
                        requestedCount,
                        clampToInt(runs),
                        true,
                        acceptedVariants,
                        preferredChoiceId,
                        alternativeChoiceIds
                );

        activePath.add(
                itemKey
        );

        CraftScopeProductionStep step =
                selectedRoute
                        .steps()
                        .getFirst();

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            /*
             * Recipe Tree / Total Materials currently represent
             * consumed item materials.
             *
             * Machines, fluids, chemicals, reusable tools, heat,
             * etc. belong in Process Diagram / Setup.
             */
            if (input.kind()
                    != CraftScopeResourceKind.ITEM
                    || !input.consumed()
                    || input.amount() <= 0) {

                continue;
            }

            List<ItemStack> variants =
                    getVariantStacks(
                            input
                    );

            ItemStack representative =
                    getRepresentativeStack(
                            input,
                            variants
                    );

            if (representative.isEmpty()) {
                continue;
            }

            long requiredAmount =
                    safeMultiply(
                            input.amount(),
                            runs
                    );

            int childIndex =
                    node
                            .getChildren()
                            .size();

            String childPath =
                    nodePath
                            + "/"
                            + childIndex
                            + ":"
                            + getItemKey(
                                    representative
                            );

            CraftScopeRecipeNode child =
                    resolveNode(
                            representative,
                            clampToInt(requiredAmount),
                            variants.isEmpty()
                                    ? List.of(representative)
                                    : variants,
                            childPath,
                            recipeOverrides,
                            activePath,
                            depth + 1
                    );

            node.addChild(
                    child
            );
        }

        activePath.remove(
                itemKey
        );

        return node;
    }

    private static List<CraftScopeProductionRoute> findCandidateRoutes(
            ItemStack target,
            Set<String> activePath
    ) {
        List<CraftScopeProductionRoute> directRoutes =
                CraftScopeProductionRouteQuery.findDirectRoutes(
                        target
                );

        if (directRoutes.isEmpty()) {
            return List.of();
        }

        List<CraftScopeProductionRoute> candidates =
                new ArrayList<>();

        for (CraftScopeProductionRoute route :
                directRoutes) {

            /*
             * Recipe Tree is recursive, so only a one-step direct
             * route belongs at one node.
             */
            if (route.steps().size() != 1) {
                continue;
            }

            if (!hasConsumedItemInput(
                    route
            )) {

                continue;
            }

            if (routeUsesActivePathItem(
                    route,
                    activePath
            )) {

                continue;
            }

            candidates.add(
                    route
            );
        }

        return List.copyOf(
                candidates
        );
    }

    private static boolean hasConsumedItemInput(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().isEmpty()) {

            return false;
        }

        for (CraftScopeResourceAmount input :
                route
                        .steps()
                        .getFirst()
                        .inputs()) {

            if (input.kind()
                    == CraftScopeResourceKind.ITEM
                    && input.consumed()
                    && input.amount() > 0) {

                return true;
            }
        }

        return false;
    }

    private static boolean routeUsesActivePathItem(
            CraftScopeProductionRoute route,
            Set<String> activePath
    ) {
        if (route == null
                || route.steps().isEmpty()
                || activePath == null
                || activePath.isEmpty()) {

            return false;
        }

        for (CraftScopeResourceAmount input :
                route
                        .steps()
                        .getFirst()
                        .inputs()) {

            if (input.kind()
                    != CraftScopeResourceKind.ITEM) {

                continue;
            }

            for (ResourceLocation variantId :
                    input.acceptedVariantIds()) {

                if (activePath.contains(
                        variantId.toString()
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    private static CraftScopeProductionRoute chooseRoute(
            List<CraftScopeProductionRoute> candidates,
            ResourceLocation overrideId
    ) {
        if (candidates == null
                || candidates.isEmpty()) {

            return null;
        }

        if (overrideId != null) {
            for (CraftScopeProductionRoute candidate :
                    candidates) {

                if (routeMatchesChoice(
                        candidate,
                        overrideId
                )) {

                    return candidate;
                }
            }
        }

        /*
         * findDirectRoutes() is already priority sorted.
         */
        return candidates.getFirst();
    }

    public static boolean routeMatchesChoice(
            CraftScopeProductionRoute route,
            ResourceLocation choiceId
    ) {
        if (route == null
                || choiceId == null) {

            return false;
        }

        if (route.id().equals(
                choiceId
        )) {

            return true;
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                if (method.recipeIds().contains(
                        choiceId
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    public static ResourceLocation getRouteChoiceId(
            CraftScopeProductionRoute route
    ) {
        if (route == null) {
            return null;
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                ResourceLocation recipeId =
                        method.getPrimaryRecipeId();

                if (recipeId != null) {
                    return recipeId;
                }
            }
        }

        /*
         * A provider route without a vanilla/Minecraft recipe ID
         * still needs a stable selectable identity.
         */
        return route.id();
    }

    private static ItemStack getRepresentativeStack(
            CraftScopeResourceAmount resource,
            List<ItemStack> variants
    ) {
        if (variants != null
                && !variants.isEmpty()) {

            return variants
                    .getFirst()
                    .copy();
        }

        return getItemStack(
                resource.id()
        );
    }

    private static List<ItemStack> getVariantStacks(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null
                || resource.kind()
                != CraftScopeResourceKind.ITEM) {

            return List.of();
        }

        Set<ResourceLocation> ids =
                new LinkedHashSet<>();

        ids.addAll(
                resource.acceptedVariantIds()
        );

        if (ids.isEmpty()
                && resource.id() != null) {

            ids.add(
                    resource.id()
            );
        }

        List<ItemStack> result =
                new ArrayList<>();

        for (ResourceLocation id :
                ids) {

            ItemStack stack =
                    getItemStack(
                            id
                    );

            if (!stack.isEmpty()) {
                result.add(
                        stack
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    private static ItemStack getItemStack(
            ResourceLocation id
    ) {
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        id
                );

        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
                new ItemStack(
                        item
                );

        return stack.isEmpty()
                ? ItemStack.EMPTY
                : stack;
    }

    private static CraftScopeRecipeNode createLeaf(
            ItemStack stack,
            int requiredCount,
            List<ItemStack> acceptedVariants
    ) {
        return new CraftScopeRecipeNode(
                stack,
                requiredCount,
                0,
                false,
                acceptedVariants
        );
    }

    private static String getItemKey(
            ItemStack stack
    ) {
        return BuiltInRegistries.ITEM
                .getKey(
                        stack.getItem()
                )
                .toString();
    }

    private static long ceilDiv(
            long value,
            long divisor
    ) {
        if (divisor <= 0) {
            return 0;
        }

        return value / divisor
                + (
                value % divisor == 0
                        ? 0
                        : 1
        );
    }

    private static long safeMultiply(
            long left,
            long right
    ) {
        if (left <= 0
                || right <= 0) {

            return 0;
        }

        if (left
                > Long.MAX_VALUE / right) {

            return Long.MAX_VALUE;
        }

        return left * right;
    }

    private static int clampToInt(
            long value
    ) {
        if (value <= 0) {
            return 0;
        }

        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) value;
    }
}
