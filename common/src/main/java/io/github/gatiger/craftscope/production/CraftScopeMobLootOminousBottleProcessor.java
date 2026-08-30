package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Resolves minecraft:set_ominous_bottle_amplifier into
 * component-aware CraftScope item identities.
 *
 * Vanilla Pillager loot uses:
 *
 *     ominous_bottle
 *         amplifier = uniform 0..4
 *
 * Each amplifier value represents a distinct ItemStack identity.
 *
 * CraftScope models the uniform provider as multiple marginal
 * outcomes:
 *
 *     amplifier 0 -> 20%
 *     amplifier 1 -> 20%
 *     amplifier 2 -> 20%
 *     amplifier 3 -> 20%
 *     amplifier 4 -> 20%
 *
 * The loot function is removed from the copied JSON before the core
 * interpreter runs. After interpretation, the one generic drop is
 * replaced with one component-aware DropDefinition per amplifier.
 *
 * This keeps the ordinary loot interpreter simple while preserving
 * exact Minecraft item-component identity.
 */
public final class CraftScopeMobLootOminousBottleProcessor {

    private static final int MAX_VARIANTS =
            64;

    private CraftScopeMobLootOminousBottleProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    List.of(),
                    0,
                    0
            );
        }

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        List<Assignment> assignments =
                new ArrayList<>();

        int functionsResolved =
                0;

        int identitiesGenerated =
                0;

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                TableResult result =
                        processTable(
                                snapshot.entityTypeId(),
                                copied.getAsJsonObject(),
                                assignments
                        );

                functionsResolved +=
                        result.functionsResolved();

                identitiesGenerated +=
                        result.identitiesGenerated();
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
                functionsResolved,
                identitiesGenerated
        );
    }

    public static CraftScopeMobLootTableInterpreter.InterpretationResult
    applyVariants(
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

        int producedDropCount =
                0;

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                interpretation.definitions()) {

            boolean changed =
                    false;

            List<CraftScopeMobDropCatalog.DropDefinition> drops =
                    new ArrayList<>();

            for (CraftScopeMobDropCatalog.DropDefinition drop :
                    definition.drops()) {

                Assignment assignment =
                        findUniqueAssignment(
                                prepared.assignments(),
                                definition.entityTypeId(),
                                drop.itemId()
                        );

                if (assignment == null
                        || drop.itemIdentity() != null) {

                    drops.add(
                            drop
                    );

                    continue;
                }

                for (Variant variant :
                        assignment.variants()) {

                    double combinedChance =
                            drop.chance()
                                    * variant.chance();

                    if (!(combinedChance > 0.0D)
                            || combinedChance > 1.0D
                            || Double.isNaN(
                            combinedChance
                    )
                            || Double.isInfinite(
                            combinedChance
                    )) {

                        continue;
                    }

                    drops.add(
                            new CraftScopeMobDropCatalog.DropDefinition(
                                    drop.itemId(),
                                    drop.mode(),
                                    drop.minimum(),
                                    drop.maximum(),
                                    drop.amount(),
                                    combinedChance,
                                    drop.targetRequirements(),
                                    drop.transformedItemId(),
                                    drop.baseTransformationRequirements(),
                                    drop.transformationRequirements(),
                                    variant.identity(),
                                    drop.transformedItemIdentity()
                            )
                    );

                    producedDropCount++;
                }

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

        /*
         * dropsProduced is primarily diagnostic information.
         *
         * The interpreter originally counted one generic output for
         * each resolved amplifier function. Replace that one output
         * with the actual number of component-aware variants.
         */
        int adjustedDropCount =
                interpretation.dropsProduced();

        for (Assignment assignment :
                prepared.assignments()) {

            if (!assignment.variants().isEmpty()) {

                adjustedDropCount -=
                        1;

                adjustedDropCount +=
                        assignment.variants().size();
            }
        }

        if (adjustedDropCount < 0) {
            adjustedDropCount =
                    producedDropCount;
        }

        return new CraftScopeMobLootTableInterpreter.InterpretationResult(
                interpretation.tablesChecked(),
                interpretation.completeMobDefinitions(),
                interpretation.partialMobsSkipped(),
                interpretation.emptyMobsSkipped(),
                adjustedDropCount,
                interpretation.unsupportedPools(),
                interpretation.unsupportedEntries(),
                definitions
        );
    }

    private static TableResult processTable(
            ResourceLocation entityTypeId,
            JsonObject root,
            List<Assignment> assignments
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

        int functionsResolved =
                0;

        int identitiesGenerated =
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

            if (entries == null
                    || entries.isEmpty()) {

                continue;
            }

            for (JsonElement entryElement :
                    entries) {

                if (entryElement == null
                        || !entryElement.isJsonObject()) {

                    continue;
                }

                EntryResult result =
                        processEntry(
                                entityTypeId,
                                entryElement.getAsJsonObject(),
                                assignments
                        );

                if (result.resolved()) {

                    functionsResolved++;

                    identitiesGenerated +=
                            result.variantCount();
                }
            }
        }

        return new TableResult(
                functionsResolved,
                identitiesGenerated
        );
    }

    private static EntryResult processEntry(
            ResourceLocation entityTypeId,
            JsonObject entry,
            List<Assignment> assignments
    ) {
        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return EntryResult.notResolved();
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
            return EntryResult.notResolved();
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null
                || functions.isEmpty()) {

            return EntryResult.notResolved();
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

            if ("minecraft:set_ominous_bottle_amplifier".equals(
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
         * Multiple amplifier functions require function-order
         * semantics. Leave them unsupported.
         */
        if (matchingCount != 1
                || matchingIndex < 0) {

            return EntryResult.notResolved();
        }

        JsonElement functionElement =
                functions.get(
                        matchingIndex
                );

        if (functionElement == null
                || !functionElement.isJsonObject()) {

            return EntryResult.notResolved();
        }

        JsonObject function =
                functionElement.getAsJsonObject();

        /*
         * Conditional component assignment can yield both modified
         * and unmodified stacks from one entry. That needs explicit
         * branching and is intentionally left unsupported.
         */
        if (hasNonEmptyArray(
                function,
                "conditions"
        )
                || hasMalformedArray(
                function,
                "conditions"
        )) {

            return EntryResult.notResolved();
        }

        IntRange amplifierRange =
                readUniformIntegerRange(
                        function.get(
                                "amplifier"
                        )
                );

        if (amplifierRange == null) {

            return EntryResult.notResolved();
        }

        long variantCountLong =
                (long) amplifierRange.maximum()
                        - (long) amplifierRange.minimum()
                        + 1L;

        if (variantCountLong <= 0L
                || variantCountLong > MAX_VARIANTS) {

            return EntryResult.notResolved();
        }

        int variantCount =
                (int) variantCountLong;

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

            return EntryResult.notResolved();
        }

        double variantChance =
                1.0D
                        / (double) variantCount;

        List<Variant> variants =
                new ArrayList<>();

        for (int amplifier =
             amplifierRange.minimum();
             amplifier <= amplifierRange.maximum();
             amplifier++) {

            ItemStack stack =
                    new ItemStack(
                            item
                    );

            stack.set(
                    DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
                    amplifier
            );

            CraftScopeItemIdentity identity =
                    CraftScopeItemIdentity.fromStack(
                            stack
                    );

            variants.add(
                    new Variant(
                            amplifier,
                            variantChance,
                            identity
                    )
            );
        }

        /*
         * Remove only the function we have completely modeled.
         *
         * set_count and any other understood functions remain for the
         * normal interpreter.
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
                new Assignment(
                        entityTypeId,
                        itemId,
                        variants
                )
        );

        return new EntryResult(
                true,
                variants.size()
        );
    }

    private static Assignment findUniqueAssignment(
            List<Assignment> assignments,
            ResourceLocation entityTypeId,
            ResourceLocation itemId
    ) {
        Assignment result =
                null;

        for (Assignment assignment :
                assignments) {

            if (!entityTypeId.equals(
                    assignment.entityTypeId()
            )
                    || !itemId.equals(
                    assignment.itemId()
            )) {

                continue;
            }

            if (result != null) {

                /*
                 * Two independently amplified instances of the same
                 * item for one mob need a richer distribution model.
                 */
                return null;
            }

            result =
                    assignment;
        }

        return result;
    }

    private static IntRange readUniformIntegerRange(
            JsonElement element
    ) {
        if (element == null
                || !element.isJsonObject()) {

            return null;
        }

        JsonObject provider =
                element.getAsJsonObject();

        if (!"minecraft:uniform".equals(
                getString(
                        provider,
                        "type"
                )
        )) {

            return null;
        }

        Integer minimum =
                getWholeInteger(
                        provider,
                        "min"
                );

        Integer maximum =
                getWholeInteger(
                        provider,
                        "max"
                );

        if (minimum == null
                || maximum == null
                || minimum < 0
                || maximum < minimum) {

            return null;
        }

        return new IntRange(
                minimum,
                maximum
        );
    }

    private static Integer getWholeInteger(
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

        double rounded =
                Math.rint(
                        value
                );

        if (Math.abs(
                value - rounded
        ) > 0.0000001D) {

            return null;
        }

        if (rounded < Integer.MIN_VALUE
                || rounded > Integer.MAX_VALUE) {

            return null;
        }

        return (int) rounded;
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

    private record IntRange(
            int minimum,
            int maximum
    ) {
    }

    private record EntryResult(
            boolean resolved,
            int variantCount
    ) {

        private static EntryResult notResolved() {
            return new EntryResult(
                    false,
                    0
            );
        }
    }

    private record TableResult(
            int functionsResolved,
            int identitiesGenerated
    ) {
    }

    public record Variant(
            int amplifier,
            double chance,
            CraftScopeItemIdentity identity
    ) {

        public Variant {
            if (amplifier < 0) {

                throw new IllegalArgumentException(
                        "Ominous Bottle amplifier cannot be negative"
                );
            }

            if (!(chance > 0.0D)
                    || chance > 1.0D
                    || Double.isNaN(
                    chance
            )
                    || Double.isInfinite(
                    chance
            )) {

                throw new IllegalArgumentException(
                        "Invalid Ominous Bottle variant chance"
                );
            }

            Objects.requireNonNull(
                    identity,
                    "identity"
            );
        }
    }

    public record Assignment(
            ResourceLocation entityTypeId,
            ResourceLocation itemId,
            List<Variant> variants
    ) {

        public Assignment {
            Objects.requireNonNull(
                    entityTypeId,
                    "entityTypeId"
            );

            Objects.requireNonNull(
                    itemId,
                    "itemId"
            );

            variants =
                    variants == null
                            ? List.of()
                            : List.copyOf(
                            variants
                    );

            if (variants.isEmpty()) {

                throw new IllegalArgumentException(
                        "Ominous Bottle assignment requires variants"
                );
            }
        }
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            List<Assignment> assignments,
            int functionsResolved,
            int identitiesGenerated
    ) {

        public PreparedScan {
            assignments =
                    assignments == null
                            ? List.of()
                            : List.copyOf(
                            assignments
                    );

            if (functionsResolved < 0
                    || identitiesGenerated < 0) {

                throw new IllegalArgumentException(
                        "Ominous Bottle diagnostic counts cannot be negative"
                );
            }
        }
    }
}