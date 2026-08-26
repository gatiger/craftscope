package io.github.gatiger.craftscope.production;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Objects;

/*
 * Runtime data available to production-route providers.
 *
 * Keeping this in one object means we can add more context
 * later without changing every provider method signature.
 */
public record CraftScopeProductionContext(
        RecipeManager recipeManager,
        RegistryAccess registryAccess
) {

    public CraftScopeProductionContext {
        Objects.requireNonNull(
                recipeManager,
                "recipeManager"
        );

        Objects.requireNonNull(
                registryAccess,
                "registryAccess"
        );
    }
}