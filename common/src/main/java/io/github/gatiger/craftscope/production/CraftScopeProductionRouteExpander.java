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

            /*
             * Try the branch-aware planner first.
             *
             * Only use its result when the generated route really
             * contains a convergence point. That keeps ordinary
             * one-input chains on the existing, proven linear path.
             */
            CraftScopeProductionRoute expanded =
                    expandBranchingRoute(
                            route,
                            selections
                    );

            if (expanded != null) {

                CraftScopeProductionGraph graph =
                        CraftScopeProductionGraph.fromRoute(
                                expanded
                        );

                if (!graph.hasBranchingInputs()) {

                    expanded =
                            null;
                }
            }

            /*
             * No real branch:
             *
             * fall back to the existing linear expansion behavior.
             */
            if (expanded == null) {

                expanded =
                        expandRoute(
                                route,
                                selections
                        );
            }

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

    /*
     * ---------------------------------------------------------
     * Branch-aware expansion
     * ---------------------------------------------------------
     *
     * This path can expand more than one consumed input of a step.
     *
     * It intentionally remains separate from expand()/expandRoute()
     * until Process Diagram is switched to graph rendering.
     *
     * That prevents a branching route from temporarily appearing as
     * a misleading linear sequence in the old renderer.
     */
    public static CraftScopeProductionRoute expandBranchingRoute(
            CraftScopeProductionRoute route
    ) {
        return expandBranchingRoute(
                route,
                Map.of()
        );
    }

    public static CraftScopeProductionRoute expandBranchingRoute(
            CraftScopeProductionRoute route,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return null;
        }

        Set<ResourceLocation> visited =
                new LinkedHashSet<>();

        Expansion expansion =
                expandBranchingInternal(
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
                buildExpandedRouteId(
                        route
                ),
                SOURCE_MOD_ID,
                SOURCE_MOD_NAME,
                EXPANDED_ROUTE_NAME,
                route.targetOutput(),
                expansion.steps(),
                addPriorityBonus(
                        route.priority()
                )
        );
    }

    /*
     * Recursively expand every consumed item input that has a usable
     * upstream route.
     *
     * Each upstream branch is completed before the current step is
     * appended, so the resulting route remains topologically ordered:
     *
     *     branch A
     *     branch B
     *     branch C
     *     final process
     *
     * CraftScopeProductionGraph later reconstructs the actual
     * dependency edges from resource flow.
     */
    private static Expansion expandBranchingInternal(
            CraftScopeProductionRoute route,
            Set<ResourceLocation> visited,
            int depth,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (route == null
                || route.steps().size() != 1
                || depth >= MAX_DEPTH) {

            return unchanged(
                    route
            );
        }

        ResourceLocation targetId =
                route
                        .targetOutput()
                        .id();

        if (visited.contains(
                targetId
        )) {

            return unchanged(
                    route
            );
        }

        Set<ResourceLocation> nextVisited =
                new LinkedHashSet<>(
                        visited
                );

        nextVisited.add(
                targetId
        );

        CraftScopeProductionStep currentStep =
                route
                        .steps()
                        .getFirst();

        List<ExpansionCandidate> candidates =
                findAllExpandableInputs(
                        currentStep,
                        nextVisited,
                        recipeSelections
                );

        if (candidates.isEmpty()) {

            return unchanged(
                    route
            );
        }

        List<CraftScopeProductionStep> steps =
                new ArrayList<>();

        boolean expanded =
                false;

        for (ExpansionCandidate candidate :
                candidates) {

            CraftScopeResourceAmount input =
                    candidate.input();

            CraftScopeProductionRoute upstreamRoute =
                    candidate.route();

            long requiredAmount =
                    Math.max(
                            1L,
                            input.amount()
                    );

            long upstreamRuns =
                    CraftScopeChancePlanner.requiredRuns(
                            upstreamRoute,
                            requiredAmount
                    );

            if (upstreamRuns == Long.MAX_VALUE) {
                continue;
            }

            /*
             * Each branch gets its own copy of the visited set.
             *
             * Independent branches must not incorrectly block each
             * other merely because they happen to share an ancestor
             * resource somewhere deeper in their trees.
             */
            Set<ResourceLocation> branchVisited =
                    new LinkedHashSet<>(
                            nextVisited
                    );

            Expansion upstreamExpansion =
                    expandBranchingInternal(
                            upstreamRoute,
                            branchVisited,
                            depth + 1,
                            recipeSelections
                    );

            for (CraftScopeProductionStep upstreamStep :
                    upstreamExpansion.steps()) {

                steps.add(
                        scaleStep(
                                upstreamStep,
                                upstreamRuns
                        )
                );
            }

            expanded =
                    true;
        }

        if (!expanded) {

            return unchanged(
                    route
            );
        }

        /*
         * All upstream branches feed into this step.
         */
        steps.add(
                currentStep
        );

        return new Expansion(
                List.copyOf(
                        steps
                ),
                true
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

        /*
         * A step may contain several consumed inputs.
         *
         * For now, expand it only when exactly ONE of those inputs
         * has a usable upstream production route.
         *
         * Other inputs remain external requirements.
         *
         * If two or more inputs have upstream routes, that is a true
         * branching production graph and is intentionally deferred
         * to the branch-aware planner/renderer.
         */
        ExpansionCandidate expansionCandidate =
                findSingleExpandableInput(
                        currentStep,
                        nextVisited,
                        recipeSelections
                );

        if (expansionCandidate == null) {
            return unchanged(route);
        }

        CraftScopeResourceAmount expandableInput =
                expansionCandidate.input();

        CraftScopeProductionRoute upstreamRoute =
                expansionCandidate.route();

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

    /*
     * Find exactly one consumed item input that CraftScope can
     * produce upstream.
     *
     * Examples that are safe for the current linear model:
     *
     *     Produced Ingredient
     *             ↓
     *     Final Recipe + external ingredient(s)
     *
     * A recipe where two separate inputs can both be produced is a
     * branching graph:
     *
     *     Branch A ─┐
     *               ├─> Final Recipe
     *     Branch B ─┘
     *
     * Do not flatten that into a fake A -> B -> Final sequence.
     */
    private static ExpansionCandidate findSingleExpandableInput(
            CraftScopeProductionStep step,
            Set<ResourceLocation> visited,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (step == null
                || step.inputs().isEmpty()) {

            return null;
        }

        ExpansionCandidate found =
                null;

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            if (input == null
                    || input.kind()
                    != CraftScopeResourceKind.ITEM
                    || !input.consumed()
                    || input.amount() <= 0L) {

                continue;
            }

            CraftScopeProductionRoute upstreamRoute =
                    findPreferredUpstreamRoute(
                            input,
                            visited,
                            recipeSelections
                    );

            if (upstreamRoute == null) {
                continue;
            }

            /*
             * More than one producible input means the route really
             * branches. Preserve correctness and leave that route
             * unexpanded until the branch-aware graph pass.
             */
            if (found != null) {
                return null;
            }

            found =
                    new ExpansionCandidate(
                            input,
                            upstreamRoute
                    );
        }

        return found;
    }

    /*
     * Return every consumed item input for which CraftScope can find
     * a valid upstream production route.
     *
     * The existing findSingleExpandableInput() remains the conservative
     * helper used by the old linear expansion path.
     */
    private static List<ExpansionCandidate> findAllExpandableInputs(
            CraftScopeProductionStep step,
            Set<ResourceLocation> visited,
            Map<ResourceLocation, ResourceLocation> recipeSelections
    ) {
        if (step == null
                || step.inputs().isEmpty()) {

            return List.of();
        }

        List<ExpansionCandidate> result =
                new ArrayList<>();

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            if (input == null
                    || input.kind()
                    != CraftScopeResourceKind.ITEM
                    || !input.consumed()
                    || input.amount() <= 0L) {

                continue;
            }

            CraftScopeProductionRoute upstreamRoute =
                    findPreferredUpstreamRoute(
                            input,
                            visited,
                            recipeSelections
                    );

            if (upstreamRoute == null) {
                continue;
            }

            result.add(
                    new ExpansionCandidate(
                            input,
                            upstreamRoute
                    )
            );
        }

        return List.copyOf(
                result
        );
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

                    return CraftScopeRecipeVariantFamilyPolicy
                            .adaptRoute(
                                    candidate,
                                    input
                            );
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

        return CraftScopeRecipeVariantFamilyPolicy
                .adaptRoute(
                        best,
                        input
                );
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

            /*
             * Consumed resources scale with the number of executions.
             *
             * Reusable tools/catalysts remain a one-time requirement.
             */
            long inputFactor =
                    input.consumed()
                            ? factor
                            : 1L;

            inputs.add(
                    scaleResource(
                            input,
                            inputFactor
                    )
            );
        }

        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (CraftScopeResourceAmount output :
                step.outputs()) {

            outputs.add(
                    scaleResource(
                            output,
                            factor
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

    /*
     * Scale a resource across multiple executions while preserving
     * CraftScope's complete yield model.
     *
     * For a probabilistic result:
     *
     *     chance of >=1 after N executions
     *
     * becomes:
     *
     *     1 - (1 - p)^N
     *
     * Expected amount and min/max range scale independently.
     *
     * Example:
     *
     *     one kill:
     *         chance  = 17.9%
     *         range   = 0-6
     *         expected = 0.286
     *
     *     four kills:
     *         chance  ~= 54.5%
     *         range   = 0-24
     *         expected ~= 1.144
     */
    private static CraftScopeResourceAmount scaleResource(
            CraftScopeResourceAmount resource,
            long factor
    ) {
        if (resource == null) {
            return null;
        }

        if (factor <= 1L) {
            return resource;
        }

        long amount =
                safeMultiply(
                        resource.amount(),
                        factor
                );

        long minimum =
                safeMultiply(
                        resource.minimumAmount(),
                        factor
                );

        long maximum =
                safeMultiply(
                        resource.maximumAmount(),
                        factor
                );

        double expected =
                scaleExpectedAmount(
                        resource.expectedAmount(),
                        factor,
                        minimum,
                        maximum
                );

        double chance =
                scaleChance(
                        resource.chance(),
                        factor
                );

        return new CraftScopeResourceAmount(
                resource.kind(),
                resource.id(),
                resource.displayName(),
                amount,
                resource.unit(),
                resource.consumed(),
                chance,
                resource.acceptedVariantIds(),
                minimum,
                maximum,
                expected,
                resource.itemIdentity()
        );
    }

    /*
     * Probability that at least one successful outcome occurs over
     * multiple independent executions.
     */
    private static double scaleChance(
            double chance,
            long factor
    ) {
        if (chance <= 0.0D) {
            return 0.0D;
        }

        if (chance >= 1.0D) {
            return 1.0D;
        }

        if (factor <= 1L) {
            return chance;
        }

        double scaled =
                1.0D
                        - Math.pow(
                        1.0D - chance,
                        (double) factor
                );

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        scaled
                )
        );
    }

    /*
     * Keep expected yield finite and inside the scaled range even
     * when an extremely large execution count approaches numeric
     * limits.
     */
    private static double scaleExpectedAmount(
            double expected,
            long factor,
            long minimum,
            long maximum
    ) {
        if (expected <= 0.0D
                || factor <= 0L) {

            return 0.0D;
        }

        double scaled =
                expected
                        * (double) factor;

        if (!Double.isFinite(
                scaled
        )) {

            return (double) maximum;
        }

        return Math.max(
                (double) minimum,
                Math.min(
                        (double) maximum,
                        scaled
                )
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

    private record ExpansionCandidate(
            CraftScopeResourceAmount input,
            CraftScopeProductionRoute route
    ) {
        private ExpansionCandidate {
            if (input == null) {
                throw new IllegalArgumentException(
                        "Expansion input cannot be null"
                );
            }

            if (route == null) {
                throw new IllegalArgumentException(
                        "Expansion route cannot be null"
                );
            }
        }
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
