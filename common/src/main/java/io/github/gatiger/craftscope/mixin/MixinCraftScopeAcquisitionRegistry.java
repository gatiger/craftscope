package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionRouteRegistry;
import io.github.gatiger.craftscope.production.CraftScopeVanillaAcquisitionRouteProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Registers world-acquisition providers after CraftScope's normal
 * production provider registry has finished initializing.
 *
 * Keeping acquisition providers in the same generic route registry
 * means Recipe Tree, Recipe Source, Process Diagram, Setup, route
 * normalization, and future planners all see them automatically.
 */
@Mixin(CraftScopeProductionRouteRegistry.class)
public abstract class MixinCraftScopeAcquisitionRegistry {

    @Inject(
            method = "<clinit>",
            at = @At("TAIL")
    )
    private static void craftscope$registerAcquisitionProviders(
            CallbackInfo ci
    ) {
        CraftScopeProductionRouteRegistry.register(
                new CraftScopeVanillaAcquisitionRouteProvider()
        );
    }
}
