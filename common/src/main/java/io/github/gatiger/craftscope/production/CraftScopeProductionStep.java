package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/*
 * One operation in a production route.
 *
 * Vanilla example:
 *
 * Iron Ore
 *     ↓
 * Smelting
 *     ↓
 * Iron Ingot
 *
 * Create example:
 *
 * Cobblestone
 *     ↓
 * Crushing
 *     ↓
 * Gravel
 *
 * Mekanism example:
 *
 * Iron Ore + Sulfuric Acid
 *     ↓
 * Chemical Dissolution
 *     ↓
 * Dirty Iron Slurry
 */
public record CraftScopeProductionStep(
        String id,
        String sourceModId,
        ResourceLocation processId,
        Component displayName,
        ResourceLocation recipeId,
        List<CraftScopeResourceAmount> inputs,
        List<CraftScopeResourceAmount> outputs,
        List<CraftScopeProcessRequirement> requirements
) {

    public CraftScopeProductionStep {
        Objects.requireNonNull(
                id,
                "id"
        );

        Objects.requireNonNull(
                sourceModId,
                "sourceModId"
        );

        Objects.requireNonNull(
                processId,
                "processId"
        );

        Objects.requireNonNull(
                displayName,
                "displayName"
        );

        inputs =
                List.copyOf(
                        inputs
                );

        outputs =
                List.copyOf(
                        outputs
                );

        requirements =
                List.copyOf(
                        requirements
                );
    }

    /*
     * recipeId is intentionally optional.
     *
     * If present, our JEI/EMI bridge will eventually use it
     * for the View Recipe button.
     */
    public boolean hasRecipe() {

        return recipeId != null;
    }

    public boolean hasMachineRequirement() {

        for (CraftScopeProcessRequirement requirement :
                requirements) {

            if (requirement.kind()
                    == CraftScopeRequirementKind.MACHINE) {

                return true;
            }
        }

        return false;
    }
}