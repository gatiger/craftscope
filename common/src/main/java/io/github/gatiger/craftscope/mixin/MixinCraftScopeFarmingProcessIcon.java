package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeFarmingProcessIcon {

    @Inject(
            method = "getProcessStack",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$showFarmingStarterIcon(
            CraftScopeProductionStep step,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack existing = cir.getReturnValue();

        if (existing != null && !existing.isEmpty()) {
            return;
        }

        if (step == null) {
            return;
        }

        CraftScopeProductionMethod method = step.getPrimaryMethod();

        if (method == null
                || method.processId() == null
                || !"craftscope:farming".equals(
                        method.processId().toString()
                )) {
            return;
        }

        /*
         * Tool-based farmland crops are already handled by the
         * generic TOOL icon mixin and rotate through valid hoes.
         *
         * Tool-less farms fall back to their registered Starter
         * requirement (Nether Wart, Sugar Cane, Bamboo, etc.).
         */
        for (CraftScopeProcessRequirement requirement :
                method.requirements()) {

            if (requirement.kind() != CraftScopeRequirementKind.OTHER
                    || requirement.id() == null) {
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(requirement.id());

            if (item != null && item != Items.AIR) {
                cir.setReturnValue(new ItemStack(item));
                return;
            }
        }
    }
}
