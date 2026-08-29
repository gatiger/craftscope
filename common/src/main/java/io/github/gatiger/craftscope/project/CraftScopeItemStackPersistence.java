package io.github.gatiger.craftscope.project;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;


/*
 * Serializes a complete ItemStack for CraftScope project storage.
 *
 * ResourceLocation alone is insufficient for modern Minecraft items
 * because many item identities live in data components.
 *
 * The ItemStack codec preserves those components while keeping
 * CraftScope's projects.json human-portable.
 */
public final class CraftScopeItemStackPersistence {

    private CraftScopeItemStackPersistence() {
    }

    public static String encode(
            ItemStack stack,
            RegistryAccess registryAccess
    ) {
        if (stack == null
                || stack.isEmpty()
                || registryAccess == null) {

            return null;
        }

        try {
            RegistryOps<JsonElement> ops =
                    registryAccess
                            .createSerializationContext(
                                    JsonOps.INSTANCE
                            );

            ItemStack normalized =
                    stack.copyWithCount(
                            1
                    );

            return ItemStack.CODEC
                    .encodeStart(
                            ops,
                            normalized
                    )
                    .result()
                    .map(
                            JsonElement::toString
                    )
                    .orElse(
                            null
                    );

        } catch (RuntimeException ignored) {

            return null;
        }
    }

    public static ItemStack decode(
            String json,
            RegistryAccess registryAccess
    ) {
        if (json == null
                || json.isBlank()
                || registryAccess == null) {

            return ItemStack.EMPTY;
        }

        try {
            RegistryOps<JsonElement> ops =
                    registryAccess
                            .createSerializationContext(
                                    JsonOps.INSTANCE
                            );

            JsonElement element =
                    JsonParser.parseString(
                            json
                    );

            ItemStack stack =
                    ItemStack.CODEC
                            .parse(
                                    ops,
                                    element
                            )
                            .result()
                            .orElse(
                                    ItemStack.EMPTY
                            );

            if (stack == null
                    || stack.isEmpty()) {

                return ItemStack.EMPTY;
            }

            return stack.copyWithCount(
                    1
            );

        } catch (RuntimeException ignored) {

            return ItemStack.EMPTY;
        }
    }
}