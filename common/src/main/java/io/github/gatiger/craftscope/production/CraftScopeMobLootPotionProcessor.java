package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Resolves minecraft:set_potion loot functions into CraftScope's
 * component-aware item identity model.
 *
 * Example:
 *
 *     minecraft:tipped_arrow
 *         + minecraft:set_potion minecraft:poison
 *
 * becomes:
 *
 *     minecraft:tipped_arrow
 *         + POTION_CONTENTS = minecraft:poison
 *
 * The set_potion function is then removed from the copied loot-table
 * JSON so the normal conservative loot interpreter can process the
 * remaining count/chance/requirement structure.
 *
 * The resulting component identity is applied back to the interpreted
 * DropDefinition afterward.
 *
 * This processor deliberately handles only:
 *
 * - direct minecraft:item entries
 * - exactly one set_potion function on that entry
 * - unconditional set_potion
 * - a valid registered item
 * - a valid registered potion
 *
 * Anything more complicated remains untouched and therefore remains
 * unsupported by the core interpreter.
 */
public final class CraftScopeMobLootPotionProcessor {

    private CraftScopeMobLootPotionProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            RegistryAccess registryAccess
    ) {
        Objects.requireNonNull(
                registryAccess,
                "registryAccess"
        );

        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    List.of(),
                    0
            );
        }

        Registry<Potion> potionRegistry =
                registryAccess.registryOrThrow(
                        Registries.POTION
                );

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        List<PotionAssignment> assignments =
                new ArrayList<>();

        int identityCount =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                identityCount +=
                        processTable(
                                snapshot.entityTypeId(),
                                copied.getAsJsonObject(),
                                potionRegistry,
                                assignments
                        );
            }

            preparedSnapshots.add(
                    new CraftScopeMobLootTableScanner.LootTableSnapshot(
                            snapshot.entityTypeId(),
                            snapshot.lootTableId(),
                            copied
                    )
            );
        }

        CraftScopeMobLootTableScanner.ScanResult preparedResult =
                new CraftScopeMobLootTableScanner.ScanResult(
                        scanResult.entityTypesChecked(),
                        scanResult.emptyOrMissingTables(),
                        scanResult.serializationFailures(),
                        preparedSnapshots
                );

        return new PreparedScan(
                preparedResult,
                assignments,
                identityCount
        );
    }

    /*
     * Apply the component identities discovered during preprocessing
     * to the DropDefinitions created by the ordinary interpreter.
     *
     * This occurs AFTER interpretation because the core interpreter
     * intentionally remains concerned with loot structure rather than
     * registry-backed ItemStack construction.
     */
    public static CraftScopeMobLootTableInterpreter.InterpretationResult
    applyIdentities(
            CraftScopeMobLootTableInterpreter.InterpretationResult
                    interpretation,
            PreparedScan prepared
    ) {
        if (interpretation == null) {
            return null;
        }

        if (prepared == null
                || prepared.assignments().isEmpty()
                || interpretation.definitions().isEmpty()) {

            return interpretation;
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                interpretation.definitions()) {

            boolean changed =
                    false;

            List<CraftScopeMobDropCatalog.DropDefinition> drops =
                    new ArrayList<>();

            for (CraftScopeMobDropCatalog.DropDefinition drop :
                    definition.drops()) {

                CraftScopeItemIdentity identity =
                        findUniqueIdentity(
                                prepared.assignments(),
                                definition.entityTypeId(),
                                drop.itemId()
                        );

                if (identity == null
                        || drop.itemIdentity() != null) {

                    drops.add(
                            drop
                    );

                    continue;
                }

                drops.add(
                        new CraftScopeMobDropCatalog.DropDefinition(
                                drop.itemId(),
                                drop.mode(),
                                drop.minimum(),
                                drop.maximum(),
                                drop.amount(),
                                drop.chance(),
                                drop.targetRequirements(),
                                drop.transformedItemId(),
                                drop.baseTransformationRequirements(),
                                drop.transformationRequirements(),
                                identity,
                                drop.transformedItemIdentity()
                        )
                );

                changed =
                        true;
            }

            if (!changed) {

                definitions.add(
                        definition
                );

                continue;
            }

            definitions.add(
                    new CraftScopeMobDropCatalog.MobDefinition(
                            definition.entityTypeId(),
                            definition.sourceModId(),
                            definition.iconItemId(),
                            definition.priority(),
                            definition.requirements(),
                            drops
                    )
            );
        }

        return new CraftScopeMobLootTableInterpreter.InterpretationResult(
                interpretation.tablesChecked(),
                interpretation.completeMobDefinitions(),
                interpretation.partialMobsSkipped(),
                interpretation.emptyMobsSkipped(),
                interpretation.dropsProduced(),
                interpretation.unsupportedPools(),
                interpretation.unsupportedEntries(),
                definitions
        );
    }

    private static int processTable(
            ResourceLocation entityTypeId,
            JsonObject root,
            Registry<Potion> potionRegistry,
            List<PotionAssignment> assignments
    ) {
        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null) {
            return 0;
        }

        int identityCount =
                0;

        for (JsonElement poolElement :
                pools) {

            if (poolElement == null
                    || !poolElement.isJsonObject()) {

                continue;
            }

            JsonArray entries =
                    getArray(
                            poolElement.getAsJsonObject(),
                            "entries"
                    );

            if (entries == null) {
                continue;
            }

            for (JsonElement entryElement :
                    entries) {

                if (entryElement == null
                        || !entryElement.isJsonObject()) {

                    continue;
                }

                identityCount +=
                        processEntry(
                                entityTypeId,
                                entryElement.getAsJsonObject(),
                                potionRegistry,
                                assignments
                        );
            }
        }

        return identityCount;
    }

    private static int processEntry(
            ResourceLocation entityTypeId,
            JsonObject entry,
            Registry<Potion> potionRegistry,
            List<PotionAssignment> assignments
    ) {
        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return 0;
        }

        ResourceLocation itemId =
                ResourceLocation.tryParse(
                        safeString(
                                getString(
                                        entry,
                                        "name"
                                )
                        )
                );

        if (itemId == null) {
            return 0;
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null
                || functions.isEmpty()) {

            return 0;
        }

        int matchingCount =
                0;

        int matchingIndex =
                -1;

        for (int i = 0;
             i < functions.size();
             i++) {

            JsonElement functionElement =
                    functions.get(
                            i
                    );

            if (functionElement == null
                    || !functionElement.isJsonObject()) {

                continue;
            }

            JsonObject function =
                    functionElement.getAsJsonObject();

            if ("minecraft:set_potion".equals(
                    getString(
                            function,
                            "function"
                    )
            )) {

                matchingCount++;

                matchingIndex =
                        i;
            }
        }

        /*
         * Multiple set_potion functions would require respecting
         * function ordering and overwrite behavior. Leave those
         * untouched rather than guessing.
         */
        if (matchingCount != 1
                || matchingIndex < 0) {

            return 0;
        }

        JsonElement functionElement =
                functions.get(
                        matchingIndex
                );

        if (functionElement == null
                || !functionElement.isJsonObject()) {

            return 0;
        }

        JsonObject function =
                functionElement.getAsJsonObject();

        /*
         * Conditional set_potion would produce two distinct ItemStack
         * outcomes from one entry. That needs a richer branch model.
         */
        if (hasNonEmptyArray(
                function,
                "conditions"
        )
                || hasMalformedArray(
                function,
                "conditions"
        )) {

            return 0;
        }

        ResourceLocation potionId =
                ResourceLocation.tryParse(
                        safeString(
                                getString(
                                        function,
                                        "id"
                                )
                        )
                );

        if (potionId == null) {
            return 0;
        }

        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                itemId
                        )
                        .orElse(
                                null
                        );

        if (item == null
                || item == Items.AIR) {

            return 0;
        }

        Holder.Reference<Potion> potion =
                potionRegistry
                        .getHolder(
                                potionId
                        )
                        .orElse(
                                null
                        );

        if (potion == null) {
            return 0;
        }

        ItemStack stack =
                PotionContents.createItemStack(
                        item,
                        potion
                );

        if (stack == null
                || stack.isEmpty()) {

            return 0;
        }

        CraftScopeItemIdentity identity =
                CraftScopeItemIdentity.fromStack(
                        stack
                );

        /*
         * Remove only the function we have fully modeled.
         *
         * set_count, enchanted_count_increase, etc. remain in place
         * and are interpreted by the normal loot interpreter.
         */
        JsonArray retainedFunctions =
                new JsonArray();

        for (int i = 0;
             i < functions.size();
             i++) {

            if (i == matchingIndex) {
                continue;
            }

            retainedFunctions.add(
                    functions.get(
                            i
                    )
            );
        }

        if (retainedFunctions.isEmpty()) {

            entry.remove(
                    "functions"
            );

        } else {

            entry.add(
                    "functions",
                    retainedFunctions
            );
        }

        assignments.add(
                new PotionAssignment(
                        entityTypeId,
                        itemId,
                        potionId,
                        identity
                )
        );

        return 1;
    }

    /*
     * One base item may only receive one component identity for a mob
     * through this simple representation.
     *
     * If future datapacks produce the same item with two different
     * potion identities in independent pools, return null and keep
     * that case conservative.
     */
    private static CraftScopeItemIdentity findUniqueIdentity(
            List<PotionAssignment> assignments,
            ResourceLocation entityTypeId,
            ResourceLocation itemId
    ) {
        CraftScopeItemIdentity result =
                null;

        for (PotionAssignment assignment :
                assignments) {

            if (!entityTypeId.equals(
                    assignment.entityTypeId()
            )
                    || !itemId.equals(
                    assignment.itemId()
            )) {

                continue;
            }

            if (result == null) {

                result =
                        assignment.identity();

                continue;
            }

            if (!result.equals(
                    assignment.identity()
            )) {

                return null;
            }
        }

        return result;
    }

    private static JsonArray getArray(
            JsonObject object,
            String name
    ) {
        if (object == null
                || name == null
                || !object.has(
                name
        )) {

            return null;
        }

        JsonElement element =
                object.get(
                        name
                );

        if (element == null
                || !element.isJsonArray()) {

            return null;
        }

        return element.getAsJsonArray();
    }

    private static String getString(
            JsonObject object,
            String name
    ) {
        if (object == null
                || name == null
                || !object.has(
                name
        )) {

            return null;
        }

        JsonElement element =
                object.get(
                        name
                );

        if (element == null
                || !element.isJsonPrimitive()) {

            return null;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isString()) {
            return null;
        }

        return primitive.getAsString();
    }

    private static boolean hasNonEmptyArray(
            JsonObject object,
            String name
    ) {
        if (object == null
                || name == null
                || !object.has(
                name
        )) {

            return false;
        }

        JsonElement element =
                object.get(
                        name
                );

        return element != null
                && element.isJsonArray()
                && !element
                .getAsJsonArray()
                .isEmpty();
    }

    private static boolean hasMalformedArray(
            JsonObject object,
            String name
    ) {
        if (object == null
                || name == null
                || !object.has(
                name
        )) {

            return false;
        }

        JsonElement element =
                object.get(
                        name
                );

        return element == null
                || !element.isJsonArray();
    }

    private static String safeString(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    public record PotionAssignment(
            ResourceLocation entityTypeId,
            ResourceLocation itemId,
            ResourceLocation potionId,
            CraftScopeItemIdentity identity
    ) {

        public PotionAssignment {
            Objects.requireNonNull(
                    entityTypeId,
                    "entityTypeId"
            );

            Objects.requireNonNull(
                    itemId,
                    "itemId"
            );

            Objects.requireNonNull(
                    potionId,
                    "potionId"
            );

            Objects.requireNonNull(
                    identity,
                    "identity"
            );
        }
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            List<PotionAssignment> assignments,
            int identityCount
    ) {

        public PreparedScan {
            assignments =
                    assignments == null
                            ? List.of()
                            : List.copyOf(
                            assignments
                    );

            if (identityCount < 0) {

                throw new IllegalArgumentException(
                        "Potion identity count cannot be negative"
                );
            }
        }
    }
}