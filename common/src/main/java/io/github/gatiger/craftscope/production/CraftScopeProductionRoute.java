package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/*
 * A complete selectable way of producing something.
 *
 * Examples:
 *
 * Minecraft
 *   Smelting
 *
 * Create
 *   Cobblestone -> Gravel -> Washing -> Nuggets -> Ingot
 *
 * Mekanism
 *   2x Ore Processing
 *
 * Mekanism
 *   5x Ore Processing
 *
 * A route may contain one step or many steps.
 */
public record CraftScopeProductionRoute(
        ResourceLocation id,
        String sourceModId,
        Component sourceModName,
        Component displayName,
        CraftScopeResourceAmount targetOutput,
        List<CraftScopeProductionStep> steps,
        int priority
) {

    public CraftScopeProductionRoute {
        Objects.requireNonNull(
                id,
                "id"
        );

        Objects.requireNonNull(
                sourceModId,
                "sourceModId"
        );

        Objects.requireNonNull(
                sourceModName,
                "sourceModName"
        );

        Objects.requireNonNull(
                displayName,
                "displayName"
        );

        Objects.requireNonNull(
                targetOutput,
                "targetOutput"
        );

        steps =
                List.copyOf(
                        steps
                );
    }

    public boolean isMultiStep() {

        return steps.size() > 1;
    }

    public int getStepCount() {

        return steps.size();
    }
}