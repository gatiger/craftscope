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
 * Minecraft recipes are only one way to obtain resources. Some
 * materials are normally obtained by interacting with the world
 * rather than through RecipeManager.
 *
 * The acquisition provider deliberately starts with deterministic
 * baseline drops. It does NOT yet guess variable loot such as:
 *
 * - Redstone Ore (variable count)
 * - Lapis Ore (variable count)
 * - Copper Ore (variable count)
 * - Nether Gold Ore (variable count)
 * - Gravel -> Flint (chance)
 * - Glowstone (variable count)
 *
 * Fortune/Silk Touch variants can be added later as explicit,
 * selectable acquisition methods. For ores whose normal no-Fortune
 * drop is deterministic, this provider models that baseline and
 * notes that Fortune can increase the yield.
 *
 * Tool requirements carry exact accepted item variants. This lets
 * CraftScope rotate the usable tool icons while still respecting
 * Minecraft harvest tiers. A Gold Pickaxe is therefore included
 * for blocks that accept wooden-tier tools, but correctly excluded
 * from Stone-or-better and Iron-or-better requirements.
 */
public final class CraftScopeVanillaAcquisitionRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:vanilla_acquisition";

    private static final String SOURCE_MOD_ID =
            "minecraft";

    private static final Component SOURCE_MOD_NAME =
            Component.literal(
                    "Minecraft"
            );

    private static final ResourceLocation MINING_PROCESS_ID =
            requireId(
                    "craftscope:mining"
            );

    private static final ResourceLocation DIGGING_PROCESS_ID =
            requireId(
                    "craftscope:digging"
            );

    private static final List<AcquisitionDefinition> DEFINITIONS =
            List.of(
                    /*
                     * -------------------------------------------------
                     * Basic block transformations
                     * -------------------------------------------------
                     */
                    mining(
                            "stone_to_cobblestone",
                            Items.STONE,
                            1,
                            Items.COBBLESTONE,
                            1,
                            "Mining Stone",
                            1600,
                            anyPickaxe(
                                    "Any Pickaxe (no Silk Touch)"
                            )
                    ),
                    mining(
                            "deepslate_to_cobbled_deepslate",
                            Items.DEEPSLATE,
                            1,
                            Items.COBBLED_DEEPSLATE,
                            1,
                            "Mining Deepslate",
                            1590,
                            anyPickaxe(
                                    "Any Pickaxe (no Silk Touch)"
                            )
                    ),
                    mining(
                            "clay_to_clay_balls",
                            Items.CLAY,
                            1,
                            Items.CLAY_BALL,
                            4,
                            "Mining Clay",
                            1580
                    ),

                    /*
                     * -------------------------------------------------
                     * Deterministic baseline ore drops
                     *
                     * Normal + deepslate definitions deliberately use
                     * the same process name and the same tier-aware
                     * requirement so they normalize into one logical
                     * material route.
                     * -------------------------------------------------
                     */
                    mining(
                            "coal_ore_to_coal",
                            Items.COAL_ORE,
                            1,
                            Items.COAL,
                            1,
                            "Mining Coal Ore",
                            1570,
                            anyPickaxe(
                                    "Any Pickaxe (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "deepslate_coal_ore_to_coal",
                            Items.DEEPSLATE_COAL_ORE,
                            1,
                            Items.COAL,
                            1,
                            "Mining Coal Ore",
                            1570,
                            anyPickaxe(
                                    "Any Pickaxe (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "iron_ore_to_raw_iron",
                            Items.IRON_ORE,
                            1,
                            Items.RAW_IRON,
                            1,
                            "Mining Iron Ore",
                            1560,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "deepslate_iron_ore_to_raw_iron",
                            Items.DEEPSLATE_IRON_ORE,
                            1,
                            Items.RAW_IRON,
                            1,
                            "Mining Iron Ore",
                            1560,
                            stonePickaxeOrBetter(
                                    "Stone Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "gold_ore_to_raw_gold",
                            Items.GOLD_ORE,
                            1,
                            Items.RAW_GOLD,
                            1,
                            "Mining Gold Ore",
                            1550,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "deepslate_gold_ore_to_raw_gold",
                            Items.DEEPSLATE_GOLD_ORE,
                            1,
                            Items.RAW_GOLD,
                            1,
                            "Mining Gold Ore",
                            1550,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "diamond_ore_to_diamond",
                            Items.DIAMOND_ORE,
                            1,
                            Items.DIAMOND,
                            1,
                            "Mining Diamond Ore",
                            1540,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "deepslate_diamond_ore_to_diamond",
                            Items.DEEPSLATE_DIAMOND_ORE,
                            1,
                            Items.DIAMOND,
                            1,
                            "Mining Diamond Ore",
                            1540,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "emerald_ore_to_emerald",
                            Items.EMERALD_ORE,
                            1,
                            Items.EMERALD,
                            1,
                            "Mining Emerald Ore",
                            1530,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "deepslate_emerald_ore_to_emerald",
                            Items.DEEPSLATE_EMERALD_ORE,
                            1,
                            Items.EMERALD,
                            1,
                            "Mining Emerald Ore",
                            1530,
                            ironPickaxeOrBetter(
                                    "Iron Pickaxe or better (no Silk Touch; Fortune may increase yield)"
                            )
                    ),
                    mining(
                            "nether_quartz_ore_to_quartz",
                            Items.NETHER_QUARTZ_ORE,
                            1,
                            Items.QUARTZ,
                            1,
                            "Mining Nether Quartz Ore",
                            1520,
                            anyPickaxe(
                                    "Any Pickaxe (no Silk Touch; Fortune may increase yield)"
                            )
                    ),

                    /*
                     * -------------------------------------------------
                     * Deterministic non-pickaxe collection
                     * -------------------------------------------------
                     */
                    digging(
                            "snow_block_to_snowballs",
                            Items.SNOW_BLOCK,
                            1,
                            Items.SNOWBALL,
                            4,
                            "Digging Snow",
                            1510,
                            anyShovel(
                                    "Any Shovel (no Silk Touch)"
                            )
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
        if (target == null
                || target.isEmpty()) {

            return List.of();
        }

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        for (AcquisitionDefinition definition :
                DEFINITIONS) {

            if (!ItemStack.isSameItem(
                    target,
                    new ItemStack(
                            definition.outputItem()
                    )
            )) {

                continue;
            }

            CraftScopeResourceAmount input =
                    CraftScopeResourceAmount.item(
                            new ItemStack(
                                    definition.inputItem()
                            ),
                            definition.inputAmount(),
                            true
                    );

            CraftScopeResourceAmount output =
                    CraftScopeResourceAmount.item(
                            new ItemStack(
                                    definition.outputItem()
                            ),
                            definition.outputAmount(),
                            false
                    );

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            definition.processId(),
                            Component.literal(
                                    definition.displayName()
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
                            List.of(
                                    input
                            ),
                            List.of(
                                    output
                            ),
                            List.of(
                                    method
                            )
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
                            List.of(
                                    step
                            ),
                            definition.priority()
                    )
            );
        }

        return List.copyOf(
                routes
        );
    }

    private static AcquisitionDefinition mining(
            String routePath,
            Item input,
            long inputAmount,
            Item output,
            long outputAmount,
            String displayName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return acquisition(
                "mining/" + routePath,
                MINING_PROCESS_ID,
                input,
                inputAmount,
                output,
                outputAmount,
                displayName,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition digging(
            String routePath,
            Item input,
            long inputAmount,
            Item output,
            long outputAmount,
            String displayName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return acquisition(
                "digging/" + routePath,
                DIGGING_PROCESS_ID,
                input,
                inputAmount,
                output,
                outputAmount,
                displayName,
                priority,
                requirements
        );
    }

    private static AcquisitionDefinition acquisition(
            String routePath,
            ResourceLocation processId,
            Item input,
            long inputAmount,
            Item output,
            long outputAmount,
            String displayName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return new AcquisitionDefinition(
                requireId(
                        "craftscope:acquisition/"
                                + routePath
                ),
                processId,
                input,
                inputAmount,
                output,
                outputAmount,
                displayName,
                priority,
                List.of(
                        requirements
                )
        );
    }

    /*
     * Wooden-tier blocks can be mined by Wood, Gold, Stone, Iron,
     * Diamond, or Netherite pickaxes.
     */
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
     * Gold tools have wooden harvest tier, so Gold Pickaxe must NOT
     * appear here even though it is a pickaxe.
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

                    ids.add(
                            id
                    );
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
                Component.literal(
                        displayName
                ),
                1,
                "",
                ids
        );
    }

    private static ResourceLocation requireId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(
                        value
                );

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
            long inputAmount,
            Item outputItem,
            long outputAmount,
            String displayName,
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
