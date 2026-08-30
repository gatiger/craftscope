package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/*
 * Normalizes a very specific vanilla loot-table count pattern that
 * CraftScope's normal DropDefinition can represent exactly.
 *
 * Vanilla uses:
 *
 *     set_count
 *     uniform -1 .. 1
 *
 * for drops such as:
 *
 *     Spider Eye
 *     Cave Spider Eye
 *     Wither Skeleton Coal
 *
 * With no Looting, the three equally possible integer outcomes are:
 *
 *     -1  -> no item
 *      0  -> no item
 *      1  -> one item
 *
 * Therefore the ordinary/base distribution is equivalent to:
 *
 *     one item at 1/3 chance
 *
 * CraftScope already represents that accurately with a random-chance
 * condition followed by its normal CHANCE DropDefinition.
 *
 * This processor recognizes ONLY the exact -1..1 pattern. Other
 * negative ranges remain untouched and will continue to be rejected
 * conservatively by the interpreter.
 */
public final class CraftScopeMobLootNegativeCountProcessor {

    private static final double EPSILON =
            0.0000001D;


    private CraftScopeMobLootNegativeCountProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    0
            );
        }

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        int normalizedCount =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                normalizedCount +=
                        processTable(
                                copied.getAsJsonObject()
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
                normalizedCount
        );
    }

    private static int processTable(
            JsonObject root
    ) {
        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null) {
            return 0;
        }

        int normalizedCount =
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

                normalizedCount +=
                        processEntry(
                                entryElement.getAsJsonObject()
                        );
            }
        }

        return normalizedCount;
    }

    private static int processEntry(
            JsonObject entry
    ) {
        /*
         * Only direct item entries are normalized.
         *
         * Nested loot tables, tags, alternatives, etc. remain under
         * the conservative interpreter rules.
         */
        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

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

        int matchingFunctions =
                0;

        int matchingIndex =
                -1;

        Double normalizedChance =
                null;

        for (int i = 0;
             i < functions.size();
             i++) {

            JsonElement functionElement =
                    functions.get(
                            i
                    );

            Double candidateChance =
                    getNegativeToOneChance(
                            functionElement
                    );

            if (candidateChance != null) {

                matchingFunctions++;

                matchingIndex =
                        i;

                normalizedChance =
                        candidateChance;
            }
        }

        /*
         * Be deliberately strict.
         *
         * If an entry somehow has more than one matching set_count
         * function, function ordering/composition would matter.
         */
        if (matchingFunctions != 1
                || matchingIndex < 0
                || normalizedChance == null) {

            return 0;
        }

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

        entry.add(
                "functions",
                retainedFunctions
        );

        /*
         * Append an ordinary random-chance condition.
         *
         * If the entry already has supported conditions, the normal
         * interpreter multiplies their probabilities, which is the
         * desired behavior.
         */
        JsonArray conditions =
                getArray(
                        entry,
                        "conditions"
                );

        if (conditions == null) {

            if (entry.has(
                    "conditions"
            )) {

                /*
                 * Malformed conditions must stay unsupported.
                 */
                return 0;
            }

            conditions =
                    new JsonArray();
        }

        JsonObject chanceCondition =
                new JsonObject();

        chanceCondition.addProperty(
                "condition",
                "minecraft:random_chance"
        );

        chanceCondition.addProperty(
                "chance",
                normalizedChance
        );

        conditions.add(
                chanceCondition
        );

        entry.add(
                "conditions",
                conditions
        );

        return 1;
    }

    /*
     * Converts a uniform whole-number count range:
     *
     *     minimum .. 1
     *
     * where minimum <= 0 into the probability of producing one
     * positive item.
     *
     * Examples:
     *
     *     -1 .. 1
     *         outcomes: -1, 0, 1
     *         chance of one item = 1/3
     *
     *     -2 .. 1
     *         outcomes: -2, -1, 0, 1
     *         chance of one item = 1/4
     *
     * Non-positive stack counts produce no item, so this conversion
     * preserves the ordinary base distribution exactly.
     */
    private static Double getNegativeToOneChance(
            JsonElement functionElement
    ) {
        if (functionElement == null
                || !functionElement.isJsonObject()) {

            return null;
        }

        JsonObject function =
                functionElement.getAsJsonObject();

        if (!"minecraft:set_count".equals(
                getString(
                        function,
                        "function"
                )
        )) {

            return null;
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

            return null;
        }

        if (conditions != null
                && !conditions.isEmpty()) {

            return null;
        }

        if (getBoolean(
                function,
                "add",
                false
        )) {

            return null;
        }

        JsonObject count =
                getObject(
                        function,
                        "count"
                );

        if (count == null) {
            return null;
        }

        if (!"minecraft:uniform".equals(
                getString(
                        count,
                        "type"
                )
        )) {

            return null;
        }

        Double minimum =
                getDouble(
                        count,
                        "min"
                );

        Double maximum =
                getDouble(
                        count,
                        "max"
                );

        if (minimum == null
                || maximum == null
                || Double.isNaN(minimum)
                || Double.isInfinite(minimum)
                || Double.isNaN(maximum)
                || Double.isInfinite(maximum)) {

            return null;
        }

        double roundedMinimum =
                Math.rint(
                        minimum
                );

        if (Math.abs(
                minimum - roundedMinimum
        ) > EPSILON) {

            return null;
        }

        if (roundedMinimum > 0.0D
                || !approximately(
                maximum,
                1.0D
        )) {

            return null;
        }

        double possibleOutcomes =
                2.0D - roundedMinimum;

        if (possibleOutcomes <= 0.0D) {
            return null;
        }

        return 1.0D
                / possibleOutcomes;
    }
    private static boolean approximately(
            Double value,
            double expected
    ) {
        return value != null
                && !Double.isNaN(
                value
        )
                && !Double.isInfinite(
                value
        )
                && Math.abs(
                value - expected
        ) <= EPSILON;
    }

    private static JsonObject getObject(
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
                || !element.isJsonObject()) {

            return null;
        }

        return element.getAsJsonObject();
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

    private static boolean getBoolean(
            JsonObject object,
            String name,
            boolean defaultValue
    ) {
        if (object == null
                || name == null
                || !object.has(
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

            return defaultValue;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isBoolean()) {

            return defaultValue;
        }

        return primitive.getAsBoolean();
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int normalizedCount
    ) {
    }
}