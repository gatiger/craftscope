package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/*
 * Normalizes a conservative subset of Minecraft multi-entry loot pools.
 *
 * Supported shape:
 *
 * - exactly one roll
 * - zero bonus rolls
 * - no pool-level functions
 * - two or more entries
 * - entries are only:
 *
 *       minecraft:item
 *       minecraft:empty
 *
 * - entries have no conditions
 * - entry quality is absent or zero
 * - entry weight is a positive whole number
 *
 * Minecraft chooses ONE eligible entry according to weight.
 *
 * CraftScope currently models each output by its marginal probability
 * rather than storing correlations between outputs. Therefore:
 *
 *     A weight 1
 *     B weight 1
 *     C weight 1
 *
 * becomes three single-entry pools with an additional 1/3 chance.
 *
 * If the original pool itself has a 2.5% condition:
 *
 *     2.5% * 1/3 = 0.8333...%
 *
 * for each item.
 *
 * Empty entries participate in the denominator but generate no
 * CraftScope output branch.
 *
 * Anything more complicated stays untouched and will continue to be
 * rejected by the conservative interpreter.
 */
public final class CraftScopeMobLootMultiEntryProcessor {

    private static final double EPSILON =
            0.0000001D;

    private CraftScopeMobLootMultiEntryProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    0,
                    0
            );
        }

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        int poolsNormalized =
                0;

        int itemBranchesGenerated =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                TableResult tableResult =
                        processTable(
                                copied.getAsJsonObject()
                        );

                poolsNormalized +=
                        tableResult.poolsNormalized();

                itemBranchesGenerated +=
                        tableResult.itemBranchesGenerated();
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
                poolsNormalized,
                itemBranchesGenerated
        );
    }

    private static TableResult processTable(
            JsonObject root
    ) {
        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null
                || pools.isEmpty()) {

            return new TableResult(
                    0,
                    0
            );
        }

        JsonArray replacementPools =
                new JsonArray();

        int poolsNormalized =
                0;

        int itemBranchesGenerated =
                0;

        for (JsonElement poolElement :
                pools) {

            if (!canNormalizePool(
                    poolElement
            )) {

                replacementPools.add(
                        poolElement
                );

                continue;
            }

            JsonObject pool =
                    poolElement.getAsJsonObject();

            JsonArray entries =
                    getArray(
                            pool,
                            "entries"
                    );

            if (entries == null) {

                replacementPools.add(
                        poolElement
                );

                continue;
            }

            long totalWeight =
                    0L;

            for (JsonElement entryElement :
                    entries) {

                Integer weight =
                        getEntryWeight(
                                entryElement
                        );

                if (weight == null) {

                    totalWeight =
                            -1L;

                    break;
                }

                totalWeight +=
                        weight;
            }

            if (totalWeight <= 0L) {

                replacementPools.add(
                        poolElement
                );

                continue;
            }

            int generatedForPool =
                    0;

            for (JsonElement entryElement :
                    entries) {

                JsonObject entry =
                        entryElement.getAsJsonObject();

                String entryType =
                        getString(
                                entry,
                                "type"
                        );

                Integer weight =
                        getEntryWeight(
                                entry
                        );

                if (weight == null) {
                    continue;
                }

                /*
                 * minecraft:empty represents the no-output branch.
                 *
                 * Its weight remains part of totalWeight, but no
                 * replacement pool is necessary.
                 */
                if ("minecraft:empty".equals(
                        entryType
                )) {

                    continue;
                }

                JsonObject branchPool =
                        pool.deepCopy();

                JsonArray branchEntries =
                        new JsonArray();

                branchEntries.add(
                        entry.deepCopy()
                );

                branchPool.add(
                        "entries",
                        branchEntries
                );

                double selectionChance =
                        (double) weight
                                / (double) totalWeight;

                if (selectionChance
                        < 1.0D - EPSILON) {

                    JsonArray conditions =
                            copyConditions(
                                    pool
                            );

                    if (conditions == null) {

                        /*
                         * Malformed conditions should already have
                         * prevented normalization, but stay safe.
                         */
                        generatedForPool =
                                0;

                        break;
                    }

                    JsonObject selectionCondition =
                            new JsonObject();

                    selectionCondition.addProperty(
                            "condition",
                            "minecraft:random_chance"
                    );

                    selectionCondition.addProperty(
                            "chance",
                            selectionChance
                    );

                    conditions.add(
                            selectionCondition
                    );

                    branchPool.add(
                            "conditions",
                            conditions
                    );
                }

                replacementPools.add(
                        branchPool
                );

                generatedForPool++;
            }

            if (generatedForPool <= 0) {

                /*
                 * This should only happen for an all-empty pool or a
                 * malformed structure. Keep the original rather than
                 * silently deleting behavior.
                 */
                replacementPools.add(
                        poolElement
                );

                continue;
            }

            poolsNormalized++;

            itemBranchesGenerated +=
                    generatedForPool;
        }

        root.add(
                "pools",
                replacementPools
        );

        return new TableResult(
                poolsNormalized,
                itemBranchesGenerated
        );
    }

    private static boolean canNormalizePool(
            JsonElement poolElement
    ) {
        if (poolElement == null
                || !poolElement.isJsonObject()) {

            return false;
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        if (!isNumberEqual(
                pool.get(
                        "rolls"
                ),
                1.0D
        )) {

            return false;
        }

        JsonElement bonusRolls =
                pool.get(
                        "bonus_rolls"
                );

        if (bonusRolls != null
                && !isNumberEqual(
                bonusRolls,
                0.0D
        )) {

            return false;
        }

        if (hasMalformedArray(
                pool,
                "functions"
        )) {

            return false;
        }

        JsonArray functions =
                getArray(
                        pool,
                        "functions"
                );

        if (functions != null
                && !functions.isEmpty()) {

            return false;
        }

        /*
         * Pool conditions are safe to duplicate because they gate
         * whether the weighted choice occurs at all.
         *
         * They still must be a valid array if present.
         */
        if (hasMalformedArray(
                pool,
                "conditions"
        )) {

            return false;
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null
                || entries.size() <= 1) {

            return false;
        }

        boolean hasItem =
                false;

        for (JsonElement entryElement :
                entries) {

            if (entryElement == null
                    || !entryElement.isJsonObject()) {

                return false;
            }

            JsonObject entry =
                    entryElement.getAsJsonObject();

            String entryType =
                    getString(
                            entry,
                            "type"
                    );

            if (!"minecraft:item".equals(
                    entryType
            )
                    && !"minecraft:empty".equals(
                    entryType
            )) {

                return false;
            }

            if ("minecraft:item".equals(
                    entryType
            )) {

                String itemName =
                        getString(
                                entry,
                                "name"
                        );

                if (itemName == null
                        || itemName.isBlank()) {

                    return false;
                }

                hasItem =
                        true;
            }

            /*
             * Entry-level conditions alter which entries participate
             * in weighted selection. Supporting that requires dynamic
             * denominator calculation, so reject those for now.
             */
            if (hasMalformedArray(
                    entry,
                    "conditions"
            )) {

                return false;
            }

            JsonArray entryConditions =
                    getArray(
                            entry,
                            "conditions"
                    );

            if (entryConditions != null
                    && !entryConditions.isEmpty()) {

                return false;
            }

            Integer weight =
                    getEntryWeight(
                            entry
                    );

            if (weight == null
                    || weight <= 0) {

                return false;
            }

            Integer quality =
                    getWholeInteger(
                            entry,
                            "quality",
                            0
                    );

            /*
             * Non-zero quality makes weight depend on Luck.
             */
            if (quality == null
                    || quality != 0) {

                return false;
            }
        }

        return hasItem;
    }

    private static Integer getEntryWeight(
            JsonElement entryElement
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            return null;
        }

        return getWholeInteger(
                entryElement.getAsJsonObject(),
                "weight",
                1
        );
    }

    private static Integer getWholeInteger(
            JsonObject object,
            String name,
            int defaultValue
    ) {
        if (object == null
                || name == null) {

            return null;
        }

        if (!object.has(
                name
        )) {

            return defaultValue;
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

        if (!primitive.isNumber()) {

            return null;
        }

        double value =
                primitive.getAsDouble();

        if (Double.isNaN(
                value
        )
                || Double.isInfinite(
                value
        )) {

            return null;
        }

        double rounded =
                Math.rint(
                        value
                );

        if (Math.abs(
                value - rounded
        ) > EPSILON) {

            return null;
        }

        if (rounded < Integer.MIN_VALUE
                || rounded > Integer.MAX_VALUE) {

            return null;
        }

        return (int) rounded;
    }

    private static JsonArray copyConditions(
            JsonObject pool
    ) {
        if (pool == null) {
            return null;
        }

        if (!pool.has(
                "conditions"
        )) {

            return new JsonArray();
        }

        JsonArray conditions =
                getArray(
                        pool,
                        "conditions"
                );

        if (conditions == null) {
            return null;
        }

        return conditions.deepCopy();
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

        return Math.abs(
                primitive.getAsDouble()
                        - expected
        ) <= EPSILON;
    }

    private record TableResult(
            int poolsNormalized,
            int itemBranchesGenerated
    ) {
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int poolsNormalized,
            int itemBranchesGenerated
    ) {
    }
}