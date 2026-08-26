package io.github.gatiger.craftscope.production;

/*
 * Requirements are things necessary to perform a process but
 * which should not automatically be counted as ordinary
 * consumed crafting materials.
 *
 * This distinction is important for the future Setup tab.
 */
public enum CraftScopeRequirementKind {

    /*
     * Furnace, Crushing Wheels, Chemical Washer,
     * Enrichment Chamber, etc.
     */
    MACHINE,

    /*
     * FE or another energy system.
     */
    ENERGY,

    /*
     * Heated / superheated / furnace-style processing
     * requirements.
     */
    HEAT,

    /*
     * Create rotational power, stress capacity, RPM, etc.
     */
    MECHANICAL_POWER,

    /*
     * Waterlogged environment, open air, dimension,
     * biome or other environmental conditions.
     */
    ENVIRONMENT,

    /*
     * Reusable tools, molds, catalysts or similar equipment.
     */
    TOOL,

    OTHER
}