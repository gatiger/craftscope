package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/*
 * Show real min-max ranges on Process Diagram resource nodes.
 *
 * Supports zero-minimum byproducts such as Beetroot Seeds:
 *
 *   x0-3 ≈1.5
 *
 * amount is the resource's stable scaling unit. This avoids
 * dividing by minimumAmount when the legitimate minimum is zero.
 *
 * Helper methods are @Unique and use the craftscope$ prefix so they
 * can never collide with similarly named methods that already exist
 * in CraftScopeProcessDiagramRenderer.
 */
@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeVariableYieldDiagramUi {

    @Inject(
            method = "formatAmount",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void craftscope$formatVariableRange(
            CraftScopeResourceAmount resource,
            long amount,
            CallbackInfoReturnable<String> cir
    ) {
        if (resource == null
                || !resource.hasVariableRange()
                || resource.amount() <= 0L) {

            return;
        }

        long runs =
                Math.max(
                        1L,
                        amount / resource.amount()
                );

        long minimum =
                craftscope$safeMultiply(
                        resource.minimumAmount(),
                        runs
                );

        long maximum =
                craftscope$safeMultiply(
                        resource.maximumAmount(),
                        runs
                );

        double expected =
                resource.expectedAmount()
                        * runs;

        cir.setReturnValue(
                "x"
                        + minimum
                        + "-"
                        + maximum
                        + " ≈"
                        + craftscope$formatNumber(
                        expected
                )
        );
    }

    @Unique
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

    @Unique
    private static String craftscope$formatNumber(
            double value
    ) {
        double nearestInteger =
                Math.rint(
                        value
                );

        if (Math.abs(
                value - nearestInteger
        ) < 0.000001D) {

            return Long.toString(
                    Math.round(
                            nearestInteger
                    )
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }
}
