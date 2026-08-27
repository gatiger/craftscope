package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeVanillaMobDropRouteProvider;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Process Diagram currently renders process nodes with ItemStacks.
 * Spawn eggs provide a recognizable vanilla icon for the source mob
 * without pretending that a spawn egg is a required input.
 */
@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeMobDropProcessIcon {

    @Inject(
            method = "getProcessStack",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$showMobIcon(
            CraftScopeProductionStep step,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack existing =
                cir.getReturnValue();

        if (existing != null
                && !existing.isEmpty()) {

            return;
        }

        if (step == null) {
            return;
        }

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null
                || method.processId() == null) {

            return;
        }

        ItemStack icon =
                CraftScopeVanillaMobDropRouteProvider
                        .getProcessIcon(
                                method.processId()
                        );

        if (!icon.isEmpty()) {
            cir.setReturnValue(
                    icon
            );
        }
    }
}
