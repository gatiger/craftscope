package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.gatiger.craftscope.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/*
 * Temporary development diagnostic.
 *
 * The normal unsupported diagnostics tell us HOW MANY unsupported
 * structures exist.
 *
 * This diagnostic tells us WHICH MOB is blocked by WHICH structures.
 *
 * It deliberately does not change interpretation behavior.
 */
public final class CraftScopeMobLootPartialDiagnostics {

    private static final double EPSILON =
            0.0000001D;

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

    private CraftScopeMobLootPartialDiagnostics() {
    }

    public static void log(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            CraftScopeMobLootTableInterpreter.InterpretationResult
                    interpretation
    ) {
        if (scanResult == null
                || scanResult.isEmpty()
                || interpretation == null) {

            return;
        }

        Set<ResourceLocation> completeMobs =
                new LinkedHashSet<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                interpretation.definitions()) {

            completeMobs.add(
                    definition.entityTypeId()
            );
        }

        int partialMobsLogged =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            if (completeMobs.contains(
                    snapshot.entityTypeId()
            )) {

                continue;
            }

            Set<String> reasons =
                    new LinkedHashSet<>();

            scanTable(
                    snapshot.json(),
                    reasons
            );

            /*
             * Empty loot tables are also absent from interpreted
             * definitions, but they are not partial/unsupported mobs.
             */
            if (reasons.isEmpty()) {
                continue;
            }

            partialMobsLogged++;

