package io.github.gatiger.craftscope.production;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/*
 * Component-aware identity for an item.
 *
 * ResourceLocation alone is not enough to uniquely identify many
 * modern Minecraft items.
 *
 * Examples:
 *
 *     minecraft:tipped_arrow
 *         + Poison potion component
 *
 *     minecraft:tipped_arrow
 *         + Slowness potion component
 *
 *     minecraft:ominous_bottle
 *         + amplifier component
 *
 * Those are materially different ItemStacks even though they share
 * the same base item ID.
 *
 * CraftScopeItemIdentity stores a one-count snapshot of the stack and
 * delegates equality/hash behavior to Minecraft's own
 * component-aware ItemStack helpers.
 *
 * The stored ItemStack is never exposed directly. Callers receive a
 * copy so external code cannot mutate the identity after creation.
 */
public final class CraftScopeItemIdentity {

    private final ItemStack stack;

    private final int hashCode;

    private CraftScopeItemIdentity(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            throw new IllegalArgumentException(
                    "CraftScope item identity cannot use an empty ItemStack"
            );
        }

        /*
         * Count is deliberately excluded from identity.
         *
         * One Poison Arrow and sixty-four Poison Arrows are the same
         * resource identity; quantity belongs to CraftScope's amount
         * model instead.
         */
        this.stack =
                stack.copyWithCount(
                        1
                );

        this.hashCode =
                ItemStack.hashItemAndComponents(
                        this.stack
                );
    }

    public static CraftScopeItemIdentity fromStack(
            ItemStack stack
    ) {
        return new CraftScopeItemIdentity(
                stack
        );
    }

    public ResourceLocation itemId() {
        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        if (id == null) {

            throw new IllegalStateException(
                    "CraftScope item identity has no registered item ID"
            );
        }

        return id;
    }

    public Component displayName() {
        /*
         * Some component-bearing items do not expose their variant
         * through ItemStack#getHoverName().
         *
         * Ominous Bottles are one example: amplifier 0 and amplifier
         * 4 both normally report simply "Ominous Bottle".
         *
         * CraftScope needs the component identity to be visible so a
         * player can tell which exact resource a route produces.
         */
        Integer ominousAmplifier =
                stack.get(
                        DataComponents.OMINOUS_BOTTLE_AMPLIFIER
                );

        if (ominousAmplifier != null) {

            int visibleLevel =
                    ominousAmplifier + 1;

            return stack
                    .getHoverName()
                    .copy()
                    .append(
                            " "
                                    + formatRomanLevel(
                                    visibleLevel
                            )
                    );
        }

        return stack
                .getHoverName()
                .copy();
    }

    private static String formatRomanLevel(
            int level
    ) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(
                    level
            );
        };
    }

    public boolean hasCustomComponents() {
        return !stack
                .getComponentsPatch()
                .isEmpty();
    }

    public boolean matches(
            ItemStack candidate
    ) {
        if (candidate == null
                || candidate.isEmpty()) {

            return false;
        }

        return ItemStack.isSameItemSameComponents(
                stack,
                candidate
        );
    }

    public ItemStack createStack() {
        return stack.copy();
    }

    @Override
    public boolean equals(
            Object object
    ) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof CraftScopeItemIdentity other)) {
            return false;
        }

        return ItemStack.isSameItemSameComponents(
                stack,
                other.stack
        );
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return itemId()
                + (
                hasCustomComponents()
                        ? stack
                        .getComponentsPatch()
                        .toString()
                        : ""
        );
    }
}