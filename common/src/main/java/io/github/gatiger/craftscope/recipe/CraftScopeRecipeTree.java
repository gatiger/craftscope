package io.github.gatiger.craftscope.recipe;

public class CraftScopeRecipeTree {

    private final CraftScopeRecipeNode root;

    public CraftScopeRecipeTree(
            CraftScopeRecipeNode root
    ) {
        this.root = root;
    }

    public CraftScopeRecipeNode getRoot() {
        return root;
    }
}