package io.github.gatiger.craftscope.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Handles the specific conditional minecraft:furnace_smelt pattern
 * used by entity loot tables.
 *
 * Important:
 *
 * This does NOT broadly declare furnace_smelt supported.
 *
 * It only recognizes the exact condition currently observed:
 *
 *     mob is on fire
 *
 *             OR
 *
 *     direct attacker's main-hand item has an enchantment
 *     in #minecraft:smelts_loot
 *
 * The actual transformed item is resolved from the server's loaded
 * SMELTING recipes. No raw -> cooked item mapping is hard-coded.
 *
 * Unknown or ambiguous cases remain in the JSON so the conservative
 * loot interpreter will reject the mob normally.
 */
public final class CraftScopeMobLootFurnaceSmeltProcessor {

    private static final String BASE_REQUIREMENT =
            "Mob not on fire and weapon has no #minecraft:smelts_loot enchantment";

    private static final String TRANSFORM_REQUIREMENT =
            "Mob on fire OR weapon has a #minecraft:smelts_loot enchantment";

    private CraftScopeMobLootFurnaceSmeltProcessor() {
    }

    public static PreparedScan prepare(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        if (scanResult == null
                || scanResult.isEmpty()) {

            return new PreparedScan(
                    scanResult,
                    List.of()
            );
        }

        List<CraftScopeMobLootTableScanner.LootTableSnapshot>
                preparedSnapshots =
                new ArrayList<>();

        Map<TransformationKey, Transformation> transformations =
                new LinkedHashMap<>();

        for (CraftScopeMobLootTableScanner.LootTableSnapshot snapshot :
                scanResult.lootTables()) {

            JsonElement copied =
                    snapshot
                            .json()
                            .deepCopy();

            if (copied.isJsonObject()) {

                processTable(
                        snapshot.entityTypeId(),
                        copied.getAsJsonObject(),
                        recipeManager,
                        registryAccess,
                        transformations
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
                List.copyOf(
                        transformations.values()
                )
        );
    }

    public static CraftScopeMobLootTableInterpreter.InterpretationResult
    applyTransformations(
            CraftScopeMobLootTableInterpreter.InterpretationResult result,
            PreparedScan preparedScan
    ) {
        if (result == null
                || preparedScan == null
                || preparedScan.transformations().isEmpty()) {

            return result;
        }

        Map<TransformationKey, Transformation> byKey =
                new LinkedHashMap<>();

        for (Transformation transformation :
                preparedScan.transformations()) {

            byKey.put(
                    new TransformationKey(
                            transformation.entityTypeId(),
                            transformation.baseItemId()
                    ),
                    transformation
            );
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                result.definitions()) {

            List<CraftScopeMobDropCatalog.DropDefinition> drops =
                    new ArrayList<>();

            for (CraftScopeMobDropCatalog.DropDefinition drop :
                    definition.drops()) {

                Transformation transformation =
                        byKey.get(
                                new TransformationKey(
                                        definition.entityTypeId(),
                                        drop.itemId()
                                )
                        );

                if (transformation == null) {

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
                                transformation.transformedItemId(),
                                transformation.baseRequirements(),
                                transformation.transformationRequirements()
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
                result.tablesChecked(),
                result.completeMobDefinitions(),
                result.partialMobsSkipped(),
                result.emptyMobsSkipped(),
                result.dropsProduced(),
                result.unsupportedPools(),
                result.unsupportedEntries(),
                definitions
        );
    }

    private static void processTable(
            ResourceLocation entityTypeId,
            JsonObject root,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            Map<TransformationKey, Transformation> transformations
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

                processEntry(
                        entityTypeId,
                        entryElement.getAsJsonObject(),
                        recipeManager,
                        registryAccess,
                        transformations
                );
            }
        }
    }

    private static void processEntry(
            ResourceLocation entityTypeId,
            JsonObject entry,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            Map<TransformationKey, Transformation> transformations
    ) {
        if (!"minecraft:item".equals(
                getString(
                        entry,
                        "type"
                )
        )) {

            return;
        }

        ResourceLocation inputItemId =
                ResourceLocation.tryParse(
                        getString(
                                entry,
                                "name"
                        )
                );

        if (inputItemId == null) {
            return;
        }

        JsonArray functions =
                getArray(
                        entry,
                        "functions"
                );

        if (functions == null
                || functions.isEmpty()) {

            return;
        }

        JsonArray retained =
                new JsonArray();

        for (JsonElement functionElement :
                functions) {

            if (functionElement == null
                    || !functionElement.isJsonObject()) {

                retained.add(
                        functionElement
                );

                continue;
            }

            JsonObject function =
                    functionElement.getAsJsonObject();

            if (!"minecraft:furnace_smelt".equals(
                    getString(
                            function,
                            "function"
                    )
            )) {

                retained.add(
                        functionElement
                );

                continue;
            }

            if (!hasSupportedCondition(
                    function
            )) {

                retained.add(
                        functionElement
                );

                continue;
            }

            ResourceLocation transformedItemId =
                    resolveSmeltingResult(
                            inputItemId,
                            recipeManager,
                            registryAccess
                    );

            if (transformedItemId == null) {

                retained.add(
                        functionElement
                );

                continue;
            }

            /*
             * If smelting resolves to the same item, the function is
             * effectively a no-op for CraftScope's item-level model.
             */
            if (inputItemId.equals(
                    transformedItemId
            )) {

                continue;
            }

            Transformation transformation =
                    new Transformation(
                            entityTypeId,
                            inputItemId,
                            transformedItemId,
                            List.of(
                                    BASE_REQUIREMENT
                            ),
                            List.of(
                                    TRANSFORM_REQUIREMENT
                            )
                    );

            TransformationKey key =
                    new TransformationKey(
                            entityTypeId,
                            inputItemId
                    );

            Transformation existing =
                    transformations.get(
                            key
                    );

            if (existing != null
                    && !existing.equals(
                    transformation
            )) {

                /*
                 * Conflicting interpretations for the same entity/item
                 * are unsafe. Leave the function intact so the normal
                 * interpreter rejects the mob.
                 */
                retained.add(
                        functionElement
                );

                continue;
            }

            transformations.put(
                    key,
                    transformation
            );

            /*
             * Do not retain this function in the prepared copy.
             *
             * We understand its effect and store it separately as
             * transformation metadata.
             */
        }

        entry.add(
                "functions",
                retained
        );
    }

    private static ResourceLocation resolveSmeltingResult(
            ResourceLocation inputItemId,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        if (inputItemId == null
                || recipeManager == null
                || registryAccess == null) {

            return null;
        }

        Item inputItem =
                BuiltInRegistries.ITEM
                        .getOptional(
                                inputItemId
                        )
                        .orElse(
                                null
                        );

        if (inputItem == null
                || inputItem == Items.AIR) {

            return null;
        }

        ItemStack input =
                new ItemStack(
                        inputItem
                );

        Set<ResourceLocation> results =
                new LinkedHashSet<>();

        for (RecipeHolder<SmeltingRecipe> holder :
                recipeManager.getAllRecipesFor(
                        RecipeType.SMELTING
                )) {

            SmeltingRecipe recipe =
                    holder.value();

            boolean matches =
                    false;

            for (Ingredient ingredient :
                    recipe.getIngredients()) {

                if (ingredient != null
                        && ingredient.test(
                        input
                )) {

                    matches =
                            true;

                    break;
                }
            }

            if (!matches) {
                continue;
            }

            ItemStack output =
                    recipe.getResultItem(
                            registryAccess
                    );

            if (output == null
                    || output.isEmpty()) {

                continue;
            }

            ResourceLocation outputId =
                    BuiltInRegistries.ITEM
                            .getKey(
                                    output.getItem()
                            );

            if (outputId != null) {

                results.add(
                        outputId
                );
            }
        }

        /*
         * Zero results:
         *
         *     We cannot prove what the function does.
         *
         * Multiple different results:
         *
         *     The transformation is ambiguous.
         *
         * Only one unique output is accepted.
         */
        if (results.isEmpty()) {

            return inputItemId;
        }

        /*
         * Multiple different smelting outputs are ambiguous.
         * Never guess which result the loot function would select.
         */
        if (results.size() != 1) {

            return null;
        }

        return results
                .iterator()
                .next();
    }

    private static boolean hasSupportedCondition(
            JsonObject function
    ) {
        JsonArray conditions =
                getArray(
                        function,
                        "conditions"
                );

        if (conditions == null
                || conditions.size() != 1) {

            return false;
        }

        JsonElement conditionElement =
                conditions.get(
                        0
                );

        if (conditionElement == null
                || !conditionElement.isJsonObject()) {

            return false;
        }

        JsonObject anyOf =
                conditionElement.getAsJsonObject();

        if (!hasOnly(
                anyOf,
                "terms",
                "condition"
        )) {

            return false;
        }

        if (!"minecraft:any_of".equals(
                getString(
                        anyOf,
                        "condition"
                )
        )) {

            return false;
        }

        JsonArray terms =
                getArray(
                        anyOf,
                        "terms"
                );

        if (terms == null
                || terms.size() != 2) {

            return false;
        }

        boolean foundOnFire =
                false;

        boolean foundSmeltsLoot =
                false;

        for (JsonElement termElement :
                terms) {

            if (termElement == null
                    || !termElement.isJsonObject()) {

                return false;
            }

            JsonObject term =
                    termElement.getAsJsonObject();

            if (isOnFireCondition(
                    term
            )) {

                if (foundOnFire) {
                    return false;
                }

                foundOnFire =
                        true;

                continue;
            }

            if (isSmeltsLootWeaponCondition(
                    term
            )) {

                if (foundSmeltsLoot) {
                    return false;
                }

                foundSmeltsLoot =
                        true;

                continue;
            }

            return false;
        }

        return foundOnFire
                && foundSmeltsLoot;
    }

    private static boolean isOnFireCondition(
            JsonObject condition
    ) {
        if (!hasOnly(
                condition,
                "predicate",
                "entity",
                "condition"
        )) {

            return false;
        }

        if (!"minecraft:entity_properties".equals(
                getString(
                        condition,
                        "condition"
                )
        )) {

            return false;
        }

        if (!"this".equals(
                getString(
                        condition,
                        "entity"
                )
        )) {

            return false;
        }

        JsonObject predicate =
                getObject(
                        condition,
                        "predicate"
                );

        if (!hasOnly(
                predicate,
                "flags"
        )) {

            return false;
        }

        JsonObject flags =
                getObject(
                        predicate,
                        "flags"
                );

        return hasOnly(
                flags,
                "is_on_fire"
        )
                && isTrue(
                flags,
                "is_on_fire"
        );
    }

    private static boolean isSmeltsLootWeaponCondition(
            JsonObject condition
    ) {
        if (!hasOnly(
                condition,
                "predicate",
                "entity",
                "condition"
        )) {

            return false;
        }

        if (!"minecraft:entity_properties".equals(
                getString(
                        condition,
                        "condition"
                )
        )) {

            return false;
        }

        if (!"direct_attacker".equals(
                getString(
                        condition,
                        "entity"
                )
        )) {

            return false;
        }

        JsonObject predicate =
                getObject(
                        condition,
                        "predicate"
                );

        if (!hasOnly(
                predicate,
                "equipment"
        )) {

            return false;
        }

        JsonObject equipment =
                getObject(
                        predicate,
                        "equipment"
                );

        if (!hasOnly(
                equipment,
                "mainhand"
        )) {

            return false;
        }

        JsonObject mainhand =
                getObject(
                        equipment,
                        "mainhand"
                );

        if (!hasOnly(
                mainhand,
                "predicates"
        )) {

            return false;
        }

        JsonObject predicates =
                getObject(
                        mainhand,
                        "predicates"
                );

        if (!hasOnly(
                predicates,
                "minecraft:enchantments"
        )) {

            return false;
        }

        JsonArray enchantments =
                getArray(
                        predicates,
                        "minecraft:enchantments"
                );

        if (enchantments == null
                || enchantments.size() != 1) {

            return false;
        }

        JsonElement enchantmentElement =
                enchantments.get(
                        0
                );

        if (enchantmentElement == null
                || !enchantmentElement.isJsonObject()) {

            return false;
        }

        JsonObject enchantment =
                enchantmentElement.getAsJsonObject();

        return hasOnly(
                enchantment,
                "enchantments"
        )
                && "#minecraft:smelts_loot".equals(
                getString(
                        enchantment,
                        "enchantments"
                )
        );
    }

    private static boolean hasOnly(
            JsonObject object,
            String... keys
    ) {
        if (object == null
                || keys == null
                || object.size() != keys.length) {

            return false;
        }

        for (String key :
                keys) {

            if (!object.has(
                    key
            )) {

                return false;
            }
        }

        return true;
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

    private static boolean isTrue(
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
                && element.isJsonPrimitive()
                && element
                .getAsJsonPrimitive()
                .isBoolean()
                && element.getAsBoolean();
    }

    private record TransformationKey(
            ResourceLocation entityTypeId,
            ResourceLocation baseItemId
    ) {
    }

    public record Transformation(
            ResourceLocation entityTypeId,
            ResourceLocation baseItemId,
            ResourceLocation transformedItemId,
            List<String> baseRequirements,
            List<String> transformationRequirements
    ) {

        public Transformation {
            baseRequirements =
                    baseRequirements == null
                            ? List.of()
                            : List.copyOf(
                            baseRequirements
                    );

            transformationRequirements =
                    transformationRequirements == null
                            ? List.of()
                            : List.copyOf(
                            transformationRequirements
                    );
        }
    }

    public record PreparedScan(
            CraftScopeMobLootTableScanner.ScanResult scanResult,
            List<Transformation> transformations
    ) {

        public PreparedScan {
            transformations =
                    transformations == null
                            ? List.of()
                            : List.copyOf(
                            transformations
                    );
        }

        public int transformationCount() {
            return transformations.size();
        }
    }
}