package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * One resource participating in a production step.
 *
 * Examples:
 *
 * Iron Ore x1
 * Any Iron Ore x1
 * Water 1000 mB
 * Sulfuric Acid 100 mB
 *
 * acceptedVariantIds lets one logical resource represent
 * equivalent choices such as:
 *
 * Iron Ore
 * Deepslate Iron Ore
 *
 * or:
 *
 * Oak Planks
 * Spruce Planks
 * Birch Planks
 * ...
 */
public record CraftScopeResourceAmount(
        CraftScopeResourceKind kind,
        ResourceLocation id,
        Component displayName,
        long amount,
        String unit,
        boolean consumed,
        double chance,
        List<ResourceLocation> acceptedVariantIds
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

        if (acceptedVariantIds == null
                || acceptedVariantIds.isEmpty()) {

            acceptedVariantIds =
                    List.of(
                            id
                    );

        } else {

            acceptedVariantIds =
                    List.copyOf(
                            acceptedVariantIds
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
                1.0,
                List.of(
                        id
                )
        );
    }

    public static CraftScopeResourceAmount itemVariants(
            List<ItemStack> variants,
            long amount
    ) {
        return itemVariants(
                variants,
                amount,
                true
        );
    }

    public static CraftScopeResourceAmount itemVariants(
            List<ItemStack> variants,
            long amount,
            boolean consumed
    ) {
        if (variants == null
                || variants.isEmpty()) {

            throw new IllegalArgumentException(
                    "Item variants cannot be empty"
            );
        }

        ItemStack representative =
                null;

        List<ResourceLocation> ids =
                new ArrayList<>();

        for (ItemStack stack :
                variants) {

            if (stack == null
                    || stack.isEmpty()) {

                continue;
            }

            ResourceLocation id =
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    );

            if (representative == null) {

                representative =
                        stack;
            }

            if (!ids.contains(id)) {

                ids.add(
                        id
                );
            }
        }

        if (representative == null
                || ids.isEmpty()) {

            throw new IllegalArgumentException(
                    "Item variants contained no usable items"
            );
        }

        ResourceLocation representativeId =
                BuiltInRegistries.ITEM.getKey(
                        representative.getItem()
                );

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                representativeId,
                representative
                        .getHoverName()
                        .copy(),
                amount,
                "",
                consumed,
                1.0,
                ids
        );
    }

    public boolean isGuaranteed() {

        return chance >= 1.0;
    }

    public boolean hasUnit() {

        return !unit.isBlank();
    }

    public boolean hasVariants() {

        return acceptedVariantIds.size() > 1;
    }

    public boolean accepts(
            ResourceLocation resourceId
    ) {
        return acceptedVariantIds.contains(
                resourceId
        );
    }
}