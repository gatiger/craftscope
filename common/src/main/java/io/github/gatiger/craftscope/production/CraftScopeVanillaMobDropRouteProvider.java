package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * Converts CraftScopeMobDropCatalog entries into selectable
 * CraftScope production routes.
 *
 * Mob/drop data lives in CraftScopeMobDropCatalog. This provider only
 * translates those definitions into CraftScope's normal production
 * model.
 *
 * Conditional item transformations are represented as separate kill
 * outcomes.
 *
 * Example:
 *
 * Cow:
 *
 *   Normal outcome
 *       Leather
 *       Raw Beef
 *
 *   Smelted-loot outcome
 *       Leather
 *       Cooked Beef
 *
 * These are deliberately separate routes because one cow does not
 * drop both Raw Beef and Cooked Beef at the same time.
 */
public final class CraftScopeVanillaMobDropRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:vanilla_mob_drops";

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

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                CraftScopeMobDropCatalog.getDefinitions()) {

            addRouteIfMatching(
                    definition,
                    target,
                    OutcomeVariant.BASE,
                    routes
            );

            if (hasTransformation(
                    definition
            )) {

                addRouteIfMatching(
                        definition,
                        target,
                        OutcomeVariant.TRANSFORMED,
                        routes
                );
            }
        }

        return List.copyOf(
                routes
        );
    }

    private static void addRouteIfMatching(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ItemStack target,
            OutcomeVariant variant,
            List<CraftScopeProductionRoute> routes
    ) {
        if (target == null
                || target.isEmpty()) {

            return;
        }

        List<CraftScopeMobDropCatalog.DropDefinition> targetDrops =
                findTargetDrops(
                        definition,
                        target,
                        variant
                );

        for (CraftScopeMobDropCatalog.DropDefinition targetDrop :
                targetDrops) {

            addRouteForDrop(
                    definition,
                    target,
                    variant,
                    targetDrop,
                    routes
            );
        }
    }

    private static void addRouteForDrop(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ItemStack target,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.DropDefinition targetDrop,
            List<CraftScopeProductionRoute> routes
    ) {
        if (definition == null
                || target == null
                || target.isEmpty()
                || targetDrop == null) {

            return;
        }

        List<CraftScopeResourceAmount> outputs =
                buildOutputs(
                        definition,
                        target,
                        variant,
                        targetDrop
                );

        CraftScopeResourceAmount targetOutput =
                findTargetOutput(
                        outputs,
                        target
                );

        if (targetOutput == null) {
            return;
        }

        Component mobName =
                getMobDisplayName(
                        definition
                );

        ResourceLocation processId =
                getProcessId(
                        definition
                );

        List<CraftScopeProcessRequirement> requirements =
                new ArrayList<>();

        requirements.add(
                otherRequirement(
                        "Source mob: "
                                + mobName.getString()
                )
        );

        addRequirements(
                requirements,
                definition.requirements()
        );

        addRequirements(
                requirements,
                getVariantRequirements(
                        definition,
                        variant
                )
        );

        addRequirements(
                requirements,
                targetDrop.targetRequirements()
        );

        String sourceModId =
                definition.sourceModId();

        String actionName =
                getActionName(
                        mobName,
                        variant,
                        definition,
                        targetDrop
                );

        CraftScopeProductionMethod method =
                new CraftScopeProductionMethod(
                        sourceModId,
                        processId,
                        Component.literal(
                                actionName
                        ),
                        List.of(),
                        requirements
                );

        CraftScopeProductionStep step =
                new CraftScopeProductionStep(
                        buildStepId(
                                definition,
                                variant,
                                targetDrop
                        ),
                        Component.literal(
                                actionName
                        ),
                        List.of(),
                        outputs,
                        List.of(
                                method
                        )
                );

        routes.add(
                new CraftScopeProductionRoute(
                        buildRouteId(
                                definition,
                                targetOutput.id(),
                                variant,
                                targetDrop
                        ),
                        sourceModId,
                        Component.literal(
                                formatSourceName(
                                        sourceModId
                                )
                        ),
                        Component.literal(
                                getRouteName(
                                        mobName,
                                        variant,
                                        definition,
                                        targetDrop
                                )
                        ),
                        targetOutput,
                        List.of(
                                step
                        ),
                        definition.priority()
                )
        );
    }

    private static List<CraftScopeMobDropCatalog.DropDefinition>
    findTargetDrops(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ItemStack target,
            OutcomeVariant variant
    ) {
        if (definition == null
                || target == null
                || target.isEmpty()) {

            return List.of();
        }

        ResourceLocation targetId =
                BuiltInRegistries.ITEM.getKey(
                        target.getItem()
                );

        List<CraftScopeMobDropCatalog.DropDefinition> matches =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            ResourceLocation effectiveId =
                    getEffectiveItemId(
                            drop,
                            variant
                    );

            if (!targetId.equals(
                    effectiveId
            )) {

                continue;
            }

            CraftScopeItemIdentity identity =
                    getEffectiveItemIdentity(
                            drop,
                            variant
                    );

            /*
             * Component-aware drops must match the exact target
             * ItemStack identity.
             */
            if (identity != null) {

                if (identity.matches(
                        target
                )) {

                    matches.add(
                            drop
                    );
                }

                continue;
            }

            /*
             * Componentless definitions represent only the ordinary
             * componentless form of an item.
             */
            if (target
                    .getComponentsPatch()
                    .isEmpty()) {

                matches.add(
                        drop
                );
            }
        }

        return List.copyOf(
                matches
        );
    }
    private static List<CraftScopeResourceAmount> buildOutputs(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ItemStack target,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        ResourceLocation targetId =
                target == null
                        || target.isEmpty()
                        ? null
                        : BuiltInRegistries.ITEM.getKey(
                        target.getItem()
                );

        boolean guardianCodTarget =
                isGuardianCodTarget(
                        definition,
                        targetDrop
                );

        boolean guardianCodOutputAdded =
                false;

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            ResourceLocation effectiveItemId =
                    getEffectiveItemId(
                            drop,
                            variant
                    );

            /*
             * Guardian/Elder Guardian Cod has an ordinary contribution
             * plus an additional independent player-kill contribution.
             *
             * The ordinary route must not claim outputs that require
             * the player-kill branch.
             */
            if (guardianCodTarget
                    && !isOutputCompatibleWithTarget(
                    targetDrop,
                    drop
            )) {

                continue;
            }

            /*
             * Some mob loot entries represent mutually-exclusive
             * kill outcomes rather than simultaneous co-products.
             *
             * Vanilla Magma Cube:
             *
             *     not killed by Frog
             *         -> Magma Cream
             *
             *     killed by Warm Frog
             *         -> Pearlescent Froglight
             *
             *     killed by Cold Frog
             *         -> Verdant Froglight
             *
             *     killed by Temperate Frog
             *         -> Ochre Froglight
             *
             * Only outputs compatible with the selected target's
             * branch belong in this route.
             */
            if (isMutuallyExclusiveFrogOutcome(
                    definition,
                    targetDrop,
                    drop
            )) {

                continue;
            }

            /*
             * Guardian/Elder Guardian Cod can come from two independent
             * pools on a player/tamed-wolf kill.
             *
             * Represent those as one resource with the correct:
             *
             *     chance of at least one
             *     expected amount
             *     minimum
             *     maximum
             *
             * rather than displaying two separate Cod output rows.
             */
            if (guardianCodTarget
                    && targetId != null
                    && targetId.equals(
                    effectiveItemId
            )) {

                if (!guardianCodOutputAdded) {

                    CraftScopeResourceAmount guardianCodOutput =
                            buildGuardianCodOutput(
                                    definition,
                                    targetDrop,
                                    variant,
                                    targetId
                            );

                    if (guardianCodOutput != null) {

                        outputs.add(
                                guardianCodOutput
                        );
                    }

                    guardianCodOutputAdded =
                            true;
                }

                continue;
            }

            /*
             * Component variants of the target item are mutually
             * exclusive outcomes.
             *
             * Example:
             *
             * Ominous Bottle I-V are five possible amplifier results
             * from one Pillager Captain drop. When this route was
             * requested for Bottle I, do not show II-V as additional
             * co-products in the output pane.
             *
             * Outputs with different registry IDs remain untouched.
             */
            if (targetId != null
                    && targetId.equals(
                    effectiveItemId
            )) {

                CraftScopeItemIdentity outputIdentity =
                        getEffectiveItemIdentity(
                                drop,
                                variant
                        );

                if (outputIdentity != null) {

                    if (!outputIdentity.matches(
                            target
                    )) {

                        continue;
                    }

                } else if (!target
                        .getComponentsPatch()
                        .isEmpty()) {

                    /*
                     * A componentless definition cannot represent a
                     * component-bearing target variant.
                     */
                    continue;
                }
            }

            CraftScopeResourceAmount output =
                    buildOutput(
                            drop,
                            effectiveItemId,
                            variant
                    );

            if (output != null) {

                outputs.add(
                        output
                );
            }
        }

        /*
         * Guardian/Elder Guardian Cod is represented by one combined
         * target resource.
         *
         * The underlying runtime definition intentionally retains the
         * two independent Cod contributions so their probability and
         * expected-value math can be calculated correctly. They must
         * not, however, survive as duplicate visible output rows.
         *
         * Keep the first target resource, which is the combined
         * Guardian Cod output created above, and preserve every other
         * co-product unchanged.
         */
        if (guardianCodTarget
                && targetId != null) {

            List<CraftScopeResourceAmount> cleanedOutputs =
                    new ArrayList<>();

            boolean targetOutputSeen =
                    false;

            for (CraftScopeResourceAmount output :
                    outputs) {

                if (output != null
                        && output.kind()
                        == CraftScopeResourceKind.ITEM
                        && targetId.equals(
                        output.id()
                )) {

                    if (targetOutputSeen) {
                        continue;
                    }

                    targetOutputSeen =
                            true;
                }

                cleanedOutputs.add(
                        output
                );
            }

            return List.copyOf(
                    cleanedOutputs
            );
        }

        return List.copyOf(
                outputs
        );
    }

    private static boolean isGuardianCodTarget(
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        if (definition == null
                || targetDrop == null) {

            return false;
        }

        String entityId =
                definition
                        .entityTypeId()
                        .toString();

        if (!"minecraft:guardian".equals(
                entityId
        )
                && !"minecraft:elder_guardian".equals(
                entityId
        )) {

            return false;
        }

        return "minecraft:cod".equals(
                targetDrop
                        .itemId()
                        .toString()
        );
    }

    private static boolean isGuardianPlayerBonusBranch(
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        return isGuardianCodTarget(
                definition,
                targetDrop
        )
                && hasTargetRequirement(
                targetDrop,
                "Player or tamed-wolf kill"
        );
    }

    private static boolean hasTargetRequirement(
            CraftScopeMobDropCatalog.DropDefinition drop,
            String expected
    ) {
        if (drop == null
                || expected == null
                || drop.targetRequirements() == null) {

            return false;
        }

        for (String requirement :
                drop.targetRequirements()) {

            if (expected.equals(
                    requirement
            )) {

                return true;
            }
        }

        return false;
    }

    private static Set<String> getGatingRequirements(
            CraftScopeMobDropCatalog.DropDefinition drop
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (drop == null
                || drop.targetRequirements() == null) {

            return result;
        }

        for (String requirement :
                drop.targetRequirements()) {

            if (requirement == null
                    || requirement.isBlank()
                    || requirement.startsWith(
                    "Optional:"
            )) {

                continue;
            }

            result.add(
                    requirement
            );
        }

        return result;
    }

    private static boolean isOutputCompatibleWithTarget(
            CraftScopeMobDropCatalog.DropDefinition targetDrop,
            CraftScopeMobDropCatalog.DropDefinition candidateDrop
    ) {
        if (targetDrop == null
                || candidateDrop == null) {

            return false;
        }

        Set<String> targetRequirements =
                getGatingRequirements(
                        targetDrop
                );

        Set<String> candidateRequirements =
                getGatingRequirements(
                        candidateDrop
                );

        /*
         * An unconditional output is compatible with every branch.
         *
         * A conditional output is included only when the selected
         * target branch guarantees every one of its gating conditions.
         */
        return targetRequirements.containsAll(
                candidateRequirements
        );
    }

    private static CraftScopeResourceAmount buildGuardianCodOutput(
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop,
            OutcomeVariant variant,
            ResourceLocation targetId
    ) {
        if (!isGuardianCodTarget(
                definition,
                targetDrop
        )
                || targetId == null) {

            return null;
        }

        long minimum =
                0L;

        long maximum =
                0L;

        double expected =
                0.0D;

        double probabilityOfNone =
                1.0D;

        Component displayName =
                null;

        CraftScopeItemIdentity itemIdentity =
                null;

        String unit =
                "";

        int contributionCount =
                0;

        for (CraftScopeMobDropCatalog.DropDefinition candidate :
                definition.drops()) {

            if (!isOutputCompatibleWithTarget(
                    targetDrop,
                    candidate
            )) {

                continue;
            }

            ResourceLocation candidateId =
                    getEffectiveItemId(
                            candidate,
                            variant
                    );

            if (!targetId.equals(
                    candidateId
            )) {

                continue;
            }

            CraftScopeResourceAmount contribution =
                    buildOutput(
                            candidate,
                            candidateId,
                            variant
                    );

            if (contribution == null) {
                continue;
            }

            contributionCount++;

            minimum +=
                    contribution.minimumAmount();

            maximum +=
                    contribution.maximumAmount();

            expected +=
                    contribution.expectedAmount();

            probabilityOfNone *=
                    1.0D
                            - contribution.chance();

            if (displayName == null) {

                displayName =
                        contribution.displayName();

                itemIdentity =
                        contribution.itemIdentity();

                unit =
                        contribution.unit();
            }
        }

        if (contributionCount == 0
                || displayName == null
                || maximum <= 0L) {

            return null;
        }

        double chanceOfAtLeastOne =
                1.0D
                        - probabilityOfNone;

        chanceOfAtLeastOne =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                chanceOfAtLeastOne
                        )
                );

        /*
         * "amount" remains the nominal single successful yield.
         *
         * The explicit min/max/expected fields carry the complete
         * combined planning information.
         */
        long nominalAmount =
                1L;

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                targetId,
                displayName,
                nominalAmount,
                unit,
                false,
                chanceOfAtLeastOne,
                List.of(
                        targetId
                ),
                minimum,
                maximum,
                expected,
                itemIdentity
        );
    }
    private static boolean isMutuallyExclusiveFrogOutcome(
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop,
            CraftScopeMobDropCatalog.DropDefinition candidateDrop
    ) {
        if (definition == null
                || targetDrop == null
                || candidateDrop == null
                || targetDrop == candidateDrop) {

            return false;
        }

        String entityId =
                definition
                        .entityTypeId()
                        .toString();

        FrogOutcome targetOutcome =
                getFrogOutcome(
                        targetDrop.targetRequirements()
                );

        FrogOutcome candidateOutcome =
                getFrogOutcome(
                        candidateDrop.targetRequirements()
                );

        if (targetOutcome == FrogOutcome.NONE
                || candidateOutcome == FrogOutcome.NONE) {

            return false;
        }

        /*
         * Vanilla Magma Cube's four frog-related branches are all
         * mutually exclusive.
         */
        if ("minecraft:magma_cube".equals(
                entityId
        )) {

            return targetOutcome
                    != candidateOutcome;
        }

        /*
         * Vanilla Slime has two mutually-exclusive branches that both
         * happen to produce Slime Balls:
         *
         *     frog kill
         *     non-frog kill
         *
         * This filtering will become useful once the interpreter is
         * allowed to retain both same-item branches.
         */
        if ("minecraft:slime".equals(
                entityId
        )) {

            return (
                    targetOutcome == FrogOutcome.FROG
                            && candidateOutcome == FrogOutcome.NOT_FROG
            )
                    || (
                    targetOutcome == FrogOutcome.NOT_FROG
                            && candidateOutcome == FrogOutcome.FROG
            );
        }

        return false;
    }

    private static FrogOutcome getFrogOutcome(
            List<String> requirements
    ) {
        if (requirements == null
                || requirements.isEmpty()) {

            return FrogOutcome.NONE;
        }

        for (String requirement :
                requirements) {

            if ("Not killed by Frog".equals(
                    requirement
            )) {

                return FrogOutcome.NOT_FROG;
            }

            if ("Killed by Frog".equals(
                    requirement
            )) {

                return FrogOutcome.FROG;
            }

            if ("Killed by Warm Frog".equals(
                    requirement
            )) {

                return FrogOutcome.WARM_FROG;
            }

            if ("Killed by Cold Frog".equals(
                    requirement
            )) {

                return FrogOutcome.COLD_FROG;
            }

            if ("Killed by Temperate Frog".equals(
                    requirement
            )) {

                return FrogOutcome.TEMPERATE_FROG;
            }
        }

        return FrogOutcome.NONE;
    }

    private enum FrogOutcome {
        NONE,
        NOT_FROG,
        FROG,
        WARM_FROG,
        COLD_FROG,
        TEMPERATE_FROG
    }
    private static ResourceLocation getEffectiveItemId(
            CraftScopeMobDropCatalog.DropDefinition drop,
            OutcomeVariant variant
    ) {
        if (drop == null) {
            return null;
        }

        if (variant == OutcomeVariant.TRANSFORMED
                && drop.hasTransformation()) {

            return drop.transformedItemId();
        }

        return drop.itemId();
    }

    private static CraftScopeItemIdentity getEffectiveItemIdentity(
            CraftScopeMobDropCatalog.DropDefinition drop,
            OutcomeVariant variant
    ) {
        if (drop == null) {
            return null;
        }

        if (variant == OutcomeVariant.TRANSFORMED
                && drop.hasTransformation()) {

            return drop.transformedItemIdentity();
        }

        return drop.itemIdentity();
    }
    private static CraftScopeResourceAmount buildOutput(
            CraftScopeMobDropCatalog.DropDefinition drop,
            ResourceLocation effectiveItemId,
            OutcomeVariant variant
    ) {
        if (drop == null
                || effectiveItemId == null) {

            return null;
        }

        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                effectiveItemId
                        )
                        .orElse(
                                null
                        );

        if (item == null
                || item == Items.AIR) {

            return null;
        }

        CraftScopeItemIdentity itemIdentity =
                getEffectiveItemIdentity(
                        drop,
                        variant
                );

        ItemStack stack =
                itemIdentity == null
                        ? new ItemStack(
                        item
                )
                        : itemIdentity.createStack();

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        item
                );

        if (drop.mode()
                == CraftScopeMobDropCatalog.DropMode.CHANCE) {

            return new CraftScopeResourceAmount(
                    CraftScopeResourceKind.ITEM,
                    id,
                    itemIdentity == null
                            ? stack.getHoverName().copy()
                            : itemIdentity.displayName(),
                    drop.amount(),
                    "",
                    false,
                    drop.chance(),
                    List.of(
                            id
                    ),
                    itemIdentity
            );
        }

        long nominalAmount;

        if (drop.minimum() > 0L) {

            nominalAmount =
                    drop.minimum();

        } else if (drop.maximum() > 0L) {

            nominalAmount =
                    1L;

        } else {

            nominalAmount =
                    0L;
        }

        double chance =
                drop.chance();

        /*
         * Once a range is probabilistic, zero becomes a possible
         * overall outcome even if the conditional range itself starts
         * above zero.
         */
        long effectiveMinimum =
                chance < 1.0D
                        ? 0L
                        : drop.minimum();

        double conditionalExpected =
                (
                        (double) drop.minimum()
                                + (double) drop.maximum()
                ) / 2.0D;

        double expected =
                conditionalExpected
                        * chance;

        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                id,
                stack.getHoverName().copy(),
                nominalAmount,
                "",
                false,
                chance,
                List.of(
                        id
                ),
                effectiveMinimum,
                drop.maximum(),
                expected,
                itemIdentity
        );
    }

    private static List<String> getVariantRequirements(
            CraftScopeMobDropCatalog.MobDefinition definition,
            OutcomeVariant variant
    ) {
        if (definition == null) {
            return List.of();
        }

        Set<String> requirements =
                new LinkedHashSet<>();

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            if (!drop.hasTransformation()) {
                continue;
            }

            if (variant == OutcomeVariant.TRANSFORMED) {

                requirements.addAll(
                        drop.transformationRequirements()
                );

            } else {

                requirements.addAll(
                        drop.baseTransformationRequirements()
                );
            }
        }

        return List.copyOf(
                requirements
        );
    }

    private static boolean hasTransformation(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        if (definition == null) {
            return false;
        }

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            if (drop != null
                    && drop.hasTransformation()) {

                return true;
            }
        }

        return false;
    }

    private static CraftScopeResourceAmount findTargetOutput(
            List<CraftScopeResourceAmount> outputs,
            ItemStack target
    ) {
        if (outputs == null
                || target == null
                || target.isEmpty()) {

            return null;
        }

        for (CraftScopeResourceAmount output :
                outputs) {

            if (output != null
                    && output.accepts(
                    target
            )) {

                return output;
            }
        }

        return null;
    }

    private static void addRequirements(
            List<CraftScopeProcessRequirement> target,
            List<String> requirements
    ) {
        if (target == null
                || requirements == null) {

            return;
        }

        for (String requirement :
                requirements) {

            if (requirement == null
                    || requirement.isBlank()) {

                continue;
            }

            target.add(
                    otherRequirement(
                            requirement
                    )
            );
        }
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

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                CraftScopeMobDropCatalog.getDefinitions()) {

            ResourceLocation definitionProcessId =
                    getProcessId(
                            definition
                    );

            if (!processId.equals(
                    definitionProcessId
            )) {

                continue;
            }

            return getDefinitionIcon(
                    definition
            );
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getDefinitionIcon(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        if (definition.iconItemId() != null) {

            Item explicitIcon =
                    BuiltInRegistries.ITEM
                            .getOptional(
                                    definition.iconItemId()
                            )
                            .orElse(
                                    null
                            );

            if (explicitIcon != null
                    && explicitIcon != Items.AIR) {

                return new ItemStack(
                        explicitIcon
                );
            }
        }

        EntityType<?> entityType =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(
                                definition.entityTypeId()
                        )
                        .orElse(
                                null
                        );

        if (entityType == null) {
            return ItemStack.EMPTY;
        }

        SpawnEggItem spawnEgg =
                SpawnEggItem.byId(
                        entityType
                );

        if (spawnEgg == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(
                spawnEgg
        );
    }

    private static Component getMobDisplayName(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        EntityType<?> entityType =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(
                                definition.entityTypeId()
                        )
                        .orElse(
                                null
                        );

        if (entityType != null) {
            return entityType.getDescription();
        }

        return Component.literal(
                formatPathName(
                        definition
                                .entityTypeId()
                                .getPath()
                )
        );
    }

    private static String getActionName(
            Component mobName,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        String base =
                "Kill "
                        + mobName.getString();

        /*
         * The Process Diagram shows this action name directly.
         *
         * When two production routes use the same mob but require
         * different kill conditions, the distinction therefore needs
         * to be visible here rather than only in the Setup tab.
         */
        if (definition != null
                && targetDrop != null) {

            String entityId =
                    definition
                            .entityTypeId()
                            .toString();

            if (isGuardianPlayerBonusBranch(
                    definition,
                    targetDrop
            )) {

                base +=
                        " (Player Kill Bonus)";

            } else if ("minecraft:slime".equals(
                    entityId
            )
                    || "minecraft:magma_cube".equals(
                    entityId
            )) {

                FrogOutcome frogOutcome =
                        getFrogOutcome(
                                targetDrop.targetRequirements()
                        );

                if (frogOutcome == FrogOutcome.NOT_FROG) {

                    base +=
                            " (Non-Frog Kill)";

                } else if (frogOutcome == FrogOutcome.FROG) {

                    base +=
                            " (Frog Kill)";

                } else if (frogOutcome == FrogOutcome.WARM_FROG) {

                    base +=
                            " (Warm Frog Kill)";

                } else if (frogOutcome == FrogOutcome.COLD_FROG) {

                    base +=
                            " (Cold Frog Kill)";

                } else if (frogOutcome
                        == FrogOutcome.TEMPERATE_FROG) {

                    base +=
                            " (Temperate Frog Kill)";
                }
            }
        }

        if (variant == OutcomeVariant.TRANSFORMED) {

            base +=
                    " (Smelted Loot)";
        }

        return base;
    }

    private static String getRouteName(
            Component mobName,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        String base =
                mobName.getString()
                        + " Drops";

        if (isGuardianPlayerBonusBranch(
                definition,
                targetDrop
        )) {

            base +=
                    " (Player Kill Bonus)";
        }

        /*
         * Keep the ordinary Slime route name unchanged so existing
         * projects remain as stable as possible.
         *
         * Only the new Frog-kill branch receives a suffix.
         */
        if (isSlimeFrogKillBranch(
                definition,
                targetDrop
        )) {

            base +=
                    " (Frog Kill)";
        }

        if (variant == OutcomeVariant.TRANSFORMED) {

            base +=
                    " (Smelted Loot)";
        }

        return base;
    }

    private static boolean isSlimeFrogKillBranch(
            CraftScopeMobDropCatalog.MobDefinition definition,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        if (definition == null
                || targetDrop == null) {

            return false;
        }

        if (!"minecraft:slime".equals(
                definition
                        .entityTypeId()
                        .toString()
        )) {

            return false;
        }

        if (!"minecraft:slime_ball".equals(
                targetDrop
                        .itemId()
                        .toString()
        )) {

            return false;
        }

        return getFrogOutcome(
                targetDrop.targetRequirements()
        ) == FrogOutcome.FROG;
    }

    private static ResourceLocation getProcessId(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        ResourceLocation entityId =
                definition.entityTypeId();

        return requireId(
                "craftscope:mob_drop/"
                        + entityId.getNamespace()
                        + "/"
                        + entityId.getPath()
        );
    }

    private static String buildStepId(
            CraftScopeMobDropCatalog.MobDefinition definition,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        ResourceLocation entityId =
                definition.entityTypeId();

        String id =
                "craftscope:mob_drop:"
                        + entityId.getNamespace()
                        + ":"
                        + entityId.getPath()
                        + ":step";

        if (variant == OutcomeVariant.TRANSFORMED) {

            id +=
                    ":smelted";
        }

        if (isSlimeFrogKillBranch(
                definition,
                targetDrop
        )) {

            id +=
                    ":frog";
        }

        if (isGuardianPlayerBonusBranch(
                definition,
                targetDrop
        )) {

            id +=
                    ":player_bonus";
        }

        return id;
    }

    private static ResourceLocation buildRouteId(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ResourceLocation targetId,
            OutcomeVariant variant,
            CraftScopeMobDropCatalog.DropDefinition targetDrop
    ) {
        ResourceLocation entityId =
                definition.entityTypeId();

        String value =
                "craftscope:acquisition/mob/"
                        + entityId.getNamespace()
                        + "/"
                        + entityId.getPath()
                        + "/"
                        + targetId.getNamespace()
                        + "/"
                        + targetId.getPath();

        if (variant == OutcomeVariant.TRANSFORMED) {

            value +=
                    "/smelted";
        }

        /*
         * Preserve the historic ID for the ordinary Slime route.
         * Only the additional mutually-exclusive Frog route needs a
         * new route identity.
         */
        if (isSlimeFrogKillBranch(
                definition,
                targetDrop
        )) {

            value +=
                    "/frog";
        }

        if (isGuardianPlayerBonusBranch(
                definition,
                targetDrop
        )) {

            value +=
                    "/player_bonus";
        }

        return requireId(
                value
        );
    }

    private static String formatSourceName(
            String sourceModId
    ) {
        if (sourceModId == null
                || sourceModId.isBlank()) {

            return "";
        }

        if ("minecraft".equals(
                sourceModId
        )) {

            return "Minecraft";
        }

        if ("craftscope".equals(
                sourceModId
        )) {

            return "CraftScope";
        }

        return formatPathName(
                sourceModId
        );
    }

    private static String formatPathName(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "";
        }

        String[] pieces =
                value
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .split(
                                "[_\\-/]"
                        );

        StringBuilder result =
                new StringBuilder();

        for (String piece :
                pieces) {

            if (piece == null
                    || piece.isBlank()) {

                continue;
            }

            if (!result.isEmpty()) {

                result.append(
                        ' '
                );
            }

            result.append(
                    Character.toUpperCase(
                            piece.charAt(
                                    0
                            )
                    )
            );

            if (piece.length() > 1) {

                result.append(
                        piece.substring(
                                1
                        )
                );
            }
        }

        return result.isEmpty()
                ? value
                : result.toString();
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

    private enum OutcomeVariant {
        BASE,
        TRANSFORMED
    }
}