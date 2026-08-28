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
 * Besides the nominal amount/chance representation, a resource can
 * now carry an explicit minimum, maximum, and expected amount.
 *
 * This matters for normal vanilla drops such as:
 *
 *   Redstone Ore -> 4-5 Redstone Dust
 *   Copper Ore   -> 2-5 Raw Copper
 *
 * Those are ranges, not "5 items at 90% chance". Keeping range
 * metadata separate preserves the real drop behavior while still
 * giving the planner one expected value to use for quantity math.
 */
public record CraftScopeResourceAmount(
        CraftScopeResourceKind kind,
        ResourceLocation id,
        Component displayName,
        long amount,
        String unit,
        boolean consumed,
        double chance,
        List<ResourceLocation> acceptedVariantIds,
        long minimumAmount,
        long maximumAmount,
        double expectedAmount
) {

    /*
     * Backward-compatible constructor used by all existing
     * providers. Deterministic resources become min=max=amount.
     * Chance resources become a 0..amount outcome with expected
     * amount*chance.
     */
    public CraftScopeResourceAmount(
            CraftScopeResourceKind kind,
            ResourceLocation id,
            Component displayName,
            long amount,
            String unit,
            boolean consumed,
            double chance,
            List<ResourceLocation> acceptedVariantIds
    ) {
        this(
                kind,
                id,
                displayName,
                amount,
                unit,
                consumed,
                chance,
                acceptedVariantIds,
                chance >= 1.0D ? amount : 0L,
                amount,
                amount * chance
        );
    }

    public CraftScopeResourceAmount {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");

        unit = unit == null ? "" : unit;

        if (amount < 0L) {
            throw new IllegalArgumentException(
                    "Resource amount cannot be negative"
            );
        }

        if (Double.isNaN(chance)
                || Double.isInfinite(chance)
                || chance < 0.0D
                || chance > 1.0D) {

            throw new IllegalArgumentException(
                    "Chance must be between 0.0 and 1.0"
            );
        }

        if (minimumAmount < 0L
                || maximumAmount < minimumAmount) {

            throw new IllegalArgumentException(
                    "Invalid resource yield range"
            );
        }

        if (Double.isNaN(expectedAmount)
                || Double.isInfinite(expectedAmount)
                || expectedAmount < 0.0D) {

            throw new IllegalArgumentException(
                    "Expected amount must be finite and non-negative"
            );
        }

        if (expectedAmount + 0.000001D < minimumAmount
                || expectedAmount - 0.000001D > maximumAmount) {

            throw new IllegalArgumentException(
                    "Expected amount must fall inside the yield range"
            );
        }

        if (acceptedVariantIds == null
                || acceptedVariantIds.isEmpty()) {

            acceptedVariantIds = List.of(id);

        } else {

            acceptedVariantIds = List.copyOf(
                    acceptedVariantIds
            );
        }
    }

    public static CraftScopeResourceAmount item(
            ItemStack stack,
            long amount
    ) {
        return item(stack, amount, true);
    }

    public static CraftScopeResourceAmount item(
            ItemStack stack,
            long amount,
            boolean consumed
    ) {
        if (stack == null || stack.isEmpty()) {
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
                1.0D,
                List.of(id)
        );
    }

    /*
     * Uniform integer range, used by ordinary vanilla block drops.
     *
     * Example 4..5 has an expected value of 4.5.
     */
    public static CraftScopeResourceAmount variableItem(
            ItemStack stack,
            long minimumAmount,
            long maximumAmount,
            boolean consumed
    ) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "Variable item resource cannot use an empty ItemStack"
            );
        }

        if (minimumAmount < 0L
                || maximumAmount < minimumAmount) {

            throw new IllegalArgumentException(
                    "Invalid variable item range"
            );
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        double expected =
                (
                        (double) minimumAmount
                                + (double) maximumAmount
                ) / 2.0D;

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                minimumAmount,
                "",
                consumed,
                1.0D,
                List.of(id),
                minimumAmount,
                maximumAmount,
                expected
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
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Item variants cannot be empty"
            );
        }

        ItemStack representative = null;
        List<ResourceLocation> ids =
                new ArrayList<>();

        for (ItemStack stack : variants) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            ResourceLocation id =
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    );

            if (representative == null) {
                representative = stack;
            }

            if (!ids.contains(id)) {
                ids.add(id);
            }
        }

        if (representative == null || ids.isEmpty()) {
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
                representative.getHoverName().copy(),
                amount,
                "",
                consumed,
                1.0D,
                ids
        );
    }

    /*
     * "Guaranteed" means at least amount/minimumAmount is guaranteed.
     * A variable 4-5 drop is therefore still guaranteed to produce
     * four, while its expected yield is 4.5.
     */
    public boolean isGuaranteed() {
        return chance >= 1.0D;
    }

    public boolean isProbabilistic() {
        return chance < 1.0D;
    }

    public boolean hasVariableRange() {
        return minimumAmount != maximumAmount;
    }

    public boolean hasVariableYield() {
        return minimumAmount != maximumAmount
                || Math.abs(
                expectedAmount - amount
        ) > 0.000001D;
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
