package io.github.gatiger.craftscope.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Registry for all storage sources CraftScope knows how to scan.
 *
 * The vanilla player inventory provider is always available.
 * Optional mod integrations can register additional providers later.
 */
public final class CraftScopeStorageRegistry {

    private static final Map<String, CraftScopeStorageProvider>
            PROVIDERS =
            new LinkedHashMap<>();

    static {
        registerProvider(
                new CraftScopeVanillaInventoryStorageProvider()
        );
    }

    private CraftScopeStorageRegistry() {
    }

    public static synchronized void registerProvider(
            CraftScopeStorageProvider provider
    ) {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "CraftScope storage provider cannot be null"
            );
        }

        String id = provider.id();

        if (id == null
                || id.isBlank()) {

            throw new IllegalArgumentException(
                    "CraftScope storage provider must have an ID"
            );
        }

        PROVIDERS.put(
                id,
                provider
        );
    }

    public static synchronized boolean unregisterProvider(
            String id
    ) {
        if (id == null
                || id.isBlank()
                || CraftScopeVanillaInventoryStorageProvider.ID.equals(id)) {

            return false;
        }

        return PROVIDERS.remove(id) != null;
    }

    public static synchronized List<CraftScopeStorageProvider>
    providers() {

        return List.copyOf(
                PROVIDERS.values()
        );
    }

    public static CraftScopeStorageSnapshot capture(
            Minecraft minecraft
    ) {
        List<ItemStack> availableStacks =
                new ArrayList<>();

        List<String> sourceNames =
                new ArrayList<>();

        for (CraftScopeStorageProvider provider :
                providers()) {

            try {
                List<ItemStack> providerStacks =
                        provider.captureAvailableStacks(
                                minecraft
                        );

                sourceNames.add(
                        provider.displayName()
                );

                if (providerStacks == null) {
                    continue;
                }

                for (ItemStack stack :
                        providerStacks) {

                    if (stack == null
                            || stack.isEmpty()) {

                        continue;
                    }

                    availableStacks.add(
                            stack.copy()
                    );
                }

            } catch (RuntimeException ignored) {
                /*
                 * Optional storage integrations must not be able to
                 * break the entire CraftScope material screen.
                 *
                 * A failing provider is skipped for this scan while
                 * the remaining providers continue normally.
                 */
            }
        }

        return CraftScopeStorageSnapshot.scanned(
                sourceNames,
                availableStacks
        );
    }
}