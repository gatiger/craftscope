package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeChancePlanner;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/*
 * Formats non-fixed output totals without confusing ordinary
 * min-max drops with true chance outputs.
 *
 * Examples:
 *
 * Create chance output:
 *   ≈1 exp. @25%
 *
 * Vanilla range output:
 *   ≈67.5 exp. (60-75)
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeChanceUi {

    @Inject(
            method = "formatSummaryResourceAmount",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$formatExpectedSummaryAmount(
            CraftScopeResourceAmount resource,
            CallbackInfoReturnable<String> cir
    ) {
        if (!CraftScopeChancePlanner.isChanceOutput(
                resource
        )) {
            return;
        }

        String expectedText =
                craftscope$formatNumber(
                        resource.expectedAmount()
                );

        String unitText =
                resource.hasUnit()
                        ? " " + resource.unit()
                        : "";

        if (resource.hasVariableRange()) {
            cir.setReturnValue(
                    "≈"
                            + expectedText
                            + unitText
                            + " exp. ("
                            + resource.minimumAmount()
                            + "-"
                            + resource.maximumAmount()
                            + unitText
                            + ")"
            );

            return;
        }

        String percentText =
                craftscope$formatNumber(
                        resource.chance()
                                * 100.0D
                );

        cir.setReturnValue(
                "≈"
                        + expectedText
                        + unitText
                        + " exp. @"
                        + percentText
                        + "%"
        );
    }

    @Unique
    private static String craftscope$formatNumber(
            double value
    ) {
        if (Double.isNaN(value)
                || value <= 0.0D) {

            return "0";
        }

        if (Double.isInfinite(value)) {
            return "∞";
        }

        double nearestInteger =
                Math.rint(value);

        if (Math.abs(
                value - nearestInteger
        ) < 0.000001D) {

            if (nearestInteger >= Long.MAX_VALUE) {
                return Long.toString(
                        Long.MAX_VALUE
                );
            }

            return Long.toString(
                    Math.round(
                            nearestInteger
                    )
            );
        }

        int decimals =
                value >= 10.0D
                        ? 1
                        : 2;

        String text =
                String.format(
                        Locale.ROOT,
                        "%." + decimals + "f",
                        value
                );

        while (text.contains(".")
                && text.endsWith("0")) {

            text =
                    text.substring(
                            0,
                            text.length() - 1
                    );
        }

        if (text.endsWith(".")) {
            text =
                    text.substring(
                            0,
                            text.length() - 1
                    );
        }

        return text;
    }
}
