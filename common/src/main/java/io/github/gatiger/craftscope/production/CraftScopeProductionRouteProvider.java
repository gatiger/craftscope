package io.github.gatiger.craftscope.production;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/*
 * A provider teaches CraftScope about one source of production
 * routes.
 *
 * Examples:
 *
 * Vanilla provider
 * Mekanism provider
 * Create provider
 * Thermal provider
 *
 * The central CraftScope planner does not need to know the
 * implementation details of those mods.
 */
public interface CraftScopeProductionRouteProvider {

    /*
     * Stable provider identifier.
     *
     * Examples:
     *
     * craftscope:vanilla
     * craftscope:mekanism
     * craftscope:create
     */
    String getProviderId();

    /*
     * Find every production route this provider knows about
     * for the requested output item.
     *
     * Providers should return an empty list when they have
     * nothing applicable.
     */
    List<CraftScopeProductionRoute> findRoutes(
            ItemStack target,
            CraftScopeProductionContext context
    );
}