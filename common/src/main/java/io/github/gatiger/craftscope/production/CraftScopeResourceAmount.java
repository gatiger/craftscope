package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/*
 * One resource participating in a production step.
 *
 * Examples:
 *
 * Iron Ore x1
 * Water 1000 mB
 * Sulfuric Acid 100 mB
 *
 * "consumed" lets us distinguish normal materials from things
 * such as reusable catalysts.
 *
 * "chance" supports recipes with probabilistic outputs without
 * pretending those outputs are guaranteed.
 */
public record CraftScopeResourceAmount(
        CraftScopeResourceKind kind,
        ResourceLocation id,
        Component displayName,
        long amount,
        String unit,
        boolean consumed,
        double chance
) {

    public CraftScopeResourceAmount {
        Objects.requireNonNull(
                kind,
                "kind"
        );

        Objects.requireNonNull(
                id,
                "id"
        );

        Objects.requireNonNull(
                displayName,
                "displayName"
        );

        unit =
                unit == null
                        ? ""
                        : unit;

        if (amount < 0) {

            throw new IllegalArgumentException(
                    "Resource amount cannot be negative"
            );
        }

        if (chance < 0.0
                || chance > 1.0) {

            throw new IllegalArgumentException(
                    "Chance must be between 0.0 and 1.0"
            );
        }
    }

    public static CraftScopeResourceAmount item(
            ItemStack stack,
            long amount
    ) {
        return item(
                stack,
                amount,
                true
        );
    }

    public static CraftScopeResourceAmount item(
            ItemStack stack,
            long amount,
            boolean consumed
    ) {
        if (stack == null
                || stack.isEmpty()) {

            throw new IllegalArgumentException(
                    "Item resource cannot use an empty ItemStack"
            );
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                amount,
                "",
                consumed,
                1.0
        );
    }

    public boolean isGuaranteed() {

        return chance >= 1.0;
    }

    public boolean hasUnit() {

        return !unit.isBlank();
    }
}