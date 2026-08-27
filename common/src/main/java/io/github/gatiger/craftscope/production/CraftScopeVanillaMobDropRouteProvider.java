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
 * Vanilla mob-drop acquisition routes.
 *
 * This first pass intentionally models ordinary, unenchanted Java
 * Edition drops only. Looting is NOT included in the production
 * math, matching CraftScope's rule that enchantments belong in a
 * separate visual-guide system rather than changing baseline route
 * calculations.
 *
 * The provider uses explicit min/max/expected metadata because mob
 * drops often include zero:
 *
 *   Creeper   -> Gunpowder 0-2, expected 1
 *   Enderman  -> Ender Pearl 0-1, expected 0.5
 *
 * A nominal amount of 1 is used as the scaling unit even when the
 * minimum is zero. That keeps expected-run planning and scaled
 * Process Diagram ranges well-defined.
 */
public final class CraftScopeVanillaMobDropRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:vanilla_mob_drops";

    private static final String SOURCE_MOD_ID =
            "minecraft";

    private static final Component SOURCE_MOD_NAME =
            Component.literal(
                    "Minecraft"
            );

    private static final int BASE_PRIORITY =
            3600;

    private static final List<MobDefinition> DEFINITIONS =
            List.of(
                    mob(
                            "creeper",
                            "Creeper",
                            Items.CREEPER_SPAWN_EGG,
                            BASE_PRIORITY,
                            drops(
                                    drop(
                                            Items.GUNPOWDER,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "skeleton",
                            "Skeleton",
                            Items.SKELETON_SPAWN_EGG,
                            BASE_PRIORITY - 10,
                            drops(
                                    drop(
                                            Items.BONE,
                                            0,
                                            2
                                    ),
                                    drop(
                                            Items.ARROW,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "zombie",
                            "Zombie",
                            Items.ZOMBIE_SPAWN_EGG,
                            BASE_PRIORITY - 20,
                            drops(
                                    drop(
                                            Items.ROTTEN_FLESH,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "spider",
                            "Spider",
                            Items.SPIDER_SPAWN_EGG,
                            BASE_PRIORITY - 30,
                            drops(
                                    drop(
                                            Items.STRING,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "enderman",
                            "Enderman",
                            Items.ENDERMAN_SPAWN_EGG,
                            BASE_PRIORITY - 40,
                            drops(
                                    drop(
                                            Items.ENDER_PEARL,
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "blaze",
                            "Blaze",
                            Items.BLAZE_SPAWN_EGG,
                            BASE_PRIORITY - 50,
                            List.of(
                                    otherRequirement(
                                            "Player or tamed-wolf kill"
                                    )
                            ),
                            drops(
                                    drop(
                                            Items.BLAZE_ROD,
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "cow",
                            "Cow",
                            Items.COW_SPAWN_EGG,
                            BASE_PRIORITY - 60,
                            drops(
                                    drop(
                                            Items.LEATHER,
                                            0,
                                            2
                                    ),
                                    drop(
                                            Items.BEEF,
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "chicken",
                            "Chicken",
                            Items.CHICKEN_SPAWN_EGG,
                            BASE_PRIORITY - 70,
                            drops(
                                    drop(
                                            Items.FEATHER,
                                            0,
                                            2
                                    ),
                                    drop(
                                            Items.CHICKEN,
                                            1,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "pig",
                            "Pig",
                            Items.PIG_SPAWN_EGG,
                            BASE_PRIORITY - 80,
                            drops(
                                    drop(
                                            Items.PORKCHOP,
                                            1,
                                            3
                                    )
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

        for (MobDefinition definition :
                DEFINITIONS) {

            List<CraftScopeResourceAmount> outputs =
                    buildOutputs(
                            definition
                    );

            CraftScopeResourceAmount targetOutput =
                    findTargetOutput(
                            outputs,
                            target
                    );

            if (targetOutput == null) {
                continue;
            }

            ResourceLocation processId =
                    requireId(
                            "craftscope:mob_drop/"
                                    + definition.path()
                    );

            List<CraftScopeProcessRequirement> requirements =
                    new ArrayList<>();

            requirements.add(
                    otherRequirement(
                            "Source mob: "
                                    + definition.displayName()
                    )
            );

            requirements.addAll(
                    definition.extraRequirements()
            );

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            processId,
                            Component.literal(
                                    "Kill "
                                            + definition.displayName()
                            ),
                            List.of(),
                            requirements
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            "craftscope:mob_drop:"
                                    + definition.path()
                                    + ":step",
                            Component.literal(
                                    "Kill "
                                            + definition.displayName()
                            ),
                            List.of(),
                            outputs,
                            List.of(
                                    method
                            )
                    );

            routes.add(
                    new CraftScopeProductionRoute(
                            requireId(
                                    "craftscope:acquisition/mob/"
                                            + definition.path()
                                            + "/"
                                            + targetOutput
                                            .id()
                                            .getPath()
                            ),
                            SOURCE_MOD_ID,
                            SOURCE_MOD_NAME,
                            Component.literal(
                                    definition.displayName()
                                            + " Drops"
                            ),
                            targetOutput,
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

    private static List<CraftScopeResourceAmount> buildOutputs(
            MobDefinition definition
    ) {
        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (DropDefinition drop :
                definition.drops()) {

            ItemStack stack =
                    new ItemStack(
                            drop.item()
                    );

            ResourceLocation id =
                    BuiltInRegistries.ITEM.getKey(
                            drop.item()
                    );

            double expected =
                    (
                            (double) drop.minimum()
                                    + (double) drop.maximum()
                    ) / 2.0D;

            outputs.add(
                    new CraftScopeResourceAmount(
                            CraftScopeResourceKind.ITEM,
                            id,
                            stack.getHoverName().copy(),
                            1,
                            "",
                            false,
                            1.0D,
                            List.of(
                                    id
                            ),
                            drop.minimum(),
                            drop.maximum(),
                            expected
                    )
            );
        }

        return List.copyOf(
                outputs
        );
    }

    private static CraftScopeResourceAmount findTargetOutput(
            List<CraftScopeResourceAmount> outputs,
            ItemStack target
    ) {
        ResourceLocation targetId =
                BuiltInRegistries.ITEM.getKey(
                        target.getItem()
                );

        for (CraftScopeResourceAmount output :
                outputs) {

            if (output.id().equals(
                    targetId
            )) {

                return output;
            }
        }

        return null;
    }

    private static List<DropDefinition> drops(
            DropDefinition... drops
    ) {
        return List.of(
                drops
        );
    }

    private static DropDefinition drop(
            Item item,
            long minimum,
            long maximum
    ) {
        return new DropDefinition(
                item,
                minimum,
                maximum
        );
    }

    private static MobDefinition mob(
            String path,
            String displayName,
            Item spawnEgg,
            int priority,
            List<DropDefinition> drops
    ) {
        return mob(
                path,
                displayName,
                spawnEgg,
                priority,
                List.of(),
                drops
        );
    }

    private static MobDefinition mob(
            String path,
            String displayName,
            Item spawnEgg,
            int priority,
            List<CraftScopeProcessRequirement> extraRequirements,
            List<DropDefinition> drops
    ) {
        return new MobDefinition(
                path,
                displayName,
                spawnEgg,
                priority,
                extraRequirements,
                drops
        );
    }

    private static CraftScopeProcessRequirement otherRequirement(
            String displayName
    ) {
        return new CraftScopeProcessRequirement(
                CraftScopeRequirementKind.OTHER,
                null,
                Component.literal(
                        displayName
                ),
                1,
                ""
        );
    }

    public static ItemStack getProcessIcon(
            ResourceLocation processId
    ) {
        if (processId == null
                || !"craftscope".equals(
                processId.getNamespace()
        )
                || !processId
                .getPath()
                .startsWith(
                        "mob_drop/"
                )) {

            return ItemStack.EMPTY;
        }

        String path =
                processId
                        .getPath()
                        .substring(
                                "mob_drop/".length()
                        );

        for (MobDefinition definition :
                DEFINITIONS) {

            if (definition.path().equals(
                    path
            )) {

                return new ItemStack(
                        definition.spawnEgg()
                );
            }
        }

        return ItemStack.EMPTY;
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

    private record DropDefinition(
            Item item,
            long minimum,
            long maximum
    ) {
        private DropDefinition {
            if (item == null
                    || item == Items.AIR) {

                throw new IllegalArgumentException(
                        "Mob drop item cannot be empty"
                );
            }

            if (minimum < 0L
                    || maximum < minimum) {

                throw new IllegalArgumentException(
                        "Invalid mob drop range"
                );
            }
        }
    }

    private record MobDefinition(
            String path,
            String displayName,
            Item spawnEgg,
            int priority,
            List<CraftScopeProcessRequirement> extraRequirements,
            List<DropDefinition> drops
    ) {
        private MobDefinition {
            if (path == null
                    || path.isBlank()) {

                throw new IllegalArgumentException(
                        "Mob path cannot be blank"
                );
            }

            if (displayName == null
                    || displayName.isBlank()) {

                throw new IllegalArgumentException(
                        "Mob display name cannot be blank"
                );
            }

            if (spawnEgg == null
                    || spawnEgg == Items.AIR) {

                throw new IllegalArgumentException(
                        "Mob spawn egg cannot be empty"
                );
            }

            extraRequirements =
                    extraRequirements == null
                            ? List.of()
                            : List.copyOf(
                            extraRequirements
                    );

            drops =
                    drops == null
                            ? List.of()
                            : List.copyOf(
                            drops
                    );
        }
    }
}
