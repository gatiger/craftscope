package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.gatiger.craftscope.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * Diagnostic helper for CraftScope's conservative mob-loot interpreter.
 *
 * This class does NOT change interpretation behavior.
 *
 * It reports which live loot-table structures are preventing more mobs
 * from being interpreted completely.
 */
public final class CraftScopeMobLootUnsupportedDiagnostics {

    private static final Set<String> SUPPORTED_CONDITIONS =
            Set.of(
                    "minecraft:killed_by_player",
                    "minecraft:random_chance",
                    "minecraft:random_chance_with_enchanted_bonus"
            );

    private static final Set<String> SUPPORTED_ENTRY_FUNCTIONS =
            Set.of(
                    "minecraft:set_count",
                    "minecraft:enchanted_count_increase"
            );

    private CraftScopeMobLootUnsupportedDiagnostics() {
    }

    public static void log(
            CraftScopeMobLootTableScanner.ScanResult result
    ) {
        if (result == null
                || result.isEmpty()) {

            return;
        }

        Map<String, Integer> entryTypes =
                new TreeMap<>();

        Map<String, Integer> conditionTypes =
                new TreeMap<>();

        Map<String, Integer> functionTypes =
                new TreeMap<>();

        Map<String, Integer> functionConditionTypes =
                new TreeMap<>();

        Map<String, Integer> structuralReasons =
                new TreeMap<>();

        List<String> furnaceSmeltCases =
                new ArrayList<>();

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                result.lootTables()) {

            JsonElement rootElement =
                    snapshot.json();

            if (rootElement == null
                    || !rootElement.isJsonObject()) {

                increment(
                        structuralReasons,
                        "non-object loot table"
                );

                continue;
            }

            JsonObject root =
                    rootElement.getAsJsonObject();

            scanFunctions(
                    snapshot.entityTypeId().toString(),
                    null,
                    root,
                    "table",
                    functionTypes,
                    functionConditionTypes,
                    structuralReasons,
                    furnaceSmeltCases
            );

            if (!root.has(
                    "pools"
            )) {

                continue;
            }

            JsonArray pools =
                    getArray(
                            root,
                            "pools"
                    );

            if (pools == null) {

                increment(
                        structuralReasons,
                        "malformed pools field"
                );

                continue;
            }

            Map<String, Integer> directItemsSeen =
                    new TreeMap<>();

            for (JsonElement poolElement :
                    pools) {

                if (poolElement == null
                        || !poolElement.isJsonObject()) {

                    increment(
                            structuralReasons,
                            "non-object pool"
                    );

                    continue;
                }

                scanPool(
                        snapshot.entityTypeId().toString(),
                        poolElement.getAsJsonObject(),
                        directItemsSeen,
                        entryTypes,
                        conditionTypes,
                        functionTypes,
                        functionConditionTypes,
                        structuralReasons,
                        furnaceSmeltCases
                );
            }
        }

        Constants.LOG.info(
                "CraftScope unsupported entry types: {}",
                format(
                        entryTypes
                )
        );

        Constants.LOG.info(
                "CraftScope unsupported condition types: {}",
                format(
                        conditionTypes
                )
        );

        Constants.LOG.info(
                "CraftScope unsupported function types: {}",
                format(
                        functionTypes
                )
        );

        Constants.LOG.info(
                "CraftScope unsupported function-condition types: {}",
                format(
                        functionConditionTypes
                )
        );

        Constants.LOG.info(
                "CraftScope unsupported structural cases: {}",
                format(
                        structuralReasons
                )
        );

