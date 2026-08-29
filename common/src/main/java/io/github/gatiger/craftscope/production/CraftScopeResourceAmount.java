package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * One resource participating in a production step.
 *
 * Besides the nominal amount/chance representation, a resource can
 * carry an explicit minimum, maximum, and expected amount.
 *
 * Item resources can also carry a component-aware item identity.
 *
 * This matters for items such as:
 *
 *     minecraft:tipped_arrow + Poison
 *     minecraft:tipped_arrow + Slowness
 *
 * Those share the same base ResourceLocation but are different
 * Minecraft ItemStacks.
 *
 * Component identity is optional for backward compatibility. Existing
 * providers that only know an item ID continue to behave exactly as
 * before and receive a componentless identity automatically whenever
 * the item is registered.
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
        double expectedAmount,
        CraftScopeItemIdentity itemIdentity
) {

    /*
     * Backward-compatible constructor matching the previous canonical
     * CraftScopeResourceAmount signature.
     *
     * Every existing provider using the eleven-field constructor can
     * continue compiling unchanged.
     */
    public CraftScopeResourceAmount(
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
        this(
                kind,
                id,
                displayName,
                amount,
                unit,
                consumed,
                chance,
                acceptedVariantIds,
                minimumAmount,
                maximumAmount,
                expectedAmount,
                null
        );
    }

    /*
     * Backward-compatible short constructor used throughout the
     * production providers.
     *
     * Deterministic resources:
     *
     *     min = max = amount
     *
     * Chance resources:
     *
     *     min      = 0
     *     max      = amount
     *     expected = amount * chance
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
                chance >= 1.0D
                        ? amount
                        : 0L,
                amount,
                amount * chance,
                null
        );
    }

    /*
     * Short constructor that preserves a component-aware item
     * identity while retaining the ordinary amount/chance behavior.
     */
    public CraftScopeResourceAmount(
            CraftScopeResourceKind kind,
            ResourceLocation id,
            Component displayName,
            long amount,
            String unit,
            boolean consumed,
            double chance,
            List<ResourceLocation> acceptedVariantIds,
            CraftScopeItemIdentity itemIdentity
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
                chance >= 1.0D
                        ? amount
                        : 0L,
                amount,
                amount * chance,
                itemIdentity
        );
    }
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

        if (amount < 0L) {

            throw new IllegalArgumentException(
                    "Resource amount cannot be negative"
            );
        }

        if (Double.isNaN(
                chance
        )
                || Double.isInfinite(
                chance
        )
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

        if (Double.isNaN(
                expectedAmount
        )
                || Double.isInfinite(
                expectedAmount
        )
                || expectedAmount < 0.0D) {

            throw new IllegalArgumentException(
                    "Expected amount must be finite and non-negative"
            );
        }

        if (expectedAmount + 0.000001D
                < minimumAmount
                || expectedAmount - 0.000001D
                > maximumAmount) {

            throw new IllegalArgumentException(
                    "Expected amount must fall inside the yield range"
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

        /*
         * Existing item resources that were created only from an ID
         * receive a normal componentless identity automatically.
         *
         * A provider supplying a real ItemStack can pass an explicit
         * CraftScopeItemIdentity so custom components are retained.
         */
        if (kind == CraftScopeResourceKind.ITEM) {

            if (itemIdentity == null) {

                itemIdentity =
                        createDefaultItemIdentity(
                                id
                        );

            } else if (!id.equals(
                    itemIdentity.itemId()
            )) {

                throw new IllegalArgumentException(
                        "Item identity does not match resource item ID"
                );
            }

        } else {

            /*
             * Components only apply to ITEM resources.
             */
            itemIdentity =
                    null;
        }
    }

    /*
     * ---------------------------------------------------------
     * Item factories
     * ---------------------------------------------------------
     */

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

        CraftScopeItemIdentity identity =
                CraftScopeItemIdentity.fromStack(
                        stack
                );

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack
                        .getHoverName()
                        .copy(),
                amount,
                "",
                consumed,
                1.0D,
                List.of(
                        id
                ),
                amount,
                amount,
                amount,
                identity
        );
    }

    /*
     * Uniform integer range.
     *
     * Example:
     *
     *     Redstone Ore -> 4..5 Redstone
     *
     * has expected value:
     *
     *     4.5
     */
    public static CraftScopeResourceAmount variableItem(
            ItemStack stack,
            long minimumAmount,
            long maximumAmount,
            boolean consumed
    ) {
        if (stack == null
                || stack.isEmpty()) {

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

        CraftScopeItemIdentity identity =
                CraftScopeItemIdentity.fromStack(
                        stack
                );

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack
                        .getHoverName()
                        .copy(),
                minimumAmount,
                "",
                consumed,
                1.0D,
                List.of(
                        id
                ),
                minimumAmount,
                maximumAmount,
                expected,
                identity
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

            if (!ids.contains(
                    id
            )) {

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

        CraftScopeItemIdentity identity =
                CraftScopeItemIdentity.fromStack(
                        representative
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
                1.0D,
                ids,
                amount,
                amount,
                amount,
                identity
        );
    }

    /*
     * ---------------------------------------------------------
     * Yield behavior
     * ---------------------------------------------------------
     */

    /*
     * "Guaranteed" means at least the resource's minimum outcome is
     * guaranteed by probability.
     *
     * A deterministic variable 4-5 drop therefore remains
     * guaranteed, while a 75% 0-2 outcome is probabilistic.
     */
    public boolean isGuaranteed() {
        return chance >= 1.0D;
    }

    public boolean isProbabilistic() {
        return chance < 1.0D;
    }

    public boolean hasVariableRange() {
        return minimumAmount
                != maximumAmount;
    }

    public boolean hasVariableYield() {
        return minimumAmount
                != maximumAmount
                || Math.abs(
                expectedAmount
                        - amount
        ) > 0.000001D;
    }

    public boolean hasUnit() {
        return !unit.isBlank();
    }

    /*
     * ---------------------------------------------------------
     * Variant / ItemStack identity
     * ---------------------------------------------------------
     */

    public boolean hasVariants() {
        return acceptedVariantIds.size()
                > 1;
    }

    /*
     * Legacy ID-only matching.
     *
     * This remains useful for ordinary componentless resources and
     * providers that operate only at the registry-ID level.
     */
    public boolean accepts(
            ResourceLocation resourceId
    ) {
        return resourceId != null
                && acceptedVariantIds.contains(
                resourceId
        );
    }

    /*
     * Preferred matching path for real ItemStacks.
     *
     * Component-bearing resources require an exact Minecraft
     * item+component match.
     *
     * Ordinary componentless resources retain the existing
     * acceptedVariantIds behavior.
     */
    public boolean accepts(
            ItemStack candidate
    ) {
        if (candidate == null
                || candidate.isEmpty()
                || kind
                != CraftScopeResourceKind.ITEM) {

            return false;
        }

        if (hasCustomItemComponents()) {

            return itemIdentity.matches(
                    candidate
            );
        }

        ResourceLocation candidateId =
                BuiltInRegistries.ITEM.getKey(
                        candidate.getItem()
                );

        return accepts(
                candidateId
        );
    }

    public boolean hasItemIdentity() {
        return itemIdentity != null;
    }

    public boolean hasCustomItemComponents() {
        return itemIdentity != null
                && itemIdentity
                .hasCustomComponents();
    }

    /*
     * Returns a safe ItemStack copy suitable for GUI rendering,
     * tooltips, JEI interaction, etc.
     *
     * For component-bearing resources, the returned stack includes
     * those components.
     */
    public ItemStack createDisplayStack() {
        if (kind
                != CraftScopeResourceKind.ITEM) {

            return ItemStack.EMPTY;
        }

        if (itemIdentity != null) {

            return itemIdentity.createStack();
        }

        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                id
                        )
                        .orElse(
                                null
                        );

        if (item == null) {

            return ItemStack.EMPTY;
        }

        return new ItemStack(
                item
        );
    }

    /*
     * ---------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------
     */

    private static CraftScopeItemIdentity
    createDefaultItemIdentity(
            ResourceLocation id
    ) {
        if (id == null) {
            return null;
        }

        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                id
                        )
                        .orElse(
                                null
                        );

        if (item == null) {
            return null;
        }

        ItemStack stack =
                new ItemStack(
                        item
                );

        if (stack.isEmpty()) {
            return null;
        }

        return CraftScopeItemIdentity.fromStack(
                stack
        );
    }
}
