package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * Central mob-acquisition catalog.
 *
 * The production provider translates catalog definitions into normal
 * CraftScope production routes.
 *
 * Runtime definitions replace the complete baseline definition for
 * an entity so removed or modified server drops cannot leak through
 * from the built-in fallback catalog.
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

    /*
     * transformedItemId represents an alternate item produced by a
     * conditional loot transformation.
     *
     * Example:
     *
     *     minecraft:beef
     *          ↓ furnace_smelt when condition is true
     *     minecraft:cooked_beef
     *
     * This is NOT another independent drop. It is an alternate form
     * of the same drop.
     *
     * baseTransformationRequirements describe what must be true for
     * the normal itemId form to remain unchanged.
     *
     * transformationRequirements describe what must be true for the
     * transformedItemId form to be produced.
     */
    public record DropDefinition(
            ResourceLocation itemId,
            DropMode mode,
            long minimum,
            long maximum,
            long amount,
            double chance,
            List<String> targetRequirements,
            ResourceLocation transformedItemId,
            List<String> baseTransformationRequirements,
            List<String> transformationRequirements,
            CraftScopeItemIdentity itemIdentity,
            CraftScopeItemIdentity transformedItemIdentity
    ) {

        /*
         * Backward-compatible transformation constructor.
         *
         * Existing furnace-smelt processing does not yet need an
         * explicit component identity, so its existing constructor
         * remains valid.
         */
        public DropDefinition(
                ResourceLocation itemId,
                DropMode mode,
                long minimum,
                long maximum,
                long amount,
                double chance,
                List<String> targetRequirements,
                ResourceLocation transformedItemId,
                List<String> baseTransformationRequirements,
                List<String> transformationRequirements
        ) {
            this(
                    itemId,
                    mode,
                    minimum,
                    maximum,
                    amount,
                    chance,
                    targetRequirements,
                    transformedItemId,
                    baseTransformationRequirements,
                    transformationRequirements,
                    null,
                    null
            );
        }
        /*
         * Backward-compatible constructor.
         *
         * Existing baseline definitions, integrations, interpreter
         * code, and runtime factories can continue creating normal
         * drops without knowing anything about transformations.
         */
        public DropDefinition(
                ResourceLocation itemId,
                DropMode mode,
                long minimum,
                long maximum,
                long amount,
                double chance,
                List<String> targetRequirements
        ) {
            this(
                    itemId,
                    mode,
                    minimum,
                    maximum,
                    amount,
                    chance,
                    targetRequirements,
                    null,
                    List.of(),
                    List.of()
            );
        }

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

            if (mode == DropMode.CHANCE
                    && amount <= 0L) {

                throw new IllegalArgumentException(
                        "Chance drop amount must be positive"
                );
            }

            /*
             * RANGE drops can also be probabilistic, so chance is
             * validated for every drop mode.
             */
            if (Double.isNaN(
                    chance
            )
                    || Double.isInfinite(
                    chance
            )
                    || chance < 0.0D
                    || chance > 1.0D) {

                throw new IllegalArgumentException(
                        "Chance must be between 0 and 1"
                );
            }

            if (itemIdentity != null
                    && !itemId.equals(
                    itemIdentity.itemId()
            )) {

                throw new IllegalArgumentException(
                        "Mob drop item identity does not match item ID"
                );
            }

            if (transformedItemIdentity != null) {

                if (transformedItemId == null) {

                    throw new IllegalArgumentException(
                            "Transformed item identity requires a transformed item"
                    );
                }

                if (!transformedItemId.equals(
                        transformedItemIdentity.itemId()
                )) {

                    throw new IllegalArgumentException(
                            "Transformed item identity does not match transformed item ID"
                    );
                }
            }

            targetRequirements =
                    targetRequirements == null
                            ? List.of()
                            : List.copyOf(
                            targetRequirements
                    );

            baseTransformationRequirements =
                    baseTransformationRequirements == null
                            ? List.of()
                            : List.copyOf(
                            baseTransformationRequirements
                    );

            transformationRequirements =
                    transformationRequirements == null
                            ? List.of()
                            : List.copyOf(
                            transformationRequirements
                    );

            /*
             * A drop without an alternate item cannot legitimately
             * have transformation-specific requirements.
             */
            if (transformedItemId == null
                    && (
                    !baseTransformationRequirements.isEmpty()
                            || !transformationRequirements.isEmpty()
            )) {

                throw new IllegalArgumentException(
                        "Transformation requirements require a transformed item"
                );
            }
        }

        public boolean hasTransformation() {
            return transformedItemId != null;
        }
    }
}