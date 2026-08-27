package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Converts raw provider routes into the logical routes that
 * should be presented to the player.
 *
 * Responsibilities:
 *
 * 1. Combine equivalent resource variants.
 *
 *    Iron Ore
 *    Deepslate Iron Ore
 *
 *    become one input group.
 *
 * 2. Combine equivalent processing methods, even when the
 *    methods are supplied by different providers/mods.
 *
 *    Smelting
 *    Blasting
 *    Create Bulk Blasting
 *
 *    can become alternative methods on one production step when
 *    they represent the same material transformation.
 *
 * 3. Keep materially different paths separate.
 *
 *    Ore
 *    Raw Iron
 *    Iron Nuggets
 *
 *    remain distinct production routes.
 *
 * 4. Keep routes with materially different output sets separate.
 *
 *    This is important for processes such as crushing where two
 *    methods may accept the same input and produce the same target
 *    item but differ in secondary/chance outputs. CraftScope's
 *    current method model does not yet store method-specific
 *    outputs, so those routes must not be collapsed together.
 */
public final class CraftScopeProductionRouteNormalizer {

    private CraftScopeProductionRouteNormalizer() {
    }

    public static List<CraftScopeProductionRoute> normalize(
            List<CraftScopeProductionRoute> rawRoutes
    ) {
        if (rawRoutes == null
                || rawRoutes.isEmpty()) {

            return List.of();
        }

        List<CraftScopeProductionRoute> result =
                new ArrayList<>();

        Map<String, List<CraftScopeProductionRoute>>
                mergeGroups =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                rawRoutes) {

            /*
             * Multi-step routes are already meaningful process
             * chains. Do not attempt to merge their internal
             * structure with this one-step normalizer.
             */
            if (route.steps().size() != 1) {

                result.add(route);
                continue;
            }

            String mergeKey =
                    buildMergeKey(route);

            mergeGroups
                    .computeIfAbsent(
                            mergeKey,
                            ignored -> new ArrayList<>()
                    )
                    .add(route);
        }

        for (List<CraftScopeProductionRoute> group :
                mergeGroups.values()) {

            result.add(
                    mergeGroup(group)
            );
        }

        result.sort(
                Comparator
                        .comparingInt(
                                CraftScopeProductionRoute::priority
                        )
                        .reversed()
                        .thenComparing(
                                route ->
                                        route
                                                .sourceModName()
                                                .getString()
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .displayName()
                                                .getString()
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .id()
                                                .toString()
                        )
        );

