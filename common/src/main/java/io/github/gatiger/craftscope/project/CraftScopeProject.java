package io.github.gatiger.craftscope.project;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CraftScopeProject {

    private String id;
    private String name;
    private String targetItemId;

    private String targetItemStackJson;

    private int targetCount;

    /*
     * Key:
     *   Recipe-tree node path.
     *
     * Value:
     *   Selected recipe/route ResourceLocation.
     */
    private Map<String, String> recipeOverrides;

    /*
     * Persisted ingredient-alternative choices.
     *
     * Example:
     *
     * Book ingredient accepts:
     *   minecraft:leather
     *   create:cardboard
     *
     * The node path stores whichever alternative the player chose.
     * This is intentionally separate from recipeOverrides because
     * choosing WHAT material to use is different from choosing HOW
     * to produce that material.
     */
    private Map<String, String> ingredientVariantOverrides;

    /*
     * Persisted Production Routes selection.
     *
     * These are intentionally independent from recipeOverrides.
     *
     * Example:
     *
     *     source = "create"
     *     process = "minecraft:crafting"
     *
     * The selected source/process acts as the player's preferred
     * production context across the whole Recipe Tree.
     *
     * Older projects deserialize both fields as null and continue
     * using CraftScope's normal automatic route selection.
     */
    private String productionProcessSourceId;
    private String productionProcessId;

    public CraftScopeProject(
            String id,
            String name,
            String targetItemId,
            int targetCount
    ) {
        this.id = id;
        this.name = name;
        this.targetItemId = targetItemId;
        this.targetItemStackJson = null;
        this.targetCount = targetCount;
        this.recipeOverrides = new HashMap<>();
        this.ingredientVariantOverrides = new HashMap<>();
        this.productionProcessSourceId = null;
        this.productionProcessId = null;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTargetItemId() {
        return targetItemId;
    }

    public String getTargetItemStackJson() {
        return targetItemStackJson;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public Map<String, String> getRecipeOverrides() {
        ensureRecipeOverrides();

        return Collections.unmodifiableMap(
                recipeOverrides
        );
    }

    public String getRecipeOverride(
            String nodePath
    ) {
        ensureRecipeOverrides();

        return recipeOverrides.get(
                nodePath
        );
    }

    public Map<String, String> getIngredientVariantOverrides() {
        ensureIngredientVariantOverrides();

        return Collections.unmodifiableMap(
                ingredientVariantOverrides
        );
    }

    public String getIngredientVariantOverride(
            String nodePath
    ) {
        ensureIngredientVariantOverrides();

        return ingredientVariantOverrides.get(
                nodePath
        );
    }

    public String getProductionProcessSourceId() {
        return productionProcessSourceId;
    }

    public String getProductionProcessId() {
        return productionProcessId;
    }

    public void setName(
            String name
    ) {
        this.name =
                name;
    }

    public void setTargetItemId(
            String targetItemId
    ) {
        this.targetItemId =
                targetItemId;

        this.targetItemStackJson =
                null;
    }

    public void setTargetItemStackJson(
            String targetItemStackJson
    ) {
        this.targetItemStackJson =
                targetItemStackJson == null
                        || targetItemStackJson.isBlank()
                        ? null
                        : targetItemStackJson;
    }

    public void setTargetCount(
            int targetCount
    ) {
        this.targetCount =
                targetCount;
    }

    public void setProductionProcessSelection(
            String sourceId,
            String processId
    ) {
        String normalizedSource =
                sourceId == null
                        || sourceId.isBlank()
                        ? null
                        : sourceId;

        String normalizedProcess =
                processId == null
                        || processId.isBlank()
                        ? null
                        : processId;

        /*
         * A partial selection is never useful. Keep the saved state
         * either fully valid or fully empty.
         */
        if (normalizedSource == null
                || normalizedProcess == null) {

            clearProductionProcessSelection();
            return;
        }

        productionProcessSourceId =
                normalizedSource;

        productionProcessId =
                normalizedProcess;
    }

    public void clearProductionProcessSelection() {
        productionProcessSourceId = null;
        productionProcessId = null;
    }

    public void setRecipeOverride(
            String nodePath,
            String recipeId
    ) {
        ensureRecipeOverrides();

        if (nodePath == null
                || nodePath.isBlank()) {

            return;
        }

        if (recipeId == null
                || recipeId.isBlank()) {

            recipeOverrides.remove(
                    nodePath
            );

            return;
        }

        recipeOverrides.put(
                nodePath,
                recipeId
        );
    }

    public void removeRecipeOverride(
            String nodePath
    ) {
        ensureRecipeOverrides();

        recipeOverrides.remove(
                nodePath
        );
    }

    public void clearRecipeOverrides() {
        ensureRecipeOverrides();

        recipeOverrides.clear();
    }

    public void setIngredientVariantOverride(
            String nodePath,
            String itemId
    ) {
        ensureIngredientVariantOverrides();

        if (nodePath == null
                || nodePath.isBlank()) {

            return;
        }

        if (itemId == null
                || itemId.isBlank()) {

            ingredientVariantOverrides.remove(
                    nodePath
            );

            return;
        }

        ingredientVariantOverrides.put(
                nodePath,
                itemId
        );
    }

    public void removeIngredientVariantOverride(
            String nodePath
    ) {
        ensureIngredientVariantOverrides();

        ingredientVariantOverrides.remove(
                nodePath
        );
    }

    public void clearIngredientVariantOverrides() {
        ensureIngredientVariantOverrides();

        ingredientVariantOverrides.clear();
    }

    private void ensureRecipeOverrides() {
        if (recipeOverrides == null) {
            recipeOverrides =
                    new HashMap<>();
        }
    }

    private void ensureIngredientVariantOverrides() {
        /*
         * Projects created before ingredient alternative selection
         * existed deserialize this field as null.
         */
        if (ingredientVariantOverrides == null) {
            ingredientVariantOverrides =
                    new HashMap<>();
        }
    }
}