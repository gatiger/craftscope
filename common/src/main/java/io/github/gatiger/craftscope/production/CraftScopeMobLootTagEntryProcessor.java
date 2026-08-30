package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Expands the conservative minecraft:tag loot-entry shape into
 * ordinary minecraft:item entries before CraftScope's weighted
 * multi-entry processor runs.
 *
 * Example:
 *
 *     {
 *         "type": "minecraft:tag",
 *         "name": "minecraft:creeper_drop_music_discs",
 *         "expand": true
 *     }
 *
 * becomes one minecraft:item entry for every item currently bound
 * to that server-side item tag.
 *
 * This is important for datapacks and modpacks because CraftScope
 * resolves the actual runtime registry tag rather than embedding a
 * hard-coded vanilla list.
 *
 * Only expand=true is supported. Non-expanded tag entries have
 * different loot semantics and remain untouched.
 *
 * For now, entry-level conditions/functions must be absent or empty.
 * More complicated tag-entry behavior remains unsupported rather
 * than being approximated.
 */
public final class CraftScopeMobLootTagEntryProcessor {

    private CraftScopeMobLootTagEntryProcessor() {
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
                    0,
                    0
            );
        }

        Registry<Item> itemRegistry =
                registryAccess.registryOrThrow(
                        Registries.ITEM
                );

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        int tagEntriesExpanded =
                0;

        int itemEntriesGenerated =
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
                                copied.getAsJsonObject(),
                                itemRegistry
                        );

                tagEntriesExpanded +=
                        result.tagEntriesExpanded();

                itemEntriesGenerated +=
                        result.itemEntriesGenerated();
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
                tagEntriesExpanded,
                itemEntriesGenerated
        );
    }

    private static TableResult processTable(
            JsonObject root,
            Registry<Item> itemRegistry
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

        int tagEntriesExpanded =
                0;

        int itemEntriesGenerated =
                0;

        for (JsonElement poolElement :
                pools) {

            if (poolElement == null
                    || !poolElement.isJsonObject()) {

                continue;
            }

            JsonObject pool =
                    poolElement.getAsJsonObject();

            JsonArray entries =
                    getArray(
                            pool,
                            "entries"
                    );

            if (entries == null
                    || entries.isEmpty()) {

                continue;
            }

            JsonArray replacementEntries =
                    new JsonArray();

            boolean changed =
                    false;

            for (JsonElement entryElement :
                    entries) {

                ExpansionResult expansion =
                        expandEntry(
                                entryElement,
                                itemRegistry
                        );

                if (!expansion.expanded()) {

                    replacementEntries.add(
                            entryElement
                    );

                    continue;
                }

                for (JsonObject generated :
                        expansion.generatedEntries()) {

                    replacementEntries.add(
                            generated
                    );
                }

                changed =
                        true;

                tagEntriesExpanded++;

                itemEntriesGenerated +=
                        expansion
                                .generatedEntries()
                                .size();
            }

            if (changed) {

                pool.add(
                        "entries",
                        replacementEntries
                );
            }
        }

        return new TableResult(
                tagEntriesExpanded,
                itemEntriesGenerated
        );
    }

    private static ExpansionResult expandEntry(
            JsonElement entryElement,
            Registry<Item> itemRegistry
    ) {
        if (entryElement == null
                || !entryElement.isJsonObject()) {

            return ExpansionResult.notExpanded();
        }

        JsonObject entry =
                entryElement.getAsJsonObject();

        if (!"minecraft:tag".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return ExpansionResult.notExpanded();
        }

        Boolean expand =
                getBoolean(
                        entry,
                        "expand"
                );

        if (!Boolean.TRUE.equals(
                expand
        )) {

            return ExpansionResult.notExpanded();
        }

        /*
         * Conditions/functions on the tag container may affect the
         * expansion as a whole. Leave those cases untouched until
         * CraftScope explicitly models them.
         */
        if (hasNonEmptyArray(
                entry,
                "conditions"
        )
                || hasMalformedArray(
                entry,
                "conditions"
        )
                || hasNonEmptyArray(
                entry,
                "functions"
        )
                || hasMalformedArray(
                entry,
                "functions"
        )) {

            return ExpansionResult.notExpanded();
        }

        String tagName =
                getString(
                        entry,
                        "name"
                );

        ResourceLocation tagId =
                ResourceLocation.tryParse(
                        tagName == null
                                ? ""
                                : tagName
                );

        if (tagId == null) {

            return ExpansionResult.notExpanded();
        }

        TagKey<Item> tagKey =
                TagKey.create(
                        Registries.ITEM,
                        tagId
                );

        Iterable<Holder<Item>> holders =
                itemRegistry.getTagOrEmpty(
                        tagKey
                );

        List<JsonObject> generated =
                new ArrayList<>();

        for (Holder<Item> holder :
                holders) {

            if (holder == null
                    || holder.value() == null) {

                return ExpansionResult.notExpanded();
            }

            ResourceLocation itemId =
                    itemRegistry.getKey(
                            holder.value()
                    );

            if (itemId == null) {

                return ExpansionResult.notExpanded();
            }

            /*
             * Preserve weight, quality, and any harmless metadata
             * already present on the tag entry.
             */
            JsonObject itemEntry =
                    entry.deepCopy();

            itemEntry.addProperty(
                    "type",
                    "minecraft:item"
            );

            itemEntry.addProperty(
                    "name",
                    itemId.toString()
            );

            itemEntry.remove(
                    "expand"
            );

            generated.add(
                    itemEntry
            );
        }

        /*
         * An unresolved or empty tag remains unsupported rather than
         * silently turning into an empty loot pool.
         */
        if (generated.isEmpty()) {

            return ExpansionResult.notExpanded();
        }

        return new ExpansionResult(
                true,
                List.copyOf(
                        generated
                )
        );
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

    private static Boolean getBoolean(
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

        if (!primitive.isBoolean()) {

            return null;
        }

        return primitive.getAsBoolean();
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

    private record ExpansionResult(
            boolean expanded,
            List<JsonObject> generatedEntries
    ) {

        private ExpansionResult {
            generatedEntries =
                    generatedEntries == null
                            ? List.of()
                            : List.copyOf(
                            generatedEntries
                    );
        }

        private static ExpansionResult notExpanded() {
            return new ExpansionResult(
                    false,
                    List.of()
            );
        }
    }

    private record TableResult(
            int tagEntriesExpanded,
            int itemEntriesGenerated
    ) {
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            int tagEntriesExpanded,
            int itemEntriesGenerated
    ) {
    }
}