package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Builds simple multi-step production chains from the existing
 * one-step production routes.
 *
 * Example:
 *
 * Iron Ore x9
 *      ↓
 * Ore Processing
 *      ↓
 * Iron Ingot x9
 *      ↓
 * Crafting
 *      ↓
 * Iron Block x1
 *
 * This class intentionally starts with LINEAR chains.
 *
 * A route is expanded only when the next step has exactly one
 * consumed item-resource input. This gives CraftScope a reliable
 * multi-step foundation without pretending that a linear list can
 * already represent every branching factory graph.
 *
 * Later, Create/Mekanism providers can feed their one-step routes
 * into this same expander, and a future graph planner can extend
 * the model for branching dependencies.
 */
public final class CraftScopeProductionRouteExpander {

    private static final int MAX_DEPTH = 6;
    private static final int EXPANDED_PRIORITY_BONUS = 5000;

    private static final String SOURCE_MOD_ID = "craftscope";

    private static final Component SOURCE_MOD_NAME =
            Component.literal("CraftScope");

    private static final Component EXPANDED_ROUTE_NAME =
            Component.literal("Full Production Chain");

    private CraftScopeProductionRouteExpander() {
    }

    public static List<CraftScopeProductionRoute> expand(
            List<CraftScopeProductionRoute> directRoutes
    ) {
        return expand(
                directRoutes,
                Map.of()
        );
    }

    /*
     * Expand while honoring the recipes selected in Recipe Tree.
     *
     * Key: output item ID
     * Value: selected recipe/route choice ID
     */
    public static List<CraftScopeProductionRoute> expand(
            List<CraftScopeProductionRoute> directRoutes,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (directRoutes == null || directRoutes.isEmpty()) {
            return List.of();
        }

        Map<ResourceLocation, ResourceLocation> selections =
                recipeSelections == null
                        ? Map.of()
                        : recipeSelections;

        List<CraftScopeProductionRoute> result =
                new ArrayList<>();

        Set<ResourceLocation> createdIds =
                new HashSet<>();

        for (CraftScopeProductionRoute route : directRoutes) {
            CraftScopeProductionRoute expanded =
                    expandRoute(
                            route,
                            selections
                    );

            if (expanded == null) {
                continue;
            }

            if (createdIds.add(expanded.id())) {
                result.add(expanded);
            }
        }

        return List.copyOf(result);
    }

    public static CraftScopeProductionRoute expandRoute(
            CraftScopeProductionRoute route
    ) {
        return expandRoute(
                route,
                Map.of()
        );
    }

    public static CraftScopeProductionRoute expandRoute(
            CraftScopeProductionRoute route,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (route == null || route.steps().size() != 1) {
            return null;
        }

        Set<ResourceLocation> visited =
                new LinkedHashSet<>();

        Expansion expansion =
                expandInternal(
                        route,
                        visited,
                        0,
                        recipeSelections == null
                                ? Map.of()
                                : recipeSelections
                );

        if (!expansion.expanded()
                || expansion.steps().size()
                <= route.steps().size()) {

            return null;
        }

        return new CraftScopeProductionRoute(
                buildExpandedRouteId(route),
                SOURCE_MOD_ID,
                SOURCE_MOD_NAME,
                EXPANDED_ROUTE_NAME,
                route.targetOutput(),
                expansion.steps(),
                addPriorityBonus(route.priority())
        );
    }

