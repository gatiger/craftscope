package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/*
 * Makes Process Diagram route selection authoritative for the ROOT
 * material route too.
 *
 * Before this pass, choosing a different row in Production Routes
 * changed only the diagram's selected row. If the desired process
 * belonged to a different Recipe Tree route, the player had to go
 * back to Recipe Tree, cycle recipes, return to Process Diagram,
 * inspect it, and repeat.
 *
 * New behavior:
 *
 * 1. Click a process option in Production Routes.
 * 2. If it belongs to the same material route, no Recipe Tree
 *    rebuild is necessary; the selected process option is session
 *    state and the diagram changes immediately.
 * 3. If it belongs to a different material route, CraftScope writes
 *    that choice to the root Recipe Tree override, clears stale
 *    descendant choices, saves the project, and rebuilds.
 *
 * This preserves one synchronized project state instead of letting
 * Recipe Tree and Process Diagram disagree.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeProcessRouteSelectionSync {

    @Shadow
    @Final
    private CraftScopeProject project;

    @Shadow
    @Final
    private Map<String, ResourceLocation> recipeOverrides;

    @Shadow
    private CraftScopeRecipeTree currentTree;

    @Unique
    private boolean craftscope$syncingProcessRoute;

    @Invoker("getTargetStack")
    protected abstract ItemStack craftscope$invokeGetTargetStack();

    @Invoker("rebuildTree")
    protected abstract void craftscope$invokeRebuildTree();

    @Invoker("clearDescendantRecipeState")
    protected abstract void craftscope$invokeClearDescendantRecipeState(
            String nodePath
    );

    /*
     * getSelectedProductionRoute() is called immediately after the
     * user chooses a Production Routes row and again while the
     * Process Diagram renders. It is a reliable synchronization
     * point that does not conflict with the existing scrollable
     * route-list click mixin.
     */
    @Inject(
            method = "getSelectedProductionRoute",
            at = @At("RETURN")
    )
    private void craftscope$syncRootRouteFromProcessSelection(
            CallbackInfoReturnable<CraftScopeProductionRoute> cir
    ) {
        if (craftscope$syncingProcessRoute) {
            return;
        }

        CraftScopeProductionRoute selectedOption =
                cir.getReturnValue();

        if (selectedOption == null) {
            return;
        }

        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            return;
        }

        List<CraftScopeProductionRoute> directRoutes =
                CraftScopeProductionRouteQuery.findDirectRoutes(
                        target
                );

        if (directRoutes.isEmpty()) {
            return;
        }

        ResourceLocation selectedChoice =
                CraftScopeProductionRecipeTreeBuilder
                        .getRouteChoiceId(
                                selectedOption
                        );

        if (selectedChoice == null) {
            return;
        }

        CraftScopeProductionRoute selectedMaterialRoute =
                null;

        for (CraftScopeProductionRoute route :
                directRoutes) {

            if (CraftScopeProductionRecipeTreeBuilder
                    .routeMatchesChoice(
                            route,
                            selectedChoice
                    )) {

                selectedMaterialRoute =
                        route;
                break;
            }
        }

        /*
         * UI-only multi-method options without recipe IDs may have
         * synthetic IDs. They represent another method of the
         * current material route and do not require a tree rebuild.
         */
        if (selectedMaterialRoute == null) {
            return;
        }

        ResourceLocation currentChoice =
                currentTree == null
                        || currentTree.getRoot() == null
                        ? null
                        : currentTree
                        .getRoot()
                        .getPreferredRecipeId();

        /*
         * A consolidated process option can carry both the normal
         * ore and deepslate ore recipe IDs. If the current Recipe
         * Tree choice is one of those IDs, this is already the same
         * logical material route. Keep the tree exactly as-is and
         * let the Process Diagram rotate the accepted ore variants.
         */
        if (currentChoice != null
                && CraftScopeProductionRecipeTreeBuilder
                .routeMatchesChoice(
                        selectedOption,
                        currentChoice
                )) {

            return;
        }

        if (currentChoice != null
                && CraftScopeProductionRecipeTreeBuilder
                .routeMatchesChoice(
                        selectedMaterialRoute,
                        currentChoice
                )) {

            /*
             * Same material transformation, different process
             * method. Keep the Process Diagram selection without
             * disturbing Recipe Tree.
             */
            return;
        }

        /*
         * This is a genuinely different root material route.
         * Persist it just as if the player selected the alternate
         * recipe in Recipe Tree.
         */
        craftscope$syncingProcessRoute =
                true;

        try {
            recipeOverrides.put(
                    "root",
                    selectedChoice
            );

            project.setRecipeOverride(
                    "root",
                    selectedChoice.toString()
            );

            craftscope$invokeClearDescendantRecipeState(
                    "root"
            );

            CraftScopeProjectManager.save();

            craftscope$invokeRebuildTree();

        } finally {
            craftscope$syncingProcessRoute =
                    false;
        }
    }
}
