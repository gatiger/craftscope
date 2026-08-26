package io.github.gatiger.craftscope.integration;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Loader-independent bridge between CraftScope and optional
 * recipe-viewer mods such as JEI and, later, EMI.
 *
 * Common CraftScope code never needs to directly depend on the
 * JEI or EMI APIs.
 *
 * A loader-specific integration registers a Handler when its
 * recipe viewer becomes available.
 */
public final class CraftScopeRecipeViewer {

    private static final Map<String, Handler> HANDLERS =
            new LinkedHashMap<>();

    private CraftScopeRecipeViewer() {
    }

    @FunctionalInterface
    public interface Handler {

        /*
         * Open one or more exact recipe IDs.
         *
         * processId is the logical production process/category,
         * such as:
         *
         * minecraft:crafting
         * minecraft:smelting
         * minecraft:blasting
         *
         * recipeIds contains the exact recipes represented by
         * the selected CraftScope production method.
         */
        boolean openRecipe(
                ResourceLocation processId,
                List<ResourceLocation> recipeIds
        );
    }

    public static synchronized void register(
            String viewerId,
            Handler handler
    ) {
        if (viewerId == null
                || viewerId.isBlank()
                || handler == null) {

            return;
        }

        HANDLERS.put(
                viewerId,
                handler
        );
    }

    public static synchronized void unregister(
            String viewerId,
            Handler handler
    ) {
        if (viewerId == null) {
            return;
        }

        Handler registered =
                HANDLERS.get(
                        viewerId
                );

        if (registered == handler) {

            HANDLERS.remove(
                    viewerId
            );
        }
    }

    public static synchronized boolean isAvailable() {
        return !HANDLERS.isEmpty();
    }

    public static boolean openRecipe(
            ResourceLocation processId,
            List<ResourceLocation> recipeIds
    ) {
        if (processId == null
                || recipeIds == null
                || recipeIds.isEmpty()) {

            return false;
        }

        List<Handler> handlers;

        synchronized (CraftScopeRecipeViewer.class) {

            handlers =
                    List.copyOf(
                            HANDLERS.values()
                    );
        }

        for (Handler handler :
                handlers) {

            try {

                if (handler.openRecipe(
                        processId,
                        recipeIds
                )) {

                    return true;
                }

            } catch (RuntimeException ignored) {

                /*
                 * One optional viewer failing should never make
                 * CraftScope itself unusable.
                 *
                 * This also lets another registered viewer try
                 * the same request.
                 */
            }
        }

        return false;
    }
}