    private static Expansion expandInternal(
            CraftScopeProductionRoute route,
            Set<ResourceLocation> visited,
            int depth,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (route == null
                || route.steps().size() != 1
                || depth >= MAX_DEPTH) {

            return unchanged(route);
        }

        ResourceLocation targetId =
                route.targetOutput().id();

        if (visited.contains(targetId)) {
            return unchanged(route);
        }

        Set<ResourceLocation> nextVisited =
                new LinkedHashSet<>(visited);

        nextVisited.add(targetId);

        CraftScopeProductionStep currentStep =
                route.steps().getFirst();

        CraftScopeResourceAmount expandableInput =
                getLinearExpandableInput(currentStep);

        if (expandableInput == null) {
            return unchanged(route);
        }

        CraftScopeProductionRoute upstreamRoute =
                findPreferredUpstreamRoute(
                        expandableInput,
                        nextVisited,
                        recipeSelections
                );

        if (upstreamRoute == null) {
            return unchanged(route);
        }

        long requiredAmount =
                Math.max(
                        1,
                        expandableInput.amount()
                );

        /*
         * An upstream chance route must be attempted enough times
         * to supply the expected quantity required by the next
         * step. This keeps multi-step expansion consistent with
         * Recipe Tree and Production Summary.
         */
        long upstreamRuns =
                CraftScopeChancePlanner.requiredRuns(
                        upstreamRoute,
                        requiredAmount
                );

        if (upstreamRuns == Long.MAX_VALUE) {
            return unchanged(route);
        }

        Expansion upstreamExpansion =
                expandInternal(
                        upstreamRoute,
                        nextVisited,
                        depth + 1,
                        recipeSelections
                );

        List<CraftScopeProductionStep> steps =
                new ArrayList<>();

        for (CraftScopeProductionStep step :
                upstreamExpansion.steps()) {

            steps.add(
                    scaleStep(
                            step,
                            upstreamRuns
                    )
            );
        }

        steps.add(currentStep);

        return new Expansion(
                List.copyOf(steps),
                true
        );
    }

    private static CraftScopeResourceAmount getLinearExpandableInput(
            CraftScopeProductionStep step
    ) {
        if (step == null || step.inputs().size() != 1) {
            return null;
        }

        CraftScopeResourceAmount input =
                step.inputs().getFirst();

        if (input.kind() != CraftScopeResourceKind.ITEM) {
            return null;
        }

        if (!input.consumed()) {
            return null;
        }

        if (input.amount() <= 0) {
            return null;
        }

        return input;
    }

    private static CraftScopeProductionRoute
    findPreferredUpstreamRoute(
            CraftScopeResourceAmount input,
            Set<ResourceLocation> visited,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (input == null
                || input.kind() != CraftScopeResourceKind.ITEM) {

            return null;
        }

        Map<ResourceLocation, CraftScopeProductionRoute>
                candidatesById =
                new LinkedHashMap<>();

        for (ResourceLocation inputId :
                input.acceptedVariantIds()) {

            ItemStack stack =
                    getItemStack(inputId);

            if (stack.isEmpty()) {
                continue;
            }

            List<CraftScopeProductionRoute> raw =
                    CraftScopeProductionRouteRegistry.findRoutes(
                            stack
                    );

            List<CraftScopeProductionRoute> normalized =
                    CraftScopeProductionRouteNormalizer.normalize(
                            raw
                    );

            for (CraftScopeProductionRoute candidate :
                    normalized) {

                if (candidate.steps().size() != 1) {
                    continue;
                }

                if (!input.accepts(
                        candidate
                                .targetOutput()
                                .id()
                )) {

                    continue;
                }

                if (routeDependsOnVisitedResource(
                        candidate,
                        visited
                )) {

                    continue;
                }

                candidatesById.putIfAbsent(
                        candidate.id(),
                        candidate
                );
            }
        }

        if (candidatesById.isEmpty()) {
            return null;
        }

        /*
         * Recipe Tree is authoritative. If the player selected a
         * recipe for this intermediate item, use the route that
         * contains that exact recipe before considering priority.
         *
         * This prevents cases such as Iron Sheet unexpectedly
         * expanding Iron Ingot through Create crushing an Iron
         * Horse Armor simply because Crushing had a larger numeric
         * route priority.
         */
        ResourceLocation selectedRecipe =
                getSelectedRecipeForInput(
                        input,
                        recipeSelections
                );

        if (selectedRecipe != null) {
            for (CraftScopeProductionRoute candidate :
                    candidatesById.values()) {

                if (routeMatchesRecipeChoice(
                        candidate,
                        selectedRecipe
                )) {

                    return candidate;
                }
            }
        }

        CraftScopeProductionRoute best = null;

        for (CraftScopeProductionRoute candidate :
                candidatesById.values()) {

            if (best == null
                    || candidate.priority()
                    > best.priority()) {

                best = candidate;
                continue;
            }

            if (candidate.priority()
                    == best.priority()
                    && candidate
                    .displayName()
                    .getString()
                    .compareToIgnoreCase(
                            best
                                    .displayName()
                                    .getString()
                    ) < 0) {

                best = candidate;
            }
        }

        return best;
    }

