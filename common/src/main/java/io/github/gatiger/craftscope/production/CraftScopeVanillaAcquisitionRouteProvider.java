package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/*
 * Vanilla world-acquisition routes.
 *
 * CraftScope models ordinary/default harvesting only. Enchantment
 * effects are intentionally outside this provider. A future
 * enchantment guide can explain enchanting without changing normal
 * recipe/drop planning.
 *
 * Tool requirements still enforce the real minimum harvest tier.
 *
 * Direct world acquisition is intentionally prioritized above
 * inefficient furnace recovery recipes. For example, Redstone Ore
 * normally drops 4-5 Redstone Dust when mined but smelts into only
 * one dust. Recipe Tree should therefore default to Mining Redstone
 * Ore while still keeping smelting available as an alternate route.
 */
public final class CraftScopeVanillaAcquisitionRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:vanilla_acquisition";

    private static final String SOURCE_MOD_ID =
            "minecraft";

    private static final Component SOURCE_MOD_NAME =
            Component.literal("Minecraft");

    private static final ResourceLocation MINING_PROCESS_ID =
            requireId("craftscope:mining");

    private static final ResourceLocation DIGGING_PROCESS_ID =
            requireId("craftscope:digging");

    private static final ResourceLocation BREAKING_PROCESS_ID =
            requireId("craftscope:breaking");

    private static final List<AcquisitionDefinition> DEFINITIONS =
            List.of(
                    /*
                     * Fixed normal drops
                     */
                    fixedMining(
                            "stone_to_cobblestone",
                            Items.STONE,
                            Items.COBBLESTONE,
                            1,
                            "Mining Stone",
                            4100,
                            anyPickaxe("Any Pickaxe")
                    ),
                    fixedMining(
                            "deepslate_to_cobbled_deepslate",
                            Items.DEEPSLATE,
                            Items.COBBLED_DEEPSLATE,
                            1,
                            "Mining Deepslate",
                            4090,
                            anyPickaxe("Any Pickaxe")
                    ),
                    fixedMining(
                            "clay_to_clay_balls",
                            Items.CLAY,
                            Items.CLAY_BALL,
                            4,
                            "Mining Clay",
                            1580
                    ),
                    fixedMining(
                            "coal_ore_to_coal",
                            Items.COAL_ORE,
                            Items.COAL,
                            1,
                            "Mining Coal Ore",
                            4070,
                            anyPickaxe("Any Pickaxe")
                    ),
                    fixedMining(
                            "deepslate_coal_ore_to_coal",
                            Items.DEEPSLATE_COAL_ORE,
                            Items.COAL,
                            1,
                            "Mining Coal Ore",
                            4070,
                            anyPickaxe("Any Pickaxe")
                    ),
                    fixedMining(
                            "iron_ore_to_raw_iron",
                            Items.IRON_ORE,
                            Items.RAW_IRON,
                            1,
                            "Mining Iron Ore",
                            4060,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "deepslate_iron_ore_to_raw_iron",
                            Items.DEEPSLATE_IRON_ORE,
                            Items.RAW_IRON,
                            1,
                            "Mining Iron Ore",
                            4060,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "gold_ore_to_raw_gold",
                            Items.GOLD_ORE,
                            Items.RAW_GOLD,
                            1,
                            "Mining Gold Ore",
                            4050,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "deepslate_gold_ore_to_raw_gold",
                            Items.DEEPSLATE_GOLD_ORE,
                            Items.RAW_GOLD,
                            1,
                            "Mining Gold Ore",
                            4050,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "diamond_ore_to_diamond",
                            Items.DIAMOND_ORE,
                            Items.DIAMOND,
                            1,
                            "Mining Diamond Ore",
                            4040,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "deepslate_diamond_ore_to_diamond",
                            Items.DEEPSLATE_DIAMOND_ORE,
                            Items.DIAMOND,
                            1,
                            "Mining Diamond Ore",
                            4040,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "emerald_ore_to_emerald",
                            Items.EMERALD_ORE,
                            Items.EMERALD,
                            1,
                            "Mining Emerald Ore",
                            4030,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "deepslate_emerald_ore_to_emerald",
                            Items.DEEPSLATE_EMERALD_ORE,
                            Items.EMERALD,
                            1,
                            "Mining Emerald Ore",
                            4030,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    fixedMining(
                            "nether_quartz_ore_to_quartz",
                            Items.NETHER_QUARTZ_ORE,
                            Items.QUARTZ,
                            1,
                            "Mining Nether Quartz Ore",
                            4020,
                            anyPickaxe("Any Pickaxe")
                    ),

                    /*
                     * Normal variable drops.
                     *
                     * These are explicit min-max ranges. CraftScope
                     * uses the midpoint as expected yield for planning
                     * while the UI still shows the real range.
                     */
                    rangedMining(
                            "redstone_ore_to_redstone",
                            Items.REDSTONE_ORE,
                            Items.REDSTONE,
                            4,
                            5,
                            "Mining Redstone Ore",
                            4010,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "deepslate_redstone_ore_to_redstone",
                            Items.DEEPSLATE_REDSTONE_ORE,
                            Items.REDSTONE,
                            4,
                            5,
                            "Mining Redstone Ore",
                            4010,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "lapis_ore_to_lapis",
                            Items.LAPIS_ORE,
                            Items.LAPIS_LAZULI,
                            4,
                            9,
                            "Mining Lapis Ore",
                            4000,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "deepslate_lapis_ore_to_lapis",
                            Items.DEEPSLATE_LAPIS_ORE,
                            Items.LAPIS_LAZULI,
                            4,
                            9,
                            "Mining Lapis Ore",
                            4000,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "copper_ore_to_raw_copper",
                            Items.COPPER_ORE,
                            Items.RAW_COPPER,
                            2,
                            5,
                            "Mining Copper Ore",
                            3990,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "deepslate_copper_ore_to_raw_copper",
                            Items.DEEPSLATE_COPPER_ORE,
                            Items.RAW_COPPER,
                            2,
                            5,
                            "Mining Copper Ore",
                            3990,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better"
                            )
                    ),
                    rangedMining(
                            "nether_gold_ore_to_gold_nuggets",
                            Items.NETHER_GOLD_ORE,
                            Items.GOLD_NUGGET,
                            2,
                            6,
                            "Mining Nether Gold Ore",
                            3980,
                            anyPickaxe("Any Pickaxe")
                    ),

                    /*
                     * Non-pickaxe/default collection.
                     */
                    fixedDigging(
                            "snow_block_to_snowballs",
                            Items.SNOW_BLOCK,
                            Items.SNOWBALL,
                            4,
                            "Digging Snow",
                            3970,
                            anyShovel("Any Shovel")
                    ),
                    rangedBreakingMethod(
                            "glowstone_to_glowstone_dust_hand",
                            Items.GLOWSTONE,
                            Items.GLOWSTONE_DUST,
                            2,
                            4,
                            "Breaking Glowstone",
                            "Breaking by Hand",
                            1460
                    ),
                    rangedBreakingMethod(
                            "glowstone_to_glowstone_dust_pickaxe",
                            Items.GLOWSTONE,
                            Items.GLOWSTONE_DUST,
                            2,
                            4,
                            "Breaking Glowstone",
                            "Breaking with Pickaxe",
                            3959,
                            anyPickaxe("Any Pickaxe")
                    )
            );

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public List<CraftScopeProductionRoute> findRoutes(
            ItemStack target,
            CraftScopeProductionContext context
    ) {
        if (target == null || target.isEmpty()) {
            return List.of();
        }

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        for (AcquisitionDefinition definition : DEFINITIONS) {
            ItemStack outputStack =
                    new ItemStack(
                            definition.outputItem()
                    );

            if (!ItemStack.isSameItem(
                    target,
                    outputStack
            )) {
                continue;
            }

            CraftScopeResourceAmount input =
                    CraftScopeResourceAmount.item(
                            new ItemStack(
                                    definition.inputItem()
                            ),
                            1,
                            true
                    );

            CraftScopeResourceAmount output;

            if (definition.minimumOutput()
                    == definition.maximumOutput()) {

                output =
                        CraftScopeResourceAmount.item(
                                outputStack,
                                definition.minimumOutput(),
                                false
                        );

            } else {

                output =
                        CraftScopeResourceAmount.variableItem(
                                outputStack,
                                definition.minimumOutput(),
                                definition.maximumOutput(),
                                false
                        );
            }

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            definition.processId(),
                            Component.literal(
                                    definition.methodDisplayName()
                            ),
                            List.of(),
                            definition.requirements()
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            definition.routeId()
                                    + ":step",
                            Component.literal(
                                    definition.displayName()
                            ),
                            List.of(input),
                            List.of(output),
                            List.of(method)
                    );

            routes.add(
                    new CraftScopeProductionRoute(
                            definition.routeId(),
                            SOURCE_MOD_ID,
                            SOURCE_MOD_NAME,
                            Component.literal(
                                    definition.displayName()
                            ),
                            output,
                            List.of(step),
                            definition.priority()
                    )
            );
        }

        return List.copyOf(routes);
    }

    private static AcquisitionDefinition fixedMining(
            String path,
            Item input,
            Item output,
            long amount,
            String name,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                "mining/" + path,
                MINING_PROCESS_ID,
                input,
                output,
                amount,
                amount,
                name,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition rangedMining(
            String path,
            Item input,
            Item output,
            long minimum,
            long maximum,
            String name,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                "mining/" + path,
                MINING_PROCESS_ID,
                input,
                output,
                minimum,
                maximum,
                name,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition fixedDigging(
            String path,
            Item input,
            Item output,
            long amount,
            String name,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                "digging/" + path,
                DIGGING_PROCESS_ID,
                input,
                output,
                amount,
                amount,
                name,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition rangedBreaking(
            String path,
            Item input,
            Item output,
            long minimum,
            long maximum,
            String name,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                "breaking/" + path,
                BREAKING_PROCESS_ID,
                input,
                output,
                minimum,
                maximum,
                name,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition rangedBreakingMethod(
            String path,
            Item input,
            Item output,
            long minimum,
            long maximum,
            String routeName,
            String methodName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                "breaking/" + path,
                BREAKING_PROCESS_ID,
                input,
                output,
                minimum,
                maximum,
                routeName,
                methodName,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition definition(
            String path,
            ResourceLocation processId,
            Item input,
            Item output,
            long minimum,
            long maximum,
            String name,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return definition(
                path,
                processId,
                input,
                output,
                minimum,
                maximum,
                name,
                name,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition definition(
            String path,
            ResourceLocation processId,
            Item input,
            Item output,
            long minimum,
            long maximum,
            String routeName,
            String methodName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return new AcquisitionDefinition(
                requireId(
                        "craftscope:acquisition/"
                                + path
                ),
                processId,
                input,
                output,
                minimum,
                maximum,
                routeName,
                methodName,
                priority,
                List.of(requirements)
        );
    }

    private static CraftScopeProcessRequirement anyPickaxe(
            String displayName
    ) {
        return toolVariants(
                displayName,
                Items.WOODEN_PICKAXE,
                Items.GOLDEN_PICKAXE,
                Items.STONE_PICKAXE,
                Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.NETHERITE_PICKAXE
        );
    }

    /*
     * Golden pickaxes have wooden harvest tier, so they are
     * deliberately absent from Stone-or-better and Iron-or-better.
     */
    private static CraftScopeProcessRequirement stonePickaxeOrBetter(
            String displayName
    ) {
        return toolVariants(
                displayName,
                Items.STONE_PICKAXE,
                Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.NETHERITE_PICKAXE
        );
    }

    private static CraftScopeProcessRequirement ironPickaxeOrBetter(
            String displayName
    ) {
        return toolVariants(
                displayName,
                Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.NETHERITE_PICKAXE
        );
    }

    private static CraftScopeProcessRequirement anyShovel(
            String displayName
    ) {
        return toolVariants(
                displayName,
                Items.WOODEN_SHOVEL,
                Items.GOLDEN_SHOVEL,
                Items.STONE_SHOVEL,
                Items.IRON_SHOVEL,
                Items.DIAMOND_SHOVEL,
                Items.NETHERITE_SHOVEL
        );
    }

    private static CraftScopeProcessRequirement toolVariants(
            String displayName,
            Item... tools
    ) {
        List<ResourceLocation> ids =
                new ArrayList<>();

        if (tools != null) {
            for (Item tool : tools) {
                if (tool == null
                        || tool == Items.AIR) {

                    continue;
                }

                ResourceLocation id =
                        BuiltInRegistries.ITEM.getKey(
                                tool
                        );

                if (id != null
                        && !ids.contains(id)) {

                    ids.add(id);
                }
            }
        }

        ResourceLocation representativeId =
                ids.isEmpty()
                        ? null
                        : ids.getFirst();

        return new CraftScopeProcessRequirement(
                CraftScopeRequirementKind.TOOL,
                representativeId,
                Component.literal(displayName),
                1,
                "",
                ids
        );
    }

    private static ResourceLocation requireId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(value);

        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid resource location: "
                            + value
            );
        }

        return id;
    }

    private record AcquisitionDefinition(
            ResourceLocation routeId,
            ResourceLocation processId,
            Item inputItem,
            Item outputItem,
            long minimumOutput,
            long maximumOutput,
            String displayName,
            String methodDisplayName,
            int priority,
            List<CraftScopeProcessRequirement> requirements
    ) {
        private AcquisitionDefinition {
            requirements =
                    requirements == null
                            ? List.of()
                            : List.copyOf(
                            requirements
                    );
        }
    }
}