        return List.copyOf(result);
    }

    /*
     * Deliberately does NOT include route.sourceModId().
     *
     * The material route and the processing method are different
     * concepts. If Minecraft smelting and Create bulk blasting
     * perform the same transformation, Recipe Tree should show one
     * material route while Process Diagram exposes both methods.
     *
     * The full output signature IS included so methods with
     * different byproducts/chances do not get merged until
     * CraftScope can model method-specific outputs.
     */
    private static String buildMergeKey(
            CraftScopeProductionRoute route
    ) {
        CraftScopeProductionStep step =
                route
                        .steps()
                        .getFirst();

        List<String> inputKeys =
                new ArrayList<>();

        for (CraftScopeResourceAmount input :
                step.inputs()) {

            inputKeys.add(
                    buildLogicalResourceKey(input)
            );
        }

        inputKeys.sort(String::compareTo);

        List<String> outputKeys =
                new ArrayList<>();

        for (CraftScopeResourceAmount output :
                step.outputs()) {

            outputKeys.add(
                    buildLogicalResourceKey(output)
            );
        }

        outputKeys.sort(String::compareTo);

        return buildLogicalResourceKey(
                route.targetOutput()
        )
                + "|inputs="
                + String.join(
                        ";",
                        inputKeys
                )
                + "|outputs="
                + String.join(
                        ";",
                        outputKeys
                );
    }

    private static String buildLogicalResourceKey(
            CraftScopeResourceAmount resource
    ) {
        Set<String> canonicalVariants =
                new LinkedHashSet<>();

        for (ResourceLocation variantId :
                resource.acceptedVariantIds()) {

            canonicalVariants.add(
                    canonicalizeVariant(
                            variantId
                    ).toString()
            );
        }

        List<String> sorted =
                new ArrayList<>(
                        canonicalVariants
                );

        sorted.sort(String::compareTo);

        return resource.kind()
                + ":"
                + String.join(
                        ",",
                        sorted
                )
                + ":"
                + resource.amount()
                + ":"
                + resource.unit()
                + ":"
                + resource.consumed()
                + ":chance="
                + resource.chance();
    }

    /*
     * Initial family normalization.
     *
     * minecraft:iron_ore
     * minecraft:deepslate_iron_ore
     *
     * both normalize to:
     *
     * minecraft:iron_ore
     *
     * The same convention also works for modded ores following
     * the normal deepslate_<material>_ore naming pattern.
     *
     * Later this can be augmented with tags where useful.
     */
    private static ResourceLocation canonicalizeVariant(
            ResourceLocation id
    ) {
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

        ResourceLocation normalized =
                ResourceLocation.tryParse(
                        id.getNamespace()
                                + ":"
                                + path
                );

        return normalized == null
                ? id
                : normalized;
    }

    private static CraftScopeProductionRoute mergeGroup(
            List<CraftScopeProductionRoute> group
    ) {
        CraftScopeProductionRoute representative =
                group.stream()
                        .max(
                                Comparator
                                        .comparingInt(
                                                CraftScopeProductionRoute::priority
                                        )
                                        .thenComparing(
                                                route ->
                                                        route
                                                                .id()
                                                                .toString()
                                        )
                        )
                        .orElseThrow();

        CraftScopeProductionStep representativeStep =
                representative
                        .steps()
                        .getFirst();

        List<CraftScopeResourceAmount> mergedInputs =
                mergeInputs(group);

        List<CraftScopeProductionMethod> mergedMethods =
                mergeMethods(group);

        Component routeName =
                buildRouteDisplayName(
                        representative,
                        mergedInputs
                );

        CraftScopeProductionStep mergedStep =
                new CraftScopeProductionStep(
                        representativeStep.id()
                                + ":normalized",
                        routeName,
                        mergedInputs,
                        representativeStep.outputs(),
                        mergedMethods
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

        return new CraftScopeProductionRoute(
                representative.id(),
                representative.sourceModId(),
                representative.sourceModName(),
                routeName,
                representative.targetOutput(),
                List.of(mergedStep),
                priority
        );
    }

    private static List<CraftScopeResourceAmount> mergeInputs(
            List<CraftScopeProductionRoute> group
    ) {
        Map<String, List<CraftScopeResourceAmount>>
                groupedInputs =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                group) {

            CraftScopeProductionStep step =
                    route
                            .steps()
                            .getFirst();

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                String key =
                        buildLogicalResourceKey(
                                input
                        );

                groupedInputs
                        .computeIfAbsent(
                                key,
                                ignored ->
                                        new ArrayList<>()
                        )
                        .add(input);
            }
        }

        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        for (List<CraftScopeResourceAmount> equivalents :
                groupedInputs.values()) {

            CraftScopeResourceAmount representative =
                    equivalents.getFirst();

            Set<ResourceLocation> variantIds =
                    new LinkedHashSet<>();

            for (CraftScopeResourceAmount equivalent :
                    equivalents) {

                variantIds.addAll(
                        equivalent.acceptedVariantIds()
                );
            }

            List<ResourceLocation> sortedVariants =
                    new ArrayList<>(
                            variantIds
                    );

            sortedVariants.sort(
                    Comparator.comparing(
                            ResourceLocation::toString
                    )
            );

            ResourceLocation representativeId =
                    chooseRepresentativeVariant(
                            sortedVariants
                    );

            result.add(
                    new CraftScopeResourceAmount(
                            representative.kind(),
                            representativeId,
                            representative.displayName(),
                            representative.amount(),
                            representative.unit(),
                            representative.consumed(),
                            representative.chance(),
                            sortedVariants
                    )
            );
        }

        return result;
    }

    private static ResourceLocation chooseRepresentativeVariant(
            List<ResourceLocation> variants
    ) {
        if (variants.isEmpty()) {

            throw new IllegalArgumentException(
                    "Cannot choose representative from empty variants"
            );
        }

        for (ResourceLocation id :
                variants) {

            if (!id
                    .getPath()
                    .startsWith(
                            "deepslate_"
                    )) {

                return id;
            }
        }

        return variants.getFirst();
    }

    private static List<CraftScopeProductionMethod> mergeMethods(
            List<CraftScopeProductionRoute> group
    ) {
        Map<String, MethodAccumulator> methods =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                group) {

            CraftScopeProductionStep step =
                    route
                            .steps()
                            .getFirst();

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                String key =
                        buildMethodKey(
                                method
                        );

                MethodAccumulator accumulator =
                        methods.get(
                                key
                        );

                if (accumulator == null) {

                    accumulator =
                            new MethodAccumulator(
                                    method
                            );

                    methods.put(
                            key,
                            accumulator
                    );
                }

                accumulator.addRecipeIds(
                        method.recipeIds()
                );
            }
        }

        List<CraftScopeProductionMethod> result =
                new ArrayList<>();

        for (MethodAccumulator accumulator :
                methods.values()) {

            result.add(
                    accumulator.build()
            );
        }

        return result;
    }

    private static String buildMethodKey(
            CraftScopeProductionMethod method
    ) {
        StringBuilder builder =
                new StringBuilder();

        builder.append(
                method.sourceModId()
        );

        builder.append("|");

        builder.append(
                method.processId()
        );

        for (CraftScopeProcessRequirement requirement :
                method.requirements()) {

            builder.append("|");

            builder.append(
                    requirement.kind()
            );

            builder.append(":");

            if (requirement.id() != null) {

                builder.append(
                        requirement.id()
                );
            }

            builder.append(":");

            builder.append(
                    requirement.amount()
            );

            builder.append(":");

            builder.append(
                    requirement.unit()
            );
        }

        return builder.toString();
    }

    private static Component buildRouteDisplayName(
            CraftScopeProductionRoute representative,
            List<CraftScopeResourceAmount> inputs
    ) {
        if (inputs.size() != 1) {

            return representative.displayName();
        }

        CraftScopeResourceAmount input =
                inputs.getFirst();

        if (input.kind()
                != CraftScopeResourceKind.ITEM) {

            return representative.displayName();
        }

        String path =
                canonicalizeVariant(
                        input.id()
                )
                        .getPath();

        if (path.endsWith(
                "_ore"
        )) {

            return Component.literal(
                    "Ore Processing"
            );
        }

        if (path.startsWith(
                "raw_"
        )) {

            return Component.literal(
                    "Raw Material Processing"
            );
        }

        if (path.endsWith(
                "_nugget"
        )
                && input.amount() >= 9) {

            return Component.literal(
                    "Nugget Recombination"
            );
        }

        if (path.endsWith(
                "_block"
        )
                && representative
                .targetOutput()
                .amount() > 1) {

            return Component.literal(
                    "Storage Conversion"
            );
        }

        return representative.displayName();
    }

    private static final class MethodAccumulator {

        private final CraftScopeProductionMethod representative;

        private final Set<ResourceLocation> recipeIds =
                new LinkedHashSet<>();

        private MethodAccumulator(
                CraftScopeProductionMethod representative
        ) {
            this.representative =
                    representative;
        }

        private void addRecipeIds(
                List<ResourceLocation> ids
        ) {
            recipeIds.addAll(ids);
        }

        private CraftScopeProductionMethod build() {

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
    }
}
