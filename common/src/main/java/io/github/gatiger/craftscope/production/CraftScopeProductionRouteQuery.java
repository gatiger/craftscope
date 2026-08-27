package io.github.gatiger.craftscope.production;

import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *     Which material route did the player choose?
 *
 * Expander:
 *     Build a linear multi-step chain while honoring those recipe
 *     choices for intermediate items.
 *
 * Process Diagram:
 *     Present the selected full chain PLUS every loaded direct
 *     process option for the target from the moment the view opens.
 *
 * This distinction is important:
 *
 *     MATERIAL ROUTE
 *         Redstone Ore -> Redstone Dust
 *
 *     PROCESS OPTIONS
 *         Mining
 *         Crushing
 *         Milling
 *         Smelting
 *
 * Recipe Tree remains responsible for material-route selection.
 * Process Diagram can now expose the concrete process choices
 * directly instead of collapsing them into a generic label such as
 * "Create: Ore Processing".
 */
public final class CraftScopeProductionRouteQuery {

    private CraftScopeProductionRouteQuery() {
    }

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
     * The selected Recipe Tree still determines the synchronized
     * full production chain. Direct process alternatives are then
     * added alongside that chain so the Process Diagram becomes a
     * useful route/process selector instead of a read-only mirror.
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

        /*
         * These are the material routes compatible with the Recipe
         * Tree's current root choice. Only these routes feed the
         * multi-step chain expander.
         */
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

        /*
         * Process options should remain inside the source ecosystem
         * the player is currently working in.
         *
         * Example:
         *   Recipe Source = Create
         *
         * Process Diagram may show Create Crushing, Milling, Washing,
         * etc., without suddenly mixing unrelated Minecraft-only
         * choices into that selector.
         *
         * A cross-source logical route (for example a Minecraft
         * smelting transform with a Create Bulk Blasting method)
         * intentionally exposes both ecosystems.
         */
        /*
         * Production Routes must be stable and complete as soon as
         * Process Diagram opens.
         *
         * Earlier versions inferred which process options to show
         * from the Recipe Tree's CURRENT root choice. Clicking one
         * option could change that root choice, which in turn caused
         * additional routes to appear. That made the list look as
         * though CraftScope was discovering recipes only after the
         * player clicked around.
         *
         * The process selector now always starts from every loaded
         * direct route for the target. Recipe Source still controls
         * Recipe Tree's default/root selection; Process Diagram is
         * intentionally the complete place to compare and switch
         * actual process options.
         */
        List<CraftScopeProductionRoute> processMaterialRoutes =
                directRoutes;

        List<CraftScopeProductionRoute> processOptions =
                consolidateEquivalentProcessOptions(
                        buildProcessOptions(
                                processMaterialRoutes
                        )
                );

        Map<String, CraftScopeProductionRoute> unique =
                new LinkedHashMap<>();

        /*
         * Keep the synchronized full chain available when one exists.
         */
        for (CraftScopeProductionRoute route :
                expandedRoutes) {

            unique.putIfAbsent(
                    route.id().toString(),
                    route
            );
        }

