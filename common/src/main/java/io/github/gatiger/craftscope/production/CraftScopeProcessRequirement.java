package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/*
 * Describes infrastructure or an operating requirement for
 * one production step.
 *
 * id may be null for requirements that do not correspond to
 * a registered Minecraft object.
 *
 * Examples:
 *
 * MACHINE
 *   mekanism:chemical_dissolution_chamber
 *
 * HEAT
 *   "Heated"
 *
 * MECHANICAL_POWER
 *   "Rotational Power"
 */
public record CraftScopeProcessRequirement(
        CraftScopeRequirementKind kind,
        ResourceLocation id,
        Component displayName,
        long amount,
        String unit
) {

    public CraftScopeProcessRequirement {
        Objects.requireNonNull(
                kind,
                "kind"
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
                    "Requirement amount cannot be negative"
            );
        }
    }

    public boolean hasRegistryId() {

        return id != null;
    }

    public boolean hasUnit() {

        return !unit.isBlank();
    }
}