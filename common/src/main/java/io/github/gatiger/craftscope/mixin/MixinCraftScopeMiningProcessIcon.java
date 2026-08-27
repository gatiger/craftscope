package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/*
 * Rotates through only the tools that satisfy the acquisition's
 * harvest tier. Tool-less breaking routes fall back to showing the
 * block being broken instead of a generic P icon.
 */
@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeMiningProcessIcon {

    private static final long CRAFTSCOPE_TOOL_CYCLE_MS =
            1400L;

    @Inject(
            method = "getProcessStack",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$showAcquisitionIcon(
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

        if (method == null) {
            return;
        }

        for (CraftScopeProcessRequirement requirement :
                method.requirements()) {

            if (requirement.kind()
                    != CraftScopeRequirementKind.TOOL) {

                continue;
            }

            ItemStack stack =
                    craftscope$getCycledToolStack(
                            requirement
                    );

            if (!stack.isEmpty()) {
                cir.setReturnValue(stack);
                return;
            }
        }

        if (method.processId() != null
                && "craftscope:breaking".equals(
                method.processId().toString()
        )
                && !step.inputs().isEmpty()) {

            CraftScopeResourceAmount input =
                    step.inputs().getFirst();

            if (input.kind()
                    == CraftScopeResourceKind.ITEM) {

                ItemStack stack =
                        craftscope$getItemStack(
                                input.id()
                        );

                if (!stack.isEmpty()) {
                    cir.setReturnValue(stack);
                }
            }
        }
    }

    private static ItemStack craftscope$getCycledToolStack(
            CraftScopeProcessRequirement requirement
    ) {
        if (requirement == null) {
            return ItemStack.EMPTY;
        }

        List<ResourceLocation> ids =
                requirement.acceptedVariantIds();

        if (ids == null || ids.isEmpty()) {
            return craftscope$getItemStack(
                    requirement.id()
            );
        }

        long cycle =
                System.currentTimeMillis()
                        / CRAFTSCOPE_TOOL_CYCLE_MS;

        int index =
                (int) (
                        cycle
                                % ids.size()
                );

        return craftscope$getItemStack(
                ids.get(index)
        );
    }

    private static ItemStack craftscope$getItemStack(
            ResourceLocation id
    ) {
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(id);

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }
}
