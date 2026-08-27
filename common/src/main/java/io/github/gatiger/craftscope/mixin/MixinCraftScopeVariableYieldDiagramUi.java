package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/*
 * Show a real min-max range on Process Diagram resource nodes.
 *
 * Example:
 *   x4-5 ≈4.5
 *
 * After multiple planned runs:
 *   x60-75 ≈67.5
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
                || resource.minimumAmount() <= 0L) {

            return;
        }

        long runs =
                Math.max(
                        1L,
                        amount
                                / resource.minimumAmount()
                );

        long minimum =
                resource.minimumAmount()
                        * runs;

        long maximum =
                resource.maximumAmount()
                        * runs;

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

    private static String craftscope$formatNumber(
            double value
    ) {
        double nearestInteger =
                Math.rint(value);

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
