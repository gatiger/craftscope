package io.github.gatiger.craftscope.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CraftScopeVanillaInventoryStorageProvider
        implements CraftScopeStorageProvider {

    public static final String ID =
            "craftscope:player_inventory";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Player Inventory";
    }

    @Override
    public List<ItemStack> captureAvailableStacks(
            Minecraft minecraft
    ) {
        if (minecraft == null
                || minecraft.player == null) {

            return List.of();
        }

        Inventory inventory =
                minecraft.player.getInventory();

        List<ItemStack> result =
                new ArrayList<>();

        copyStacks(
                inventory.items,
                result
        );

        copyStacks(
                inventory.armor,
                result
        );

        copyStacks(
                inventory.offhand,
                result
        );

        return List.copyOf(
                result
        );
    }

    private static void copyStacks(
            Iterable<ItemStack> source,
            List<ItemStack> destination
    ) {
        if (source == null) {
            return;
        }

        for (ItemStack stack : source) {

            if (stack == null
                    || stack.isEmpty()
                    || stack.getCount() <= 0) {

                continue;
            }

            destination.add(
                    stack.copy()
            );
        }
    }
}