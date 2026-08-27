package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/*
 * Describes infrastructure or an operating requirement for
 * one production step.
 *
 * id may be null for requirements that do not correspond to
 * a registered Minecraft object.
 *
 * acceptedVariantIds lets one logical requirement expose every
 * interchangeable registered item that satisfies it.
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
 *
 * TOOL
 *   "Stone Pickaxe or better"
 *   accepted variants:
 *     minecraft:stone_pickaxe
 *     minecraft:iron_pickaxe
 *     minecraft:diamond_pickaxe
 *     minecraft:netherite_pickaxe
 *
 * UI code can rotate those variants without losing the actual
 * minimum-tier rule represented by the list.
 */
public record CraftScopeProcessRequirement(
        CraftScopeRequirementKind kind,
        ResourceLocation id,
        Component displayName,
        long amount,
        String unit,
        List<ResourceLocation> acceptedVariantIds
) {

    /*
     * Backward-compatible constructor used by existing providers.
     * A normal single-ID machine/tool requirement automatically
     * treats its ID as the only accepted registered variant.
     */
    public CraftScopeProcessRequirement(
            CraftScopeRequirementKind kind,
            ResourceLocation id,
            Component displayName,
            long amount,
            String unit
    ) {
        this(
                kind,
                id,
                displayName,
                amount,
                unit,
                id == null
                        ? List.of()
                        : List.of(id)
        );
    }

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

        if (acceptedVariantIds == null
                || acceptedVariantIds.isEmpty()) {

            acceptedVariantIds =
                    id == null
                            ? List.of()
                            : List.of(id);

        } else {

            acceptedVariantIds =
                    acceptedVariantIds
                            .stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
        }
    }

    public boolean hasRegistryId() {
        return id != null;
    }

    public boolean hasUnit() {
        return !unit.isBlank();
    }

    public boolean hasVariants() {
        return acceptedVariantIds.size() > 1;
    }

    public boolean accepts(
            ResourceLocation candidateId
    ) {
        if (candidateId == null) {
            return false;
        }

        if (!acceptedVariantIds.isEmpty()) {
            return acceptedVariantIds.contains(
                    candidateId
            );
        }

        return candidateId.equals(
                id
        );
    }
}
