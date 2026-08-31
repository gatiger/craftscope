package io.github.gatiger.craftscope.material;

import io.github.gatiger.craftscope.production.CraftScopeItemIdentity;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftScopeMaterialSummarizer {

    private CraftScopeMaterialSummarizer() {
    }

    public static CraftScopeMaterialSummary summarize(
            CraftScopeRecipeTree tree
    ) {
        if (tree == null
                || tree.getRoot() == null) {

            return new CraftScopeMaterialSummary(
                    List.of()
            );
        }

        Map<String, MaterialAccumulator> materials =
                new LinkedHashMap<>();

        collectLeaves(
                tree.getRoot(),
                materials
        );

        List<CraftScopeMaterialSummary.Entry> entries =
                new ArrayList<>();

        for (MaterialAccumulator accumulator :
                materials.values()) {

            entries.add(
                    new CraftScopeMaterialSummary.Entry(
                            accumulator.stack(),
                            accumulator.requiredCount(),
                            accumulator.acceptedVariants()
                    )
            );
        }

        return new CraftScopeMaterialSummary(
                entries
        );
    }

    private static void collectLeaves(
            CraftScopeRecipeNode node,
            Map<String, MaterialAccumulator> materials
    ) {
        if (node == null) {
            return;
        }

        /*
         * Only leaf nodes become total materials.
         *
         * Intermediate crafted items are intentionally omitted.
         *
         * Example:
         *
         * Bed
         * ├─ Wool
         * └─ Planks
         *    └─ Logs
         *
         * If Wool and Logs are the leaves, the final summary is:
         *
         * Wool
         * Logs
         *
         * Planks and the Bed itself are not counted.
         */
        if (node.isLeaf()) {

            addLeaf(
                    node,
                    materials
            );

            return;
        }

        for (CraftScopeRecipeNode child :
                node.getChildren()) {

            collectLeaves(
                    child,
                    materials
            );
        }
    }

    private static void addLeaf(
            CraftScopeRecipeNode node,
            Map<String, MaterialAccumulator> materials
    ) {
        if (node.getRequiredCount() <= 0) {
            return;
        }

        List<ItemStack> variants =
                normalizeVariants(
                        node.getAcceptedVariants()
                );

        ItemStack representative;

        if (!variants.isEmpty()) {

            representative =
                    variants.getFirst().copy();

        } else {

            representative =
                    node.getStack();
        }

        if (representative.isEmpty()) {
            return;
        }

        List<ItemStack> groupedVariants =
                variants.isEmpty()
                        ? List.of(representative)
                        : variants;

        String groupKey =
                buildVariantGroupKey(
                        groupedVariants
                );

        MaterialAccumulator existing =
                materials.get(groupKey);

        if (existing == null) {

            materials.put(
                    groupKey,
                    new MaterialAccumulator(
                            representative,
                            node.getRequiredCount(),
                            groupedVariants
                    )
            );

            return;
        }

        materials.put(
                groupKey,
                new MaterialAccumulator(
                        existing.stack(),
                        safeAdd(
                                existing.requiredCount(),
                                node.getRequiredCount()
                        ),
                        existing.acceptedVariants()
                )
        );
    }

    /*
     * Component-aware variant normalization.
     *
     * ResourceLocation alone cannot distinguish items such as:
     *
     *     Poison Tipped Arrow
     *     Slowness Tipped Arrow
     *
     * Both use minecraft:tipped_arrow as their base item ID.
     *
     * CraftScopeItemIdentity preserves Minecraft data components so
     * those variants remain separate while legitimate ingredient
     * families such as "Any Log" can still contain several accepted
     * stacks.
     */
    private static List<ItemStack> normalizeVariants(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return List.of();
        }

        Map<CraftScopeItemIdentity, ItemStack> unique =
                new LinkedHashMap<>();

        for (ItemStack stack : variants) {

            if (stack == null
                    || stack.isEmpty()) {

                continue;
            }

            CraftScopeItemIdentity identity =
                    CraftScopeItemIdentity.fromStack(
                            stack
                    );

            unique.putIfAbsent(
                    identity,
                    stack.copy()
            );
        }

        List<ItemStack> result =
                new ArrayList<>(
                        unique.values()
                );

        result.sort(
                Comparator.comparing(
                        CraftScopeMaterialSummarizer
                                ::getIdentityKey
                )
        );

        return result;
    }

    private static String buildVariantGroupKey(
            List<ItemStack> variants
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (ItemStack variant :
                variants) {

            if (!builder.isEmpty()) {
                builder.append("|");
            }

            builder.append(
                    getIdentityKey(
                            variant
                    )
            );
        }

        return builder.toString();
    }

    private static String getIdentityKey(
            ItemStack stack
    ) {
        return CraftScopeItemIdentity
                .fromStack(stack)
                .toString();
    }

    private static int safeAdd(
            int first,
            int second
    ) {
        if (second > 0
                && first > Integer.MAX_VALUE - second) {

            return Integer.MAX_VALUE;
        }

        return first + second;
    }

    private record MaterialAccumulator(
            ItemStack stack,
            int requiredCount,
            List<ItemStack> acceptedVariants
    ) {

        private MaterialAccumulator {

            stack =
                    stack == null
                            ? ItemStack.EMPTY
                            : stack.copy();

            List<ItemStack> copiedVariants =
                    new ArrayList<>();

            if (acceptedVariants != null) {

                for (ItemStack variant :
                        acceptedVariants) {

                    if (variant != null
                            && !variant.isEmpty()) {

                        copiedVariants.add(
                                variant.copy()
                        );
                    }
                }
            }

            acceptedVariants =
                    List.copyOf(
                            copiedVariants
                    );
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public List<ItemStack> acceptedVariants() {

            List<ItemStack> result =
                    new ArrayList<>();

            for (ItemStack variant :
                    acceptedVariants) {

                result.add(
                        variant.copy()
                );
            }

            return List.copyOf(result);
        }
    }
}