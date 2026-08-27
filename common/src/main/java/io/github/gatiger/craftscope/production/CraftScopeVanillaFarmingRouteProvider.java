package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class CraftScopeVanillaFarmingRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID = "craftscope:vanilla_farming";
    private static final String SOURCE_MOD_ID = "minecraft";
    private static final Component SOURCE_MOD_NAME = Component.literal("Minecraft");
    private static final ResourceLocation FARMING_PROCESS_ID = requireId("craftscope:farming");
    private static final int BASE_PRIORITY = 3800;

    private static final List<FarmingDefinition> DEFINITIONS = List.of(
            fixedFarm("wheat", Items.WHEAT_SEEDS, Items.WHEAT, 1,
                    "Farming Wheat", BASE_PRIORITY,
                    anyHoe(), environment("Farmland"), environment("Light level 9+")),
            rangedFarm("carrots", Items.CARROT, Items.CARROT, 1, 4,
                    "Farming Carrots", BASE_PRIORITY - 10,
                    anyHoe(), environment("Farmland"), environment("Light level 9+")),
            rangedFarm("potatoes", Items.POTATO, Items.POTATO, 1, 4,
                    "Farming Potatoes", BASE_PRIORITY - 20,
                    anyHoe(), environment("Farmland"), environment("Light level 9+")),
            fixedFarm("beetroots", Items.BEETROOT_SEEDS, Items.BEETROOT, 1,
                    "Farming Beetroot", BASE_PRIORITY - 30,
                    anyHoe(), environment("Farmland"), environment("Light level 9+")),
            rangedFarm("nether_wart", Items.NETHER_WART, Items.NETHER_WART, 2, 4,
                    "Farming Nether Wart", BASE_PRIORITY - 40,
                    environmentItem("Soul Sand", Items.SOUL_SAND)),
            fixedFarm("cocoa_beans", Items.COCOA_BEANS, Items.COCOA_BEANS, 3,
                    "Farming Cocoa Beans", BASE_PRIORITY - 50,
                    environmentItems("Jungle Log or Jungle Wood",
                            Items.JUNGLE_LOG, Items.JUNGLE_WOOD,
                            Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_JUNGLE_WOOD)),
            rangedFarm("sweet_berries", Items.SWEET_BERRIES, Items.SWEET_BERRIES, 2, 3,
                    "Farming Sweet Berries", BASE_PRIORITY - 60,
                    environment("Dirt/grass-type ground")),
            fixedFarm("sugar_cane", Items.SUGAR_CANE, Items.SUGAR_CANE, 1,
                    "Farming Sugar Cane", BASE_PRIORITY - 70,
                    environment("Water-adjacent support block")),
            fixedFarm("cactus", Items.CACTUS, Items.CACTUS, 1,
                    "Farming Cactus", BASE_PRIORITY - 80,
                    environmentItems("Sand or Red Sand", Items.SAND, Items.RED_SAND),
                    environment("No horizontally adjacent blocks")),
            fixedFarm("bamboo", Items.BAMBOO, Items.BAMBOO, 1,
                    "Farming Bamboo", BASE_PRIORITY - 90,
                    environment("Suitable plantable ground"),
                    environment("Light level 9+ for growth")),
            fixedFarm("kelp", Items.KELP, Items.KELP, 1,
                    "Farming Kelp", BASE_PRIORITY - 100,
                    environment("Submerged water column"))
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

        List<CraftScopeProductionRoute> routes = new ArrayList<>();

        for (FarmingDefinition definition : DEFINITIONS) {
            ItemStack outputStack = new ItemStack(definition.outputItem());

            if (!ItemStack.isSameItem(target, outputStack)) {
                continue;
            }

            CraftScopeResourceAmount mainOutput =
                    definition.minimumOutput() == definition.maximumOutput()
                            ? CraftScopeResourceAmount.item(
                                    outputStack,
                                    definition.minimumOutput(),
                                    false
                            )
                            : CraftScopeResourceAmount.variableItem(
                                    outputStack,
                                    definition.minimumOutput(),
                                    definition.maximumOutput(),
                                    false
                            );

            List<CraftScopeResourceAmount> outputs = new ArrayList<>();
            outputs.add(mainOutput);

            if (definition.outputItem() == Items.WHEAT) {
                outputs.add(
                        rangedByproduct(
                                Items.WHEAT_SEEDS,
                                1,
                                4
                        )
                );
            }

            if (definition.outputItem() == Items.BEETROOT) {
                outputs.add(
                        rangedByproduct(
                                Items.BEETROOT_SEEDS,
                                0,
                                3
                        )
                );
            }

            if (definition.outputItem() == Items.POTATO) {
                outputs.add(
                        chanceItem(
                                Items.POISONOUS_POTATO,
                                1,
                                0.02D
                        )
                );
            }

            CraftScopeProductionMethod method = new CraftScopeProductionMethod(
                    SOURCE_MOD_ID,
                    FARMING_PROCESS_ID,
                    Component.literal(definition.displayName()),
                    List.of(),
                    definition.requirements()
            );

            CraftScopeProductionStep step = new CraftScopeProductionStep(
                    definition.routeId() + ":step",
                    Component.literal(definition.displayName()),
                    List.of(),
                    outputs,
                    List.of(method)
            );

            routes.add(new CraftScopeProductionRoute(
                    definition.routeId(),
                    SOURCE_MOD_ID,
                    SOURCE_MOD_NAME,
                    Component.literal(definition.displayName()),
                    mainOutput,
                    List.of(step),
                    definition.priority()
            ));
        }

        return List.copyOf(routes);
    }

    private static FarmingDefinition fixedFarm(
            String path, Item starter, Item output, long amount,
            String displayName, int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return farm(path, starter, output, amount, amount,
                displayName, priority, requirements);
    }

    private static FarmingDefinition rangedFarm(
            String path, Item starter, Item output, long minimum, long maximum,
            String displayName, int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return farm(path, starter, output, minimum, maximum,
                displayName, priority, requirements);
    }

    private static FarmingDefinition farm(
            String path, Item starter, Item output, long minimum, long maximum,
            String displayName, int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        List<CraftScopeProcessRequirement> allRequirements =
                new ArrayList<>();

        allRequirements.add(
                itemRequirement(
                        CraftScopeRequirementKind.OTHER,
                        "Starter: " + new ItemStack(starter)
                                .getHoverName()
                                .getString(),
                        starter
                )
        );

        if (requirements != null) {
            allRequirements.addAll(
                    List.of(requirements)
            );
        }

        return new FarmingDefinition(
                requireId("craftscope:acquisition/farming/" + path),
                starter,
                output,
                minimum,
                maximum,
                displayName,
                priority,
                allRequirements
        );
    }

    private static CraftScopeResourceAmount rangedByproduct(
            Item item,
            long minimum,
            long maximum
    ) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        /*
         * amount=1 is a stable scaling unit even when minimum=0.
         * The explicit min/max/expected metadata carries the real
         * harvest range.
         */
        double expected =
                (
                        (double) minimum
                                + (double) maximum
                ) / 2.0D;

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                1,
                "",
                false,
                1.0D,
                List.of(id),
                minimum,
                maximum,
                expected
        );
    }

    private static CraftScopeResourceAmount chanceItem(
            Item item, long amount, double chance
    ) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                amount,
                "",
                false,
                chance,
                List.of(id)
        );
    }

    private static CraftScopeProcessRequirement anyHoe() {
        return itemRequirement(
                CraftScopeRequirementKind.TOOL,
                "Any Hoe (to prepare farmland)",
                Items.WOODEN_HOE, Items.GOLDEN_HOE, Items.STONE_HOE,
                Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE
        );
    }

    private static CraftScopeProcessRequirement environment(String displayName) {
        return new CraftScopeProcessRequirement(
                CraftScopeRequirementKind.ENVIRONMENT,
                null,
                Component.literal(displayName),
                1,
                ""
        );
    }

    private static CraftScopeProcessRequirement environmentItem(
            String displayName, Item item
    ) {
        return itemRequirement(
                CraftScopeRequirementKind.ENVIRONMENT,
                displayName,
                item
        );
    }

    private static CraftScopeProcessRequirement environmentItems(
            String displayName, Item... items
    ) {
        return itemRequirement(
                CraftScopeRequirementKind.ENVIRONMENT,
                displayName,
                items
        );
    }

    private static CraftScopeProcessRequirement itemRequirement(
            CraftScopeRequirementKind kind,
            String displayName,
            Item... items
    ) {
        List<ResourceLocation> ids = new ArrayList<>();

        if (items != null) {
            for (Item item : items) {
                if (item == null || item == Items.AIR) {
                    continue;
                }

                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }

        ResourceLocation representativeId =
                ids.isEmpty() ? null : ids.getFirst();

        return new CraftScopeProcessRequirement(
                kind,
                representativeId,
                Component.literal(displayName),
                1,
                "",
                ids
        );
    }

    private static ResourceLocation requireId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid resource location: " + value
            );
        }
        return id;
    }

    private record FarmingDefinition(
            ResourceLocation routeId,
            Item starterItem,
            Item outputItem,
            long minimumOutput,
            long maximumOutput,
            String displayName,
            int priority,
            List<CraftScopeProcessRequirement> requirements
    ) {
        private FarmingDefinition {
            requirements = requirements == null
                    ? List.of()
                    : List.copyOf(requirements);
        }
    }
}
