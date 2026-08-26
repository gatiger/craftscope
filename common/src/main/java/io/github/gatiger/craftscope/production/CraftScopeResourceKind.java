package io.github.gatiger.craftscope.production;

/*
 * Resources are things that can move through a production
 * process or be consumed by it.
 *
 * Infrastructure such as machines, heat, power requirements,
 * and tools are represented separately by
 * CraftScopeRequirementKind.
 */
public enum CraftScopeResourceKind {

    ITEM,

    FLUID,

    /*
     * Used for gases, slurries, infuse types, pigments,
     * and similar chemical systems exposed by mods such as
     * Mekanism.
     */
    CHEMICAL,

    OTHER
}