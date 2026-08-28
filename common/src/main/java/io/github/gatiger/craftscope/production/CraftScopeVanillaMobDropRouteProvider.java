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

        ResourceLocation targetId =
                BuiltInRegistries.ITEM.getKey(
                        target.getItem()
                );

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                CraftScopeMobDropCatalog.getDefinitions()) {

            addRouteIfMatching(
                    definition,
                    targetId,
                    OutcomeVariant.BASE,
                    routes
            );

            if (hasTransformation(
                    definition
            )) {

                addRouteIfMatching(
                        definition,
                        targetId,
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
            ResourceLocation targetId,
            OutcomeVariant variant,
            List<CraftScopeProductionRoute> routes
    ) {
        CraftScopeMobDropCatalog.DropDefinition targetDrop =
                findTargetDrop(
                        definition,
                        targetId,
                        variant
                );

        if (targetDrop == null) {
            return;
        }

        List<CraftScopeResourceAmount> outputs =
                buildOutputs(
                        definition,
                        variant
                );

        CraftScopeResourceAmount targetOutput =
                findTargetOutput(
                        outputs,
                        targetId
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
                        variant
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
                                variant
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
                                variant
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
                                        variant
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

    private static CraftScopeMobDropCatalog.DropDefinition
    findTargetDrop(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ResourceLocation targetId,
            OutcomeVariant variant
    ) {
        if (definition == null
                || targetId == null) {

            return null;
        }

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            ResourceLocation effectiveId =
                    getEffectiveItemId(
                            drop,
                            variant
                    );

            if (targetId.equals(
                    effectiveId
            )) {

                return drop;
            }
        }

        return null;
    }

    private static List<CraftScopeResourceAmount> buildOutputs(
            CraftScopeMobDropCatalog.MobDefinition definition,
            OutcomeVariant variant
    ) {
        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            ResourceLocation effectiveItemId =
                    getEffectiveItemId(
                            drop,
                            variant
                    );

            CraftScopeResourceAmount output =
                    buildOutput(
                            drop,
                            effectiveItemId
                    );

            if (output != null) {

                outputs.add(
                        output
                );
            }
        }

        return List.copyOf(
                outputs
        );
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

    private static CraftScopeResourceAmount buildOutput(
            CraftScopeMobDropCatalog.DropDefinition drop,
            ResourceLocation effectiveItemId
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

        ItemStack stack =
                new ItemStack(
                        item
                );

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        item
                );

        if (drop.mode()
                == CraftScopeMobDropCatalog.DropMode.CHANCE) {

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
            ResourceLocation targetId
    ) {
        if (outputs == null
                || targetId == null) {

            return null;
        }

        for (CraftScopeResourceAmount output :
                outputs) {

            if (output != null
                    && targetId.equals(
                    output.id()
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
            OutcomeVariant variant
    ) {
        String base =
                "Kill "
                        + mobName.getString();

        if (variant == OutcomeVariant.TRANSFORMED) {

            return base
                    + " (Smelted Loot)";
        }

        return base;
    }

    private static String getRouteName(
            Component mobName,
            OutcomeVariant variant
    ) {
        String base =
                mobName.getString()
                        + " Drops";

        if (variant == OutcomeVariant.TRANSFORMED) {

            return base
                    + " (Smelted Loot)";
        }

        return base;
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
            OutcomeVariant variant
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

        return id;
    }

    private static ResourceLocation buildRouteId(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ResourceLocation targetId,
            OutcomeVariant variant
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