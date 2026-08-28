package io.github.gatiger.craftscope.production;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.gatiger.craftscope.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CraftScopeMobLootTableScanner {

    private CraftScopeMobLootTableScanner() {
    }

    public static ScanResult scan(
            MinecraftServer server
    ) {
        Objects.requireNonNull(
                server,
                "server"
        );

        RegistryOps<JsonElement> registryOps =
                server
                        .registryAccess()
                        .createSerializationContext(
                                JsonOps.INSTANCE
                        );

        List<LootTableSnapshot> snapshots =
                new ArrayList<>();

        int entityTypesChecked =
                0;

        int emptyTables =
                0;

        int serializationFailures =
                0;

        for (EntityType<?> entityType :
                BuiltInRegistries.ENTITY_TYPE) {

            entityTypesChecked++;

            ResourceLocation entityTypeId =
                    BuiltInRegistries
                            .ENTITY_TYPE
                            .getKey(
                                    entityType
                            );

            if (entityTypeId == null) {
                continue;
            }

            ResourceKey<LootTable> lootTableKey =
                    entityType.getDefaultLootTable();

            if (lootTableKey == null) {

                emptyTables++;
                continue;
            }

            LootTable lootTable =
                    server
                            .reloadableRegistries()
                            .getLootTable(
                                    lootTableKey
                            );

            if (lootTable == null
                    || lootTable == LootTable.EMPTY) {

                emptyTables++;
                continue;
            }

            var encodedResult =
                    LootTable.DIRECT_CODEC.encodeStart(
                            registryOps,
                            lootTable
                    );

            var encoded =
                    encodedResult.result();

            if (encoded.isEmpty()) {

                serializationFailures++;

                Constants.LOG.warn(
                        "Could not serialize CraftScope loot table {} for entity {}",
                        lootTableKey.location(),
                        entityTypeId
                );

                continue;
            }

            snapshots.add(
                    new LootTableSnapshot(
                            entityTypeId,
                            lootTableKey.location(),
                            encoded.get()
                    )
            );
        }

        ScanResult result =
                new ScanResult(
                        entityTypesChecked,
                        emptyTables,
                        serializationFailures,
                        snapshots
                );

        Constants.LOG.info(
                "CraftScope loot scan: {} entity types checked, {} loot tables found, {} empty/missing, {} serialization failures",
                result.entityTypesChecked(),
                result.lootTablesFound(),
                result.emptyOrMissingTables(),
                result.serializationFailures()
        );

        CraftScopeMobLootFurnaceSmeltProcessor.PreparedScan
                furnacePrepared =
                CraftScopeMobLootFurnaceSmeltProcessor.prepare(
                        result,
                        server.getRecipeManager(),
                        server.registryAccess()
                );

        Constants.LOG.info(
                "CraftScope furnace-smelt preprocessing: {} transformations resolved",
                furnacePrepared.transformationCount()
        );

        CraftScopeMobLootNegativeCountProcessor.PreparedScan
                countPrepared =
                CraftScopeMobLootNegativeCountProcessor.prepare(
                        furnacePrepared.scanResult()
                );

        Constants.LOG.info(
                "CraftScope negative-count preprocessing: {} drops normalized",
                countPrepared.normalizedCount()
        );

        CraftScopeMobLootMultiEntryProcessor.PreparedScan
                multiEntryPrepared =
                CraftScopeMobLootMultiEntryProcessor.prepare(
                        countPrepared.scanResult()
                );

        Constants.LOG.info(
                "CraftScope multi-entry preprocessing: {} pools normalized, {} item branches generated",
                multiEntryPrepared.poolsNormalized(),
                multiEntryPrepared.itemBranchesGenerated()
        );

        CraftScopeMobLootTableInterpreter.InterpretationResult
                interpretation =
                CraftScopeMobLootTableInterpreter.interpret(
                        multiEntryPrepared.scanResult()
                );

        interpretation =
                CraftScopeMobLootFurnaceSmeltProcessor
                        .applyTransformations(
                                interpretation,
                                furnacePrepared
                        );

        CraftScopeMobLootUnsupportedDiagnostics.log(
                multiEntryPrepared.scanResult()
        );

        CraftScopeMobLootPartialDiagnostics.log(
                multiEntryPrepared.scanResult(),
                interpretation
        );

        CraftScopeMobDropRuntimeRegistry.replaceAll(
                interpretation.definitions()
        );

        int mergedDefinitionCount =
                CraftScopeMobDropCatalog
                        .getDefinitions()
                        .size();

        Constants.LOG.info(
                "CraftScope mob-drop runtime published: {} discovered complete definitions, {} definitions available after baseline fallback merge",
                interpretation.completeMobDefinitions(),
                mergedDefinitionCount
        );

        return result;
    }

    public record LootTableSnapshot(
            ResourceLocation entityTypeId,
            ResourceLocation lootTableId,
            JsonElement json
    ) {

        public LootTableSnapshot {
            Objects.requireNonNull(
                    entityTypeId,
                    "entityTypeId"
            );

            Objects.requireNonNull(
                    lootTableId,
                    "lootTableId"
            );

            Objects.requireNonNull(
                    json,
                    "json"
            );
        }
    }

    public record ScanResult(
            int entityTypesChecked,
            int emptyOrMissingTables,
            int serializationFailures,
            List<LootTableSnapshot> lootTables
    ) {

        public ScanResult {
            if (entityTypesChecked < 0
                    || emptyOrMissingTables < 0
                    || serializationFailures < 0) {

                throw new IllegalArgumentException(
                        "CraftScope loot scan counts cannot be negative"
                );
            }

            lootTables =
                    lootTables == null
                            ? List.of()
                            : List.copyOf(
                            lootTables
                    );
        }

        public int lootTablesFound() {
            return lootTables.size();
        }

        public boolean isEmpty() {
            return lootTables.isEmpty();
        }
    }
}