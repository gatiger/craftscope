package io.github.gatiger.craftscope.project;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CraftScopeProject {

    private String id;
    private String name;
    private String targetItemId;
    private int targetCount;

    /*
     * Stores only recipe selections that differ from
     * CraftScope's automatically preferred recipe.
     *
     * Key:
     *   Recipe-tree node path.
     *
     * Value:
     *   Recipe ResourceLocation stored as a String.
     *
     * Example:
     *
     * root/1:minecraft:stick
     *     -> minecraft:stick_from_bamboo
     */
    private Map<String, String> recipeOverrides;

    public CraftScopeProject(
            String id,
            String name,
            String targetItemId,
            int targetCount
    ) {
        this.id = id;
        this.name = name;
        this.targetItemId = targetItemId;
        this.targetCount = targetCount;
        this.recipeOverrides = new HashMap<>();
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

        return recipeOverrides.get(nodePath);
    }

    public void setName(
            String name
    ) {
        this.name = name;
    }

    public void setTargetItemId(
            String targetItemId
    ) {
        this.targetItemId = targetItemId;
    }

    public void setTargetCount(
            int targetCount
    ) {
        this.targetCount = targetCount;
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

            recipeOverrides.remove(nodePath);
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

        recipeOverrides.remove(nodePath);
    }

    public void clearRecipeOverrides() {
        ensureRecipeOverrides();

        recipeOverrides.clear();
    }

    private void ensureRecipeOverrides() {
        /*
         * Projects created before recipe persistence was
         * introduced will not contain recipeOverrides in
         * projects.json.
         *
         * Gson therefore loads the field as null. Creating
         * the map here keeps old project files compatible.
         */
        if (recipeOverrides == null) {
            recipeOverrides =
                    new HashMap<>();
        }
    }
}