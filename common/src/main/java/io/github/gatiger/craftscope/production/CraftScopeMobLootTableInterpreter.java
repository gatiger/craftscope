package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.gatiger.craftscope.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Converts authoritative server-loaded entity loot tables into
 * CraftScope runtime mob definitions.
 *
 * SAFETY RULE
 * -----------
 *
 * Runtime definitions replace the COMPLETE definition for a mob.
 *
 * Therefore CraftScope only produces a runtime definition when every
 * relevant part of that mob's loot table is understood.
 *
 * If any pool, condition, entry, or loot function cannot be safely
 * interpreted, the entire mob is skipped and CraftScope's baseline
 * fallback remains available.
 */
public final class CraftScopeMobLootTableInterpreter {

    private static final int DEFAULT_RUNTIME_PRIORITY =
            3400;

    private static final double EPSILON =
            0.0000001D;

    private CraftScopeMobLootTableInterpreter() {
    }

    public static InterpretationResult interpret(
            CraftScopeMobLootTableScanner.ScanResult scanResult
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new InterpretationResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                new ArrayList<>();

        int tablesChecked =
                0;

        int completeMobDefinitions =
                0;

        int partialMobsSkipped =
                0;

        int emptyMobsSkipped =
                0;

        int dropsProduced =
                0;

        int unsupportedPools =
                0;

        int unsupportedEntries =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            tablesChecked++;

            MobInterpretation mob =
                    interpretMob(
                            snapshot
                    );

            unsupportedPools +=
                    mob.unsupportedPools();

            unsupportedEntries +=
                    mob.unsupportedEntries();

            if (!mob.complete()) {

                partialMobsSkipped++;

                continue;
            }

            if (mob.drops().isEmpty()) {

                emptyMobsSkipped++;

                continue;
            }

            definitions.add(
                    new CraftScopeMobDropCatalog.MobDefinition(
                            snapshot.entityTypeId(),
                            snapshot
                                    .entityTypeId()
                                    .getNamespace(),
                            null,
                            findPriority(
                                    snapshot.entityTypeId()
                            ),
                            List.of(),
                            mob.drops()
                    )
            );

            completeMobDefinitions++;

            dropsProduced +=
                    mob.drops().size();
        }

        InterpretationResult result =
                new InterpretationResult(
                        tablesChecked,
                        completeMobDefinitions,
                        partialMobsSkipped,
                        emptyMobsSkipped,
                        dropsProduced,
                        unsupportedPools,
                        unsupportedEntries,
                        definitions
                );

        Constants.LOG.info(
                "CraftScope loot interpretation: {} tables checked, {} complete mob definitions, {} partial mobs skipped, {} empty mobs skipped, {} drops produced, {} unsupported pools, {} unsupported entries",
                result.tablesChecked(),
                result.completeMobDefinitions(),
                result.partialMobsSkipped(),
                result.emptyMobsSkipped(),
                result.dropsProduced(),
                result.unsupportedPools(),
                result.unsupportedEntries()
        );

