package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/*
 * Splits the known vanilla Slime/Magma Cube frog-dependent loot pools
 * into independent single-entry pools.
 *
 * This processor is deliberately narrow.
 *
 * Minecraft normally chooses one eligible entry from a multi-entry
 * loot pool. Splitting arbitrary conditional entries into separate
 * pools would be unsafe because several entries could be eligible at
 * the same time.
 *
 * The vanilla 1.21.1 Slime and Magma Cube tables are different:
 *
 * Slime:
 *
 *     not frog -> Slime Ball
 *     frog     -> Slime Ball
 *
 * Magma Cube:
 *
 *     not frog + size >= 2 -> Magma Cream
 *     warm frog            -> Pearlescent Froglight
 *     cold frog            -> Verdant Froglight
 *     temperate frog       -> Ochre Froglight
 *
 * Those branches are mutually exclusive.
 *
 * We recognize ONLY those exact structures. Datapacks that alter the
 * structure remain untouched and therefore continue through the
 * normal conservative interpreter path.
 */
public final class CraftScopeMobLootFrogBranchProcessor {

    private static final double EPSILON =
            0.0000001D;

    private static final String SLIME_ID =
            "minecraft:slime";

    private static final String MAGMA_CUBE_ID =
            "minecraft:magma_cube";

    private CraftScopeMobLootFrogBranchProcessor() {
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

        int poolsSplit =
                0;

        int branchesGenerated =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            TableResult result =
                    new TableResult(
                            0,
                            0
                    );

            if (copied.isJsonObject()) {

                String entityId =
                        snapshot
                                .entityTypeId()
                                .toString();

                if (MAGMA_CUBE_ID.equals(
                        entityId
                )) {

                    result =
                            splitMagmaCubeTable(
                                    copied.getAsJsonObject()
                            );

                } else if (SLIME_ID.equals(
                        entityId
                )) {

                    result =
                            splitSlimeTable(
                                    copied.getAsJsonObject()
                            );
                }
            }

            poolsSplit +=
                    result.poolsSplit();

            branchesGenerated +=
                    result.branchesGenerated();

            preparedSnapshots.add(
                    new CraftScopeMobLootTableScanner.LootTableSnapshot(
                            snapshot.entityTypeId(),
                            snapshot.lootTableId(),
                            copied
                    )
            );
        }

        CraftScopeMobLootTableScanner.ScanResult prepared =
                new CraftScopeMobLootTableScanner.ScanResult(
                        scanResult.entityTypesChecked(),
                        scanResult.emptyOrMissingTables(),
                        scanResult.serializationFailures(),
                        preparedSnapshots
                );

