package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * Runtime registration layer for mob-drop definitions.
 *
 * CraftScopeMobDropCatalog owns the final merged view:
 *
 *     built-in baseline definitions
 *              +
 *     runtime definitions
 *
 * This registry owns the runtime side of that equation.
 *
 * Future sources may include:
 *
 * - Fabric discovery
 * - NeoForge discovery
 * - datapack / loot-table inspection
 * - compatibility modules
 * - third-party CraftScope integrations
 *
 * Callers register whole mob definitions. Registering the same
 * entityTypeId again replaces the previous runtime definition for
 * that mob.
 *
 * That behavior is intentional. A runtime source must be able to
 * represent the real loaded drop table for a mob without removed
 * baseline drops leaking back into the result.
 */
public final class CraftScopeMobDropRuntimeRegistry {

    /*
     * LinkedHashMap gives us:
     *
     * - one runtime definition per entity
     * - deterministic iteration order
     * - inexpensive replacement by entity ID
     */
    private static final Map<
            ResourceLocation,
            CraftScopeMobDropCatalog.MobDefinition
            > DEFINITIONS =
            new LinkedHashMap<>();

    private CraftScopeMobDropRuntimeRegistry() {
    }

    /*
     * Register or replace one mob definition.
     */
    public static synchronized void register(
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        Objects.requireNonNull(
                definition,
                "definition"
        );

        DEFINITIONS.put(
                definition.entityTypeId(),
                definition
        );

        publish();
    }

    /*
     * Register several definitions at once.
     *
     * Publishing happens only after the entire collection has been
     * processed so discovery code can efficiently add many mobs.
     */
    public static synchronized void registerAll(
            Collection<CraftScopeMobDropCatalog.MobDefinition> definitions
    ) {
        if (definitions == null
                || definitions.isEmpty()) {

            return;
        }

        boolean changed =
                false;

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                definitions) {

            if (definition == null) {
                continue;
            }

            DEFINITIONS.put(
                    definition.entityTypeId(),
                    definition
            );

            changed =
                    true;
        }

        if (changed) {
            publish();
        }
    }

    /*
     * Replace the entire runtime registry.
     *
     * This will be useful when automatic discovery is rerun after
     * resources / datapacks reload.
     */
    public static synchronized void replaceAll(
            Collection<CraftScopeMobDropCatalog.MobDefinition> definitions
    ) {
        DEFINITIONS.clear();

        if (definitions != null) {

            for (CraftScopeMobDropCatalog.MobDefinition definition :
                    definitions) {

                if (definition == null) {
                    continue;
                }

                DEFINITIONS.put(
                        definition.entityTypeId(),
                        definition
                );
            }
        }

        publish();
    }

    /*
     * Remove the runtime override for one mob.
     *
     * If that mob has a built-in baseline definition, the catalog
     * automatically falls back to it after this removal.
     */
    public static synchronized boolean remove(
            ResourceLocation entityTypeId
    ) {
        if (entityTypeId == null) {
            return false;
        }

        CraftScopeMobDropCatalog.MobDefinition removed =
                DEFINITIONS.remove(
                        entityTypeId
                );

        if (removed == null) {
            return false;
        }

        publish();

        return true;
    }

    /*
     * Clear every runtime definition.
     *
     * CraftScopeMobDropCatalog then returns to its normal baseline
     * definitions.
     */
    public static synchronized void clear() {
        if (DEFINITIONS.isEmpty()) {

            /*
             * Make sure the catalog cannot retain stale runtime data
             * even if this registry itself is already empty.
             */
            CraftScopeMobDropCatalog
                    .clearRuntimeDefinitions();

            return;
        }

        DEFINITIONS.clear();

        publish();
    }

    public static synchronized boolean contains(
            ResourceLocation entityTypeId
    ) {
        if (entityTypeId == null) {
            return false;
        }

        return DEFINITIONS.containsKey(
                entityTypeId
        );
    }

    public static synchronized int size() {
        return DEFINITIONS.size();
    }

    public static synchronized boolean isEmpty() {
        return DEFINITIONS.isEmpty();
    }

    /*
     * Snapshot only.
     *
     * Callers cannot mutate the registry by modifying the returned
     * list.
     */
    public static synchronized List<
            CraftScopeMobDropCatalog.MobDefinition
            > getDefinitions() {

        return List.copyOf(
                DEFINITIONS.values()
        );
    }

    /*
     * Convenience factory for runtime discovery code.
     */
    public static CraftScopeMobDropCatalog.MobDefinition mob(
            ResourceLocation entityTypeId,
            String sourceModId,
            ResourceLocation iconItemId,
            int priority,
            List<String> requirements,
            List<CraftScopeMobDropCatalog.DropDefinition> drops
    ) {
        return new CraftScopeMobDropCatalog.MobDefinition(
                Objects.requireNonNull(
                        entityTypeId,
                        "entityTypeId"
                ),
                sourceModId,
                iconItemId,
                priority,
                requirements,
                drops
        );
    }

    /*
     * Uniform integer-range drop.
     *
     * Example:
     *
     *     0-2 gunpowder
     *     3-5 iron ingots
     */
    public static CraftScopeMobDropCatalog.DropDefinition rangeDrop(
            ResourceLocation itemId,
            long minimum,
            long maximum,
            String... targetRequirements
    ) {
        return new CraftScopeMobDropCatalog.DropDefinition(
                Objects.requireNonNull(
                        itemId,
                        "itemId"
                ),
                CraftScopeMobDropCatalog.DropMode.RANGE,
                minimum,
                maximum,
                0L,
                1.0D,
                requirementList(
                        targetRequirements
                )
        );
    }

    /*
     * Independent chance drop.
     *
     * Example:
     *
     *     1 shulker shell at 50%
     */
    public static CraftScopeMobDropCatalog.DropDefinition chanceDrop(
            ResourceLocation itemId,
            long amount,
            double chance,
            String... targetRequirements
    ) {
        return new CraftScopeMobDropCatalog.DropDefinition(
                Objects.requireNonNull(
                        itemId,
                        "itemId"
                ),
                CraftScopeMobDropCatalog.DropMode.CHANCE,
                0L,
                amount,
                amount,
                chance,
                requirementList(
                        targetRequirements
                )
        );
    }

    private static List<String> requirementList(
            String... requirements
    ) {
        if (requirements == null
                || requirements.length == 0) {

            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        for (String requirement :
                requirements) {

            if (requirement == null
                    || requirement.isBlank()) {

                continue;
            }

            result.add(
                    requirement
            );
        }

        return List.copyOf(
                result
        );
    }

    /*
     * Push the current runtime snapshot into the catalog.
     *
     * An empty list intentionally means "use baseline definitions".
     */
    private static void publish() {
        CraftScopeMobDropCatalog
                .replaceRuntimeDefinitions(
                        List.copyOf(
                                DEFINITIONS.values()
                        )
                );
    }
}