        if (furnaceSmeltCases.isEmpty()) {

            Constants.LOG.info(
                    "CraftScope furnace-smelt cases: none"
            );

        } else {

            for (String furnaceSmeltCase :
                    furnaceSmeltCases) {

                Constants.LOG.info(
                        "CraftScope furnace-smelt case: {}",
                        furnaceSmeltCase
                );
            }
        }
    }

    private static void scanPool(
            String entityId,
            JsonObject pool,
            Map<String, Integer> directItemsSeen,
            Map<String, Integer> entryTypes,
            Map<String, Integer> conditionTypes,
            Map<String, Integer> functionTypes,
            Map<String, Integer> functionConditionTypes,
            Map<String, Integer> structuralReasons,
            List<String> furnaceSmeltCases
    ) {
        scanFunctions(
                entityId,
                null,
                pool,
                "pool",
                functionTypes,
                functionConditionTypes,
                structuralReasons,
                furnaceSmeltCases
        );

        scanConditions(
                pool,
                conditionTypes,
                structuralReasons
        );

        JsonElement rolls =
                pool.get(
                        "rolls"
                );

        if (!isNumberEqual(
                rolls,
                1.0D
        )) {

            increment(
                    structuralReasons,
                    "non-single pool rolls: "
                            + describeProvider(
                            rolls
                    )
            );
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

            increment(
                    structuralReasons,
                    "non-zero bonus rolls: "
                            + describeProvider(
                            bonusRolls
                    )
            );
        }

        if (!pool.has(
                "entries"
        )) {

            increment(
                    structuralReasons,
                    "pool missing entries"
            );

            return;
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null) {

            increment(
                    structuralReasons,
                    "malformed entries field"
            );

            return;
        }

        if (entries.size() != 1) {

            increment(
                    structuralReasons,
                    "multiple entries in pool ("
                            + entries.size()
                            + ")"
            );
        }

        boolean poolHasChance =
                hasChanceCondition(
                        pool
                );

        for (JsonElement entryElement :
                entries) {

            scanEntry(
                    entityId,
                    entryElement,
                    poolHasChance,
                    directItemsSeen,
                    entryTypes,
                    conditionTypes,
                    functionTypes,
                    functionConditionTypes,
                    structuralReasons,
                    furnaceSmeltCases
            );
        }
    }

    private static void scanEntry(
            String entityId,
            JsonElement entryElement,
            boolean poolHasChance,
            Map<String, Integer> directItemsSeen,
            Map<String, Integer> entryTypes,
            Map<String, Integer> conditionTypes,
            Map<String, Integer> functionTypes,
            Map<String, Integer> functionConditionTypes,
            Map<String, Integer> structuralReasons,
            List<String> furnaceSmeltCases
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            increment(
                    structuralReasons,
                    "non-object entry"
            );

            return;
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
        )) {

            increment(
                    entryTypes,
                    entryType == null
                            ? "<missing>"
                            : entryType
            );
        }

        String itemName =
                null;

        if ("minecraft:item".equals(
                entryType
        )) {

            itemName =
                    getString(
                            entry,
                            "name"
                    );

            if (itemName != null) {

                int count =
                        directItemsSeen.merge(
                                itemName,
                                1,
                                Integer::sum
                        );

                if (count > 1) {

                    increment(
                            structuralReasons,
                            "same item appears in multiple pools"
                    );
                }
            }
        }

        scanConditions(
                entry,
                conditionTypes,
                structuralReasons
        );

        scanFunctions(
                entityId,
                itemName,
                entry,
                "entry",
                functionTypes,
                functionConditionTypes,
                structuralReasons,
                furnaceSmeltCases
        );

        boolean entryHasChance =
                hasChanceCondition(
                        entry
                );

        if ((poolHasChance
                || entryHasChance)
                && hasVariableSetCount(
                entry
        )) {

            increment(
                    structuralReasons,
                    "variable count combined with chance"
            );
        }
    }

    private static void scanConditions(
            JsonObject parent,
            Map<String, Integer> conditionTypes,
            Map<String, Integer> structuralReasons
    ) {
        if (!parent.has(
                "conditions"
        )) {

            return;
        }

        JsonArray conditions =
                getArray(
                        parent,
                        "conditions"
                );

        if (conditions == null) {

            increment(
                    structuralReasons,
                    "malformed conditions field"
            );

            return;
        }

        for (JsonElement conditionElement :
                conditions) {

            if (conditionElement == null
                    || !conditionElement.isJsonObject()) {

                increment(
                        structuralReasons,
                        "non-object condition"
                );

                continue;
            }

            JsonObject condition =
                    conditionElement.getAsJsonObject();

            String conditionType =
                    getString(
                            condition,
                            "condition"
                    );

            if (conditionType == null) {

                increment(
                        conditionTypes,
                        "<missing>"
                );

                continue;
            }

            if (!SUPPORTED_CONDITIONS.contains(
                    conditionType
            )) {

                increment(
                        conditionTypes,
                        conditionType
                );
            }
        }
    }

    private static void scanFunctions(
            String entityId,
            String itemName,
            JsonObject parent,
            String scope,
            Map<String, Integer> functionTypes,
            Map<String, Integer> functionConditionTypes,
            Map<String, Integer> structuralReasons,
            List<String> furnaceSmeltCases
    ) {
        if (!parent.has(
                "functions"
        )) {

            return;
        }

        JsonArray functions =
                getArray(
                        parent,
                        "functions"
                );

        if (functions == null) {

            increment(
                    structuralReasons,
                    "malformed "
                            + scope
                            + " functions field"
            );

            return;
        }

        if (!functions.isEmpty()
                && !"entry".equals(
                scope
        )) {

            increment(
                    structuralReasons,
                    scope
                            + "-level functions"
            );
        }

        for (JsonElement functionElement :
                functions) {

            if (functionElement == null
                    || !functionElement.isJsonObject()) {

                increment(
                        structuralReasons,
                        "non-object "
                                + scope
                                + " function"
                );

                continue;
            }

            JsonObject function =
                    functionElement.getAsJsonObject();

            String functionType =
                    getString(
                            function,
                            "function"
                    );

            if (functionType == null) {

                increment(
                        functionTypes,
                        scope
                                + ":<missing>"
                );

                continue;
            }

            if (!"entry".equals(
                    scope
            )
                    || !SUPPORTED_ENTRY_FUNCTIONS.contains(
                    functionType
            )) {

                increment(
                        functionTypes,
                        scope
                                + ":"
                                + functionType
                );
            }

            JsonArray functionConditions =
                    getArray(
                            function,
                            "conditions"
                    );

            if (function.has(
                    "conditions"
            )
                    && functionConditions == null) {

                increment(
                        structuralReasons,
                        "malformed function conditions field"
                );
            }

            if (functionConditions != null
                    && !functionConditions.isEmpty()) {

                increment(
                        structuralReasons,
                        "conditional loot function"
                );

                for (JsonElement conditionElement :
                        functionConditions) {

                    scanFunctionCondition(
                            conditionElement,
                            functionConditionTypes
                    );
                }
            }

            if ("minecraft:furnace_smelt".equals(
                    functionType
            )) {

                furnaceSmeltCases.add(
                        entityId
                                + " | item="
                                + (
                                itemName == null
                                        ? "<unknown>"
                                        : itemName
                        )
                                + " | conditions="
                                + describeConditions(
                                functionConditions
                        )
                );
            }

            if ("minecraft:set_count".equals(
                    functionType
            )) {

                JsonElement count =
                        function.get(
                                "count"
                        );

                if (!isSupportedCountProvider(
                        count
                )) {

                    increment(
                            structuralReasons,
                            "unsupported count provider: "
                                    + describeProvider(
                                    count
                            )
                    );
                }
            }
        }
    }

    private static void scanFunctionCondition(
            JsonElement conditionElement,
            Map<String, Integer> functionConditionTypes
    ) {
        if (conditionElement == null
                || !conditionElement.isJsonObject()) {

            increment(
                    functionConditionTypes,
                    "<non-object>"
            );

            return;
        }

        JsonObject condition =
                conditionElement.getAsJsonObject();

        String conditionType =
                getString(
                        condition,
                        "condition"
                );

        increment(
                functionConditionTypes,
                conditionType == null
                        ? "<missing>"
                        : conditionType
        );
    }

    private static String describeConditions(
            JsonArray conditions
    ) {
        if (conditions == null
                || conditions.isEmpty()) {

            return "none";
        }

        List<String> descriptions =
                new ArrayList<>();

        for (JsonElement conditionElement :
                conditions) {

            if (conditionElement == null) {

                descriptions.add(
                        "<null>"
                );

                continue;
            }

            descriptions.add(
                    conditionElement.toString()
            );
        }

        return String.join(
                " + ",
                descriptions
        );
    }

    private static boolean hasChanceCondition(
            JsonObject parent
    ) {
        JsonArray conditions =
                getArray(
                        parent,
                        "conditions"
                );

        if (conditions == null) {

            return false;
        }

        for (JsonElement conditionElement :
                conditions) {

            if (conditionElement == null
                    || !conditionElement.isJsonObject()) {

                continue;
            }

            String conditionType =
                    getString(
                            conditionElement.getAsJsonObject(),
                            "condition"
                    );

            if ("minecraft:random_chance".equals(
                    conditionType
            )
                    || "minecraft:random_chance_with_enchanted_bonus".equals(
                    conditionType
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean hasVariableSetCount(
            JsonObject entry
    ) {
        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null) {

            return false;
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

            JsonElement count =
                    function.get(
                            "count"
                    );

            if (count == null
                    || !count.isJsonObject()) {

                continue;
            }

            JsonObject provider =
                    count.getAsJsonObject();

            if (!"minecraft:uniform".equals(
                    getString(
                            provider,
                            "type"
                    )
            )) {

                continue;
            }

            Double minimum =
                    getDouble(
                            provider,
                            "min"
                    );

            Double maximum =
                    getDouble(
                            provider,
                            "max"
                    );

            if (minimum != null
                    && maximum != null
                    && Math.abs(
                    minimum - maximum
            ) > 0.0000001D) {

                return true;
            }
        }

        return false;
    }

    private static boolean isSupportedCountProvider(
            JsonElement count
    ) {
        if (count == null) {

            return false;
        }

        if (count.isJsonPrimitive()) {

            return count
                    .getAsJsonPrimitive()
                    .isNumber();
        }

        if (!count.isJsonObject()) {

            return false;
        }

        String type =
                getString(
                        count.getAsJsonObject(),
                        "type"
                );

        return "minecraft:uniform".equals(
                type
        )
                || "minecraft:constant".equals(
                type
        );
    }

    private static String describeProvider(
            JsonElement element
    ) {
        if (element == null) {

            return "<missing>";
        }

        if (element.isJsonPrimitive()) {

            return element.toString();
        }

        if (element.isJsonObject()) {

            String type =
                    getString(
                            element.getAsJsonObject(),
                            "type"
                    );

            return type == null
                    ? "<object>"
                    : type;
        }

        if (element.isJsonArray()) {

            return "<array>";
        }

        return "<unknown>";
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
                || !element.isJsonPrimitive()
                || !element
                .getAsJsonPrimitive()
                .isNumber()) {

            return null;
        }

        return element.getAsDouble();
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
        ) <= 0.0000001D;
    }

    private static void increment(
            Map<String, Integer> counts,
            String key
    ) {
        counts.merge(
                key,
                1,
                Integer::sum
        );
    }

    private static String format(
            Map<String, Integer> counts
    ) {
        if (counts.isEmpty()) {

            return "none";
        }

        return counts
                .entrySet()
                .stream()
                .sorted(
                        (left, right) -> {

                            int countCompare =
                                    Integer.compare(
                                            right.getValue(),
                                            left.getValue()
                                    );

                            if (countCompare != 0) {

                                return countCompare;
                            }

                            return left
                                    .getKey()
                                    .compareTo(
                                            right.getKey()
                                    );
                        }
                )
                .map(
                        entry ->
                                entry.getKey()
                                        + "="
                                        + entry.getValue()
                )
                .reduce(
                        (left, right) ->
                                left
                                        + ", "
                                        + right
                )
                .orElse(
                        "none"
                );
    }
}