package io.github.gatiger.craftscope.production;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Expander:
 *     Can a direct route be turned into a useful linear
 *     multi-step production chain?
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

        List<CraftScopeProductionRoute> directRoutes =
                CraftScopeProductionRouteNormalizer.normalize(
                        rawRoutes
                );

        if (directRoutes.isEmpty()) {
            return List.of();
        }

        List<CraftScopeProductionRoute> expandedRoutes =
                CraftScopeProductionRouteExpander.expand(
                        directRoutes
                );

        if (expandedRoutes.isEmpty()) {
            return directRoutes;
        }

        Map<String, CraftScopeProductionRoute> unique =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                expandedRoutes) {

            unique.putIfAbsent(
                    route.id().toString(),
                    route
            );
        }

        for (CraftScopeProductionRoute route :
                directRoutes) {

            unique.putIfAbsent(
                    route.id().toString(),
                    route
            );
        }

        List<CraftScopeProductionRoute> result =
                new ArrayList<>(
                        unique.values()
                );

        result.sort(
                Comparator
                        .comparingInt(
                                CraftScopeProductionRoute
                                        ::priority
                        )
                        .reversed()
                        .thenComparing(
                                (CraftScopeProductionRoute route) ->
                                        route
                                                .sourceModName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                (CraftScopeProductionRoute route) ->
                                        route
                                                .displayName()
                                                .getString(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                route ->
                                        route
                                                .id()
                                                .toString()
                        )
        );

        return List.copyOf(result);
    }

    public static List<CraftScopeProductionRoute> findRawRoutes(
            ItemStack target
    ) {
        return CraftScopeProductionRouteRegistry.findRoutes(
                target
        );
    }

    public static List<CraftScopeProductionRoute> findDirectRoutes(
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
}
