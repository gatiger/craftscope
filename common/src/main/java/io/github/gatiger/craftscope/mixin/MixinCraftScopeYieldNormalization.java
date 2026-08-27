package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionRouteNormalizer;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Variable-yield transforms only merge when their yield profiles
 * match. Ratios keep the profile stable if a resource is ever
 * compared after scaling.
 */
@Mixin(CraftScopeProductionRouteNormalizer.class)
public abstract class MixinCraftScopeYieldNormalization {

    @Inject(
            method = "buildLogicalResourceKey",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$includeVariableRange(
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
}
