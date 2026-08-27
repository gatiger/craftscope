package io.github.gatiger.craftscope.production;

import java.util.List;

/*
 * Central expected-value math for probabilistic production.
 *
 * The production model already stores a per-output chance on
 * CraftScopeResourceAmount. This helper makes every caller use the
 * same interpretation instead of duplicating chance math in Recipe
 * Tree, Process Diagram, Setup, and route expansion.
 *
 * Planning policy:
 *
 * - Guaranteed output: ordinary integer recipe math.
 * - Chance output: plan enough recipe runs for the EXPECTED output
 *   to meet the requested quantity.
 * - This is an expectation, not a guarantee. The UI labels chance
 *   outputs as expected values rather than promising the player a
 *   deterministic result.
 *
 * Example:
 *
 * Sand -> Clay Ball x1 at 25%
 *
 * Expected yield = 0.25 Clay Ball / run.
 * Request 1 Clay Ball -> 4 planned runs.
 */
public final class CraftScopeChancePlanner {

    private static final double GUARANTEED_EPSILON = 0.000001D;

    private static final double MATH_EPSILON = 0.000000001D;

    private CraftScopeChancePlanner() {
    }

    public static boolean isChanceOutput(
            CraftScopeResourceAmount resource
    ) {
        return resource != null
                && resource.chance()
                < 1.0D - GUARANTEED_EPSILON;
    }

    public static double expectedAmount(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null) {
            return 0.0D;
        }

        return safeExpectedMultiply(
                resource.amount(),
                resource.chance()
        );
    }

    public static double expectedAmount(
            CraftScopeResourceAmount resource,
            long runs
    ) {
        if (resource == null || runs <= 0) {
            return 0.0D;
        }

        double perRun =
                expectedAmount(resource);

        if (perRun <= 0.0D) {
            return 0.0D;
        }

        double result =
                perRun * runs;

        if (Double.isNaN(result)) {
            return 0.0D;
        }

        if (Double.isInfinite(result)) {
            return Double.MAX_VALUE;
        }

        return Math.max(
                0.0D,
                result
        );
    }

    /*
     * Expected yield of the route's requested target per route run.
     *
     * Prefer outputs from the final step. This correctly handles a
     * recipe that contains more than one roll for the same target
     * item while avoiding accidental double-counting of an
     * intermediate resource in a multi-step route.
     */
    public static double expectedTargetAmountPerRun(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.targetOutput() == null) {

            return 0.0D;
        }

        List<CraftScopeProductionStep> steps =
                route.steps();

        if (steps != null && !steps.isEmpty()) {
            CraftScopeProductionStep finalStep =
                    steps.getLast();

            double finalStepExpected =
                    expectedMatchingOutputs(
                            finalStep == null
                                    ? List.of()
                                    : finalStep.outputs(),
                            route.targetOutput()
                    );

            if (finalStepExpected > MATH_EPSILON) {
                return finalStepExpected;
            }
        }

        /*
         * Defensive fallback for providers that expose targetOutput
         * but do not duplicate it in the final step output list.
         */
        return expectedAmount(
                route.targetOutput()
        );
    }

    public static long requiredRuns(
            CraftScopeProductionRoute route,
            long requestedAmount
    ) {
        long requested =
                Math.max(
                        1L,
                        requestedAmount
                );

        double expectedPerRun =
                expectedTargetAmountPerRun(
                        route
                );

        if (!(expectedPerRun > MATH_EPSILON)) {
            /*
             * A zero-chance target is not meaningfully plannable.
             * Providers should not expose one, but returning a
             * conservative saturated value is safer than silently
             * treating it as guaranteed.
             */
            return Long.MAX_VALUE;
        }

        double rawRuns =
                requested
                        / expectedPerRun;

        if (Double.isNaN(rawRuns)) {
            return Long.MAX_VALUE;
        }

        if (Double.isInfinite(rawRuns)
                || rawRuns >= Long.MAX_VALUE) {

            return Long.MAX_VALUE;
        }

        long runs =
                (long) Math.ceil(
                        rawRuns - MATH_EPSILON
                );

        return Math.max(
                1L,
                runs
        );
    }

    public static double expectedMatchingOutputs(
            List<CraftScopeResourceAmount> outputs,
            CraftScopeResourceAmount target
    ) {
        if (outputs == null
                || outputs.isEmpty()
                || target == null) {

            return 0.0D;
        }

        double expected =
                0.0D;

        for (CraftScopeResourceAmount output : outputs) {
            if (!matchesTarget(
                    output,
                    target
            )) {
                continue;
            }

            expected +=
                    expectedAmount(output);

            if (Double.isInfinite(expected)) {
                return Double.MAX_VALUE;
            }
        }

        return Math.max(
                0.0D,
                expected
        );
    }

    private static boolean matchesTarget(
            CraftScopeResourceAmount candidate,
            CraftScopeResourceAmount target
    ) {
        if (candidate == null || target == null) {
            return false;
        }

        if (candidate.kind() != target.kind()) {
            return false;
        }

        if (!candidate.id().equals(
                target.id()
        )) {
            return false;
        }

        return candidate.unit().equals(
                target.unit()
        );
    }

    private static double safeExpectedMultiply(
            long amount,
            double chance
    ) {
        if (amount <= 0L) {
            return 0.0D;
        }

        double normalizedChance =
                normalizeChance(
                        chance
                );

        if (normalizedChance <= 0.0D) {
            return 0.0D;
        }

        double result =
                amount
                        * normalizedChance;

        if (Double.isNaN(result)) {
            return 0.0D;
        }

        if (Double.isInfinite(result)) {
            return Double.MAX_VALUE;
        }

        return Math.max(
                0.0D,
                result
        );
    }

    private static double normalizeChance(
            double chance
    ) {
        if (Double.isNaN(chance)
                || Double.isInfinite(chance)) {

            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        chance
                )
        );
    }
}
