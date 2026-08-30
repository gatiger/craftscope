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
        return summarize(
                route,
                requestedTargetCount,
                false
        );
    }

    /*
     * singleExecution is intended for presentation layers that need
     * to describe what ONE execution of a process produces.
     *
     * Planning callers should continue using the normal two-argument
     * summarize(...) method.
     */
    public static CraftScopeProductionSummary summarize(
            CraftScopeProductionRoute route,
            long requestedTargetCount,
            boolean singleExecution
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
                singleExecution
                        ? 1L
                        : CraftScopeChancePlanner.requiredRuns(
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
         * Production from upstream steps that feeds later steps in
         * this same route.
         *
         * Keep two views:
         *
         * expected supply
         *     Used by planning. A probabilistic upstream route was
         *     already expanded to enough attempts to meet the
         *     downstream requirement in expectation.
         *
         * nominal supply
         *     Used to hide those intermediate rows from the final
         *     Outputs list. Their detailed probability/range remains
         *     visible in Process Diagram instead.
         */
        Map<String, Double> internalExpectedSupply =
                new LinkedHashMap<>();

        Map<String, Long> internalNominalSupply =
                new LinkedHashMap<>();

        /*
         * Every produced resource, including final output and
         * possible byproducts.
         */
        Map<String, ResourceAccumulator> produced =
                new LinkedHashMap<>();

        for (int stepIndex = 0;
             stepIndex < route.steps().size();
             stepIndex++) {

            CraftScopeProductionStep step =
                    route.steps().get(
                            stepIndex
                    );

            boolean internalStep =
                    stepIndex
                            < route.steps().size() - 1;

            collectMachineRequirements(
                    step,
                    machines
            );

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                String key =
                        buildResourceFamilyKey(
                                input
                        );

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

                if (internalStep) {

                    String familyKey =
                            buildResourceFamilyKey(
                                    output
                            );

                    double expected =
                            scaleExpectedAmount(
                                    output.expectedAmount(),
                                    runs
                            );

                    internalExpectedSupply.merge(
                            familyKey,
                            expected,
                            CraftScopeProductionSummary
                                    ::safeAddExpected
                    );

                    internalNominalSupply.merge(
                            familyKey,
                            amount,
                            CraftScopeProductionSummary
                                    ::safeAdd
                    );
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

        if (!containsResourceFamily(
                produced,
                route.targetOutput()
        )) {
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

            /*
             * The route target is final production, not internal
             * intermediate supply.
             */
        }

        List<CraftScopeResourceAmount> requiredResources =
                buildRequiredResources(
                        consumedDemand,
                        reusableDemand,
                        internalExpectedSupply
                );

        List<CraftScopeResourceAmount> finalOutputs =
                buildNetOutputs(
                        produced,
                        internalNominalSupply,
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
            Map<String, Double> internalExpectedSupply
    ) {
        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        for (Map.Entry<String, ResourceAccumulator> entry :
                consumedDemand.entrySet()) {

            ResourceAccumulator demand =
                    entry.getValue();

            double expectedSupply =
                    internalExpectedSupply.getOrDefault(
                            entry.getKey(),
                            0.0D
                    );

            /*
             * Items are discrete. Only complete expected items can
             * satisfy a downstream integer requirement.
             *
             * The tiny epsilon protects exact values such as 9.0
             * from floating-point representation noise.
             */
            long supplied =
                    wholeExpectedAmount(
                            expectedSupply
                    );

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
            Map<String, Long> internalNominalSupply,
            Map<String, ResourceAccumulator> consumedDemand,
            CraftScopeResourceAmount targetOutput
    ) {
        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        String targetKey =
                buildResourceKey(
                        targetOutput
                );

        String targetFamilyKey =
                buildResourceFamilyKey(
                        targetOutput
                );

        /*
         * Only an intermediate family that is actually consumed by a
         * later step should be hidden from final outputs.
         */
        Map<String, Long> remainingInternalSuppression =
                new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry :
                internalNominalSupply.entrySet()) {

            ResourceAccumulator demand =
                    consumedDemand.get(
                            entry.getKey()
                    );

            if (demand == null
                    || demand.amount <= 0
                    || entry.getValue() == null
                    || entry.getValue() <= 0L) {

                continue;
            }

            remainingInternalSuppression.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        for (Map.Entry<String, ResourceAccumulator> entry :
                produced.entrySet()) {

            ResourceAccumulator producedResource =
                    entry.getValue();

            String familyKey =
                    buildResourceFamilyKey(
                            producedResource.representative
                    );

            long suppress =
                    remainingInternalSuppression.getOrDefault(
                            familyKey,
                            0L
                    );

            long internallyUsed =
                    Math.min(
                            producedResource.amount,
                            suppress
                    );

            if (internallyUsed > 0L) {

                long remainingSuppression =
                        suppress
                                - internallyUsed;

                if (remainingSuppression <= 0L) {

                    remainingInternalSuppression.remove(
                            familyKey
                    );

                } else {

                    remainingInternalSuppression.put(
                            familyKey,
                            remainingSuppression
                    );
                }
            }

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

            if (buildResourceFamilyKey(
                    output
            ).equals(
                    targetFamilyKey
            )) {

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
     * Internal planning helpers
     * ---------------------------------------------------------
     */

    private static double scaleExpectedAmount(
            double expectedAmount,
            long runs
    ) {
        if (expectedAmount <= 0.0D
                || runs <= 0L) {

            return 0.0D;
        }

        double result =
                expectedAmount
                        * (double) runs;

        return Double.isFinite(
                result
        )
                ? result
                : Double.MAX_VALUE;
    }

    private static double safeAddExpected(
            double left,
            double right
    ) {
        double result =
                left + right;

        return Double.isFinite(
                result
        )
                ? result
                : Double.MAX_VALUE;
    }

    private static long wholeExpectedAmount(
            double expected
    ) {
        if (!Double.isFinite(
                expected
        )) {

            return Long.MAX_VALUE;
        }

        if (expected <= 0.0D) {
            return 0L;
        }

        if (expected >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        return (long) Math.floor(
                expected
                        + 0.0000001D
        );
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

    private static boolean containsResourceFamily(
            Map<String, ResourceAccumulator> resources,
            CraftScopeResourceAmount target
    ) {
        if (resources == null
                || resources.isEmpty()
                || target == null) {

            return false;
        }

        String targetFamilyKey =
                buildResourceFamilyKey(
                        target
                );

        for (ResourceAccumulator accumulator :
                resources.values()) {

            if (accumulator == null
                    || accumulator.representative == null) {

                continue;
            }

            if (targetFamilyKey.equals(
                    buildResourceFamilyKey(
                            accumulator.representative
                    )
            )) {

                return true;
            }
        }

        return false;
    }

    private static String buildResourceFamilyKey(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null) {
            return "null";
        }

        List<String> variants =
                new ArrayList<>();

        for (ResourceLocation id :
                resource.acceptedVariantIds()) {

            if (id != null) {
                variants.add(
                        id.toString()
                );
            }
        }

        variants.sort(
                String::compareTo
        );

        if (variants.isEmpty()
                && resource.id() != null) {

            variants.add(
                    resource
                            .id()
                            .toString()
            );
        }

        String key =
                resource.kind()
                        + "|"
                        + String.join(
                        ",",
                        variants
                )
                        + "|"
                        + resource.unit();

        /*
         * Component-bearing items remain distinct.
         *
         * Poison Tipped Arrow and Slowness Tipped Arrow, for
         * example, must never be treated as the same resource.
         */
        if (resource.hasCustomItemComponents()) {

            key +=
                    "|itemIdentity="
                            + resource.itemIdentity();
        }

        return key;
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
