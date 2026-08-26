package io.github.gatiger.craftscope.production;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/*
 * Public query layer used by CraftScope screens and planners.
 *
 * Screens should normally use this class rather than talking
 * directly to the raw provider registry.
 *
 * Registry:
 *     What recipes/routes exist?
 *
 * Normalizer:
 *     Which of those represent the same logical route?
 *
 * Query:
 *     Give the UI the final clean list.
 */
public final class CraftScopeProductionRouteQuery {

    private CraftScopeProductionRouteQuery() {
    }

    public static List<CraftScopeProductionRoute> findRoutes(
            ItemStack target
    ) {
        List<CraftScopeProductionRoute> rawRoutes =
                CraftScopeProductionRouteRegistry.findRoutes(
                        target
                );

        return CraftScopeProductionRouteNormalizer.normalize(
                rawRoutes
        );
    }

    /*
     * Useful later for diagnostics and integrations.
     *
     * This deliberately bypasses normalization.
     */
    public static List<CraftScopeProductionRoute> findRawRoutes(
            ItemStack target
    ) {
        return CraftScopeProductionRouteRegistry.findRoutes(
                target
        );
    }
}