        return result;
    }

    private static MobInterpretation interpretMob(
            CraftScopeMobLootTableScanner.LootTableSnapshot snapshot
    ) {
        JsonElement rootElement =
                snapshot.json();

        if (rootElement == null
                || !rootElement.isJsonObject()) {

            return MobInterpretation.unsupported();
        }

        JsonObject root =
                rootElement.getAsJsonObject();

        /*
         * Loot-table-level functions can modify every generated
         * stack. We do not model those yet.
         */
        if (hasNonEmptyArray(
                root,
                "functions"
        )) {

            return MobInterpretation.unsupported();
        }

        /*
         * If the field exists but is not an array, treat the table as
         * unsupported instead of silently accepting malformed data.
         */
        if (hasMalformedArray(
                root,
                "functions"
        )) {

            return MobInterpretation.unsupported();
        }

        if (!root.has(
                "pools"
        )) {

            return MobInterpretation.empty();
        }

        JsonArray pools =
                getArray(
                        root,
                        "pools"
                );

        if (pools == null) {

            return MobInterpretation.unsupported();
        }

        if (pools.isEmpty()) {

            return MobInterpretation.empty();
        }

        Map<ResourceLocation, CraftScopeMobDropCatalog.DropDefinition>
                dropsByItem =
                new LinkedHashMap<>();

        Set<ResourceLocation> ambiguousItems =
                new LinkedHashSet<>();

        int unsupportedPools =
                0;

        int unsupportedEntries =
                0;

        boolean complete =
                true;

        for (JsonElement poolElement :
                pools) {

            PoolInterpretation pool =
                    interpretPool(
                            poolElement
                    );

            unsupportedEntries +=
                    pool.unsupportedEntries();

            if (!pool.supported()) {

                unsupportedPools++;

                complete =
                        false;

                continue;
            }

            CraftScopeMobDropCatalog.DropDefinition drop =
                    pool.drop();

            if (drop == null) {
                continue;
            }

            ResourceLocation itemId =
                    drop.itemId();

            if (ambiguousItems.contains(
                    itemId
            )) {

                complete =
                        false;

                continue;
            }

            CraftScopeMobDropCatalog.DropDefinition previous =
                    dropsByItem.get(
                            itemId
                    );

            if (previous == null) {

                dropsByItem.put(
                        itemId,
                        drop
                );

                continue;
            }

            /*
             * Multiple independent pools producing the same item
             * alter its total probability distribution.
             *
             * Even if the individual definitions look identical,
             * CraftScope cannot safely collapse them into one yet.
             */
            dropsByItem.remove(
                    itemId
            );

            ambiguousItems.add(
                    itemId
            );

            complete =
                    false;
        }

        return new MobInterpretation(
                complete,
                List.copyOf(
                        dropsByItem.values()
                ),
                unsupportedPools,
                unsupportedEntries
        );
    }

    private static PoolInterpretation interpretPool(
            JsonElement poolElement
    ) {
        if (poolElement == null
                || !poolElement.isJsonObject()) {

            return PoolInterpretation.unsupported(
                    0
            );
        }

        JsonObject pool =
                poolElement.getAsJsonObject();

        /*
         * Pool-level functions can transform every result generated
         * by this pool.
         *
         * Until those are modeled explicitly, a non-empty function
         * list makes the pool unsupported.
         */
        if (hasNonEmptyArray(
                pool,
                "functions"
        )
                || hasMalformedArray(
                pool,
                "functions"
        )) {

            return PoolInterpretation.unsupported(
                    0
            );
        }

        /*
         * First pass supports exactly one ordinary roll.
         */
        if (!isNumberEqual(
                pool.get(
                        "rolls"
                ),
                1.0D
        )) {

            return PoolInterpretation.unsupported(
                    0
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

            return PoolInterpretation.unsupported(
                    0
            );
        }

        ConditionInterpretation poolConditions =
                interpretConditions(
                        pool
                );

        if (!poolConditions.supported()) {

            return PoolInterpretation.unsupported(
                    0
            );
        }

        if (!pool.has(
                "entries"
        )) {

            return PoolInterpretation.unsupported(
                    0
            );
        }

        JsonArray entries =
                getArray(
                        pool,
                        "entries"
                );

        if (entries == null) {

            return PoolInterpretation.unsupported(
                    0
            );
        }

        if (entries.isEmpty()) {

            return new PoolInterpretation(
                    true,
                    null,
                    0
            );
        }

        /*
         * Several entries inside one pool may be weighted choices,
         * alternatives, groups, or sequences.
         */
        if (entries.size() != 1) {

            return PoolInterpretation.unsupported(
                    entries.size()
            );
        }

        EntryInterpretation entry =
                interpretEntry(
                        entries.get(
                                0
                        ),
                        poolConditions
                );

        if (!entry.supported()) {

            return PoolInterpretation.unsupported(
                    1
            );
        }

        return new PoolInterpretation(
                true,
                entry.drop(),
                0
        );
    }

    private static EntryInterpretation interpretEntry(
            JsonElement entryElement,
            ConditionInterpretation poolConditions
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            return EntryInterpretation.unsupported();
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

            return EntryInterpretation.unsupported();
        }

        String itemName =
                getString(
                        entry,
                        "name"
                );

        ResourceLocation itemId =
                ResourceLocation.tryParse(
                        itemName == null
                                ? ""
                                : itemName
                );

        if (itemId == null) {

            return EntryInterpretation.unsupported();
        }

        ConditionInterpretation entryConditions =
                interpretConditions(
                        entry
                );

        if (!entryConditions.supported()) {

            return EntryInterpretation.unsupported();
        }

        long minimum =
                1L;

        long maximum =
                1L;

        Set<String> requirements =
                new LinkedHashSet<>();

        requirements.addAll(
                poolConditions.requirements()
        );

        requirements.addAll(
                entryConditions.requirements()
        );

        if (hasMalformedArray(
                entry,
                "functions"
        )) {

            return EntryInterpretation.unsupported();
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions != null) {

            for (JsonElement functionElement :
                    functions) {

                if (functionElement == null
                        || !functionElement.isJsonObject()) {

                    return EntryInterpretation.unsupported();
                }

                JsonObject function =
                        functionElement.getAsJsonObject();

                /*
                 * A function with its own conditions is not equivalent
                 * to an unconditional function.
                 *
                 * Example:
                 *
                 *     apply set_count only when X is true
                 *
                 * CraftScope cannot represent that distribution yet,
                 * so reject it rather than applying it globally.
                 */
                if (hasNonEmptyArray(
                        function,
                        "conditions"
                )
                        || hasMalformedArray(
                        function,
                        "conditions"
                )) {

                    return EntryInterpretation.unsupported();
                }

                String functionType =
                        getString(
                                function,
                                "function"
                        );

                if ("minecraft:set_count".equals(
                        functionType
                )) {

                    CountRange range =
                            readCountRange(
                                    function.get(
                                            "count"
                                    )
                            );

                    if (range == null) {

                        return EntryInterpretation.unsupported();
                    }

                    boolean add =
                            getBoolean(
                                    function,
                                    "add",
                                    false
                            );

                    if (add) {

                        minimum +=
                                range.minimum();

                        maximum +=
                                range.maximum();

                    } else {

                        minimum =
                                range.minimum();

                        maximum =
                                range.maximum();
                    }

                    continue;
                }

                if ("minecraft:enchanted_count_increase".equals(
                        functionType
                )) {

                    requirements.add(
                            "Optional: Looting increases drop count"
                    );

                    continue;
                }

                /*
                 * Unknown functions may change:
                 *
                 * - count
                 * - item type
                 * - components
                 * - smelting state
                 * - enchantments
                 * - damage
                 *
                 * Never guess.
                 */
                return EntryInterpretation.unsupported();
            }
        }

        if (minimum < 0L
                || maximum < minimum
                || maximum <= 0L) {

            return EntryInterpretation.unsupported();
        }

        double chance =
                poolConditions.chance()
                        * entryConditions.chance();

        if (!isValidChance(
                chance
        )) {

            return EntryInterpretation.unsupported();
        }

        List<String> requirementList =
                List.copyOf(
                        requirements
                );

        /*
         * Fixed-count probabilistic output:
         *
         *     1 item at 11%
         *
         * remains the ordinary CHANCE representation.
         */
        if (chance < 1.0D - EPSILON
                && minimum == maximum) {

            return new EntryInterpretation(
                    true,
                    new CraftScopeMobDropCatalog.DropDefinition(
                            itemId,
                            CraftScopeMobDropCatalog.DropMode.CHANCE,
                            0L,
                            maximum,
                            maximum,
                            chance,
                            requirementList
                    )
            );
        }

        /*
         * RANGE can also carry a probability.
         *
         * Example after weighted-pool normalization:
         *
         *     Polar Bear Cod
         *         0-2 items
         *         selected 75% of the time
         *
         * DropDefinition already transports min/max/chance
         * independently, so no new network format is required.
         */
        return new EntryInterpretation(
                true,
                new CraftScopeMobDropCatalog.DropDefinition(
                        itemId,
                        CraftScopeMobDropCatalog.DropMode.RANGE,
                        minimum,
                        maximum,
                        0L,
                        chance,
                        requirementList
                )
        );
    }

    private static ConditionInterpretation interpretConditions(
            JsonObject parent
    ) {
        if (parent == null) {

            return ConditionInterpretation.unsupported();
        }

        if (!parent.has(
                "conditions"
        )) {

            return ConditionInterpretation.normal();
        }

        JsonArray conditions =
                getArray(
                        parent,
                        "conditions"
                );

        if (conditions == null) {

            return ConditionInterpretation.unsupported();
        }

        if (conditions.isEmpty()) {

            return ConditionInterpretation.normal();
        }

        double chance =
                1.0D;

        Set<String> requirements =
                new LinkedHashSet<>();

        for (JsonElement conditionElement :
                conditions) {

            if (conditionElement == null
                    || !conditionElement.isJsonObject()) {

                return ConditionInterpretation.unsupported();
            }

            JsonObject condition =
                    conditionElement.getAsJsonObject();

            String conditionType =
                    getString(
                            condition,
                            "condition"
                    );

            if ("minecraft:killed_by_player".equals(
                    conditionType
            )) {

                requirements.add(
                        "Player or tamed-wolf kill"
                );

                continue;
            }

            if ("minecraft:random_chance".equals(
                    conditionType
            )) {

                Double conditionChance =
                        getDouble(
                                condition,
                                "chance"
                        );

                if (!isValidChance(
                        conditionChance
                )) {

                    return ConditionInterpretation.unsupported();
                }

                chance *=
                        conditionChance;

                continue;
            }

            if ("minecraft:random_chance_with_enchanted_bonus".equals(
                    conditionType
            )) {

                Double unenchantedChance =
                        getDouble(
                                condition,
                                "unenchanted_chance"
                        );

                if (!isValidChance(
                        unenchantedChance
                )) {

                    return ConditionInterpretation.unsupported();
                }

                chance *=
                        unenchantedChance;

                requirements.add(
                        "Optional: Looting increases drop chance"
                );

                continue;
            }

            /*
             * Conservative entity-properties support.
             *
             * Vanilla Creeper music discs use:
             *
             *     entity = attacker
             *     predicate = {
             *         type = #minecraft:skeletons
             *     }
             *
             * Only that simple "attacker + entity type/tag" shape is
             * accepted here. Position, equipment, flags, NBT, vehicle,
             * effects, team, distance, etc. remain unsupported.
             */
            if ("minecraft:entity_properties".equals(
                    conditionType
            )) {

                String entityTarget =
                        getString(
                                condition,
                                "entity"
                        );

                JsonElement predicateElement =
                        condition.get(
                                "predicate"
                        );

                if (predicateElement == null
                        || !predicateElement.isJsonObject()) {

                    return ConditionInterpretation.unsupported();
                }

                JsonObject predicate =
                        predicateElement.getAsJsonObject();

                /*
                 * Creeper music-disc condition:
                 *
                 * attacker must match an entity type or entity tag.
                 */
                if ("attacker".equals(
                        entityTarget
                )) {

                    if (predicate.size() != 1) {

                        return ConditionInterpretation.unsupported();
                    }

                    String typePredicate =
                            getString(
                                    predicate,
                                    "type"
                            );

                    if (typePredicate == null
                            || typePredicate.isBlank()) {

                        return ConditionInterpretation.unsupported();
                    }

                    if (typePredicate.startsWith(
                            "#"
                    )) {

                        ResourceLocation tagId =
                                ResourceLocation.tryParse(
                                        typePredicate.substring(
                                                1
                                        )
                                );

                        if (tagId == null) {

                            return ConditionInterpretation.unsupported();
                        }

                        requirements.add(
                                "Killed by attacker matching entity tag "
                                        + tagId
                        );

                    } else {

                        ResourceLocation entityId =
                                ResourceLocation.tryParse(
                                        typePredicate
                                );

                        if (entityId == null) {

                            return ConditionInterpretation.unsupported();
                        }

                        requirements.add(
                                "Killed by "
                                        + entityId
                        );
                    }

                    continue;
                }

                /*
                 * Pillager Ominous Bottle condition:
                 *
                 * this entity must be a Raider Captain.
                 *
                 * Only this exact type_specific predicate is accepted.
                 */
                if ("this".equals(
                        entityTarget
                )
                        && predicate.size() == 1
                        && predicate.has(
                        "type_specific"
                )) {

                    JsonElement typeSpecificElement =
                            predicate.get(
                                    "type_specific"
                            );

                    if (typeSpecificElement == null
                            || !typeSpecificElement.isJsonObject()) {

                        return ConditionInterpretation.unsupported();
                    }

                    JsonObject typeSpecific =
                            typeSpecificElement.getAsJsonObject();

                    if (typeSpecific.size() != 2
                            || !"minecraft:raider".equals(
                            getString(
                                    typeSpecific,
                                    "type"
                            )
                    )) {

                        return ConditionInterpretation.unsupported();
                    }

                    JsonElement captainElement =
                            typeSpecific.get(
                                    "is_captain"
                            );

                    if (captainElement == null
                            || !captainElement.isJsonPrimitive()
                            || !captainElement
                            .getAsJsonPrimitive()
                            .isBoolean()
                            || !captainElement
                            .getAsBoolean()) {

                        return ConditionInterpretation.unsupported();
                    }

                    requirements.add(
                            "Mob must be a Raider Captain"
                    );

                    continue;
                }

                return ConditionInterpretation.unsupported();
            }

            /*
             * Examples currently unsupported:
             *
             * - entity_properties
             * - damage_source_properties
             * - location_check
             * - match_tool
             * - inverted
             * - any_of
             * - all_of
             */
            return ConditionInterpretation.unsupported();
        }

        if (!isValidChance(
                chance
        )) {

            return ConditionInterpretation.unsupported();
        }

        return new ConditionInterpretation(
                true,
                chance,
                List.copyOf(
                        requirements
                )
        );
    }

    private static CountRange readCountRange(
            JsonElement countElement
    ) {
        if (countElement == null) {

            return null;
        }

        if (countElement.isJsonPrimitive()) {

            JsonPrimitive primitive =
                    countElement.getAsJsonPrimitive();

            if (!primitive.isNumber()) {

                return null;
            }

            Long amount =
                    toWholeNonNegative(
                            primitive.getAsDouble()
                    );

            if (amount == null) {

                return null;
            }

            return new CountRange(
                    amount,
                    amount
            );
        }

        if (!countElement.isJsonObject()) {

            return null;
        }

        JsonObject provider =
                countElement.getAsJsonObject();

        String providerType =
                getString(
                        provider,
                        "type"
                );

        if ("minecraft:uniform".equals(
                providerType
        )) {

            Double minimumDouble =
                    getDouble(
                            provider,
                            "min"
                    );

            Double maximumDouble =
                    getDouble(
                            provider,
                            "max"
                    );

            if (minimumDouble == null
                    || maximumDouble == null) {

                return null;
            }

            Long minimum =
                    toWholeNonNegative(
                            minimumDouble
                    );

            Long maximum =
                    toWholeNonNegative(
                            maximumDouble
                    );

            if (minimum == null
                    || maximum == null
                    || maximum < minimum) {

                return null;
            }

            return new CountRange(
                    minimum,
                    maximum
            );
        }

        if ("minecraft:constant".equals(
                providerType
        )) {

            Double value =
                    getDouble(
                            provider,
                            "value"
                    );

            if (value == null) {

                return null;
            }

            Long amount =
                    toWholeNonNegative(
                            value
                    );

            if (amount == null) {

                return null;
            }

            return new CountRange(
                    amount,
                    amount
            );
        }

        return null;
    }

    private static int findPriority(
            ResourceLocation entityTypeId
    ) {
        for (CraftScopeMobDropCatalog.MobDefinition baseline :
                CraftScopeMobDropCatalog.getBaselineDefinitions()) {

            if (baseline
                    .entityTypeId()
                    .equals(
                            entityTypeId
                    )) {

                return baseline.priority();
            }
        }

        return DEFAULT_RUNTIME_PRIORITY;
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

    private static boolean isValidChance(
            Double chance
    ) {
        return chance != null
                && !Double.isNaN(
                chance
        )
                && !Double.isInfinite(
                chance
        )
                && chance >= 0.0D
                && chance <= 1.0D;
    }

    private static Long toWholeNonNegative(
            double value
    ) {
        if (Double.isNaN(
                value
        )
                || Double.isInfinite(
                value
        )
                || value < 0.0D) {

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

        if (rounded > Long.MAX_VALUE) {

            return null;
        }

        return (long) rounded;
    }

    private record CountRange(
            long minimum,
            long maximum
    ) {
    }

    private record ConditionInterpretation(
            boolean supported,
            double chance,
            List<String> requirements
    ) {

        private static ConditionInterpretation normal() {
            return new ConditionInterpretation(
                    true,
                    1.0D,
                    List.of()
            );
        }

        private static ConditionInterpretation unsupported() {
            return new ConditionInterpretation(
                    false,
                    0.0D,
                    List.of()
            );
        }
    }

    private record EntryInterpretation(
            boolean supported,
            CraftScopeMobDropCatalog.DropDefinition drop
    ) {

        private static EntryInterpretation unsupported() {
            return new EntryInterpretation(
                    false,
                    null
            );
        }
    }

    private record PoolInterpretation(
            boolean supported,
            CraftScopeMobDropCatalog.DropDefinition drop,
            int unsupportedEntries
    ) {

        private static PoolInterpretation unsupported(
                int unsupportedEntries
        ) {
            return new PoolInterpretation(
                    false,
                    null,
                    unsupportedEntries
            );
        }
    }

    private record MobInterpretation(
            boolean complete,
            List<CraftScopeMobDropCatalog.DropDefinition> drops,
            int unsupportedPools,
            int unsupportedEntries
    ) {

        private static MobInterpretation empty() {
            return new MobInterpretation(
                    true,
                    List.of(),
                    0,
                    0
            );
        }

        private static MobInterpretation unsupported() {
            return new MobInterpretation(
                    false,
                    List.of(),
                    1,
                    0
            );
        }
    }

    public record InterpretationResult(
            int tablesChecked,
            int completeMobDefinitions,
            int partialMobsSkipped,
            int emptyMobsSkipped,
            int dropsProduced,
            int unsupportedPools,
            int unsupportedEntries,
            List<CraftScopeMobDropCatalog.MobDefinition> definitions
    ) {

        public InterpretationResult {
            definitions =
                    definitions == null
                            ? List.of()
                            : List.copyOf(
                            definitions
                    );
        }

        public boolean isEmpty() {
            return definitions.isEmpty();
        }
    }
}