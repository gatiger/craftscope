package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeChancePlanner;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * Process Diagram originally calculated runs only from the nominal
 * target stack size. Recipe Tree and ProductionSummary are already
 * expected-yield aware, so the diagram must use the same planner.
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
    private static long craftscope$useExpectedRuns(
            long value,
            long divisor,
            CraftScopeProductionRoute route,
            long requestedTargetCount
    ) {
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
