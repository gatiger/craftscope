package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionRouteRegistry;
import io.github.gatiger.craftscope.production.CraftScopeVanillaAcquisitionRouteProvider;
import io.github.gatiger.craftscope.production.CraftScopeVanillaFarmingRouteProvider;
import io.github.gatiger.craftscope.production.CraftScopeVanillaMobDropRouteProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

        CraftScopeProductionRouteRegistry.register(
                new CraftScopeVanillaFarmingRouteProvider()
        );

        CraftScopeProductionRouteRegistry.register(
                new CraftScopeVanillaMobDropRouteProvider()
        );
    }
}
