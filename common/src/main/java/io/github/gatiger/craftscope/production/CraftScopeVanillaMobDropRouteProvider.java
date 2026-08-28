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
import java.util.List;
import java.util.Locale;

/*
 * Converts CraftScopeMobDropCatalog entries into selectable
 * CraftScope production routes.
 *
 * Mob/drop data no longer lives in this provider. The provider only
 * translates catalog definitions into the normal CraftScope
 * production model.
 *
 * Keeping this class name also preserves compatibility with the
 * existing acquisition-registration and Process Diagram icon mixins.
 */
public final class CraftScopeVanillaMobDropRouteProvider
        implements CraftScopeProductionRouteProvider {

    /*
     * Keep the existing provider ID for compatibility.
     *
     * Although the backing catalog can now contain runtime/modded
     * definitions, changing the provider ID is unnecessary and could
     * disturb code that already identifies this provider.
     */
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

            CraftScopeMobDropCatalog.DropDefinition targetDrop =
                    findTargetDrop(
                            definition,
                            targetId
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
                            targetId
                    );

            if (targetOutput == null) {
                continue;
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
                    targetDrop.targetRequirements()
            );

            String sourceModId =
                    definition.sourceModId();

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            sourceModId,
                            processId,
                            Component.literal(
                                    "Kill "
                                            + mobName.getString()
                            ),
                            List.of(),
                            requirements
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            buildStepId(
                                    definition
                            ),
                            Component.literal(
                                    "Kill "
                                            + mobName.getString()
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
                                    targetOutput.id()
                            ),
                            sourceModId,
                            Component.literal(
                                    formatSourceName(
                                            sourceModId
                                    )
                            ),
                            Component.literal(
                                    mobName.getString()
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

    private static CraftScopeMobDropCatalog.DropDefinition
    findTargetDrop(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ResourceLocation targetId
    ) {
        if (definition == null
                || targetId == null) {

            return null;
        }

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            if (targetId.equals(
                    drop.itemId()
            )) {

                return drop;
            }
        }

        return null;
    }

    private static List<CraftScopeResourceAmount> buildOutputs(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                definition.drops()) {

            CraftScopeResourceAmount output =
                    buildOutput(
                            drop
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

    private static CraftScopeResourceAmount buildOutput(
            CraftScopeMobDropCatalog.DropDefinition drop
    ) {
        if (drop == null
                || drop.itemId() == null) {

            return null;
        }

        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                drop.itemId()
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

    /*
     * Process Diagram uses this method through
     * MixinCraftScopeMobDropProcessIcon.
     *
     * Explicit catalog icons take priority. If no explicit icon is
     * supplied, use the registered spawn egg for the entity type.
     */
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
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        ResourceLocation entityId =
                definition.entityTypeId();

        return "craftscope:mob_drop:"
                + entityId.getNamespace()
                + ":"
                + entityId.getPath()
                + ":step";
    }

    private static ResourceLocation buildRouteId(
            CraftScopeMobDropCatalog.MobDefinition definition,
            ResourceLocation targetId
    ) {
        ResourceLocation entityId =
                definition.entityTypeId();

        return requireId(
                "craftscope:acquisition/mob/"
                        + entityId.getNamespace()
                        + "/"
                        + entityId.getPath()
                        + "/"
                        + targetId.getNamespace()
                        + "/"
                        + targetId.getPath()
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
}