package io.github.gatiger.craftscope.production;

import io.github.gatiger.craftscope.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Central registry for all production-route providers.
 *
 * CraftScope itself asks this registry:
 *
 * "How can I produce this item?"
 *
 * The registry asks every installed provider and combines the
 * results.
 */
public final class CraftScopeProductionRouteRegistry {

    private static final List<CraftScopeProductionRouteProvider>
            PROVIDERS =
            new ArrayList<>();

    static {

        /*
         * Minecraft's normal recipe system is always available.
         *
         * Optional mod providers will be registered separately
         * only when their integration is available.
         */
        register(
                new CraftScopeVanillaProductionRouteProvider()
        );
    }

    private CraftScopeProductionRouteRegistry() {
    }

    public static void register(
            CraftScopeProductionRouteProvider provider
    ) {
        if (provider == null) {
            return;
        }

        String providerId =
                provider.getProviderId();

        /*
         * Replace an existing provider with the same ID.
         *
         * This makes initialization safe if an integration is
         * accidentally registered more than once.
         */
        PROVIDERS.removeIf(
                existing ->
                        existing
                                .getProviderId()
                                .equals(
                                        providerId
                                )
        );

        PROVIDERS.add(
                provider
        );
    }

    public static List<CraftScopeProductionRouteProvider>
    getProviders() {

        return List.copyOf(
                PROVIDERS
        );
    }

    /*
     * Convenience method for client screens.
     */
    public static List<CraftScopeProductionRoute> findRoutes(
            ItemStack target
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null
                || target == null
                || target.isEmpty()) {

            return List.of();
        }

        CraftScopeProductionContext context =
                new CraftScopeProductionContext(
                        minecraft
                                .level
                                .getRecipeManager(),
                        minecraft
                                .level
                                .registryAccess()
                );

        return findRoutes(
                target,
                context
        );
    }

    public static List<CraftScopeProductionRoute> findRoutes(
            ItemStack target,
            CraftScopeProductionContext context
    ) {
        if (target == null
                || target.isEmpty()
                || context == null) {

            return List.of();
        }

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        for (CraftScopeProductionRouteProvider provider :
                PROVIDERS) {

            try {

                List<CraftScopeProductionRoute>
                        providerRoutes =
                        provider.findRoutes(
                                target,
                                context
                        );

                if (providerRoutes != null) {

                    routes.addAll(
                            providerRoutes
                    );
                }

            } catch (Exception e) {

                /*
                 * One broken optional integration should never
                 * prevent CraftScope from using all of the other
                 * providers.
                 */
                Constants.LOG.error(
                        "CraftScope production provider {} failed",
                        provider.getProviderId(),
                        e
                );
            }
        }

        routes.sort(
                Comparator
                        .comparingInt(
                                CraftScopeProductionRoute
                                        ::priority
                        )
                        .reversed()
                        .thenComparing(
                                route ->
                                        route
                                                .displayName()
                                                .getString()
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .id()
                                                .toString()
                        )
        );

        return List.copyOf(
                routes
        );
    }
}