    private static ResourceLocation getSelectedRecipeForInput(
            CraftScopeResourceAmount input,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (input == null
                || recipeSelections == null
                || recipeSelections.isEmpty()) {

            return null;
        }

        for (ResourceLocation variantId :
                input.acceptedVariantIds()) {

            ResourceLocation selected =
                    recipeSelections.get(
                            variantId
                    );

            if (selected != null) {
                return selected;
            }
        }

        return recipeSelections.get(
                input.id()
        );
    }

    private static boolean routeMatchesRecipeChoice(
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

    private static boolean routeDependsOnVisitedResource(
            CraftScopeProductionRoute route,
            Set<ResourceLocation> visited
    ) {
        if (route == null
                || visited == null
                || visited.isEmpty()) {

            return false;
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                if (input.kind() != CraftScopeResourceKind.ITEM) {
                    continue;
                }

                for (ResourceLocation variant :
                        input.acceptedVariantIds()) {

                    if (visited.contains(variant)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static ItemStack getItemStack(
            ResourceLocation id
    ) {
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(id);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
                new ItemStack(item);

        return stack.isEmpty()
                ? ItemStack.EMPTY
                : stack;
    }

    private static CraftScopeProductionStep scaleStep(
            CraftScopeProductionStep step,
            long factor
    ) {
        if (factor <= 1) {
            return step;
        }

        List<CraftScopeResourceAmount> inputs =
                new ArrayList<>();

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            long amount =
                    input.consumed()
                            ? safeMultiply(
                            input.amount(),
                            factor
                    )
                            : input.amount();

            inputs.add(
                    copyResource(
                            input,
                            amount
                    )
            );
        }

        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (CraftScopeResourceAmount output :
                step.outputs()) {

            outputs.add(
                    copyResource(
                            output,
                            safeMultiply(
                                    output.amount(),
                                    factor
                            )
                    )
            );
        }

        return new CraftScopeProductionStep(
                step.id(),
                step.displayName(),
                inputs,
                outputs,
                step.methods()
        );
    }

    private static CraftScopeResourceAmount copyResource(
            CraftScopeResourceAmount resource,
            long amount
    ) {
        return new CraftScopeResourceAmount(
                resource.kind(),
                resource.id(),
                resource.displayName(),
                amount,
                resource.unit(),
                resource.consumed(),
                resource.chance(),
                resource.acceptedVariantIds()
        );
    }

    private static ResourceLocation buildExpandedRouteId(
            CraftScopeProductionRoute route
    ) {
        ResourceLocation original =
                route.id();

        ResourceLocation id =
                ResourceLocation.tryParse(
                        "craftscope:expanded/"
                                + original.getNamespace()
                                + "/"
                                + original.getPath()
                );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Could not build expanded route ID for "
                            + original
            );
        }

        return id;
    }

    private static int addPriorityBonus(
            int priority
    ) {
        long result =
                (long) priority
                        + EXPANDED_PRIORITY_BONUS;

        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        return (int) result;
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
        if (left <= 0 || right <= 0) {
            return 0;
        }

        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }

        return left * right;
    }

    private static Expansion unchanged(
            CraftScopeProductionRoute route
    ) {
        if (route == null) {
            return new Expansion(
                    List.of(),
                    false
            );
        }

        return new Expansion(
                route.steps(),
                false
        );
    }

    private record Expansion(
            List<CraftScopeProductionStep> steps,
            boolean expanded
    ) {
        private Expansion {
            steps =
                    steps == null
                            ? List.of()
                            : List.copyOf(steps);
        }
    }
}