        /*
         * Then expose concrete process choices such as:
         *
         *   Create: Crushing
         *   Create: Milling
         *   Minecraft: Mining Redstone Ore
         */
        for (CraftScopeProductionRoute route :
                processOptions) {

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

    public static List<CraftScopeProductionRoute> findRawRoutes(
            ItemStack target
    ) {
        return CraftScopeProductionRouteRegistry.findRoutes(
                target
        );
    }

    /*
     * Clean one-step/direct material routes. Recipe Tree continues
     * using these normalized routes recursively.
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

    /*
     * Convert each one-step material route into concrete process
     * options for Process Diagram.
     *
     * One logical normalized route can contain multiple methods.
     * Instead of one vague row such as "Create: Ore Processing",
     * each method receives a selectable UI route.
     *
     * The original route ID is preserved when there is only one
     * method. Multi-method routes receive deterministic UI-only IDs
     * so selection can be preserved across screen rebuilds.
     */
    private static List<CraftScopeProductionRoute> buildProcessOptions(
            List<CraftScopeProductionRoute> routes
    ) {
        if (routes == null
                || routes.isEmpty()) {

            return List.of();
        }

        List<CraftScopeProductionRoute> result =
                new ArrayList<>();

        for (CraftScopeProductionRoute route : routes) {
            if (route == null
                    || route.steps().size() != 1) {

                if (route != null) {
                    result.add(route);
                }

                continue;
            }

            CraftScopeProductionStep step =
                    route.steps().getFirst();

            if (step.methods().isEmpty()) {
                result.add(route);
                continue;
            }

            for (int methodIndex = 0;
                 methodIndex < step.methods().size();
                 methodIndex++) {

                CraftScopeProductionMethod method =
                        step.methods().get(
                                methodIndex
                        );

                ResourceLocation optionId =
                        step.methods().size() == 1
                                ? route.id()
                                : buildProcessOptionId(
                                route,
                                method,
                                methodIndex
                        );

                Component optionName =
                        method.displayName();

                CraftScopeProductionStep optionStep =
                        new CraftScopeProductionStep(
                                step.id()
                                        + ":process-option:"
                                        + methodIndex,
                                optionName,
                                step.inputs(),
                                step.outputs(),
                                List.of(
                                        method
                                )
                        );

                String sourceModId =
                        method.sourceModId() == null
                                || method.sourceModId().isBlank()
                                ? route.sourceModId()
                                : method.sourceModId();

                Component sourceModName =
                        Component.literal(
                                formatSourceName(
                                        sourceModId,
                                        route
                                )
                        );

                result.add(
                        new CraftScopeProductionRoute(
                                optionId,
                                sourceModId,
                                sourceModName,
                                optionName,
                                route.targetOutput(),
                                List.of(
                                        optionStep
                                ),
                                route.priority()
                        )
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    /*
     * ---------------------------------------------------------
     * Process-option consolidation
     * ---------------------------------------------------------
     *
     * Normal Ore and Deepslate Ore are variants of one material
     * family, not two different processes.
     *
     * Create can legitimately register separate recipe IDs for:
     *
     *   minecraft:redstone_ore
     *   minecraft:deepslate_redstone_ore
     *
     * and their secondary stone byproducts can also differ:
     *
     *   Cobblestone
     *   Cobbled Deepslate
     *
     * The raw recipes must stay separate because that information
     * is real. The Process Diagram, however, should present one
     * "Create: Crushing" choice with the ore input cycling between
     * both accepted variants.
     *
     * This consolidation is deliberately UI/query-layer behavior.
     * Recipe Tree and provider data remain exact.
     */
    private static List<CraftScopeProductionRoute>
    consolidateEquivalentProcessOptions(
            List<CraftScopeProductionRoute> options
    ) {
        if (options == null
                || options.isEmpty()) {

            return List.of();
        }

        Map<String, List<CraftScopeProductionRoute>> groups =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute option : options) {
            groups
                    .computeIfAbsent(
                            buildProcessFamilyKey(
                                    option
                            ),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            option
                    );
        }

        List<CraftScopeProductionRoute> result =
                new ArrayList<>();

        for (List<CraftScopeProductionRoute> group :
                groups.values()) {

            result.add(
                    mergeProcessOptionGroup(
                            group
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static String buildProcessFamilyKey(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return route == null
                    ? "null"
                    : route.id().toString();
        }

        CraftScopeProductionStep step =
                route.steps().getFirst();

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null) {
            return route.id().toString();
        }

        List<String> inputFamilies =
                new ArrayList<>();

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            inputFamilies.add(
                    buildProcessResourceFamilyKey(
                            input,
                            true
                    )
            );
        }

        inputFamilies.sort(
                String::compareTo
        );

        CraftScopeResourceAmount target =
                route.targetOutput();

        return route.sourceModId()
                + "|process="
                + (
                method.processId() == null
                        ? ""
                        : method.processId()
        )
                + "|target="
                + buildExactResourceIdentity(
                target
        )
                + "|inputs="
                + String.join(
                ";",
                inputFamilies
        );
    }

    /*
     * Input-family identity recognizes the standard
     * deepslate_<material>_ore naming convention.
     */
    private static String buildProcessResourceFamilyKey(
            CraftScopeResourceAmount resource,
            boolean input
    ) {
        if (resource == null) {
            return "null";
        }

        List<String> variants =
                new ArrayList<>();

        for (ResourceLocation id :
                resource.acceptedVariantIds()) {

            if (id == null) {
                continue;
            }

            ResourceLocation familyId =
                    input
                            ? canonicalizeProcessInputVariant(
                            id
                    )
                            : canonicalizeProcessOutputVariant(
                            id
                    );

            String text =
                    familyId.toString();

            if (!variants.contains(
                    text
            )) {
                variants.add(
                        text
                );
            }
        }

        if (variants.isEmpty()
                && resource.id() != null) {

            ResourceLocation familyId =
                    input
                            ? canonicalizeProcessInputVariant(
                            resource.id()
                    )
                            : canonicalizeProcessOutputVariant(
                            resource.id()
                    );

            variants.add(
                    familyId.toString()
            );
        }

        variants.sort(
                String::compareTo
        );

        return resource.kind()
                + "|"
                + String.join(
                ",",
                variants
        )
                + "|amount="
                + resource.amount()
                + "|unit="
                + resource.unit()
                + "|consumed="
                + resource.consumed()
                + "|chance="
                + Double.toHexString(
                resource.chance()
        )
                + "|min="
                + resource.minimumAmount()
                + "|max="
                + resource.maximumAmount()
                + "|expected="
                + Double.toHexString(
                resource.expectedAmount()
        );
    }

    private static String buildExactResourceIdentity(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null) {
            return "null";
        }

        return resource.kind()
                + "|"
                + resource.id()
                + "|amount="
                + resource.amount()
                + "|unit="
                + resource.unit()
                + "|chance="
                + Double.toHexString(
                resource.chance()
        )
                + "|min="
                + resource.minimumAmount()
                + "|max="
                + resource.maximumAmount()
                + "|expected="
                + Double.toHexString(
                resource.expectedAmount()
        );
    }

    private static ResourceLocation canonicalizeProcessInputVariant(
            ResourceLocation id
    ) {
        if (id == null) {
            return requireProcessId(
                    "craftscope:unknown"
            );
        }

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

            ResourceLocation normalized =
                    ResourceLocation.tryParse(
                            id.getNamespace()
                                    + ":"
                                    + path
                    );

            if (normalized != null) {
                return normalized;
            }
        }

        return id;
    }

    /*
     * Host-rock byproducts are the same conceptual output family
     * when the only difference is which ore variant was processed.
     * Keeping them as accepted output variants lets the resource
     * display rotate rather than inventing a second process row.
     */
    private static ResourceLocation canonicalizeProcessOutputVariant(
            ResourceLocation id
    ) {
        if (id == null) {
            return requireProcessId(
                    "craftscope:unknown"
            );
        }

        if ("minecraft:cobblestone".equals(
                id.toString()
        )
                || "minecraft:cobbled_deepslate".equals(
                id.toString()
        )) {

            return requireProcessId(
                    "craftscope:host_rock_rubble"
            );
        }

        return id;
    }

    private static CraftScopeProductionRoute mergeProcessOptionGroup(
            List<CraftScopeProductionRoute> group
    ) {
        if (group == null
                || group.isEmpty()) {

            throw new IllegalArgumentException(
                    "Cannot merge an empty process-option group"
            );
        }

        if (group.size() == 1) {
            return group.getFirst();
        }

        CraftScopeProductionRoute representative =
                group.getFirst();

        CraftScopeProductionStep representativeStep =
                representative
                        .steps()
                        .getFirst();

        List<CraftScopeResourceAmount> mergedInputs =
                mergeProcessResources(
                        group,
                        true
                );

        List<CraftScopeResourceAmount> mergedOutputs =
                mergeProcessResources(
                        group,
                        false
                );

        CraftScopeProductionMethod mergedMethod =
                mergeProcessMethods(
                        group
                );

        int priority =
                group.stream()
                        .mapToInt(
                                CraftScopeProductionRoute::priority
                        )
                        .max()
                        .orElse(
                                representative.priority()
                        );

        CraftScopeProductionStep mergedStep =
                new CraftScopeProductionStep(
                        representativeStep.id()
                                + ":ore-variants",
                        mergedMethod.displayName(),
                        mergedInputs,
                        mergedOutputs,
                        List.of(
                                mergedMethod
                        )
                );

        return new CraftScopeProductionRoute(
                representative.id(),
                representative.sourceModId(),
                representative.sourceModName(),
                mergedMethod.displayName(),
                representative.targetOutput(),
                List.of(
                        mergedStep
                ),
                priority
        );
    }

    private static List<CraftScopeResourceAmount>
    mergeProcessResources(
            List<CraftScopeProductionRoute> group,
            boolean inputs
    ) {
        Map<String, ResourceAccumulator> resources =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                group) {

            CraftScopeProductionStep step =
                    route
                            .steps()
                            .getFirst();

            List<CraftScopeResourceAmount> list =
                    inputs
                            ? step.inputs()
                            : step.outputs();

            for (CraftScopeResourceAmount resource :
                    list) {

                String key =
                        buildProcessResourceFamilyKey(
                                resource,
                                inputs
                        );

                ResourceAccumulator accumulator =
                        resources.get(
                                key
                        );

                if (accumulator == null) {
                    accumulator =
                            new ResourceAccumulator(
                                    resource
                            );

                    resources.put(
                            key,
                            accumulator
                    );
                }

                accumulator.addVariants(
                        resource.acceptedVariantIds()
                );
            }
        }

        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        for (ResourceAccumulator accumulator :
                resources.values()) {

            result.add(
                    accumulator.build()
            );
        }

        return List.copyOf(
                result
        );
    }

    private static CraftScopeProductionMethod mergeProcessMethods(
            List<CraftScopeProductionRoute> group
    ) {
        CraftScopeProductionMethod representative =
                group
                        .getFirst()
                        .steps()
                        .getFirst()
                        .getPrimaryMethod();

        if (representative == null) {
            throw new IllegalArgumentException(
                    "Process-option group has no production method"
            );
        }

        Set<ResourceLocation> recipeIds =
                new LinkedHashSet<>();

        for (CraftScopeProductionRoute route :
                group) {

            CraftScopeProductionMethod method =
                    route
                            .steps()
                            .getFirst()
                            .getPrimaryMethod();

            if (method == null) {
                continue;
            }

            recipeIds.addAll(
                    method.recipeIds()
            );
        }

        return new CraftScopeProductionMethod(
                representative.sourceModId(),
                representative.processId(),
                representative.displayName(),
                new ArrayList<>(
                        recipeIds
                ),
                representative.requirements()
        );
    }

    private static ResourceLocation requireProcessId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(
                        value
                );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid process resource ID: "
                            + value
            );
        }

