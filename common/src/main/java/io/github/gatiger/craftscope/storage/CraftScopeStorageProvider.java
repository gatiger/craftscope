package io.github.gatiger.craftscope.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/*
 * A source of items CraftScope can count when the player chooses
 * Scan Inventory / Recalculate.
 *
 * The first provider is the normal player inventory. Future storage
 * integrations such as AE2 or Refined Storage can register their own
 * providers without changing the material-tracking UI.
 */
public interface CraftScopeStorageProvider {

    String id();

    String displayName();

    List<ItemStack> captureAvailableStacks(
            Minecraft minecraft
    );
}