package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

/*
 * Encodes ingredient-alternative selections inside the existing
 * Recipe Tree -> production-expander selection map without colliding
 * with ordinary recipe IDs.
 *
 * Actual recipe selections still use:
 *
 *     item id -> recipe id
 *
 * Ingredient selections use:
 *
 *     synthetic key(item id) -> selected ingredient item id
 *
 * This lets CraftScope preserve both decisions at the same time:
 *
 *     use Leather instead of Cardboard
 *     AND
 *     choose a particular Leather production route
 */
public final class CraftScopeIngredientVariantSelection {

    public static final ResourceLocation UNRESOLVED =
            requireId(
                    "craftscope:ingredient_variant_unresolved"
            );

    private CraftScopeIngredientVariantSelection() {
    }

    public static ResourceLocation keyFor(
            ResourceLocation ingredientVariantId
    ) {
        if (ingredientVariantId == null) {
            return null;
        }

        return requireId(
                "craftscope:ingredient_variant/"
                        + ingredientVariantId.getNamespace()
                        + "/"
                        + ingredientVariantId.getPath()
        );
    }

    public static boolean isUnresolved(
            ResourceLocation value
    ) {
        return UNRESOLVED.equals(
                value
        );
    }

    private static ResourceLocation requireId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(
                        value
                );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid ingredient selection id: "
                            + value
            );
        }

        return id;
    }
}