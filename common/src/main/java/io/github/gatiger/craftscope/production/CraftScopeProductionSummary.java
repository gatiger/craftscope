package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Calculates the route-level requirements and outputs for a
 * selected production route.
 *
 * The route passed to this class should already contain the
 * player's selected method for each step.
 *
 * Responsibilities:
 *
 * - Scale consumed resources to the requested target quantity.
 * - Do not multiply reusable resources by recipe runs.
 * - Remove intermediate resources that are produced and then
 *   consumed by later steps in the same route.
 * - Collect the selected method's machine requirements.
 * - Return final outputs and route byproducts.
 *
 * This model is deliberately UI-independent so the same summary
 * can later power both Process Diagram and Setup.
 */
public record CraftScopeProductionSummary(
        long runs,
        List<CraftScopeProcessRequirement> machines,
        List<CraftScopeResourceAmount> resources,
        List<CraftScopeResourceAmount> outputs
) {

    public CraftScopeProductionSummary {
        machines =
                machines == null
                        ? List.of()
                        : List.copyOf(machines);

        resources =
                resources == null
                        ? List.of()
                        : List.copyOf(resources);

        outputs =
                outputs == null
                        ? List.of()
                        : List.copyOf(outputs);
    }

    public static CraftScopeProductionSummary empty() {
        return new CraftScopeProductionSummary(
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public static CraftScopeProductionSummary summarize(
            CraftScopeProductionRoute route,
            long requestedTargetCount
    ) {
        if (route == null) {
            return empty();
        }

        long requested =
                Math.max(
                        1,
                        requestedTargetCount
                );

        /*
         * Route runs are based on expected target yield. Guaranteed
         * outputs still use ordinary integer recipe math, while
         * probabilistic outputs scale the required attempts.
         */
        long runs =
                CraftScopeChancePlanner.requiredRuns(
                        route,
                        requested
                );

        if (runs == Long.MAX_VALUE) {
            return empty();
        }

        Map<String, RequirementAccumulator> machines =
                new LinkedHashMap<>();

        /*
         * Resources the player must provide.
         */
        Map<String, ResourceAccumulator> consumedDemand =
                new LinkedHashMap<>();

        /*
         * Catalysts/tools/resources that are required but are not
         * consumed each run.
         */
        Map<String, ResourceAccumulator> reusableDemand =
                new LinkedHashMap<>();

        /*
         * Guaranteed production from previous/internal steps.
         *
         * Only guaranteed outputs are allowed to cancel required
         * input demand.
         */
        Map<String, ResourceAccumulator> guaranteedSupply =
                new LinkedHashMap<>();

        /*
         * Every produced resource, including final output and
         * possible byproducts.
         */
        Map<String, ResourceAccumulator> produced =
                new LinkedHashMap<>();

        for (CraftScopeProductionStep step :
                route.steps()) {

            collectMachineRequirements(
                    step,
                    machines
            );

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                String key =
                        buildResourceKey(input);

                if (input.consumed()) {
                    long amount =
                            safeMultiply(
                                    input.amount(),
                                    runs
                            );

                    consumedDemand
                            .computeIfAbsent(
                                    key,
                                    ignored ->
                                            new ResourceAccumulator(
                                                    input
                                            )
                            )
                            .add(amount);
                } else {
                    /*
                     * A reusable item/resource normally only needs
                     * to exist once, regardless of the number of
                     * production runs.
                     */
                    reusableDemand
                            .computeIfAbsent(
                                    key,
                                    ignored ->
                                            new ResourceAccumulator(
                                                    input
                                            )
                            )
                            .max(input.amount());
                }
            }

            for (CraftScopeResourceAmount output :
                    step.outputs()) {

                String key =
                        buildResourceKey(output);

                long amount =
                        safeMultiply(
                                output.amount(),
                                runs
                        );

                produced
                        .computeIfAbsent(
                                key,
                                ignored ->
                                        new ResourceAccumulator(
                                                output
                                        )
                        )
                        .add(amount);

                if (output.isGuaranteed()) {
                    guaranteedSupply
                            .computeIfAbsent(
                                    key,
                                    ignored ->
                                            new ResourceAccumulator(
                                                    output
                                            )
                            )
                            .add(amount);
                }
            }
        }

        /*
         * Defensive fallback for providers that define their route
         * target but do not duplicate it in the final production
         * step's outputs.
         */
        String targetKey =
                buildResourceKey(
                        route.targetOutput()
                );

        if (!produced.containsKey(targetKey)) {
            long targetProduced =
                    safeMultiply(
                            route.targetOutput().amount(),
                            runs
                    );

            produced.put(
                    targetKey,
                    new ResourceAccumulator(
                            route.targetOutput(),
                            targetProduced
                    )
            );

            if (route.targetOutput().isGuaranteed()) {
                guaranteedSupply.put(
                        targetKey,
                        new ResourceAccumulator(
                                route.targetOutput(),
                                targetProduced
                        )
                );
            }
        }

        List<CraftScopeResourceAmount> requiredResources =
                buildRequiredResources(
                        consumedDemand,
                        reusableDemand,
                        guaranteedSupply
                );

        List<CraftScopeResourceAmount> finalOutputs =
                buildNetOutputs(
                        produced,
                        guaranteedSupply,
                        consumedDemand,
                        route.targetOutput()
                );

        List<CraftScopeProcessRequirement> machineList =
                new ArrayList<>();

        for (RequirementAccumulator accumulator :
                machines.values()) {

            machineList.add(
                    accumulator.build()
            );
        }

        /*
         * Explicit lambda parameter types are intentional here.
         *
         * Gradle/Javac in the current multi-loader build can infer
         * Object for chained Comparator lambdas when the first
         * comparison also supplies a custom String comparator.
         */
        machineList.sort(
                Comparator
                        .comparing(
                                (CraftScopeProcessRequirement requirement) ->
                                        requirement
                                                .displayName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                (CraftScopeProcessRequirement requirement) ->
                                        requirement.id() == null
                                                ? ""
                                                : requirement
                                                .id()
                                                .toString()
                        )
        );

        requiredResources.sort(
                resourceComparator()
        );

        finalOutputs.sort(
                Comparator
                        .comparingInt(
                                (CraftScopeResourceAmount resource) ->
                                        buildResourceKey(resource)
                                                .equals(targetKey)
                                                ? 0
                                                : 1
                        )
                        .thenComparing(
                                (CraftScopeResourceAmount resource) ->
                                        resource
                                                .displayName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                CraftScopeProductionSummary::resourceIdText
                        )
        );

        return new CraftScopeProductionSummary(
                runs,
                machineList,
                requiredResources,
                finalOutputs
        );
    }

    /*
     * ---------------------------------------------------------
     * Machines
     * ---------------------------------------------------------
     */

    private static void collectMachineRequirements(
            CraftScopeProductionStep step,
            Map<String, RequirementAccumulator> machines
    ) {
        if (step == null
                || step.methods().isEmpty()) {

            return;
        }

        /*
         * A route supplied to this summarizer should already have
         * the selected method first/only.
         */
        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null) {
            return;
        }

        for (CraftScopeProcessRequirement requirement :
                method.requirements()) {

            if (requirement.kind()
                    != CraftScopeRequirementKind.MACHINE) {

                continue;
            }

            String key =
                    buildRequirementKey(
                            requirement
                    );

            machines
                    .computeIfAbsent(
                            key,
                            ignored ->
                                    new RequirementAccumulator(
                                            requirement
                                    )
                    )
                    .max(requirement.amount());
        }
    }

    /*
     * ---------------------------------------------------------
     * Required external resources
     * ---------------------------------------------------------
     */

    private static List<CraftScopeResourceAmount>
    buildRequiredResources(
            Map<String, ResourceAccumulator> consumedDemand,
            Map<String, ResourceAccumulator> reusableDemand,
            Map<String, ResourceAccumulator> guaranteedSupply
    ) {
        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        for (Map.Entry<String, ResourceAccumulator> entry :
                consumedDemand.entrySet()) {

            ResourceAccumulator demand =
                    entry.getValue();

            ResourceAccumulator supply =
                    guaranteedSupply.get(
                            entry.getKey()
                    );

            long supplied =
                    supply == null
                            ? 0
                            : supply.amount;

            long required =
                    Math.max(
                            0,
                            demand.amount - supplied
                    );

            if (required <= 0) {
                continue;
            }

            result.add(
                    copyResource(
                            demand.representative,
                            required,
                            true
                    )
            );
        }

        /*
         * Non-consumed resources are listed separately as things
         * the player must have available.
         */
        for (ResourceAccumulator reusable :
                reusableDemand.values()) {

            if (reusable.amount <= 0) {
                continue;
            }

            result.add(
                    copyResource(
                            reusable.representative,
                            reusable.amount,
                            false
                    )
            );
        }

        return result;
    }

    /*
     * ---------------------------------------------------------
     * Final/net outputs
     * ---------------------------------------------------------
     */

    private static List<CraftScopeResourceAmount>
    buildNetOutputs(
            Map<String, ResourceAccumulator> produced,
            Map<String, ResourceAccumulator> guaranteedSupply,
            Map<String, ResourceAccumulator> consumedDemand,
            CraftScopeResourceAmount targetOutput
    ) {
        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        String targetKey =
                buildResourceKey(
                        targetOutput
                );

        for (Map.Entry<String, ResourceAccumulator> entry :
                produced.entrySet()) {

            String key =
                    entry.getKey();

            ResourceAccumulator producedResource =
                    entry.getValue();

            ResourceAccumulator guaranteed =
                    guaranteedSupply.get(key);

            ResourceAccumulator demand =
                    consumedDemand.get(key);

            long guaranteedAmount =
                    guaranteed == null
                            ? 0
                            : guaranteed.amount;

            long demandedAmount =
                    demand == null
                            ? 0
                            : demand.amount;

            /*
             * Only guaranteed production can be assumed to satisfy
             * a downstream internal input.
             */
            long internallyUsed =
                    Math.min(
                            guaranteedAmount,
                            demandedAmount
                    );

            long remaining =
                    Math.max(
                            0,
                            producedResource.amount
                                    - internallyUsed
                    );

            if (remaining <= 0) {
                continue;
            }

            result.add(
                    copyResource(
                            producedResource.representative,
                            remaining,
                            false
                    )
            );
        }

        /*
         * The route's requested target should always remain visible
         * as an output even if a malformed provider accidentally
         * omitted it from its step outputs.
         */
        boolean targetPresent = false;

        for (CraftScopeResourceAmount output :
                result) {

            if (buildResourceKey(output)
                    .equals(targetKey)) {

                targetPresent = true;
                break;
            }
        }

        if (!targetPresent) {
            ResourceAccumulator targetProduced =
                    produced.get(targetKey);

            if (targetProduced != null
                    && targetProduced.amount > 0) {

                result.add(
                        copyResource(
                                targetOutput,
                                targetProduced.amount,
                                false
                        )
                );
            }
        }

        return result;
    }

    /*
     * ---------------------------------------------------------
     * Keys / copies
     * ---------------------------------------------------------
     */

    private static String buildRequirementKey(
            CraftScopeProcessRequirement requirement
    ) {
        String id =
                requirement.id() == null
                        ? ""
                        : requirement
                        .id()
                        .toString();

        return requirement.kind()
                + "|"
                + id
                + "|"
                + requirement
                .displayName()
                .getString()
                + "|"
                + requirement.unit();
    }

    private static String buildResourceKey(
            CraftScopeResourceAmount resource
    ) {
        List<String> variants =
                new ArrayList<>();

        for (ResourceLocation id :
                resource.acceptedVariantIds()) {

            if (id != null) {
                variants.add(id.toString());
            }
        }

        variants.sort(String::compareTo);

        /*
         * acceptedVariantIds normally includes the primary ID.
         * Include id as a fallback so resources with no variant
         * list still receive a stable key.
         */
        if (variants.isEmpty()
                && resource.id() != null) {

            variants.add(
                    resource.id().toString()
            );
        }

        /*
         * Chance is part of output identity for aggregation.
         *
         * Example: one recipe may contain two rolls for the same
         * item at different probabilities. Merging those into one
         * ResourceAccumulator would attach one probability to a
         * mixed amount and misstate the expected yield.
         */
        String key =
                resource.kind()
                        + "|"
                        + String.join(",", variants)
                        + "|"
                        + resource.unit()
                        + "|chance="
                        + Double.toHexString(
                                resource.chance()
                        );

        /*
         * Component-bearing items must remain distinct even when
         * they share the same registry ID.
         *
         * Poison Tipped Arrow and Slowness Tipped Arrow are both
         * minecraft:tipped_arrow, but they are not interchangeable.
         */
        if (resource.hasCustomItemComponents()) {

            key +=
                    "|itemIdentity="
                            + resource.itemIdentity();
        }

        return key;
    }

    private static CraftScopeResourceAmount copyResource(
            CraftScopeResourceAmount resource,
            long amount,
            boolean consumed
    ) {
        return new CraftScopeResourceAmount(
                resource.kind(),
                resource.id(),
                resource.displayName(),
                amount,
                resource.unit(),
                consumed,
                resource.chance(),
                resource.acceptedVariantIds(),
                resource.itemIdentity()
        );
    }

    private static Comparator<CraftScopeResourceAmount>
    resourceComparator() {
        return Comparator
                .comparing(
                        (CraftScopeResourceAmount resource) ->
                                resource
                                        .kind()
                                        .name()
                )
                .thenComparing(
                        (CraftScopeResourceAmount resource) ->
                                resource
                                        .displayName()
                                        .getString(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(
                        CraftScopeProductionSummary::resourceIdText
                );
    }

    private static String resourceIdText(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null
                || resource.id() == null) {

            return "";
        }

        return resource.id().toString();
    }

    /*
     * ---------------------------------------------------------
     * Math
     * ---------------------------------------------------------
     */

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

    private static long safeAdd(
            long left,
            long right
    ) {
        if (left <= 0) {
            return Math.max(
                    0,
                    right
            );
        }

        if (right <= 0) {
            return left;
        }

        if (left
                > Long.MAX_VALUE - right) {

            return Long.MAX_VALUE;
        }

        return left + right;
    }

    /*
     * ---------------------------------------------------------
     * Accumulators
     * ---------------------------------------------------------
     */

    private static final class ResourceAccumulator {

        private final CraftScopeResourceAmount representative;
        private long amount;

        private ResourceAccumulator(
                CraftScopeResourceAmount representative
        ) {
            this(
                    representative,
                    0
            );
        }

        private ResourceAccumulator(
                CraftScopeResourceAmount representative,
                long amount
        ) {
            this.representative =
                    representative;

            this.amount =
                    Math.max(
                            0,
                            amount
                    );
        }

        private void add(long value) {
            amount =
                    safeAdd(
                            amount,
                            value
                    );
        }

        private void max(long value) {
            amount =
                    Math.max(
                            amount,
                            value
                    );
        }
    }

    private static final class RequirementAccumulator {

        private final CraftScopeProcessRequirement representative;
        private long amount;

        private RequirementAccumulator(
                CraftScopeProcessRequirement representative
        ) {
            this.representative =
                    representative;

            this.amount = 0;
        }

        private void max(long value) {
            amount =
                    Math.max(
                            amount,
                            value
                    );
        }

        private CraftScopeProcessRequirement build() {
            return new CraftScopeProcessRequirement(
                    representative.kind(),
                    representative.id(),
                    representative.displayName(),
                    amount,
                    representative.unit()
            );
        }
    }
}
