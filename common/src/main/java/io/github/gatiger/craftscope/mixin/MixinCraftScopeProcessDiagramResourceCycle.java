package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/*
 * Rotates grouped resource nodes in Process Diagram.
 *
 * Previously a process with several different inputs or outputs
 * displayed only the first resource and a "+N" counter:
 *
 *   Coal   +3
 *     -> Milling ->
 *   Sand   +2
 *
 * That hides the actual resource identities. CraftScope now cycles
 * through every resource represented by that node instead:
 *
 *   Sand -> Clay Ball -> Flint -> ...
 *
 * Accepted variants inside one logical ingredient continue using
 * their existing icon cycling independently.
 */
@Mixin(CraftScopeProcessDiagramRenderer.class)
public abstract class MixinCraftScopeProcessDiagramResourceCycle {

    @Unique
    private static final long CRAFTSCOPE_RESOURCE_GROUP_CYCLE_MS =
            1600L;

    /*
     * buildNodes() uses List.getFirst() for:
     *
     * - first production step
     * - grouped resource inputs
     * - grouped resource outputs
     *
     * ProductionStep lists are left untouched. ResourceAmount lists
     * with multiple distinct resource entries rotate by time.
     */
    @Redirect(
            method = "buildNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;getFirst()Ljava/lang/Object;"
            )
    )
    private static Object craftscope$rotateGroupedResource(
            List<?> values
    ) {
        if (values == null
                || values.isEmpty()) {

            return null;
        }

        Object first =
                values.getFirst();

        if (!(first instanceof CraftScopeResourceAmount)
                || values.size() <= 1) {

            return first;
        }

        long cycle =
                System.currentTimeMillis()
                        / CRAFTSCOPE_RESOURCE_GROUP_CYCLE_MS;

        int index =
                (int) (
                        cycle
                                % values.size()
                );

        return values.get(
                index
        );
    }

    /*
     * Once the real resources rotate through the node, the old +N
     * badge is redundant and no longer useful.
     */
    @ModifyArg(
            method = "buildNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/github/gatiger/craftscope/ui/diagram/CraftScopeProcessDiagramRenderer$DiagramNode;resource(Lio/github/gatiger/craftscope/production/CraftScopeResourceAmount;JI)Lio/github/gatiger/craftscope/ui/diagram/CraftScopeProcessDiagramRenderer$DiagramNode;"
            ),
            index = 2
    )
    private static int craftscope$hideCollapsedResourceCount(
            int extraCount
    ) {
        return 0;
    }
}
