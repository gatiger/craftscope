package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionSummary;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Preserves and scales yield-range metadata when ProductionSummary
 * turns a per-run resource into a route-total resource.
 *
 * Resource identity must remain stable when one run is scaled into
 * many runs. Earlier code appended the absolute range to the key:
 *
 *   per run:  2-6
 *   16 runs: 32-96
 *
 * The scaled output then looked different from the route target, so
 * ProductionSummary defensively added the target a second time.
 *
 * The key now stores yield ratios instead. 2-6 with nominal amount
 * 2 and 32-96 with nominal amount 32 both describe the same logical
 * yield profile: 1x-3x with 2x expected.
 */
@Mixin(CraftScopeProductionSummary.class)
public abstract class MixinCraftScopeYieldSummary {

    @Inject(
            method = "buildResourceKey",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$includeVariableRangeInKey(
            CraftScopeResourceAmount resource,
            CallbackInfoReturnable<String> cir
    ) {
        if (resource == null
                || !resource.hasVariableRange()
                || resource.amount() <= 0L) {

            return;
        }

        double minimumRatio =
                (double) resource.minimumAmount()
                        / (double) resource.amount();

        double maximumRatio =
                (double) resource.maximumAmount()
                        / (double) resource.amount();

        double expectedRatio =
                resource.expectedAmount()
                        / (double) resource.amount();

        cir.setReturnValue(
                cir.getReturnValue()
                        + "|yieldRatio="
                        + Double.toHexString(minimumRatio)
                        + "-"
                        + Double.toHexString(maximumRatio)
                        + "|expectedRatio="
                        + Double.toHexString(expectedRatio)
        );
    }

    @Inject(
            method = "copyResource",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$scaleYieldMetadata(
            CraftScopeResourceAmount resource,
            long amount,
            boolean consumed,
            CallbackInfoReturnable<CraftScopeResourceAmount> cir
    ) {
        if (resource == null
                || !resource.hasVariableYield()
                || resource.amount() <= 0L
                || amount < 0L
                || amount % resource.amount() != 0L) {

            return;
        }

        long factor =
                amount
                        / resource.amount();

        long minimum =
                craftscope$safeMultiply(
                        resource.minimumAmount(),
                        factor
                );

        long maximum =
                craftscope$safeMultiply(
                        resource.maximumAmount(),
                        factor
                );

        double expected =
                resource.expectedAmount()
                        * factor;

        if (Double.isInfinite(expected)) {
            expected =
                    Double.MAX_VALUE;
        }

        cir.setReturnValue(
                new CraftScopeResourceAmount(
                        resource.kind(),
                        resource.id(),
                        resource.displayName(),
                        amount,
                        resource.unit(),
                        consumed,
                        resource.chance(),
                        resource.acceptedVariantIds(),
                        minimum,
                        maximum,
                        expected
                )
        );
    }

    private static long craftscope$safeMultiply(
            long left,
            long right
    ) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }

        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }

        return left * right;
    }
}
