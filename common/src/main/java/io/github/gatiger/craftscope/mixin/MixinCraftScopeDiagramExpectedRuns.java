package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeChancePlanner;
import io.github.gatiger.craftscope.production.CraftScopeProductionDisplayPolicy;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * Controls how many executions Process Diagram displays.
 *
 * Most production routes remain quantity-aware:
 *
 *     target quantity
 *         ->
 *     expected recipe/process runs
 *
 * Direct mob-drop routes are different. Their process node represents
 * one physical event: killing one mob.
 *
 * Therefore:
 *
 *     Process Diagram
 *         shows ONE mob kill
 *
 * while:
 *
 *     Recipe Tree / Total Materials / Setup planning
 *         can still calculate expected kills required for the project.
 */
@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeDiagramExpectedRuns {

    @Redirect(
            method = "buildNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/github/gatiger/craftscope/ui/diagram/CraftScopeProcessDiagramRenderer;ceilDiv(JJ)J"
            )
    )
    private static long craftscope$useDisplayRuns(
            long value,
            long divisor,
            CraftScopeProductionRoute route,
            long requestedTargetCount
    ) {
        /*
         * One direct mob-drop process = one kill.
         */
        if (CraftScopeProductionDisplayPolicy
                .isSingleExecutionRoute(
                        route
                )) {

            return 1L;
        }

        /*
         * Other probabilistic processes continue using expected-run
         * planning so their diagrams remain quantity-aware.
         */
        long runs =
                CraftScopeChancePlanner.requiredRuns(
                        route,
                        requestedTargetCount
                );

        return runs == Long.MAX_VALUE
                ? 1L
                : Math.max(
                        1L,
                        runs
                );
    }
}