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

/*
 * Mob-drop routes have no consumed ITEM inputs, so the normal Recipe
 * Tree eligibility rule would treat their outputs as unproducible
 * leaves. Marking this process family as a valid acquisition route
 * lets Recipe Tree show the selected mob source without inventing a
 * fake consumed ingredient.
 */
@Mixin(CraftScopeProductionRecipeTreeBuilder.class)
public abstract class MixinCraftScopeMobDropRecipeTree {

    @Inject(
            method = "isRecipeTreeCandidate",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void craftscope$allowMobDropRoutes(
            CraftScopeProductionRoute route,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (craftscope$isMobDropRoute(
                route
        )) {

            cir.setReturnValue(
                    true
            );
        }
    }

    @Unique
    private static boolean craftscope$isMobDropRoute(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return false;
        }

        CraftScopeProductionStep step =
                route.steps()
                        .getFirst();

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        return method != null
                && method.processId() != null
                && "craftscope".equals(
                method.processId()
                        .getNamespace()
        )
                && method.processId()
                .getPath()
                .startsWith(
                        "mob_drop/"
                );
    }
}
