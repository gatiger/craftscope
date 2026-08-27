package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(CraftScopeProductionRecipeTreeBuilder.class)
public abstract class MixinCraftScopeFarmingRecipeTree {

    @Inject(
            method = "isRecipeTreeCandidate",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void craftscope$allowFarmingRoutes(
            CraftScopeProductionRoute route,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (craftscope$isFarmingRoute(route)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "routeUsesActivePathItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void craftscope$ignoreReusableFarmingStarterLoop(
            CraftScopeProductionRoute route,
            Set<String> activePath,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (craftscope$isFarmingRoute(route)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean craftscope$isFarmingRoute(
            CraftScopeProductionRoute route
    ) {
        if (route == null || route.steps().size() != 1) {
            return false;
        }

        CraftScopeProductionStep step = route.steps().getFirst();
        CraftScopeProductionMethod method = step.getPrimaryMethod();

        return method != null
                && method.processId() != null
                && "craftscope:farming".equals(
                        method.processId().toString()
                );
    }
}
