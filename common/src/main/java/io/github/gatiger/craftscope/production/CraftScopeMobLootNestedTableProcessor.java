package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Expands a deliberately conservative subset of
 * minecraft:loot_table entries before CraftScope's normal loot
 * preprocessors and interpreter run.
 *
 * Why this exists
 * ---------------
 *
 * Some entity loot tables do not directly contain the item entries
 * that they can produce.
 *
 * Example:
 *
 *     Guardian
 *         ->
 *     minecraft:gameplay/fishing/fish
 *         ->
 *     Cod / Salmon / Tropical Fish / Pufferfish
 *
 * CraftScope's normal interpreter intentionally rejects nested loot
 * tables because blindly flattening arbitrary tables can change loot
 * probabilities.
 *
 * This processor only expands a nested reference when all of the
 * following are true:
 *
 * - the outer pool contains exactly one entry
 * - that entry is minecraft:loot_table
 * - the referenced table exists on the authoritative server
 * - the referenced table has exactly one pool
 * - that pool rolls exactly once
 * - that pool has no bonus rolls
 * - that pool has no conditions or functions
 * - every referenced entry is a direct minecraft:item or
 *   minecraft:empty entry
 *
 * Because the nested entry is the only entry in the outer pool, its
 * weight and quality cannot compete with sibling entries. That makes
 * flattening this specific shape safe.
 *
 * Parent entry conditions are moved onto the outer pool.
 *
 * Parent entry functions are appended to each generated item entry,
 * preserving the semantic order:
 *
 *     referenced child functions
 *             then
 *     parent nested-entry functions
 *
 * Empty entries do not receive functions because they never create an
 * ItemStack.
 *
 * Anything outside this supported shape is left completely unchanged
 * so the conservative interpreter can reject it normally.
 */
public final class CraftScopeMobLootNestedTableProcessor {

    private CraftScopeMobLootNestedTableProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            MinecraftServer server
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    0,
                    0,
                    0
            );
        }

        Objects.requireNonNull(
                server,
                "server"
        );

        RegistryOps<JsonElement> registryOps =
                server
                        .registryAccess()
                        .createSerializationContext(
                                JsonOps.INSTANCE
                        );

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        Counter counter =
                new Counter();

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                processTable(
                        copied.getAsJsonObject(),
                        server,
                        registryOps,
                        counter
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
                counter.referencesFound,
                counter.referencesExpanded,
                counter.itemBranchesInlined
        );
    }

    private static void processTable(
            JsonObject root,
            MinecraftServer server,
            RegistryOps<JsonElement> registryOps,
            Counter counter
    ) {
        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null) {
            return;
        }

        for (JsonElement poolElement :
                pools) {

            if (poolElement == null
                    || !poolElement.isJsonObject()) {

                continue;
            }

            processPool(
                    poolElement.getAsJsonObject(),
                    server,
                    registryOps,
                    counter
            );
        }
    }

    private static void processPool(
            JsonObject outerPool,
            MinecraftServer server,
            RegistryOps<JsonElement> registryOps,
            Counter counter
    ) {
        JsonArray outerEntries =
                getArray(
                        outerPool,
                        "entries"
                );

        if (outerEntries == null
                || outerEntries.size() != 1) {

            return;
        }

        JsonElement outerEntryElement =
                outerEntries.get(
                        0
                );

        if (outerEntryElement == null
                || !outerEntryElement.isJsonObject()) {

            return;
        }

        JsonObject outerEntry =
                outerEntryElement.getAsJsonObject();

        if (!"minecraft:loot_table".equals(
                getString(
                        outerEntry,
                        "type"
                )
        )) {

            return;
        }

        counter.referencesFound++;

        Expansion expansion =
                buildExpansion(
                        outerPool,
                        outerEntry,
                        server,
                        registryOps
                );

        if (expansion == null) {
            return;
        }

        if (expansion.poolConditions() != null) {

            outerPool.add(
                    "conditions",
                    expansion.poolConditions()
            );
        }

        outerPool.add(
                "entries",
                expansion.entries()
        );

        counter.referencesExpanded++;

        counter.itemBranchesInlined +=
                expansion.itemBranches();
    }

    private static Expansion buildExpansion(
            JsonObject outerPool,
            JsonObject outerEntry,
            MinecraftServer server,
            RegistryOps<JsonElement> registryOps
    ) {
        /*
         * Only keys whose behavior is understood here are accepted.
         *
         * weight/quality are permitted only in their neutral values.
         * The outer pool contains exactly one entry, so neutral values
         * cannot affect selection anyway.
         */
        if (!hasOnly(
                outerEntry,
                "type",
                "value",
                "conditions",
                "functions",
                "weight",
                "quality"
        )) {

            return null;
        }

        JsonElement weight =
                outerEntry.get(
                        "weight"
                );

        if (weight != null
                && !isNumberEqual(
                weight,
                1.0D
        )) {

            return null;
        }

        JsonElement quality =
                outerEntry.get(
                        "quality"
                );

        if (quality != null
                && !isNumberEqual(
                quality,
                0.0D
        )) {

            return null;
        }

        if (hasMalformedArray(
                outerEntry,
                "conditions"
        )
                || hasMalformedArray(
                outerEntry,
                "functions"
        )) {

            return null;
        }

        JsonArray parentConditions =
                getArray(
                        outerEntry,
                        "conditions"
                );

        JsonArray parentFunctions =
                getArray(
                        outerEntry,
                        "functions"
                );

        ResourceLocation referencedId =
                ResourceLocation.tryParse(
                        getString(
                                outerEntry,
                                "value"
                        )
                );

        if (referencedId == null) {
            return null;
        }

        JsonObject referencedRoot =
                loadTable(
                        referencedId,
                        server,
                        registryOps
                );

        if (referencedRoot == null) {
            return null;
        }

        /*
         * Table-level functions modify everything generated by every
         * pool. They are deliberately not flattened yet.
         */
        if (hasNonEmptyArray(
                referencedRoot,
                "functions"
        )
                || hasMalformedArray(
                referencedRoot,
                "functions"
        )) {

            return null;
        }

        JsonArray referencedPools =
                getArray(
                        referencedRoot,
                        "pools"
                );

        if (referencedPools == null
                || referencedPools.size() != 1) {

            return null;
        }

        JsonElement referencedPoolElement =
                referencedPools.get(
                        0
                );

        if (referencedPoolElement == null
                || !referencedPoolElement.isJsonObject()) {

            return null;
        }

        JsonObject referencedPool =
                referencedPoolElement.getAsJsonObject();

        if (!isNumberEqual(
                referencedPool.get(
                        "rolls"
                ),
                1.0D
        )) {

            return null;
        }

        JsonElement bonusRolls =
                referencedPool.get(
                        "bonus_rolls"
                );

        if (bonusRolls != null
                && !isNumberEqual(
                bonusRolls,
                0.0D
        )) {

            return null;
        }

        /*
         * Conditions/functions on the referenced pool itself would
         * need another semantic layer when flattening.
         */
        if (hasNonEmptyArray(
                referencedPool,
                "conditions"
        )
                || hasMalformedArray(
                referencedPool,
                "conditions"
        )
                || hasNonEmptyArray(
                referencedPool,
                "functions"
        )
                || hasMalformedArray(
                referencedPool,
                "functions"
        )) {

            return null;
        }

        JsonArray referencedEntries =
                getArray(
                        referencedPool,
                        "entries"
                );

        if (referencedEntries == null
                || referencedEntries.isEmpty()) {

            return null;
        }

        JsonArray replacementEntries =
                new JsonArray();

        int itemBranches =
                0;

        /*
         * Build the complete replacement before mutating the outer
         * table. If any child is unsupported, the original nested
         * entry remains untouched.
         */
        for (JsonElement childElement :
                referencedEntries) {

            if (childElement == null
                    || !childElement.isJsonObject()) {

                return null;
            }

            JsonObject child =
                    childElement
                            .getAsJsonObject()
                            .deepCopy();

            String childType =
                    getString(
                            child,
                            "type"
                    );

            if ("minecraft:empty".equals(
                    childType
            )) {

                replacementEntries.add(
                        child
                );

                continue;
            }

            if (!"minecraft:item".equals(
                    childType
            )) {

                return null;
            }

            if (hasMalformedArray(
                    child,
                    "functions"
            )
                    || hasMalformedArray(
                    child,
                    "conditions"
            )) {

                return null;
            }

            if (parentFunctions != null
                    && !parentFunctions.isEmpty()) {

                JsonArray mergedFunctions =
                        mergeArrays(
                                getArray(
                                        child,
                                        "functions"
                                ),
                                parentFunctions
                        );

                child.add(
                        "functions",
                        mergedFunctions
                );
            }

            replacementEntries.add(
                    child
            );

            itemBranches++;
        }

        JsonArray mergedPoolConditions =
                null;

        if (parentConditions != null
                && !parentConditions.isEmpty()) {

            if (hasMalformedArray(
                    outerPool,
                    "conditions"
            )) {

                return null;
            }

            mergedPoolConditions =
                    mergeArrays(
                            getArray(
                                    outerPool,
                                    "conditions"
                            ),
                            parentConditions
                    );
        }

        return new Expansion(
                replacementEntries,
                mergedPoolConditions,
                itemBranches
        );
    }

    private static JsonObject loadTable(
            ResourceLocation tableId,
            MinecraftServer server,
            RegistryOps<JsonElement> registryOps
    ) {
        ResourceKey<LootTable> key =
                ResourceKey.create(
                        Registries.LOOT_TABLE,
                        tableId
                );

        LootTable table =
                server
                        .reloadableRegistries()
                        .getLootTable(
                                key
                        );

        if (table == null
                || table == LootTable.EMPTY) {

            return null;
        }

        var encodedResult =
                LootTable.DIRECT_CODEC.encodeStart(
                        registryOps,
                        table
                );

        var encoded =
                encodedResult.result();

        if (encoded.isEmpty()) {
            return null;
        }

        JsonElement value =
                encoded.get();

        if (!value.isJsonObject()) {
            return null;
        }

        return value.getAsJsonObject();
    }

    private static JsonArray mergeArrays(
            JsonArray first,
            JsonArray second
    ) {
        JsonArray result =
                new JsonArray();

        if (first != null) {

            for (JsonElement element :
                    first) {

                result.add(
                        element.deepCopy()
                );
            }
        }

        if (second != null) {

            for (JsonElement element :
                    second) {

                result.add(
                        element.deepCopy()
                );
            }
        }

        return result;
    }

    private static JsonArray getArray(
            JsonObject object,
            String key
    ) {
        if (object == null
                || key == null
                || !object.has(
                key
        )) {

            return null;
        }

        JsonElement value =
                object.get(
                        key
                );

        if (value == null
                || !value.isJsonArray()) {

            return null;
        }

        return value.getAsJsonArray();
    }

    private static String getString(
            JsonObject object,
            String key
    ) {
        if (object == null
                || key == null
                || !object.has(
                key
        )) {

            return null;
        }

        JsonElement value =
                object.get(
                        key
                );

        if (value == null
                || !value.isJsonPrimitive()) {

            return null;
        }

        JsonPrimitive primitive =
                value.getAsJsonPrimitive();

        if (!primitive.isString()) {
            return null;
        }

        return primitive.getAsString();
    }

    private static boolean hasMalformedArray(
            JsonObject object,
            String key
    ) {
        if (object == null
                || key == null
                || !object.has(
                key
        )) {

            return false;
        }

        JsonElement value =
                object.get(
                        key
                );

        return value == null
                || !value.isJsonArray();
    }

    private static boolean hasNonEmptyArray(
            JsonObject object,
            String key
    ) {
        JsonArray array =
                getArray(
                        object,
                        key
                );

        return array != null
                && !array.isEmpty();
    }

    private static boolean isNumberEqual(
            JsonElement element,
            double expected
    ) {
        if (element == null
                || !element.isJsonPrimitive()) {

            return false;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            return false;
        }

        double actual =
                primitive.getAsDouble();

        return Double.isFinite(
                actual
        )
                && Math.abs(
                actual - expected
        ) < 0.0000001D;
    }

    private static boolean hasOnly(
            JsonObject object,
            String... allowedKeys
    ) {
        if (object == null) {
            return false;
        }

        List<String> allowed =
                List.of(
                        allowedKeys
                );

        for (String key :
                object.keySet()) {

            if (!allowed.contains(
                    key
            )) {

                return false;
            }
        }

        return true;
    }

    private static final class Counter {

        private int referencesFound;
        private int referencesExpanded;
        private int itemBranchesInlined;
    }

    private record Expansion(
            JsonArray entries,
            JsonArray poolConditions,
            int itemBranches
    ) {
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int referencesFound,
            int referencesExpanded,
            int itemBranchesInlined
    ) {

        public PreparedScan {
            if (referencesFound < 0
                    || referencesExpanded < 0
                    || itemBranchesInlined < 0) {

                throw new IllegalArgumentException(
                        "CraftScope nested-loot counts cannot be negative"
                );
            }

            if (referencesExpanded > referencesFound) {

                throw new IllegalArgumentException(
                        "Expanded nested references cannot exceed references found"
                );
            }
        }
    }
}