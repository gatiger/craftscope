package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * Central mob-acquisition catalog.
 *
 * The important architectural point is that the production provider
 * no longer needs one Java implementation per item or per mob.
 *
 * CraftScopeVanillaMobDropRouteProvider is now only a TRANSLATOR:
 *
 *     catalog definition
 *          ↓
 *     generic production route
 *
 * The built-in definitions below are a compatibility fallback so the
 * behavior already tested by CraftScope remains available.
 *
 * Later, a server-side runtime loot-table reader can populate
 * replaceRuntimeDefinitions(...) with definitions derived from the
 * ACTUAL loaded loot tables. Runtime definitions replace the baseline
 * definition for the same entity and can also add completely new
 * modded entities without changing the provider.
 *
 * That gives us this long-term flow:
 *
 *     Loaded server loot tables
 *          ↓
 *     CraftScopeMobDropCatalog
 *          ↓
 *     one generic mob-drop provider
 *          ↓
 *     Recipe Tree / Process Diagram / Setup / Materials
 *
 * No per-item provider files are needed.
 */
public final class CraftScopeMobDropCatalog {

    private static final int BASE_PRIORITY =
            3600;

    private static final List<MobDefinition> BASELINE_DEFINITIONS =
            List.of(
                    mob(
                            "minecraft:creeper",
                            BASE_PRIORITY,
                            drops(
                                    rangeDrop(
                                            "minecraft:gunpowder",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:skeleton",
                            BASE_PRIORITY - 10,
                            drops(
                                    rangeDrop(
                                            "minecraft:bone",
                                            0,
                                            2
                                    ),
                                    rangeDrop(
                                            "minecraft:arrow",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:zombie",
                            BASE_PRIORITY - 20,
                            drops(
                                    rangeDrop(
                                            "minecraft:rotten_flesh",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:spider",
                            BASE_PRIORITY - 30,
                            drops(
                                    rangeDrop(
                                            "minecraft:string",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:enderman",
                            BASE_PRIORITY - 40,
                            drops(
                                    rangeDrop(
                                            "minecraft:ender_pearl",
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "minecraft:blaze",
                            BASE_PRIORITY - 50,
                            requirements(
                                    "Player or tamed-wolf kill"
                            ),
                            drops(
                                    rangeDrop(
                                            "minecraft:blaze_rod",
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "minecraft:cow",
                            BASE_PRIORITY - 60,
                            drops(
                                    rangeDrop(
                                            "minecraft:leather",
                                            0,
                                            2
                                    ),
                                    rangeDrop(
                                            "minecraft:beef",
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "minecraft:chicken",
                            BASE_PRIORITY - 70,
                            drops(
                                    rangeDrop(
                                            "minecraft:feather",
                                            0,
                                            2
                                    ),
                                    rangeDrop(
                                            "minecraft:chicken",
                                            1,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "minecraft:pig",
                            BASE_PRIORITY - 80,
                            drops(
                                    rangeDrop(
                                            "minecraft:porkchop",
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "minecraft:ghast",
                            BASE_PRIORITY - 90,
                            drops(
                                    rangeDrop(
                                            "minecraft:gunpowder",
                                            0,
                                            2
                                    ),
                                    rangeDrop(
                                            "minecraft:ghast_tear",
                                            0,
                                            1
                                    )
                            )
                    ),
                    mob(
                            "minecraft:slime",
                            BASE_PRIORITY - 100,
                            requirements(
                                    "Size 1 (small) Slime"
                            ),
                            drops(
                                    rangeDrop(
                                            "minecraft:slime_ball",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:magma_cube",
                            BASE_PRIORITY - 110,
                            requirements(
                                    "Medium or Large Magma Cube"
                            ),
                            drops(
                                    chanceDrop(
                                            "minecraft:magma_cream",
                                            1,
                                            0.25D
                                    )
                            )
                    ),
                    mob(
                            "minecraft:squid",
                            BASE_PRIORITY - 120,
                            drops(
                                    rangeDrop(
                                            "minecraft:ink_sac",
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "minecraft:glow_squid",
                            BASE_PRIORITY - 130,
                            drops(
                                    rangeDrop(
                                            "minecraft:glow_ink_sac",
                                            1,
                                            3
                                    )
                            )
                    ),
                    mob(
                            "minecraft:rabbit",
                            BASE_PRIORITY - 140,
                            drops(
                                    rangeDrop(
                                            "minecraft:rabbit_hide",
                                            0,
                                            1
                                    ),
                                    rangeDrop(
                                            "minecraft:rabbit",
                                            0,
                                            1
                                    ),
                                    chanceDrop(
                                            "minecraft:rabbit_foot",
                                            1,
                                            0.10D,
                                            "Player kill"
                                    )
                            )
                    ),
                    mob(
                            "minecraft:phantom",
                            BASE_PRIORITY - 150,
                            drops(
                                    rangeDrop(
                                            "minecraft:phantom_membrane",
                                            0,
                                            1,
                                            "Player or tamed-wolf kill"
                                    )
                            )
                    ),
                    mob(
                            "minecraft:shulker",
                            BASE_PRIORITY - 160,
                            drops(
                                    chanceDrop(
                                            "minecraft:shulker_shell",
                                            1,
                                            0.50D
                                    )
                            )
                    ),
                    mob(
                            "minecraft:iron_golem",
                            BASE_PRIORITY - 170,
                            drops(
                                    rangeDrop(
                                            "minecraft:iron_ingot",
                                            3,
                                            5
                                    ),
                                    rangeDrop(
                                            "minecraft:poppy",
                                            0,
                                            2
                                    )
                            )
                    ),
                    mob(
                            "minecraft:drowned",
                            BASE_PRIORITY - 180,
                            drops(
                                    rangeDrop(
                                            "minecraft:rotten_flesh",
                                            0,
                                            2
                                    ),
                                    chanceDrop(
                                            "minecraft:copper_ingot",
                                            1,
                                            0.11D,
                                            "Player or tamed-wolf kill"
                                    )
                            )
                    ),
                    mob(
                            "minecraft:guardian",
                            BASE_PRIORITY - 190,
                            drops(
                                    rangeDrop(
                                            "minecraft:prismarine_shard",
                                            0,
                                            2
                                    ),
                                    chanceDrop(
                                            "minecraft:prismarine_crystals",
                                            1,
                                            0.40D
                                    )
                            )
                    )
            );

    /*
     * Empty means "use the baseline catalog".
     *
     * Runtime entries are deliberately whole-mob definitions rather
     * than individual drops. If a datapack removes a vanilla drop,
     * the runtime definition can replace that mob completely and the
     * removed baseline drop will not leak back in.
     */
    private static volatile List<MobDefinition> runtimeDefinitions =
            List.of();

    private CraftScopeMobDropCatalog() {
    }

    public static List<MobDefinition> getDefinitions() {
        List<MobDefinition> runtime =
                runtimeDefinitions;

        if (runtime.isEmpty()) {
            return BASELINE_DEFINITIONS;
        }

        Map<ResourceLocation, MobDefinition> merged =
                new LinkedHashMap<>();

        for (MobDefinition definition :
                BASELINE_DEFINITIONS) {

            merged.put(
                    definition.entityTypeId(),
                    definition
            );
        }

        for (MobDefinition definition :
                runtime) {

            merged.put(
                    definition.entityTypeId(),
                    definition
            );
        }

        return List.copyOf(
                merged.values()
        );
    }

    public static List<MobDefinition> getBaselineDefinitions() {
        return BASELINE_DEFINITIONS;
    }

    public static List<MobDefinition> getRuntimeDefinitions() {
        return runtimeDefinitions;
    }

    public static boolean hasRuntimeDefinitions() {
        return !runtimeDefinitions.isEmpty();
    }

    public static void replaceRuntimeDefinitions(
            List<MobDefinition> definitions
    ) {
        runtimeDefinitions =
                definitions == null
                        ? List.of()
                        : List.copyOf(
                        definitions
                );
    }

    public static void clearRuntimeDefinitions() {
        runtimeDefinitions =
                List.of();
    }

    private static MobDefinition mob(
            String entityTypeId,
            int priority,
            List<DropDefinition> drops
    ) {
        return mob(
                entityTypeId,
                priority,
                List.of(),
                drops
        );
    }

    private static MobDefinition mob(
            String entityTypeId,
            int priority,
            List<String> requirements,
            List<DropDefinition> drops
    ) {
        ResourceLocation entityId =
                requireId(
                        entityTypeId
                );

        return new MobDefinition(
                entityId,
                entityId.getNamespace(),
                null,
                priority,
                requirements,
                drops
        );
    }

    private static List<String> requirements(
            String... requirements
    ) {
        return requirements == null
                ? List.of()
                : List.of(
                requirements
        );
    }

    private static List<DropDefinition> drops(
            DropDefinition... drops
    ) {
        return List.of(
                drops
        );
    }

    private static DropDefinition rangeDrop(
            String itemId,
            long minimum,
            long maximum,
            String... targetRequirements
    ) {
        return new DropDefinition(
                requireId(
                        itemId
                ),
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
            String itemId,
            long amount,
            double chance,
            String... targetRequirements
    ) {
        return new DropDefinition(
                requireId(
                        itemId
                ),
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

    public enum DropMode {
        RANGE,
        CHANCE
    }

    /*
     * iconItemId is optional.
     *
     * When null, the provider asks SpawnEggItem.byId(entityType).
     * That makes modded mobs automatically use their registered spawn
     * egg when one exists, without adding icon-specific Java code.
     */
    public record MobDefinition(
            ResourceLocation entityTypeId,
            String sourceModId,
            ResourceLocation iconItemId,
            int priority,
            List<String> requirements,
            List<DropDefinition> drops
    ) {
        public MobDefinition {
            Objects.requireNonNull(
                    entityTypeId,
                    "entityTypeId"
            );

            sourceModId =
                    sourceModId == null
                            || sourceModId.isBlank()
                            ? entityTypeId.getNamespace()
                            : sourceModId;

            requirements =
                    requirements == null
                            ? List.of()
                            : List.copyOf(
                            requirements
                    );

            drops =
                    drops == null
                            ? List.of()
                            : List.copyOf(
                            drops
                    );
        }
    }

    public record DropDefinition(
            ResourceLocation itemId,
            DropMode mode,
            long minimum,
            long maximum,
            long amount,
            double chance,
            List<String> targetRequirements
    ) {
        public DropDefinition {
            Objects.requireNonNull(
                    itemId,
                    "itemId"
            );

            Objects.requireNonNull(
                    mode,
                    "mode"
            );

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
                            "Chance must be between 0 and 1"
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
}
