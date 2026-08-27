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
 * Makes probabilistic output totals visibly probabilistic.
 *
 * CraftScopeProductionSummary keeps the nominal number of output
 * rolls together with each output's chance. This mixin converts
 * that into a concise expected-value label in the shared summary
 * panels.
 *
 * Example after four 25% Clay Ball rolls:
 *
 *   Clay Ball  ~=1 exp. @25%
 *
 * The actual UI uses the Unicode approximately-equal symbol.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeChanceUi {

    @Inject(
            method = "formatSummaryResourceAmount",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$formatChanceSummaryAmount(
            CraftScopeResourceAmount resource,
            CallbackInfoReturnable<String> cir
    ) {
        if (!CraftScopeChancePlanner.isChanceOutput(
                resource
        )) {
            return;
        }

        double expected =
                CraftScopeChancePlanner.expectedAmount(
                        resource
                );

        String expectedText =
                craftscope$formatNumber(
                        expected
                );

        String percentText =
                craftscope$formatNumber(
                        resource.chance()
                                * 100.0D
                );

        String unitText =
                resource.hasUnit()
                        ? " " + resource.unit()
                        : "";

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
