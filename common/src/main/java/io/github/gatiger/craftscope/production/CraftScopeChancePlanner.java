package io.github.gatiger.craftscope.production;

import java.util.List;

/*
 * Central expected-value math for probabilistic and variable-yield
 * production.
 *
 * Examples:
 *
 * Sand -> Clay Ball x1 at 25%
 *   expected = 0.25 / run
 *
 * Redstone Ore -> 4-5 Redstone Dust
 *   expected = 4.5 / run
 *
 * The planner uses expected values for quantity planning. The UI
 * still preserves whether the source was an actual probability or
 * an ordinary min-max range so it does not misrepresent one as the
 * other.
 */
public final class CraftScopeChancePlanner {

    private static final double GUARANTEED_EPSILON =
            0.000001D;

    private static final double MATH_EPSILON =
            0.000000001D;

    private CraftScopeChancePlanner() {
    }

    /*
     * Historical name retained for compatibility with the existing
     * UI mixin. It now means "not a single fixed amount".
     */
    public static boolean isChanceOutput(
            CraftScopeResourceAmount resource
    ) {
        return resource != null
                && (
                resource.isProbabilistic()
                        || resource.hasVariableRange()
        );
    }

    public static double expectedAmount(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null) {
            return 0.0D;
        }

        double value =
                resource.expectedAmount();

        if (Double.isNaN(value)
                || value <= 0.0D) {

            return 0.0D;
        }

        if (Double.isInfinite(value)) {
            return Double.MAX_VALUE;
        }

        return value;
    }

    public static double expectedAmount(
            CraftScopeResourceAmount resource,
            long runs
    ) {
        if (resource == null || runs <= 0L) {
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
            return Long.MAX_VALUE;
        }

        double rawRuns =
                requested
                        / expectedPerRun;

        if (Double.isNaN(rawRuns)
                || Double.isInfinite(rawRuns)
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

        double expected = 0.0D;

        for (CraftScopeResourceAmount output : outputs) {
            if (!matchesTarget(
                    output,
                    target
            )) {
                continue;
            }

            expected +=
                    expectedAmount(
                            output
                    );

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
}