        return new PreparedScan(
                prepared,
                poolsSplit,
                branchesGenerated
        );
    }

    private static TableResult splitMagmaCubeTable(
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

        int poolsSplit =
                0;

        int branchesGenerated =
                0;

        for (JsonElement poolElement :
                pools) {

            if (!isMagmaCubePool(
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

            for (JsonElement entryElement :
                    entries) {

                JsonObject branchPool =
                        pool.deepCopy();

                JsonArray branchEntries =
                        new JsonArray();

                branchEntries.add(
                        entryElement.deepCopy()
                );

                branchPool.add(
                        "entries",
                        branchEntries
                );

                replacementPools.add(
                        branchPool
                );

                branchesGenerated++;
            }

            poolsSplit++;
        }

        root.add(
                "pools",
                replacementPools
        );

        return new TableResult(
                poolsSplit,
                branchesGenerated
        );
    }

    private static TableResult splitSlimeTable(
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

        int poolsSplit =
                0;

        int branchesGenerated =
                0;

        for (JsonElement poolElement :
                pools) {

            if (!isSlimePool(
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

            for (JsonElement entryElement :
                    entries) {

                JsonObject branchPool =
                        pool.deepCopy();

                JsonArray branchEntries =
                        new JsonArray();

                branchEntries.add(
                        entryElement.deepCopy()
                );

                branchPool.add(
                        "entries",
                        branchEntries
                );

                replacementPools.add(
                        branchPool
                );

                branchesGenerated++;
            }

            poolsSplit++;
        }

        root.add(
                "pools",
                replacementPools
        );

        return new TableResult(
                poolsSplit,
                branchesGenerated
        );
    }

    private static boolean isMagmaCubePool(
            JsonElement poolElement
    ) {
        if (!isBasicSingleRollPool(
                poolElement
        )) {

            return false;
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        JsonArray poolConditions =
                getArray(
                        pool,
                        "conditions"
                );

        if (poolConditions != null
                && !poolConditions.isEmpty()) {

            return false;
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null
                || entries.size() != 4) {

            return false;
        }

        JsonObject magmaCream =
                null;

        JsonObject pearlescent =
                null;

        JsonObject verdant =
                null;

        JsonObject ochre =
                null;

        for (JsonElement entryElement :
                entries) {

            if (!isSimpleItemEntry(
                    entryElement
            )) {

                return false;
            }

            JsonObject entry =
                    entryElement.getAsJsonObject();

            String itemName =
                    getString(
                            entry,
                            "name"
                    );

            if ("minecraft:magma_cream".equals(
                    itemName
            )) {

                if (magmaCream != null) {
                    return false;
                }

                magmaCream =
                        entry;

                continue;
            }

            if ("minecraft:pearlescent_froglight".equals(
                    itemName
            )) {

                if (pearlescent != null) {
                    return false;
                }

                pearlescent =
                        entry;

                continue;
            }

            if ("minecraft:verdant_froglight".equals(
                    itemName
            )) {

                if (verdant != null) {
                    return false;
                }

                verdant =
                        entry;

                continue;
            }

            if ("minecraft:ochre_froglight".equals(
                    itemName
            )) {

                if (ochre != null) {
                    return false;
                }

                ochre =
                        entry;

                continue;
            }

            return false;
        }

        return magmaCream != null
                && pearlescent != null
                && verdant != null
                && ochre != null
                && hasMagmaCreamConditions(
                magmaCream
        )
                && hasSingleFrogVariantCondition(
                pearlescent,
                "minecraft:warm"
        )
                && hasSingleFrogVariantCondition(
                verdant,
                "minecraft:cold"
        )
                && hasSingleFrogVariantCondition(
                ochre,
                "minecraft:temperate"
        );
    }

    private static boolean isSlimePool(
            JsonElement poolElement
    ) {
        if (!isBasicSingleRollPool(
                poolElement
        )) {

            return false;
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        JsonArray poolConditions =
                getArray(
                        pool,
                        "conditions"
                );

        if (poolConditions == null
                || poolConditions.size() != 1
                || !isSlimeSizeExact(
                poolConditions.get(0),
                1
        )) {

            return false;
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null
                || entries.size() != 2) {

            return false;
        }

        boolean foundNormal =
                false;

        boolean foundFrog =
                false;

        for (JsonElement entryElement :
                entries) {

            if (!isSimpleItemEntry(
                    entryElement
            )) {

                return false;
            }

            JsonObject entry =
                    entryElement.getAsJsonObject();

            if (!"minecraft:slime_ball".equals(
                    getString(
                            entry,
                            "name"
                    )
            )) {

                return false;
            }

            JsonArray conditions =
                    getArray(
                            entry,
                            "conditions"
                    );

            if (conditions == null
                    || conditions.size() != 1) {

                return false;
            }

            JsonElement condition =
                    conditions.get(0);

            if (isInvertedFrogSource(
                    condition
            )) {

                if (foundNormal) {
                    return false;
                }

                foundNormal =
                        true;

                continue;
            }

            if (isFrogSource(
                    condition,
                    null
            )) {

                if (foundFrog) {
                    return false;
                }

                foundFrog =
                        true;

                continue;
            }

            return false;
        }

        return foundNormal
                && foundFrog;
    }

    private static boolean hasMagmaCreamConditions(
            JsonObject entry
    ) {
        JsonArray conditions =
                getArray(
                        entry,
                        "conditions"
                );

        if (conditions == null
                || conditions.size() != 2) {

            return false;
        }

        boolean foundNotFrog =
                false;

        boolean foundSize =
                false;

        for (JsonElement condition :
                conditions) {

            if (isInvertedFrogSource(
                    condition
            )) {

                if (foundNotFrog) {
                    return false;
                }

                foundNotFrog =
                        true;

                continue;
            }

            if (isSlimeSizeMinimum(
                    condition,
                    2
            )) {

                if (foundSize) {
                    return false;
                }

                foundSize =
                        true;

                continue;
            }

            return false;
        }

        return foundNotFrog
                && foundSize;
    }

    private static boolean hasSingleFrogVariantCondition(
            JsonObject entry,
            String variant
    ) {
        JsonArray conditions =
                getArray(
                        entry,
                        "conditions"
                );

        return conditions != null
                && conditions.size() == 1
                && isFrogSource(
                conditions.get(0),
                variant
        );
    }

    private static boolean isInvertedFrogSource(
            JsonElement conditionElement
    ) {
        if (conditionElement == null
                || !conditionElement.isJsonObject()) {

            return false;
        }

        JsonObject condition =
                conditionElement.getAsJsonObject();

        if (condition.size() != 2
                || !"minecraft:inverted".equals(
                getString(
                        condition,
                        "condition"
                )
        )) {

            return false;
        }

        return isFrogSource(
                condition.get(
                        "term"
                ),
                null
        );
    }

    private static boolean isFrogSource(
            JsonElement conditionElement,
            String variant
    ) {
        if (conditionElement == null
                || !conditionElement.isJsonObject()) {

            return false;
        }

        JsonObject condition =
                conditionElement.getAsJsonObject();

        if (condition.size() != 2
                || !"minecraft:damage_source_properties".equals(
                getString(
                        condition,
                        "condition"
                )
        )) {

            return false;
        }

        JsonElement predicateElement =
                condition.get(
                        "predicate"
                );

        if (predicateElement == null
                || !predicateElement.isJsonObject()) {

            return false;
        }

        JsonObject predicate =
                predicateElement.getAsJsonObject();

        if (predicate.size() != 1) {
            return false;
        }

        JsonElement sourceElement =
                predicate.get(
                        "source_entity"
                );

        if (sourceElement == null
                || !sourceElement.isJsonObject()) {

            return false;
        }

        JsonObject source =
                sourceElement.getAsJsonObject();

        if (variant == null) {

            return source.size() == 1
                    && "minecraft:frog".equals(
                    getString(
                            source,
                            "type"
                    )
            );
        }

        if (source.size() != 2
                || !"minecraft:frog".equals(
                getString(
                        source,
                        "type"
                )
        )) {

            return false;
        }

        JsonElement typeSpecificElement =
                source.get(
                        "type_specific"
                );

        if (typeSpecificElement == null
                || !typeSpecificElement.isJsonObject()) {

            return false;
        }

        JsonObject typeSpecific =
                typeSpecificElement.getAsJsonObject();

        return typeSpecific.size() == 2
                && "minecraft:frog".equals(
                getString(
                        typeSpecific,
                        "type"
                )
        )
                && variant.equals(
                getString(
                        typeSpecific,
                        "variant"
                )
        );
    }

    private static boolean isSlimeSizeExact(
            JsonElement conditionElement,
            int size
    ) {
        JsonObject typeSpecific =
                getSlimeTypeSpecific(
                        conditionElement
                );

        if (typeSpecific == null
                || typeSpecific.size() != 2) {

            return false;
        }

        JsonElement sizeElement =
                typeSpecific.get(
                        "size"
                );

        return isWholeNumberEqual(
                sizeElement,
                size
        );
    }

    private static boolean isSlimeSizeMinimum(
            JsonElement conditionElement,
            int minimum
    ) {
        JsonObject typeSpecific =
                getSlimeTypeSpecific(
                        conditionElement
                );

        if (typeSpecific == null
                || typeSpecific.size() != 2) {

            return false;
        }

        JsonElement sizeElement =
                typeSpecific.get(
                        "size"
                );

        if (sizeElement == null
                || !sizeElement.isJsonObject()) {

            return false;
        }

        JsonObject range =
                sizeElement.getAsJsonObject();

        return range.size() == 1
                && isWholeNumberEqual(
                range.get(
                        "min"
                ),
                minimum
        );
    }

    private static JsonObject getSlimeTypeSpecific(
            JsonElement conditionElement
    ) {
        if (conditionElement == null
                || !conditionElement.isJsonObject()) {

            return null;
        }

        JsonObject condition =
                conditionElement.getAsJsonObject();

        if (condition.size() != 3
                || !"minecraft:entity_properties".equals(
                getString(
                        condition,
                        "condition"
                )
        )
                || !"this".equals(
                getString(
                        condition,
                        "entity"
                )
        )) {

            return null;
        }

        JsonElement predicateElement =
                condition.get(
                        "predicate"
                );

        if (predicateElement == null
                || !predicateElement.isJsonObject()) {

            return null;
        }

        JsonObject predicate =
                predicateElement.getAsJsonObject();

        if (predicate.size() != 1) {
            return null;
        }

        JsonElement typeSpecificElement =
                predicate.get(
                        "type_specific"
                );

        if (typeSpecificElement == null
                || !typeSpecificElement.isJsonObject()) {

            return null;
        }

        JsonObject typeSpecific =
                typeSpecificElement.getAsJsonObject();

        if (!"minecraft:slime".equals(
                getString(
                        typeSpecific,
                        "type"
                )
        )) {

            return null;
        }

        return typeSpecific;
    }

    private static boolean isBasicSingleRollPool(
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

        JsonArray functions =
                getArray(
                        pool,
                        "functions"
                );

        if (pool.has(
                "functions"
        )
                && functions == null) {

            return false;
        }

        return functions == null
                || functions.isEmpty();
    }

    private static boolean isSimpleItemEntry(
            JsonElement entryElement
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            return false;
        }

        JsonObject entry =
                entryElement.getAsJsonObject();

        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return false;
        }

        String name =
                getString(
                        entry,
                        "name"
                );

        if (name == null
                || name.isBlank()) {

            return false;
        }

        Integer weight =
                getWholeInteger(
                        entry,
                        "weight",
                        1
                );

        Integer quality =
                getWholeInteger(
                        entry,
                        "quality",
                        0
                );

        return weight != null
                && weight == 1
                && quality != null
                && quality == 0;
    }

    private static Integer getWholeInteger(
            JsonObject object,
            String name,
            int defaultValue
    ) {
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

        double rounded =
                Math.rint(
                        value
                );

        if (Double.isNaN(
                value
        )
                || Double.isInfinite(
                value
        )
                || Math.abs(
                value - rounded
        ) > EPSILON
                || rounded < Integer.MIN_VALUE
                || rounded > Integer.MAX_VALUE) {

            return null;
        }

        return (int) rounded;
    }

    private static boolean isWholeNumberEqual(
            JsonElement element,
            int expected
    ) {
        if (element == null
                || !element.isJsonPrimitive()) {

            return false;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        return primitive.isNumber()
                && Math.abs(
                primitive.getAsDouble()
                        - expected
        ) <= EPSILON;
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

        return primitive.isNumber()
                && Math.abs(
                primitive.getAsDouble()
                        - expected
        ) <= EPSILON;
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

    private record TableResult(
            int poolsSplit,
            int branchesGenerated
    ) {
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int poolsSplit,
            int branchesGenerated
    ) {
    }
}