        return id;
    }

    private static final class ResourceAccumulator {

        private final CraftScopeResourceAmount representative;

        private final Set<ResourceLocation> variants =
                new LinkedHashSet<>();

        private ResourceAccumulator(
                CraftScopeResourceAmount representative
        ) {
            this.representative =
                    representative;

            addVariants(
                    representative.acceptedVariantIds()
            );
        }

        private void addVariants(
                List<ResourceLocation> ids
        ) {
            if (ids == null) {
                return;
            }

            for (ResourceLocation id :
                    ids) {

                if (id != null) {
                    variants.add(
                            id
                    );
                }
            }
        }

        private CraftScopeResourceAmount build() {
            List<ResourceLocation> accepted =
                    new ArrayList<>(
                            variants
                    );

            accepted.sort(
                    Comparator.comparing(
                            ResourceLocation::toString
                    )
            );

            ResourceLocation representativeId =
                    accepted.isEmpty()
                            ? representative.id()
                            : chooseProcessRepresentative(
                            accepted
                    );

            return new CraftScopeResourceAmount(
                    representative.kind(),
                    representativeId,
                    representative.displayName(),
                    representative.amount(),
                    representative.unit(),
                    representative.consumed(),
                    representative.chance(),
                    accepted,
                    representative.minimumAmount(),
                    representative.maximumAmount(),
                    representative.expectedAmount()
            );
        }
    }

    private static ResourceLocation chooseProcessRepresentative(
            List<ResourceLocation> ids
    ) {
        if (ids == null
                || ids.isEmpty()) {

            return requireProcessId(
                    "craftscope:unknown"
            );
        }

        for (ResourceLocation id : ids) {
            if (!id
                    .getPath()
                    .startsWith(
                            "deepslate_"
                    )
                    && !"minecraft:cobbled_deepslate".equals(
                    id.toString()
            )) {

                return id;
            }
        }

        return ids.getFirst();
    }

    private static ResourceLocation buildProcessOptionId(
            CraftScopeProductionRoute route,
            CraftScopeProductionMethod method,
            int methodIndex
    ) {
        String methodNamespace =
                method.processId() == null
                        ? "process"
                        : method.processId()
                        .getNamespace();

        String methodPath =
                method.processId() == null
                        ? "method"
                        : method.processId()
                        .getPath();

        methodPath =
                methodPath
                        .replace(
                                '/',
                                '_'
                        )
                        .replace(
                                ':',
                                '_'
                        );

        String value =
                route.id().getNamespace()
                        + ":"
                        + route.id().getPath()
                        + "/process-option/"
                        + methodNamespace
                        + "_"
                        + methodPath
                        + "_"
                        + methodIndex;

        ResourceLocation id =
                ResourceLocation.tryParse(
                        value
                );

        return id == null
                ? route.id()
                : id;
    }

    private static String formatSourceName(
            String sourceModId,
            CraftScopeProductionRoute fallbackRoute
    ) {
        if (sourceModId == null
                || sourceModId.isBlank()) {

            return fallbackRoute
                    .sourceModName()
                    .getString();
        }

        if ("minecraft".equals(
                sourceModId
        )) {
            return "Minecraft";
        }

        if ("create".equals(
                sourceModId
        )) {
            return "Create";
        }

        if ("craftscope".equals(
                sourceModId
        )) {
            return "CraftScope";
        }

        String[] pieces =
                sourceModId.split(
                        "[_\\-]"
                );

        StringBuilder result =
                new StringBuilder();

        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            piece.charAt(0)
                    )
            );

            if (piece.length() > 1) {
                result.append(
                        piece.substring(1)
                );
            }
        }

        return result.isEmpty()
                ? fallbackRoute
                .sourceModName()
                .getString()
                : result.toString();
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
         * Never blank Process Diagram because an old persisted
         * override no longer matches loaded recipe data.
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
