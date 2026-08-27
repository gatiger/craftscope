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
 * basic materials are normally obtained by interacting with the
 * world rather than through RecipeManager:
 *
 *   Stone in the world
 *       -> Mining Stone
 *       -> Cobblestone
 *
 *   Clay Block in the world
 *       -> Mining Clay
 *       -> Clay Ball x4
 *
 * This provider is deliberately conservative. It starts with
 * deterministic vanilla drops whose default behavior is stable and
 * unambiguous. Tool-enchantment-sensitive drops (Fortune, Silk
 * Touch), mob loot, biome-dependent generation, crops, etc. belong
 * in later acquisition providers rather than being guessed here.
 *
 * The provider uses normal ITEM inputs so Recipe Tree and Total
 * Materials can show the world block that must be found/mined. The
 * production method itself communicates that this is acquisition,
 * not crafting.
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

    private static final List<AcquisitionDefinition> DEFINITIONS =
            List.of(
                    new AcquisitionDefinition(
                            requireId(
                                    "craftscope:acquisition/mining/stone_to_cobblestone"
                            ),
                            Items.STONE,
                            1,
                            Items.COBBLESTONE,
                            1,
                            "Mining Stone",
                            1600,
                            List.of(
                                    new CraftScopeProcessRequirement(
                                            CraftScopeRequirementKind.TOOL,
                                            null,
                                            Component.literal(
                                                    "Pickaxe (no Silk Touch)"
                                            ),
                                            1,
                                            ""
                                    )
                            )
                    ),
                    new AcquisitionDefinition(
                            requireId(
                                    "craftscope:acquisition/mining/deepslate_to_cobbled_deepslate"
                            ),
                            Items.DEEPSLATE,
                            1,
                            Items.COBBLED_DEEPSLATE,
                            1,
                            "Mining Deepslate",
                            1590,
                            List.of(
                                    new CraftScopeProcessRequirement(
                                            CraftScopeRequirementKind.TOOL,
                                            null,
                                            Component.literal(
                                                    "Pickaxe (no Silk Touch)"
                                            ),
                                            1,
                                            ""
                                    )
                            )
                    ),
                    new AcquisitionDefinition(
                            requireId(
                                    "craftscope:acquisition/mining/clay_to_clay_balls"
                            ),
                            Items.CLAY,
                            1,
                            Items.CLAY_BALL,
                            4,
                            "Mining Clay",
                            1580,
                            List.of()
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
                            MINING_PROCESS_ID,
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
