package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Normalizes Witch's repeated weighted loot pool.
 *
 * Vanilla Minecraft 1.21.1 Witch loot contains a pool that:
 *
 *     rolls 1-3 times
 *
 * and chooses one weighted entry on every roll.
 *
 * Five entries have weight 1:
 *
 *     Glowstone Dust
 *     Sugar
 *     Spider Eye
 *     Glass Bottle
 *     Gunpowder
 *
 * Stick has weight 2.
 *
 * Every selected entry then rolls a base count of 0-2.
 *
 * CraftScope does not need to preserve the full correlated
 * probability distribution to plan production. It needs:
 *
 *     probability of at least one item
 *     minimum possible amount
 *     maximum possible amount
 *     expected amount per Witch kill
 *
 * This processor converts the repeated weighted pool into six
 * independent marginal branches containing exactly those values.
 *
 * The conversion is deliberately conservative. Only the structural
 * shape described above is normalized. Other repeated-roll pools
 * remain untouched.
 */
public final class CraftScopeMobLootWitchProcessor {

    private static final double EPSILON =
            0.0000001D;

    private static final String WITCH_ID =
            "minecraft:witch";

    private CraftScopeMobLootWitchProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    0,
                    0,
                    List.of()
            );
        }

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        List<ExpectedYield> expectedYields =
                new ArrayList<>();

        int poolsNormalized =
                0;

        int branchesGenerated =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (WITCH_ID.equals(
                    snapshot
                            .entityTypeId()
                            .toString()
            )
                    && copied.isJsonObject()) {

                TableResult result =
                        processWitchTable(
                                snapshot.entityTypeId(),
                                copied.getAsJsonObject()
                        );

                poolsNormalized +=
                        result.poolsNormalized();

                branchesGenerated +=
                        result.branchesGenerated();

                expectedYields.addAll(
                        result.expectedYields()
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

        CraftScopeMobLootTableScanner.ScanResult prepared =
                new CraftScopeMobLootTableScanner.ScanResult(
                        scanResult.entityTypesChecked(),
                        scanResult.emptyOrMissingTables(),
                        scanResult.serializationFailures(),
                        preparedSnapshots
                );

        return new PreparedScan(
                prepared,
                poolsNormalized,
                branchesGenerated,
                expectedYields
        );
    }

    private static TableResult processWitchTable(
            ResourceLocation entityTypeId,
            JsonObject root
    ) {
        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null
                || pools.isEmpty()) {

            return TableResult.empty();
        }

        JsonArray replacementPools =
                new JsonArray();

        List<ExpectedYield> expectedYields =
                new ArrayList<>();

        int poolsNormalized =
                0;

        int branchesGenerated =
                0;

        for (JsonElement poolElement :
                pools) {

            PoolPlan plan =
                    analyzeRepeatedWitchPool(
                            poolElement
                    );

            if (plan == null) {

                replacementPools.add(
                        poolElement
                );

                continue;
            }

            for (EntryPlan entryPlan :
                    plan.entries()) {

                JsonObject branchPool =
                        plan.pool()
                                .deepCopy();

                /*
                 * The repeated 1-3 rolls are collapsed into one
                 * marginal branch for this particular item.
                 */
                branchPool.addProperty(
                        "rolls",
                        1.0D
                );

                JsonObject branchEntry =
                        entryPlan
                                .entry()
                                .deepCopy();

                /*
                 * Once the item appears at least once, its overall
                 * base amount can range from 1 through 6:
                 *
                 *     maximum 3 selections
                 *     x maximum 2 items each
                 */
                replaceBaseCountWithAggregatedRange(
                        branchEntry
                );

                JsonArray branchEntries =
                        new JsonArray();

                branchEntries.add(
                        branchEntry
                );

                branchPool.add(
                        "entries",
                        branchEntries
                );

                double selectionChance =
                        (double) entryPlan.weight()
                                / (double) plan.totalWeight();

                double positivePerRoll =
                        selectionChance
                                * (2.0D / 3.0D);

                double noPositivePerRoll =
                        1.0D
                                - positivePerRoll;

                /*
                 * Witch rolls are uniformly 1, 2, or 3.
                 *
                 * Probability of no positive item:
                 *
                 *     (
                 *         p0
                 *       + p0^2
                 *       + p0^3
                 *     ) / 3
                 */
                double probabilityNone =
                        (
                                noPositivePerRoll
                                        + noPositivePerRoll
                                        * noPositivePerRoll
                                        + noPositivePerRoll
                                        * noPositivePerRoll
                                        * noPositivePerRoll
                        ) / 3.0D;

                double probabilityPositive =
                        1.0D
                                - probabilityNone;

                probabilityPositive =
                        clampChance(
                                probabilityPositive
                        );

                JsonArray conditions =
                        new JsonArray();

                JsonObject chanceCondition =
                        new JsonObject();

                chanceCondition.addProperty(
                        "condition",
                        "minecraft:random_chance"
                );

                chanceCondition.addProperty(
                        "chance",
                        probabilityPositive
                );

                conditions.add(
                        chanceCondition
                );

                branchPool.add(
                        "conditions",
                        conditions
                );

                replacementPools.add(
                        branchPool
                );

                /*
                 * Expected rolls:
                 *
                 *     average rolls = 2
                 *
                 * Expected base count once selected:
                 *
                 *     average of 0,1,2 = 1
                 *
                 * Therefore:
                 *
                 *     expected = 2 * selectionChance
                 */
                double expectedAmount =
                        2.0D
                                * selectionChance;

                expectedYields.add(
                        new ExpectedYield(
                                entityTypeId,
                                entryPlan.itemId(),
                                expectedAmount
                        )
                );

                branchesGenerated++;
            }

            poolsNormalized++;
        }

        root.add(
                "pools",
                replacementPools
        );

        return new TableResult(
                poolsNormalized,
                branchesGenerated,
                expectedYields
        );
    }

    private static PoolPlan analyzeRepeatedWitchPool(
            JsonElement poolElement
    ) {
        if (poolElement == null
                || !poolElement.isJsonObject()) {

            return null;
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        /*
         * Exact Witch repeated-roll provider:
         *
         *     uniform 1-3
         */
        if (!isUniformProvider(
                pool.get(
                        "rolls"
                ),
                1.0D,
                3.0D
        )) {

            return null;
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

            return null;
        }

        if (hasNonEmptyOrMalformedArray(
                pool,
                "conditions"
        )
                || hasNonEmptyOrMalformedArray(
                pool,
                "functions"
        )) {

            return null;
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null
                || entries.size() < 2) {

            return null;
        }

        List<EntryPlan> plans =
                new ArrayList<>();

        Set<ResourceLocation> itemIds =
                new LinkedHashSet<>();

        long totalWeight =
                0L;

        for (JsonElement entryElement :
                entries) {

            EntryPlan plan =
                    analyzeEntry(
                            entryElement
                    );

            if (plan == null) {
                return null;
            }

            /*
             * Duplicate item entries would need their weights merged
             * before marginalization. Vanilla Witch does not use that
             * shape, so remain conservative.
             */
            if (!itemIds.add(
                    plan.itemId()
            )) {

                return null;
            }

            if (Long.MAX_VALUE - totalWeight
                    < plan.weight()) {

                return null;
            }

            totalWeight +=
                    plan.weight();

            plans.add(
                    plan
            );
        }

        if (totalWeight <= 0L) {
            return null;
        }

        return new PoolPlan(
                pool,
                totalWeight,
                List.copyOf(
                        plans
                )
        );
    }

    private static EntryPlan analyzeEntry(
            JsonElement entryElement
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            return null;
        }

        JsonObject entry =
                entryElement.getAsJsonObject();

        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return null;
        }

        String itemName =
                getString(
                        entry,
                        "name"
                );

        if (itemName == null
                || itemName.isBlank()) {

            return null;
        }

        ResourceLocation itemId =
                ResourceLocation.tryParse(
                        itemName
                );

        if (itemId == null) {
            return null;
        }

        if (hasNonEmptyOrMalformedArray(
                entry,
                "conditions"
        )) {

            return null;
        }

        Integer quality =
                getWholeInteger(
                        entry,
                        "quality",
                        0
                );

        if (quality == null
                || quality != 0) {

            return null;
        }

        Integer weight =
                getWholeInteger(
                        entry,
                        "weight",
                        1
                );

        if (weight == null
                || weight <= 0) {

            return null;
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null
                || functions.size() != 2) {

            return null;
        }

        boolean foundBaseCount =
                false;

        boolean foundLooting =
                false;

        for (JsonElement functionElement :
                functions) {

            if (functionElement == null
                    || !functionElement.isJsonObject()) {

                return null;
            }

            JsonObject function =
                    functionElement.getAsJsonObject();

            String functionType =
                    getString(
                            function,
                            "function"
                    );

            if ("minecraft:set_count".equals(
                    functionType
            )) {

                if (foundBaseCount
                        || !isExactBaseCountFunction(
                        function
                )) {

                    return null;
                }

                foundBaseCount =
                        true;

                continue;
            }

            if ("minecraft:enchanted_count_increase".equals(
                    functionType
            )) {

                if (foundLooting
                        || !isExactLootingFunction(
                        function
                )) {

                    return null;
                }

                foundLooting =
                        true;

                continue;
            }

            return null;
        }

        if (!foundBaseCount
                || !foundLooting) {

            return null;
        }

        return new EntryPlan(
                entry,
                itemId,
                weight
        );
    }

    private static boolean isExactBaseCountFunction(
            JsonObject function
    ) {
        if (function == null
                || !"minecraft:set_count".equals(
                getString(
                        function,
                        "function"
                )
        )) {

            return false;
        }

        JsonElement addElement =
                function.get(
                        "add"
                );

        if (addElement == null
                || !addElement.isJsonPrimitive()
                || !addElement
                .getAsJsonPrimitive()
                .isBoolean()
                || addElement
                .getAsBoolean()) {

            return false;
        }

        return isUniformProvider(
                function.get(
                        "count"
                ),
                0.0D,
                2.0D
        );
    }

    private static boolean isExactLootingFunction(
            JsonObject function
    ) {
        if (function == null
                || !"minecraft:enchanted_count_increase".equals(
                getString(
                        function,
                        "function"
                )
        )) {

            return false;
        }

        if (!"minecraft:looting".equals(
                getString(
                        function,
                        "enchantment"
                )
        )) {

            return false;
        }

        return isUniformProvider(
                function.get(
                        "count"
                ),
                0.0D,
                1.0D
        );
    }

    private static void replaceBaseCountWithAggregatedRange(
            JsonObject entry
    ) {
        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null) {
            return;
        }

        for (JsonElement functionElement :
                functions) {

            if (functionElement == null
                    || !functionElement.isJsonObject()) {

                continue;
            }

            JsonObject function =
                    functionElement.getAsJsonObject();

            if (!"minecraft:set_count".equals(
                    getString(
                            function,
                            "function"
                    )
            )) {

                continue;
            }

            JsonObject provider =
                    new JsonObject();

            provider.addProperty(
                    "type",
                    "minecraft:uniform"
            );

            provider.addProperty(
                    "min",
                    1.0D
            );

            provider.addProperty(
                    "max",
                    6.0D
            );

            function.add(
                    "count",
                    provider
            );

            return;
        }
    }

    /*
     * Apply the exact expected values after the normal interpreter has
     * converted the prepared JSON into DropDefinition objects.
     *
     * The structural interpreter remains generic and does not need to
     * know anything specifically about Witch.
     */
    public static CraftScopeMobLootTableInterpreter.InterpretationResult
    applyExpectedYields(
            CraftScopeMobLootTableInterpreter.InterpretationResult interpretation,
            PreparedScan prepared
    ) {
        if (interpretation == null
                || prepared == null
                || prepared.expectedYields().isEmpty()
                || interpretation.definitions().isEmpty()) {

            return interpretation;
        }

        Map<ResourceLocation, Map<ResourceLocation, Double>>
                expectedByMob =
                new LinkedHashMap<>();

        for (ExpectedYield expected :
                prepared.expectedYields()) {

            expectedByMob
                    .computeIfAbsent(
                            expected.entityTypeId(),
                            ignored ->
                                    new LinkedHashMap<>()
                    )
                    .put(
                            expected.itemId(),
                            expected.expectedAmount()
                    );
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                interpretation.definitions()) {

            Map<ResourceLocation, Double> expectedByItem =
                    expectedByMob.get(
                            definition.entityTypeId()
                    );

            if (expectedByItem == null
                    || expectedByItem.isEmpty()) {

                definitions.add(
                        definition
                );

                continue;
            }

            List<CraftScopeMobDropCatalog.DropDefinition> drops =
                    new ArrayList<>();

            for (CraftScopeMobDropCatalog.DropDefinition drop :
                    definition.drops()) {

                Double expectedAmount =
                        expectedByItem.get(
                                drop.itemId()
                        );

                if (expectedAmount == null) {

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
                                drop.itemIdentity(),
                                drop.transformedItemIdentity(),
                                expectedAmount
                        )
                );
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

    private static boolean isUniformProvider(
            JsonElement element,
            double minimum,
            double maximum
    ) {
        if (element == null
                || !element.isJsonObject()) {

            return false;
        }

        JsonObject provider =
                element.getAsJsonObject();

        if (!"minecraft:uniform".equals(
                getString(
                        provider,
                        "type"
                )
        )) {

            return false;
        }

        Double actualMinimum =
                getDouble(
                        provider,
                        "min"
                );

        Double actualMaximum =
                getDouble(
                        provider,
                        "max"
                );

        return actualMinimum != null
                && actualMaximum != null
                && Math.abs(
                actualMinimum - minimum
        ) <= EPSILON
                && Math.abs(
                actualMaximum - maximum
        ) <= EPSILON;
    }

    private static boolean hasNonEmptyOrMalformedArray(
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
                || !element.isJsonArray()
                || !element
                .getAsJsonArray()
                .isEmpty();
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

    private static Double getDouble(
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

        return value;
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

        Double value =
                getDouble(
                        object,
                        name
                );

        if (value == null) {
            return null;
        }

        double rounded =
                Math.rint(
                        value
                );

        if (Math.abs(
                value - rounded
        ) > EPSILON
                || rounded < Integer.MIN_VALUE
                || rounded > Integer.MAX_VALUE) {

            return null;
        }

        return (int) rounded;
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

        double value =
                primitive.getAsDouble();

        return !Double.isNaN(
                value
        )
                && !Double.isInfinite(
                value
        )
                && Math.abs(
                value - expected
        ) <= EPSILON;
    }

    private static double clampChance(
            double chance
    ) {
        if (Double.isNaN(
                chance
        )) {

            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        chance
                )
        );
    }

    private record EntryPlan(
            JsonObject entry,
            ResourceLocation itemId,
            int weight
    ) {
    }

    private record PoolPlan(
            JsonObject pool,
            long totalWeight,
            List<EntryPlan> entries
    ) {
    }

    private record TableResult(
            int poolsNormalized,
            int branchesGenerated,
            List<ExpectedYield> expectedYields
    ) {
        private TableResult {
            expectedYields =
                    expectedYields == null
                            ? List.of()
                            : List.copyOf(
                            expectedYields
                    );
        }

        private static TableResult empty() {
            return new TableResult(
                    0,
                    0,
                    List.of()
            );
        }
    }

    public record ExpectedYield(
            ResourceLocation entityTypeId,
            ResourceLocation itemId,
            double expectedAmount
    ) {
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int poolsNormalized,
            int branchesGenerated,
            List<ExpectedYield> expectedYields
    ) {
        public PreparedScan {
            expectedYields =
                    expectedYields == null
                            ? List.of()
                            : List.copyOf(
                            expectedYields
                    );
        }
    }
}