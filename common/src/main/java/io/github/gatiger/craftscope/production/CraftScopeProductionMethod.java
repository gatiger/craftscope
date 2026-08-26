package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/*
 * One way of performing a production step.
 *
 * A single material transformation can have several methods.
 *
 * Example:
 *
 * Any Iron Ore
 *      ↓
 * Iron Ingot
 *
 * Methods:
 *   Smelting
 *   Blasting
 *
 * Each method may contain multiple recipe IDs because equivalent
 * variants can have separate recipes.
 *
 * Example:
 *
 * Smelting:
 *   iron_ore -> iron_ingot
 *   deepslate_iron_ore -> iron_ingot
 */
public record CraftScopeProductionMethod(
        String sourceModId,
        ResourceLocation processId,
        Component displayName,
        List<ResourceLocation> recipeIds,
        List<CraftScopeProcessRequirement> requirements
) {

    public CraftScopeProductionMethod {
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

        recipeIds =
                recipeIds == null
                        ? List.of()
                        : List.copyOf(
                                recipeIds
                        );

        requirements =
                requirements == null
                        ? List.of()
                        : List.copyOf(
                                requirements
                        );
    }

    public boolean hasRecipes() {

        return !recipeIds.isEmpty();
    }

    public ResourceLocation getPrimaryRecipeId() {

        if (recipeIds.isEmpty()) {
            return null;
        }

        return recipeIds.getFirst();
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