package io.github.gatiger.craftscope.production;

import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Public query layer used by CraftScope screens and planners.
 *
 * Registry:
 *     What raw provider routes exist?
 *
 * Normalizer:
 *     Which raw routes represent the same logical direct route?
 *
 * Recipe Tree:
 *     Which direct recipe did the player actually choose?
 *
 * Expander:
 *     Build a linear multi-step chain while honoring those recipe
 *     choices for intermediate items.
 *
 * Query:
 *     Give the UI the final synchronized route list.
 */
public final class CraftScopeProductionRouteQuery {

    private CraftScopeProductionRouteQuery() {
    }

    /*
     * Compatibility overload for callers that do not have a
     * Recipe Tree selection context.
     */
    public static List<CraftScopeProductionRoute> findRoutes(
            ItemStack target
    ) {
        return findRoutes(
                target,
                null
        );
    }

    /*
     * Preferred UI path.
     *
     * When a Recipe Tree is supplied, Process Diagram is derived
     * from that tree's selected recipes instead of independently
     * inventing a different production chain.
     */
    public static List<CraftScopeProductionRoute> findRoutes(
            ItemStack target,
            CraftScopeRecipeTree selectedTree
    ) {
        List<CraftScopeProductionRoute> directRoutes =
                findDirectRoutes(
                        target
                );

        if (directRoutes.isEmpty()) {
            return List.of();
        }

        Map<ResourceLocation, ResourceLocation> recipeSelections =
                buildRecipeSelections(
                        selectedTree
                );

        ResourceLocation rootChoice =
                getRootChoice(
                        selectedTree
                );

        List<CraftScopeProductionRoute> compatibleDirectRoutes =
                filterForRootChoice(
                        directRoutes,
                        rootChoice
                );

        List<CraftScopeProductionRoute> expandedRoutes =
                CraftScopeProductionRouteExpander.expand(
                        compatibleDirectRoutes,
                        recipeSelections
                );

        if (expandedRoutes.isEmpty()) {
            return compatibleDirectRoutes;
        }

        Map<String, CraftScopeProductionRoute> unique =
                new LinkedHashMap<>();

        /*
         * Expanded chain first when one exists.
         */
        for (CraftScopeProductionRoute route :
                expandedRoutes) {

            unique.putIfAbsent(
                    route.id().toString(),
                    route
            );
        }

        for (CraftScopeProductionRoute route :
                compatibleDirectRoutes) {

            unique.putIfAbsent(
                    route.id().toString(),
                    route
            );
        }

        List<CraftScopeProductionRoute> result =
                new ArrayList<>(
                        unique.values()
                );

        sortRoutes(
                result
        );

        return List.copyOf(
                result
        );
    }

    /*
     * Raw provider output. Useful for diagnostics/integrations.
     */
    public static List<CraftScopeProductionRoute> findRawRoutes(
            ItemStack target
    ) {
        return CraftScopeProductionRouteRegistry.findRoutes(
                target
        );
    }

    /*
     * Clean one-step/direct routes, without automatic chain
     * expansion. Recipe Tree uses this method recursively.
     */
    public static List<CraftScopeProductionRoute> findDirectRoutes(
            ItemStack target
    ) {
        List<CraftScopeProductionRoute> rawRoutes =
                CraftScopeProductionRouteRegistry.findRoutes(
                        target
                );

        List<CraftScopeProductionRoute> normalized =
                new ArrayList<>(
                        CraftScopeProductionRouteNormalizer.normalize(
                                rawRoutes
                        )
                );

        sortRoutes(
                normalized
        );

        return List.copyOf(
                normalized
        );
    }

    private static List<CraftScopeProductionRoute> filterForRootChoice(
            List<CraftScopeProductionRoute> directRoutes,
            ResourceLocation rootChoice
    ) {
        if (rootChoice == null) {
            return directRoutes;
        }

        List<CraftScopeProductionRoute> matching =
                new ArrayList<>();

        for (CraftScopeProductionRoute route :
                directRoutes) {

            if (CraftScopeProductionRecipeTreeBuilder.routeMatchesChoice(
                    route,
                    rootChoice
            )) {

                matching.add(
                        route
                );
            }
        }

        /*
         * Never blank the Process Diagram just because an older
         * persisted override no longer matches a loaded recipe.
         */
        if (matching.isEmpty()) {
            return directRoutes;
        }

        return List.copyOf(
                matching
        );
    }

    private static ResourceLocation getRootChoice(
            CraftScopeRecipeTree tree
    ) {
        if (tree == null
                || tree.getRoot() == null) {

            return null;
        }

        return tree
                .getRoot()
                .getPreferredRecipeId();
    }

    private static Map<ResourceLocation, ResourceLocation>
    buildRecipeSelections(
            CraftScopeRecipeTree tree
    ) {
        if (tree == null
                || tree.getRoot() == null) {

            return Map.of();
        }

        Map<ResourceLocation, ResourceLocation> result =
                new LinkedHashMap<>();

        collectRecipeSelections(
                tree.getRoot(),
                result
        );

        return Map.copyOf(
                result
        );
    }

    private static void collectRecipeSelections(
            CraftScopeRecipeNode node,
            Map<ResourceLocation, ResourceLocation> selections
    ) {
        if (node == null) {
            return;
        }

        ResourceLocation selectedRecipe =
                node.getPreferredRecipeId();

        if (selectedRecipe != null) {
            ItemStack nodeStack =
                    node.getStack();

            if (!nodeStack.isEmpty()) {
                selections.put(
                        BuiltInRegistries.ITEM.getKey(
                                nodeStack.getItem()
                        ),
                        selectedRecipe
                );
            }

            for (ItemStack variant :
                    node.getAcceptedVariants()) {

                if (variant == null
                        || variant.isEmpty()) {

                    continue;
                }

                selections.put(
                        BuiltInRegistries.ITEM.getKey(
                                variant.getItem()
                        ),
                        selectedRecipe
                );
            }
        }

        for (CraftScopeRecipeNode child :
                node.getChildren()) {

            collectRecipeSelections(
                    child,
                    selections
            );
        }
    }

    private static void sortRoutes(
            List<CraftScopeProductionRoute> routes
    ) {
        routes.sort(
                Comparator
                        .comparingInt(
                                CraftScopeProductionRoute
                                        ::priority
                        )
                        .reversed()
                        .thenComparing(
                                (CraftScopeProductionRoute route) ->
                                        route
                                                .sourceModName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                (CraftScopeProductionRoute route) ->
                                        route
                                                .displayName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .id()
                                                .toString()
                        )
        );
    }
}
