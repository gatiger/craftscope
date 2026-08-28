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
 * Baseline production math intentionally represents ordinary,
 * unenchanted Java Edition drops. Looting remains outside the core
 * production model and can later be presented by CraftScope's
 * separate enchantment-guide system.
 *
 * This provider supports two distinct drop shapes:
 *
 *   RANGE
 *     Natural integer ranges such as:
 *       Creeper -> 0-2 Gunpowder
 *       Squid   -> 1-3 Ink Sacs
 *
 *   CHANCE
 *     Explicit independent probabilities such as:
 *       Shulker    -> 1 Shell at 50%
 *       Rabbit     -> 1 Rabbit's Foot at 10%
 *       Magma Cube -> 1 Magma Cream at 25%
 *
 * A drop can also carry target-specific acquisition requirements.
 * This is important because some drops require a player/tamed-wolf
 * kill while other ordinary drops from the same mob do not.
 *
 * Example:
 *
 *   Drowned -> Rotten Flesh
 *       no special kill requirement
 *
 *   Drowned -> Copper Ingot
 *       player or tamed-wolf kill required
 *
 * The requirement is therefore attached to the Copper Ingot drop,
 * not globally to every Drowned route.
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
                                    rangeDrop(
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
                                    rangeDrop(
                                            Items.BONE,
                                            0,
                                            2
                                    ),
                                    rangeDrop(
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
                                    rangeDrop(
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
                                    rangeDrop(
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
                                    rangeDrop(
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
                                    rangeDrop(
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
                                    rangeDrop(
                                            Items.LEATHER,
                                            0,
                                            2
                                    ),
                                    rangeDrop(
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
                                    rangeDrop(
                                            Items.FEATHER,
                                            0,
                                            2
                                    ),
                                    rangeDrop(
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
                                    rangeDrop(
                                            Items.PORKCHOP,
                                            1,
                                            3
                                    )
                            )
                    ),

                    /*
                     * Expanded ordinary mob acquisition coverage.
                     */
                    mob(
                            "ghast",
                            "Ghast",
                            Items.GHAST_SPAWN_EGG,
                            BASE_PRIORITY - 90,
                            drops(
                                    rangeDrop(
                                            Items.GUNPOWDER,
                                            0,
                                            2
                                    ),
                                    rangeDrop(
                                            Items.GHAST_TEAR,
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "slime",
                            "Small Slime",
                            Items.SLIME_SPAWN_EGG,
                            BASE_PRIORITY - 100,
                            List.of(
                                    otherRequirement(
                                            "Size 1 (small) Slime"
                                    )
                            ),
                            drops(
                                    rangeDrop(
                                            Items.SLIME_BALL,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "magma_cube",
                            "Magma Cube",
                            Items.MAGMA_CUBE_SPAWN_EGG,
                            BASE_PRIORITY - 110,
                            List.of(
                                    otherRequirement(
                                            "Medium or Large Magma Cube"
                                    )
                            ),
                            drops(
                                    chanceDrop(
                                            Items.MAGMA_CREAM,
                                            1,
                                            0.25D
                                    )
                            )
                    ),
                    mob(
                            "squid",
                            "Squid",
                            Items.SQUID_SPAWN_EGG,
                            BASE_PRIORITY - 120,
                            drops(
                                    rangeDrop(
                                            Items.INK_SAC,
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "glow_squid",
                            "Glow Squid",
                            Items.GLOW_SQUID_SPAWN_EGG,
                            BASE_PRIORITY - 130,
                            drops(
                                    rangeDrop(
                                            Items.GLOW_INK_SAC,
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "rabbit",
                            "Rabbit",
                            Items.RABBIT_SPAWN_EGG,
                            BASE_PRIORITY - 140,
                            drops(
                                    rangeDrop(
                                            Items.RABBIT_HIDE,
                                            0,
                                            1
                                    ),
                                    rangeDrop(
                                            Items.RABBIT,
                                            0,
                                            1
                                    ),
                                    chanceDrop(
                                            Items.RABBIT_FOOT,
                                            1,
                                            0.10D,
                                            otherRequirement(
                                                    "Player kill"
                                            )
                                    )
                            )
                    ),
                    mob(
                            "phantom",
                            "Phantom",
                            Items.PHANTOM_SPAWN_EGG,
                            BASE_PRIORITY - 150,
                            drops(
                                    rangeDrop(
                                            Items.PHANTOM_MEMBRANE,
                                            0,
                                            1,
                                            otherRequirement(
                                                    "Player or tamed-wolf kill"
                                            )
                                    )
                            )
                    ),
                    mob(
                            "shulker",
                            "Shulker",
                            Items.SHULKER_SPAWN_EGG,
                            BASE_PRIORITY - 160,
                            drops(
                                    chanceDrop(
                                            Items.SHULKER_SHELL,
                                            1,
                                            0.50D
                                    )
                            )
                    ),
                    mob(
                            "iron_golem",
                            "Iron Golem",
                            Items.IRON_GOLEM_SPAWN_EGG,
                            BASE_PRIORITY - 170,
                            drops(
                                    rangeDrop(
                                            Items.IRON_INGOT,
                                            3,
                                            5
                                    ),
                                    rangeDrop(
                                            Items.POPPY,
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "drowned",
                            "Drowned",
                            Items.DROWNED_SPAWN_EGG,
                            BASE_PRIORITY - 180,
                            drops(
                                    rangeDrop(
                                            Items.ROTTEN_FLESH,
                                            0,
                                            2
                                    ),
                                    chanceDrop(
                                            Items.COPPER_INGOT,
                                            1,
                                            0.11D,
                                            otherRequirement(
                                                    "Player or tamed-wolf kill"
                                            )
                                    )
                            )
                    ),
                    mob(
                            "guardian",
                            "Guardian",
                            Items.GUARDIAN_SPAWN_EGG,
                            BASE_PRIORITY - 190,
                            drops(
                                    rangeDrop(
                                            Items.PRISMARINE_SHARD,
                                            0,
                                            2
                                    ),
                                    chanceDrop(
                                            Items.PRISMARINE_CRYSTALS,
                                            1,
                                            0.40D
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

            DropDefinition targetDrop =
                    findTargetDrop(
                            definition,
                            target
                    );

            if (targetDrop == null) {
                continue;
            }

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

            requirements.addAll(
                    targetDrop.targetRequirements()
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

            outputs.add(
                    buildOutput(
                            drop
                    )
            );
        }

        return List.copyOf(
                outputs
        );
    }

    private static CraftScopeResourceAmount buildOutput(
            DropDefinition drop
    ) {
        ItemStack stack =
                new ItemStack(
                        drop.item()
                );

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        drop.item()
                );

        if (drop.mode()
                == DropMode.CHANCE) {

            return new CraftScopeResourceAmount(
                    CraftScopeResourceKind.ITEM,
                    id,
                    stack.getHoverName().copy(),
                    drop.amount(),
                    "",
                    false,
                    drop.chance(),
                    List.of(
                            id
                    )
            );
        }

        long nominalAmount =
                drop.minimum() > 0L
                        ? drop.minimum()
                        : 1L;

        double expected =
                (
                        (double) drop.minimum()
                                + (double) drop.maximum()
                ) / 2.0D;

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                nominalAmount,
                "",
                false,
                1.0D,
                List.of(
                        id
                ),
                drop.minimum(),
                drop.maximum(),
                expected
        );
    }

    private static DropDefinition findTargetDrop(
            MobDefinition definition,
            ItemStack target
    ) {
        ResourceLocation targetId =
                BuiltInRegistries.ITEM.getKey(
                        target.getItem()
                );

        for (DropDefinition drop :
                definition.drops()) {

            ResourceLocation dropId =
                    BuiltInRegistries.ITEM.getKey(
                            drop.item()
                    );

            if (dropId.equals(
                    targetId
            )) {

                return drop;
            }
        }

        return null;
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

    private static DropDefinition rangeDrop(
            Item item,
            long minimum,
            long maximum,
            CraftScopeProcessRequirement... targetRequirements
    ) {
        return new DropDefinition(
                item,
                DropMode.RANGE,
                minimum,
                maximum,
                0L,
                1.0D,
                targetRequirements == null
                        ? List.of()
                        : List.of(
                        targetRequirements
                )
        );
    }

    private static DropDefinition chanceDrop(
            Item item,
            long amount,
            double chance,
            CraftScopeProcessRequirement... targetRequirements
    ) {
        return new DropDefinition(
                item,
                DropMode.CHANCE,
                0L,
                amount,
                amount,
                chance,
                targetRequirements == null
                        ? List.of()
                        : List.of(
                        targetRequirements
                )
        );
    }

    private static MobDefinition mob(
            String path,
            String displayName,
            Item iconItem,
            int priority,
            List<DropDefinition> drops
    ) {
        return mob(
                path,
                displayName,
                iconItem,
                priority,
                List.of(),
                drops
        );
    }

    private static MobDefinition mob(
            String path,
            String displayName,
            Item iconItem,
            int priority,
            List<CraftScopeProcessRequirement> extraRequirements,
            List<DropDefinition> drops
    ) {
        return new MobDefinition(
                path,
                displayName,
                iconItem,
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
                        definition.iconItem()
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

    private enum DropMode {
        RANGE,
        CHANCE
    }

    private record DropDefinition(
            Item item,
            DropMode mode,
            long minimum,
            long maximum,
            long amount,
            double chance,
            List<CraftScopeProcessRequirement> targetRequirements
    ) {
        private DropDefinition {
            if (item == null
                    || item == Items.AIR) {

                throw new IllegalArgumentException(
                        "Mob drop item cannot be empty"
                );
            }

            if (mode == null) {
                throw new IllegalArgumentException(
                        "Mob drop mode cannot be null"
                );
            }

            if (minimum < 0L
                    || maximum < minimum) {

                throw new IllegalArgumentException(
                        "Invalid mob drop range"
                );
            }

            if (mode == DropMode.CHANCE) {
                if (amount <= 0L) {
                    throw new IllegalArgumentException(
                            "Chance drop amount must be positive"
                    );
                }

                if (Double.isNaN(chance)
                        || Double.isInfinite(chance)
                        || chance < 0.0D
                        || chance > 1.0D) {

                    throw new IllegalArgumentException(
                            "Chance drop probability must be between 0 and 1"
                    );
                }
            }

            targetRequirements =
                    targetRequirements == null
                            ? List.of()
                            : List.copyOf(
                            targetRequirements
                    );
        }
    }

    private record MobDefinition(
            String path,
            String displayName,
            Item iconItem,
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

            if (iconItem == null
                    || iconItem == Items.AIR) {

                throw new IllegalArgumentException(
                        "Mob process icon cannot be empty"
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