            Constants.LOG.info(
                    "CraftScope partial mob {} -> {}",
                    snapshot.entityTypeId(),
                    String.join(
                            "; ",
                            reasons
                    )
            );
        }

        Constants.LOG.info(
                "CraftScope partial-mob diagnostic: {} mobs with blockers",
                partialMobsLogged
        );
    }

    private static void scanTable(
            JsonElement rootElement,
            Set<String> reasons
    ) {
        if (rootElement == null
                || !rootElement.isJsonObject()) {

            reasons.add(
                    "loot table is not an object"
            );

            return;
        }

        JsonObject root =
                rootElement.getAsJsonObject();

        if (hasMalformedArray(
                root,
                "functions"
        )) {

            reasons.add(
                    "malformed table functions"
            );
        }

        if (hasNonEmptyArray(
                root,
                "functions"
        )) {

            reasons.add(
                    "table-level functions"
            );
        }

        if (!root.has(
                "pools"
        )) {

            return;
        }

        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null) {

            reasons.add(
                    "malformed pools"
            );

            return;
        }

        if (pools.isEmpty()) {
            return;
        }

        Map<String, Integer> directItemsSeen =
                new LinkedHashMap<>();

        for (JsonElement poolElement :
                pools) {

            scanPool(
                    poolElement,
                    directItemsSeen,
                    reasons
            );
        }
    }

    private static void scanPool(
            JsonElement poolElement,
            Map<String, Integer> directItemsSeen,
            Set<String> reasons
    ) {
        if (poolElement == null
                || !poolElement.isJsonObject()) {

            reasons.add(
                    "pool is not an object"
            );

            return;
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        if (hasMalformedArray(
                pool,
                "functions"
        )) {

            reasons.add(
                    "malformed pool functions"
            );
        }

        if (hasNonEmptyArray(
                pool,
                "functions"
        )) {

            reasons.add(
                    "pool-level functions"
            );
        }

        JsonElement rolls =
                pool.get(
                        "rolls"
                );

        if (!isNumberEqual(
                rolls,
                1.0D
        )) {

            reasons.add(
                    "pool rolls="
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

            reasons.add(
                    "bonus rolls="
                            + describeProvider(
                            bonusRolls
                    )
            );
        }

        scanConditions(
                pool,
                reasons
        );

        if (!pool.has(
                "entries"
        )) {

            reasons.add(
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

            reasons.add(
                    "malformed entries"
            );

            return;
        }

        if (entries.isEmpty()) {

            /*
             * Empty pool is valid and produces nothing.
             */
            return;
        }

        if (entries.size() != 1) {

            reasons.add(
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
                    entryElement,
                    poolHasChance,
                    directItemsSeen,
                    reasons
            );
        }
    }

    private static void scanEntry(
            JsonElement entryElement,
            boolean poolHasChance,
            Map<String, Integer> directItemsSeen,
            Set<String> reasons
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            reasons.add(
                    "entry is not an object"
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

            reasons.add(
                    "entry type "
                            + (
                            entryType == null
                                    ? "<missing>"
                                    : entryType
                    )
            );

        } else {

            String itemName =
                    getString(
                            entry,
                            "name"
                    );

            if (itemName == null) {

                reasons.add(
                        "item entry missing name"
                );

            } else {

                int count =
                        directItemsSeen.merge(
                                itemName,
                                1,
                                Integer::sum
                        );

                if (count > 1) {

                    reasons.add(
                            "same item appears in multiple pools: "
                                    + itemName
                    );
                }
            }
        }

        scanConditions(
                entry,
                reasons
        );

        if (hasMalformedArray(
                entry,
                "functions"
        )) {

            reasons.add(
                    "malformed entry functions"
            );

            return;
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null) {
            return;
        }

        boolean entryHasChance =
                hasChanceCondition(
                        entry
                );

        for (JsonElement functionElement :
                functions) {

            scanEntryFunction(
                    functionElement,
                    reasons
            );
        }

        if ((poolHasChance
                || entryHasChance)
                && hasVariableSetCount(
                entry
        )) {

            reasons.add(
                    "variable count combined with chance"
            );
        }
    }

    private static void scanEntryFunction(
            JsonElement functionElement,
            Set<String> reasons
    ) {
        if (functionElement == null
                || !functionElement.isJsonObject()) {

            reasons.add(
                    "entry function is not an object"
            );

            return;
        }

        JsonObject function =
                functionElement.getAsJsonObject();

        String functionType =
                getString(
                        function,
                        "function"
                );

        if (functionType == null) {

            reasons.add(
                    "entry function type <missing>"
            );

            return;
        }

        JsonArray conditions =
                getArray(
                        function,
                        "conditions"
                );

        if (function.has(
                "conditions"
        )
                && conditions == null) {

            reasons.add(
                    "malformed function conditions"
            );
        }

        if (conditions != null
                && !conditions.isEmpty()) {

            reasons.add(
                    "conditional function "
                            + functionType
            );
        }

        if (!SUPPORTED_ENTRY_FUNCTIONS.contains(
                functionType
        )) {

            reasons.add(
                    "function "
                            + functionType
            );

            return;
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

                reasons.add(
                        "unsupported set_count provider "
                                + describeProvider(
                                count
                        )
                );
            }
        }
    }

    private static void scanConditions(
            JsonObject parent,
            Set<String> reasons
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

            reasons.add(
                    "malformed conditions"
            );

            return;
        }

        for (JsonElement conditionElement :
                conditions) {

            if (conditionElement == null
                    || !conditionElement.isJsonObject()) {

                reasons.add(
                        "condition is not an object"
                );

                continue;
            }

            String conditionType =
                    getString(
                            conditionElement.getAsJsonObject(),
                            "condition"
                    );

            if (conditionType == null) {

                reasons.add(
                        "condition type <missing>"
                );

                continue;
            }

            if (!SUPPORTED_CONDITIONS.contains(
                    conditionType
            )) {

                reasons.add(
                        "condition "
                                + conditionType
                );
            }
        }
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
                    || "minecraft:random_chance_with_enchanted_bonus"
                    .equals(
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
            ) > EPSILON) {

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

            JsonPrimitive primitive =
                    count.getAsJsonPrimitive();

            return primitive.isNumber();
        }

        if (!count.isJsonObject()) {
            return false;
        }

        JsonObject provider =
                count.getAsJsonObject();

        String type =
                getString(
                        provider,
                        "type"
                );

        if ("minecraft:constant".equals(
                type
        )) {

            return getDouble(
                    provider,
                    "value"
            ) != null;
        }

        if ("minecraft:uniform".equals(
                type
        )) {

            return getDouble(
                    provider,
                    "min"
            ) != null
                    && getDouble(
                    provider,
                    "max"
            ) != null;
        }

        return false;
    }

    private static boolean hasNonEmptyArray(
            JsonObject object,
            String name
    ) {
        JsonArray array =
                getArray(
                        object,
                        name
                );

        return array != null
                && !array.isEmpty();
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

        return primitive.getAsDouble();
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
                    ? "object"
                    : type;
        }

        if (element.isJsonArray()) {
            return "array";
        }

        return element.toString();
    }
}
