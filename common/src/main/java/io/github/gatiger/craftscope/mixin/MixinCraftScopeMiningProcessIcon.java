package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
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
 * Shows the actual accepted tool variants for acquisition process
 * nodes.
 *
 * The vanilla acquisition provider supplies tier-aware tool lists:
 *
 * Any Pickaxe:
 *   Wood -> Gold -> Stone -> Iron -> Diamond -> Netherite
 *
 * Stone Pickaxe or better:
 *   Stone -> Iron -> Diamond -> Netherite
 *
 * Iron Pickaxe or better:
 *   Iron -> Diamond -> Netherite
 *
 * The icon cycles only through tools that can legitimately harvest
 * the selected resource. This is intentionally data-driven through
 * CraftScopeProcessRequirement.acceptedVariantIds(), so future
 * providers can use the same behavior for axes, hoes, shears,
 * modded tools, etc.
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
    private static void craftscope$showAcceptedToolVariant(
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

                cir.setReturnValue(
                        stack
                );

                return;
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

        if (ids == null
                || ids.isEmpty()) {

            ResourceLocation id =
                    requirement.id();

            return craftscope$getItemStack(
                    id
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
                ids.get(
                        index
                )
        );
    }

    private static ItemStack craftscope$getItemStack(
            ResourceLocation id
    ) {
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        id
                );

        if (item == null
                || item == Items.AIR) {

            return ItemStack.EMPTY;
        }

        return new ItemStack(
                item
        );
    }
}
