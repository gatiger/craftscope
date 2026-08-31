package io.github.gatiger.craftscope.production;

import io.github.gatiger.craftscope.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /*
     * Route discovery can be expensive when optional integrations
     * inspect large recipe collections (Create in particular).
     *
     * A CraftScope screen rebuild asks for many of the same item routes
     * several times while building:
     *
     *     Recipe Tree
     *     Process Diagram
     *     Setup
     *
     * Keep one component-aware cache only while that rebuild is active.
     * It is removed completely afterward, so recipe/datapack reloads
     * cannot leave stale production data behind.
     */
    private static final ThreadLocal<LookupCacheState>
            LOOKUP_CACHE =
            new ThreadLocal<>();

    static {
        /*
         * Minecraft's normal recipe system is always available.
         */
        register(
                new CraftScopeVanillaProductionRouteProvider()
        );

        /*
         * Create integration is safe to register unconditionally.
         *
         * These providers have no Create compile-time dependency
         * and return no routes when the corresponding Create
         * runtime content is absent.
         *
         * The main provider handles Create-native recipe types.
         * The fan provider adds synthetic processing methods such
         * as Bulk Blasting that reuse Minecraft cooking recipes.
         */
        register(
                new CraftScopeCreateProductionRouteProvider()
        );

        register(
                new CraftScopeCreateFanProductionRouteProvider()
        );
    }

    private CraftScopeProductionRouteRegistry() {
    }

    public static void beginLookupCache() {
        LookupCacheState state =
                LOOKUP_CACHE.get();

        if (state == null) {
            state =
                    new LookupCacheState();

            LOOKUP_CACHE.set(
                    state
            );
        }

        state.depth++;
    }

    public static void endLookupCache() {
        LookupCacheState state =
                LOOKUP_CACHE.get();

        if (state == null) {
            return;
        }

        state.depth =
                Math.max(
                        0,
                        state.depth - 1
                );

        /*
         * Keep discovered provider routes warm between rebuilds.
         *
         * depth still controls whether callers are ALLOWED to use the
         * cache. At depth 0 the cache is dormant, but its contents stay
         * available for the next CraftScope rebuild on this screen.
         */
    }

    public static void clearLookupCache() {
        LookupCacheState state =
                LOOKUP_CACHE.get();

        if (state != null) {
            state.routes.clear();
        }

        LOOKUP_CACHE.remove();
    }
    public static void register(
            CraftScopeProductionRouteProvider provider
    ) {
        if (provider == null) {
            return;
        }

        String providerId =
                provider.getProviderId();

        PROVIDERS.removeIf(
                existing ->
                        existing
                                .getProviderId()
                                .equals(providerId)
        );

        PROVIDERS.add(provider);
    }

    public static List<CraftScopeProductionRouteProvider>
    getProviders() {
        return List.copyOf(PROVIDERS);
    }

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
                        minecraft.level.getRecipeManager(),
                        minecraft.level.registryAccess()
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

        LookupCacheState cacheState =
                LOOKUP_CACHE.get();

        CraftScopeItemIdentity cacheKey =
                null;

        if (cacheState != null
                && cacheState.depth > 0) {

            cacheKey =
                    CraftScopeItemIdentity.fromStack(
                            target
                    );

            List<CraftScopeProductionRoute> cached =
                    cacheState.routes.get(
                            cacheKey
                    );

            if (cached != null) {
                return cached;
            }
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
                    routes.addAll(providerRoutes);
                }

            } catch (Exception e) {
                /*
                 * One broken optional integration should never
                 * prevent CraftScope from using all other
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
                                CraftScopeProductionRoute::priority
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

        List<CraftScopeProductionRoute> result =
                List.copyOf(
                        routes
                );

        if (cacheState != null
                && cacheState.depth > 0
                && cacheKey != null) {

            cacheState.routes.put(
                    cacheKey,
                    result
            );
        }

        return result;
    }

    private static final class LookupCacheState {

        private int depth;

        private final Map<
                CraftScopeItemIdentity,
                List<CraftScopeProductionRoute>>
                routes =
                new LinkedHashMap<>();
    }
}
