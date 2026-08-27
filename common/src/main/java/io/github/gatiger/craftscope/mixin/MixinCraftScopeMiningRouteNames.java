package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteNormalizer;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/*
 * Keep acquisition route names descriptive instead of allowing
 * the generic ore-processing normalizer to rename them.
 */
@Mixin(CraftScopeProductionRouteNormalizer.class)
public abstract class MixinCraftScopeMiningRouteNames {

    @Inject(
            method = "buildRouteDisplayName",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void craftscope$keepAcquisitionRouteName(
            CraftScopeProductionRoute representative,
            List<CraftScopeResourceAmount> inputs,
            CallbackInfoReturnable<Component> cir
    ) {
        if (representative == null
                || representative.steps().size() != 1) {

            return;
        }

        CraftScopeProductionStep step =
                representative
                        .steps()
                        .getFirst();

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null
                || method.processId() == null) {

            return;
        }

        String processId =
                method.processId()
                        .toString();

        if ("craftscope:mining".equals(processId)
                || "craftscope:digging".equals(processId)
                || "craftscope:breaking".equals(processId)) {

            cir.setReturnValue(
                    representative.displayName()
            );
        }
    }
}
