package io.github.gatiger.craftscope.recipe;

import io.github.gatiger.craftscope.production.CraftScopeChancePlanner;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import io.github.gatiger.craftscope.production.CraftScopeRecipeVariantFamilyPolicy;
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
 * - Recipe Source can filter the ROOT target route by mod while
 *   allowing intermediate materials to continue using any valid
 *   provider.
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
                Map.of(),
                null
        );
    }

    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> recipeOverrides
    ) {
        return resolveTree(
                target,
                targetCount,
                recipeOverrides,
                null
        );
    }

    /*
     * rootSourceModId is a Recipe Source FILTER, not a second
     * recipe-selection system.
     *
     * It applies only to the target/root item. Once a root route
     * is selected, recursive ingredients remain free to use their
     * best available recipes from any provider. This prevents a
     * "Create only" target route from making ordinary ingredients
     * impossible just because Create does not itself define every
     * intermediate crafting recipe.
     *
     * null/blank means All Sources.
     */
    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> recipeOverrides,
            String rootSourceModId
    ) {
        return resolveTree(
                target,
                targetCount,
                recipeOverrides,
                Map.of(),
                rootSourceModId
        );
    }

    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> recipeOverrides,
            Map<String, ResourceLocation> ingredientVariantOverrides,
            String rootSourceModId
    ) {
        return resolveTree(
                target,
                targetCount,
                recipeOverrides,
                ingredientVariantOverrides,
                rootSourceModId,
                null,
                null
        );
    }

    /*
     * preferredProcessSourceId + preferredProcessId come from the
     * shared Production Routes tree.
     *
     * They are a contextual preference/filter:
     *
     * - If a Recipe Tree node has one or more routes matching that
     *   source/process, only those routes are offered.
     *
     * - If the selected process does not apply to that node, the
     *   node keeps its normal recipe choices.
     *
     * Example:
     *
     *   Selected: Create -> Crafting
     *
     *   Lectern:
     *       no Create crafting alternative -> normal Minecraft route
     *
     *   Book:
     *       Create cardboard crafting exists -> show/select Create
     *       crafting choices only
     *
     * This lets one mod participate where appropriate without
     * requiring that mod to define every intermediate material.
     */
    public static CraftScopeRecipeTree resolveTree(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> recipeOverrides,
            Map<String, ResourceLocation> ingredientVariantOverrides,
            String rootSourceModId,
            String preferredProcessSourceId,
            ResourceLocation preferredProcessId
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

        Map<String, ResourceLocation> variantOverrides =
                ingredientVariantOverrides == null
                        ? Map.of()
                        : ingredientVariantOverrides;

        String sourceFilter =
                rootSourceModId == null
                        || rootSourceModId.isBlank()
                        ? null
                        : rootSourceModId;

        String processSourceFilter =
                preferredProcessSourceId == null
                        || preferredProcessSourceId.isBlank()
                        || "craftscope".equals(
                        preferredProcessSourceId
                )
                        ? null
                        : preferredProcessSourceId;

        ResourceLocation processFilter =
                processSourceFilter == null
                        ? null
                        : preferredProcessId;

        CraftScopeRecipeNode root =
                resolveNode(
                        target,
                        targetCount,
                        List.of(target),
                        "root",
                        overrides,
                        variantOverrides,
                        new HashSet<>(),
                        0,
                        sourceFilter,
                        processSourceFilter,
                        processFilter
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
            Map<String, ResourceLocation> ingredientVariantOverrides,
            Set<String> activePath,
            int depth,
            String sourceFilter,
            String preferredProcessSourceId,
            ResourceLocation preferredProcessId
    ) {
        ItemStack stack =
                requestedStack.copy();

        boolean selectableIngredientChoice =
                isSelectableIngredientChoice(
                        acceptedVariants
                );

        boolean explicitIngredientChoice =
                selectableIngredientChoice
                        && hasValidIngredientSelection(
                        nodePath,
                        acceptedVariants,
                        ingredientVariantOverrides
                );

        /*
         * Do not invent an acquisition path before the player chooses
         * between genuinely different ingredient strategies.
         *
         * Leather OR Cardboard therefore stops here as a choice node.
         * Once the player chooses one, only that branch is expanded.
         */
        if (selectableIngredientChoice
                && !explicitIngredientChoice) {

            return createIngredientChoice(
                    stack,
                    requestedCount,
                    acceptedVariants
            );
        }

        String itemKey =
                getItemKey(
                        stack
                );

        if (depth >= MAX_DEPTH
                || activePath.contains(itemKey)) {

            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants,
                    selectableIngredientChoice,
                    explicitIngredientChoice
            );
        }

        List<CraftScopeProductionRoute> candidates =
                findCandidateRoutes(
                        stack,
                        activePath,
                        sourceFilter,
                        preferredProcessSourceId,
                        preferredProcessId
                );

        if (candidates.isEmpty()) {
            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants,
                    selectableIngredientChoice,
                    explicitIngredientChoice
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
                    acceptedVariants,
                    selectableIngredientChoice,
                    explicitIngredientChoice
            );
        }

        /*
         * A broad equivalent ingredient family must stay broad while
         * Recipe Tree expands upstream production.
         *
         * Without this adaptation the builder uses the first concrete
         * accepted item as its stable representative. For vanilla wood
         * tags that is commonly Acacia, producing a misleading tree:
         *
         *     Any Slab
         *         -> Acacia Planks
         *             -> Stripped Acacia Log
         *
         * The production route family policy already knows the correct
         * logical relationship:
         *
         *     Any Slab
         *         -> Any Planks
         *             -> Any Log
         *
         * Apply that same policy here after the concrete recipe has
         * been selected. Recipe IDs and process methods are preserved;
         * only interchangeable material-family resources are widened.
         *
         * Genuine alternatives such as Leather/Cardboard are excluded
         * because they are not equivalent ingredient families.
         */
        selectedRoute =
                adaptRecipeTreeRouteToEquivalentFamily(
                        selectedRoute,
                        acceptedVariants
                );

        /*
         * Chance-aware planning is centralized in the production
         * layer. A 25% output no longer behaves like one guaranteed
         * item per run: Recipe Tree plans enough attempts for the
         * expected output to meet the requested quantity.
         */
        long runs =
                CraftScopeChancePlanner.requiredRuns(
                        selectedRoute,
                        Math.max(
                                1L,
                                requestedCount
                        )
                );

        if (runs == Long.MAX_VALUE) {
            return createLeaf(
                    stack,
                    requestedCount,
                    acceptedVariants,
                    selectableIngredientChoice,
                    explicitIngredientChoice
            );
        }

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
                        alternativeChoiceIds,
                        selectableIngredientChoice,
                        explicitIngredientChoice
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

            ItemStack stableRepresentative =
                    getRepresentativeStack(
                            input,
                            variants
                    );

            if (stableRepresentative.isEmpty()) {
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

            /*
             * The saved node path must not change when the player
             * switches Cardboard -> Leather. Use the first accepted
             * variant as the stable path identity and use a separate
             * active stack for recursive expansion.
             */
            String childPath =
                    nodePath
                            + "/"
                            + childIndex
                            + ":"
                            + getItemKey(
                                    stableRepresentative
                            );

            ItemStack activeRepresentative =
                    getSelectedIngredientStack(
                            childPath,
                            variants,
                            stableRepresentative,
                            ingredientVariantOverrides
                    );

            /*
             * Recipe Source is intentionally root-only.
             * Intermediate ingredients return to All Sources.
             */
            CraftScopeRecipeNode child =
                    resolveNode(
                            activeRepresentative,
                            clampToInt(requiredAmount),
                            variants.isEmpty()
                                    ? List.of(activeRepresentative)
                                    : variants,
                            childPath,
                            recipeOverrides,
                            ingredientVariantOverrides,
                            activePath,
                            depth + 1,
                            null,
                            preferredProcessSourceId,
                            preferredProcessId
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
            Set<String> activePath,
            String sourceFilter,
            String preferredProcessSourceId,
            ResourceLocation preferredProcessId
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

            if (!isRecipeTreeCandidate(route)) {
                continue;
            }

            if (!routeMatchesSource(
                    route,
                    sourceFilter
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

        return filterCandidatesByPreferredProductionProcess(
                candidates,
                preferredProcessSourceId,
                preferredProcessId
        );
    }

    private static List<CraftScopeProductionRoute>
    filterCandidatesByPreferredProductionProcess(
            List<CraftScopeProductionRoute> candidates,
            String preferredProcessSourceId,
            ResourceLocation preferredProcessId
    ) {
        if (candidates == null
                || candidates.isEmpty()
                || preferredProcessSourceId == null
                || preferredProcessSourceId.isBlank()
                || preferredProcessId == null) {

            return candidates == null
                    ? List.of()
                    : List.copyOf(
                    candidates
            );
        }

        /*
         * Keep a higher-priority acquisition route as the normal default.
         *
         * The Production Routes process selection is a preference for
         * HOW craftable/processable materials are made. It must not make
         * a lower-priority conversion route become the default source of
         * a naturally acquired material.
         *
         * Example:
         *
         *     Leather
         *         Kill Cow          priority ~3540
         *         4 Rabbit Hide -> Leather (Crafting) priority ~1000
         *
         * Selecting Crafting globally should not silently replace
         * Kill Cow with Rabbit Hide. The player may still explicitly
         * choose the Rabbit Hide recipe from Recipe Tree.
         */
        CraftScopeProductionRoute normalDefault =
                candidates.getFirst();

        if (isDefaultAcquisitionRoute(
                normalDefault
        )) {

            return List.copyOf(
                    candidates
            );
        }

        List<CraftScopeProductionRoute> matching =
                new ArrayList<>();

        for (CraftScopeProductionRoute route :
                candidates) {

            if (routeMatchesProductionProcess(
                    route,
                    preferredProcessSourceId,
                    preferredProcessId
            )) {

                matching.add(
                        route
                );
            }
        }

        /*
         * Important fallback:
         *
         * A selected Create process should influence nodes where
         * Create can actually participate, but should not make
         * ordinary vanilla-only intermediates disappear.
         */
        if (matching.isEmpty()) {
            return List.copyOf(
                    candidates
            );
        }

        return List.copyOf(
                matching
        );
    }

    private static boolean isDefaultAcquisitionRoute(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().isEmpty()) {

            return false;
        }

        ResourceLocation routeId =
                route.id();

        if (routeId != null
                && "craftscope".equals(
                routeId.getNamespace()
        )
                && routeId
                .getPath()
                .startsWith(
                        "acquisition/"
                )) {

            return true;
        }

        /*
         * Mob drops and other acquisition routes normally have no
         * consumed production input. Requirements such as a mob,
         * biome, tool, or environment live on the method instead.
         */
        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                if (input != null
                        && input.consumed()) {

                    return false;
                }
            }
        }

        return true;
    }
    private static boolean routeMatchesProductionProcess(
            CraftScopeProductionRoute route,
            String preferredProcessSourceId,
            ResourceLocation preferredProcessId
    ) {
        if (route == null
                || preferredProcessSourceId == null
                || preferredProcessSourceId.isBlank()
                || preferredProcessId == null) {

            return false;
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                if (!preferredProcessId.equals(
                        method.processId()
                )) {

                    continue;
                }

                if (methodMatchesProductionSource(
                        method,
                        preferredProcessSourceId
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    private static boolean methodMatchesProductionSource(
            CraftScopeProductionMethod method,
            String preferredProcessSourceId
    ) {
        if (method == null
                || preferredProcessSourceId == null
                || preferredProcessSourceId.isBlank()) {

            return false;
        }

        String methodSource =
                method.sourceModId();

        /*
         * A non-Minecraft method is an actual modded process
         * capability. Its provider owns the process even when the
         * underlying recipe ID belongs to Minecraft or another mod.
         *
         * Example:
         *     Create Bulk Blasting using minecraft:* smelting data
         *     belongs to Create, not Minecraft.
         */
        if (methodSource != null
                && !methodSource.isBlank()
                && !"minecraft".equals(
                methodSource
        )) {

            return preferredProcessSourceId.equals(
                    methodSource
            );
        }

        /*
         * Generic Minecraft crafting/smelting methods are grouped by
         * the recipe owner's namespace when recipe IDs are available.
         *
         * create:book_from_cardboard + minecraft crafting machinery
         * therefore appears as:
         *
         *     Create -> Crafting
         */
        if (!method.recipeIds().isEmpty()) {

            for (ResourceLocation recipeId :
                    method.recipeIds()) {

                if (recipeId != null
                        && preferredProcessSourceId.equals(
                        recipeId.getNamespace()
                )) {

                    return true;
                }
            }

            return false;
        }

        /*
         * No recipe IDs: fall back to the method's explicit source.
         */
        return preferredProcessSourceId.equals(
                methodSource
        );
    }

    /*
     * Public because Recipe Source UI uses the exact same
     * eligibility rule as Recipe Tree. This keeps route counts in
     * the source selector synchronized with the routes that can
     * actually appear in the tree.
     */
    public static boolean isRecipeTreeCandidate(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return false;
        }

        return hasConsumedItemInput(
                route
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

    /*
     * A normalized logical route may contain methods contributed
     * by more than one mod/provider.
     *
     * Example:
     *
     * Nether Quartz Ore -> Quartz
     *
     * Methods:
     *   Minecraft Smelting
     *   Minecraft Blasting
     *   Create Bulk Blasting
     *
     * That route therefore belongs in BOTH the Minecraft and
     * Create Recipe Source filters.
     */
    /*
     * Recipe Source means "which mod supplies a usable recipe or
     * production method for this target?", not "which generic
     * CraftScope provider happened to discover it?".
     *
     * This distinction matters for ordinary crafting recipes added
     * by mods. A Create Smart Chute is a normal Minecraft crafting
     * recipe type, so the vanilla provider discovers it, but the
     * loaded recipe ID is create:smart_chute. Recipe Source should
     * therefore show Create rather than Minecraft.
     *
     * Synthetic/non-vanilla processing methods still contribute
     * their method source. Example: Create Bulk Blasting may reuse
     * a minecraft:* smelting recipe, so that logical route belongs
     * to both Minecraft (recipe owner) and Create (processing
     * method provider).
     */
    public static boolean routeMatchesSource(
            CraftScopeProductionRoute route,
            String sourceModId
    ) {
        if (route == null) {
            return false;
        }

        if (sourceModId == null
                || sourceModId.isBlank()) {

            return true;
        }

        return getRecipeSourceIds(
                route
        ).contains(
                sourceModId
        );
    }

    /*
     * Returns every source that should appear in Recipe Source for
     * one logical production route.
     *
     * Priority of evidence:
     *
     * 1. Recipe ID namespace = owner of an actual loaded recipe.
     *    create:smart_chute -> Create
     *    minecraft:quartz -> Minecraft
     *
     * 2. A non-Minecraft method source also counts because mods can
     *    add synthetic/alternate processing methods over another
     *    mod's recipe data (Create Bulk Blasting is the current
     *    example).
     *
     * 3. If a provider exposes a route without recipe IDs, fall
     *    back to its route/method source.
     *
     * Minecraft is deliberately NOT added merely because the
     * process is generic Crafting/Smelting/Blasting. Otherwise
     * every mod-defined shaped recipe would incorrectly appear
     * under Minecraft.
     */
    public static Set<String> getRecipeSourceIds(
            CraftScopeProductionRoute route
    ) {
        if (route == null) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        boolean foundRecipeId =
                false;

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                for (ResourceLocation recipeId :
                        method.recipeIds()) {

                    if (recipeId == null) {
                        continue;
                    }

                    String namespace =
                            recipeId.getNamespace();

                    if (namespace == null
                            || namespace.isBlank()) {

                        continue;
                    }

                    foundRecipeId = true;

                    result.add(
                            namespace
                    );
                }

                String methodSource =
                        method.sourceModId();

                if (methodSource == null
                        || methodSource.isBlank()) {

                    continue;
                }

                /*
                 * Non-Minecraft methods represent an actual modded
                 * production capability and should be selectable
                 * even when they reuse another mod's recipe ID.
                 *
                 * Minecraft methods are generic recipe machinery,
                 * so only count Minecraft through a minecraft:*
                 * recipe ID (or through the no-ID fallback below).
                 */
                if (!"minecraft".equals(
                        methodSource
                )) {

                    result.add(
                            methodSource
                    );

                } else if (method.recipeIds().isEmpty()) {

                    result.add(
                            methodSource
                    );
                }
            }
        }

        if (!foundRecipeId
                && result.isEmpty()) {

            String routeSource =
                    route.sourceModId();

            if (routeSource != null
                    && !routeSource.isBlank()) {

                result.add(
                        routeSource
                );
            }
        }

        return Set.copyOf(
                result
        );
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

    private static boolean isSelectableIngredientChoice(
            List<ItemStack> acceptedVariants
    ) {
        return acceptedVariants != null
                && acceptedVariants.size() > 1
                && !CraftScopeRecipeVariantFamilyPolicy
                .isEquivalentIngredientFamily(
                        acceptedVariants
                );
    }

    private static boolean hasValidIngredientSelection(
            String nodePath,
            List<ItemStack> variants,
            Map<String, ResourceLocation> ingredientVariantOverrides
    ) {
        if (nodePath == null
                || variants == null
                || variants.isEmpty()
                || ingredientVariantOverrides == null
                || ingredientVariantOverrides.isEmpty()) {

            return false;
        }

        ResourceLocation selected =
                ingredientVariantOverrides.get(
                        nodePath
                );

        if (selected == null) {
            return false;
        }

        for (ItemStack variant :
                variants) {

            if (variant == null
                    || variant.isEmpty()) {

                continue;
            }

            ResourceLocation variantId =
                    BuiltInRegistries.ITEM.getKey(
                            variant.getItem()
                    );

            if (selected.equals(
                    variantId
            )) {

                return true;
            }
        }

        return false;
    }

    private static ItemStack getSelectedIngredientStack(
            String nodePath,
            List<ItemStack> variants,
            ItemStack fallback,
            Map<String, ResourceLocation> ingredientVariantOverrides
    ) {
        if (!hasValidIngredientSelection(
                nodePath,
                variants,
                ingredientVariantOverrides
        )) {

            return fallback.copy();
        }

        ResourceLocation selected =
                ingredientVariantOverrides.get(
                        nodePath
                );

        for (ItemStack variant :
                variants) {

            if (variant == null
                    || variant.isEmpty()) {

                continue;
            }

            ResourceLocation variantId =
                    BuiltInRegistries.ITEM.getKey(
                            variant.getItem()
                    );

            if (selected.equals(
                    variantId
            )) {

                return variant.copy();
            }
        }

        return fallback.copy();
    }

    private static CraftScopeRecipeNode createIngredientChoice(
            ItemStack stack,
            int requiredCount,
            List<ItemStack> acceptedVariants
    ) {
        return new CraftScopeRecipeNode(
                stack,
                requiredCount,
                0,
                false,
                acceptedVariants,
                null,
                List.of(),
                true,
                false
        );
    }
    private static CraftScopeProductionRoute
    adaptRecipeTreeRouteToEquivalentFamily(
            CraftScopeProductionRoute route,
            List<ItemStack> acceptedVariants
    ) {
        if (route == null
                || acceptedVariants == null
                || acceptedVariants.size() <= 1
                || !CraftScopeRecipeVariantFamilyPolicy
                .isEquivalentIngredientFamily(
                        acceptedVariants
                )) {

            return route;
        }

        /*
         * Build the same kind of family request that the Production
         * Route expander receives from a broad recipe ingredient.
         *
         * normalizeFamilyResource() currently recognizes the logical
         * vanilla wood chain (logs -> planks -> slabs). Families that
         * are equivalent for selection purposes but do not have an
         * upstream family transform simply return unchanged.
         */
        CraftScopeResourceAmount familyRequest =
                CraftScopeResourceAmount.itemVariants(
                        acceptedVariants,
                        1L,
                        true
                );

        CraftScopeResourceAmount normalizedFamily =
                CraftScopeRecipeVariantFamilyPolicy
                        .normalizeFamilyResource(
                                familyRequest
                        );

        return CraftScopeRecipeVariantFamilyPolicy
                .adaptRoute(
                        route,
                        normalizedFamily
                );
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
            List<ItemStack> acceptedVariants,
            boolean selectableIngredientAlternatives,
            boolean explicitIngredientVariantSelection
    ) {
        return new CraftScopeRecipeNode(
                stack,
                requiredCount,
                0,
                false,
                acceptedVariants,
                null,
                List.of(),
                selectableIngredientAlternatives,
                explicitIngredientVariantSelection
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
