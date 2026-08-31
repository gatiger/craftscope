package io.github.gatiger.craftscope.material;

import io.github.gatiger.craftscope.production.CraftScopeItemIdentity;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.storage.CraftScopeStorageSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftScopeMaterialTreePlan {

    private CraftScopeMaterialTreePlan() {
    }

    public static Result build(
            CraftScopeRecipeTree tree,
            CraftScopeStorageSnapshot snapshot,
            Set<String> expandedPaths
    ) {
        if (tree == null || tree.getRoot() == null) {
            return new Result(List.of(), false);
        }

        CraftScopeStorageSnapshot safeSnapshot =
                snapshot == null
                        ? CraftScopeStorageSnapshot.notScanned()
                        : snapshot;

        Set<String> safeExpanded =
                expandedPaths == null
                        ? Set.of()
                        : Set.copyOf(expandedPaths);

        MutableLedger ledger =
                new MutableLedger(
                        safeSnapshot.countsCopy()
                );

        CraftScopeRecipeNode root =
                tree.getRoot();

        long rootRequired =
                Math.max(
                        0L,
                        root.getRequiredCount()
                );

        long rootMissing =
                rootRequired;

        if (safeSnapshot.isScanned()) {
            long rootApplied =
                    ledger.consume(
                            root.getAcceptedVariants(),
                            root.getStack(),
                            rootRequired
                    );

            rootMissing =
                    Math.max(
                            0L,
                            rootRequired - rootApplied
                    );
        }

        List<PlanNode> roots =
                new ArrayList<>();

        List<CraftScopeRecipeNode> children =
                root.getChildren();

        for (int i = 0; i < children.size(); i++) {
            CraftScopeRecipeNode child =
                    children.get(i);

            long required =
                    scaleRequired(
                            child.getRequiredCount(),
                            rootMissing,
                            rootRequired
                    );

            roots.add(
                    buildNode(
                            child,
                            "root/" + i,
                            0,
                            required,
                            safeSnapshot,
                            ledger,
                            false
                    )
            );
        }

        List<Row> visibleRows =
                new ArrayList<>();

        for (PlanNode node : roots) {
            flatten(
                    node,
                    safeExpanded,
                    visibleRows
            );
        }

        return new Result(
                visibleRows,
                safeSnapshot.isScanned()
        );
    }

    private static PlanNode buildNode(
            CraftScopeRecipeNode node,
            String path,
            int depth,
            long required,
            CraftScopeStorageSnapshot snapshot,
            MutableLedger ledger,
            boolean expandFamilyAlternatives
    ) {
        long safeRequired =
                Math.max(
                        0L,
                        required
                );

        /*
         * A grouped parent can produce equivalent children from
         * different members of the same material family.
         *
         * Example:
         *
         * Any Slab
         *   -> Acacia Planks (representative recipe)
         *
         * The representative recipe is not the only valid path.
         * Oak Slabs can use Oak Planks, Spruce Slabs can use Spruce
         * Planks, etc. Total Materials therefore widens recognized
         * material families when the parent itself represented
         * multiple valid variants.
         */
        List<ItemStack> effectiveVariants =
                getEffectiveVariants(
                        node,
                        expandFamilyAlternatives
                );

        long owned =
                snapshot.isScanned()
                        ? snapshot.countAcceptedVariants(
                        effectiveVariants,
                        node.getStack()
                )
                        : 0L;

        long applied =
                snapshot.isScanned()
                        ? ledger.consume(
                        effectiveVariants,
                        node.getStack(),
                        safeRequired
                )
                        : 0L;

        long missing =
                snapshot.isScanned()
                        ? Math.max(
                        0L,
                        safeRequired - applied
                )
                        : safeRequired;

        List<PlanNode> children =
                new ArrayList<>();

        List<CraftScopeRecipeNode> sourceChildren =
                node.getChildren();

        boolean thisNodeIsVariantFamily =
                effectiveVariants.size() > 1;

        for (int i = 0; i < sourceChildren.size(); i++) {
            CraftScopeRecipeNode child =
                    sourceChildren.get(i);

            long childRequired =
                    scaleRequired(
                            child.getRequiredCount(),
                            missing,
                            node.getRequiredCount()
                    );

            children.add(
                    buildNode(
                            child,
                            path + "/" + i,
                            depth + 1,
                            childRequired,
                            snapshot,
                            ledger,
                            thisNodeIsVariantFamily
                    )
            );
        }

        return new PlanNode(
                path,
                depth,
                node.getStack(),
                effectiveVariants,
                node.hasSelectableIngredientAlternatives(),
                node.hasExplicitIngredientVariantSelection(),
                safeRequired,
                owned,
                missing,
                List.copyOf(children)
        );
    }

    private static List<ItemStack> getEffectiveVariants(
            CraftScopeRecipeNode node,
            boolean expandFamilyAlternatives
    ) {
        List<ItemStack> original =
                new ArrayList<>();

        for (ItemStack stack :
                node.getAcceptedVariants()) {

            if (stack == null
                    || stack.isEmpty()) {

                continue;
            }

            original.add(
                    stack.copy()
            );
        }

        ItemStack fallback =
                node.getStack();

        if (original.isEmpty()
                && fallback != null
                && !fallback.isEmpty()) {

            original.add(
                    fallback.copy()
            );
        }

        /*
         * If the recipe node already knows several valid variants,
         * preserve that exact set. Those are more precise than any
         * inferred family.
         */
        if (original.size() > 1
                || !expandFamilyAlternatives
                || original.isEmpty()) {

            return List.copyOf(
                    original
            );
        }

        ItemStack probe =
                original.getFirst();

        if (probe.is(
                ItemTags.PLANKS
        )) {

            return collectTagMembers(
                    ItemTags.PLANKS
            );
        }

        if (probe.is(
                ItemTags.LOGS
        )) {

            return collectTagMembers(
                    ItemTags.LOGS
            );
        }

        if (probe.is(
                ItemTags.WOOL
        )) {

            return collectTagMembers(
                    ItemTags.WOOL
            );
        }

        return List.copyOf(
                original
        );
    }

    private static List<ItemStack> collectTagMembers(
            TagKey<Item> tag
    ) {
        List<ItemStack> result =
                new ArrayList<>();

        for (Item item :
                BuiltInRegistries.ITEM) {

            ItemStack stack =
                    new ItemStack(
                            item
                    );

            if (!stack.is(
                    tag
            )) {

                continue;
            }

            result.add(
                    stack
            );
        }

        return result.isEmpty()
                ? List.of()
                : List.copyOf(
                result
        );
    }
    private static void flatten(
            PlanNode node,
            Set<String> expandedPaths,
            List<Row> rows
    ) {
        boolean expandable =
                !node.children().isEmpty();

        boolean expanded =
                expandable
                        && expandedPaths.contains(
                        node.path()
                );

        rows.add(
                new Row(
                        node.path(),
                        node.depth(),
                        node.stack(),
                        node.acceptedVariants(),
                        node.selectableIngredientAlternatives(),
                        node.explicitIngredientVariantSelection(),
                        node.required(),
                        node.owned(),
                        node.missing(),
                        expandable,
                        expanded
                )
        );

        if (!expanded) {
            return;
        }

        for (PlanNode child : node.children()) {
            flatten(
                    child,
                    expandedPaths,
                    rows
            );
        }
    }

    private static long scaleRequired(
            long fullChildRequired,
            long remainingParentRequired,
            long fullParentRequired
    ) {
        if (fullChildRequired <= 0L
                || remainingParentRequired <= 0L
                || fullParentRequired <= 0L) {

            return 0L;
        }

        if (remainingParentRequired >= fullParentRequired) {
            return fullChildRequired;
        }

        if (fullChildRequired
                > Long.MAX_VALUE / remainingParentRequired) {

            double scaled =
                    ((double) fullChildRequired
                            * (double) remainingParentRequired)
                            / (double) fullParentRequired;

            if (Double.isInfinite(scaled)
                    || scaled >= Long.MAX_VALUE) {

                return Long.MAX_VALUE;
            }

            return Math.max(
                    1L,
                    (long) Math.ceil(scaled)
            );
        }

        long numerator =
                fullChildRequired
                        * remainingParentRequired;

        long quotient =
                numerator
                        / fullParentRequired;

        long remainder =
                numerator
                        % fullParentRequired;

        return Math.max(
                1L,
                quotient
                        + (remainder == 0L
                        ? 0L
                        : 1L)
        );
    }

    public record Result(
            List<Row> rows,
            boolean scanned
    ) {
        public Result {
            rows =
                    rows == null
                            ? List.of()
                            : List.copyOf(rows);
        }
    }

    public record Row(
            String path,
            int depth,
            ItemStack stack,
            List<ItemStack> acceptedVariants,
            boolean selectableIngredientAlternatives,
            boolean explicitIngredientVariantSelection,
            long required,
            long owned,
            long missing,
            boolean expandable,
            boolean expanded
    ) {
        public Row {
            stack =
                    stack == null
                            ? ItemStack.EMPTY
                            : stack.copy();

            List<ItemStack> copiedVariants =
                    new ArrayList<>();

            if (acceptedVariants != null) {
                for (ItemStack variant : acceptedVariants) {
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

            depth =
                    Math.max(
                            0,
                            depth
                    );

            required =
                    Math.max(
                            0L,
                            required
                    );

            owned =
                    Math.max(
                            0L,
                            owned
                    );

            missing =
                    Math.max(
                            0L,
                            missing
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

            for (ItemStack variant : acceptedVariants) {
                result.add(
                        variant.copy()
                );
            }

            return List.copyOf(result);
        }
    }

    private record PlanNode(
            String path,
            int depth,
            ItemStack stack,
            List<ItemStack> acceptedVariants,
            boolean selectableIngredientAlternatives,
            boolean explicitIngredientVariantSelection,
            long required,
            long owned,
            long missing,
            List<PlanNode> children
    ) {
        private PlanNode {
            stack =
                    stack == null
                            ? ItemStack.EMPTY
                            : stack.copy();

            List<ItemStack> copiedVariants =
                    new ArrayList<>();

            if (acceptedVariants != null) {
                for (ItemStack variant : acceptedVariants) {
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

            children =
                    children == null
                            ? List.of()
                            : List.copyOf(children);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public List<ItemStack> acceptedVariants() {
            List<ItemStack> result =
                    new ArrayList<>();

            for (ItemStack variant : acceptedVariants) {
                result.add(
                        variant.copy()
                );
            }

            return List.copyOf(result);
        }
    }

    private static final class MutableLedger {

        private final Map<CraftScopeItemIdentity, Long> remaining =
                new LinkedHashMap<>();

        private MutableLedger(
                Map<CraftScopeItemIdentity, Long> source
        ) {
            if (source != null) {
                remaining.putAll(
                        source
                );
            }
        }

        private long consume(
                List<ItemStack> acceptedVariants,
                ItemStack fallback,
                long requested
        ) {
            if (requested <= 0L) {
                return 0L;
            }

            Set<CraftScopeItemIdentity> accepted =
                    new LinkedHashSet<>();

            if (acceptedVariants != null) {
                for (ItemStack stack : acceptedVariants) {
                    if (stack == null
                            || stack.isEmpty()) {

                        continue;
                    }

                    accepted.add(
                            CraftScopeItemIdentity.fromStack(
                                    stack
                            )
                    );
                }
            }

            if (accepted.isEmpty()
                    && fallback != null
                    && !fallback.isEmpty()) {

                accepted.add(
                        CraftScopeItemIdentity.fromStack(
                                fallback
                        )
                );
            }

            long needed =
                    requested;

            long consumed =
                    0L;

            for (CraftScopeItemIdentity identity : accepted) {
                if (needed <= 0L) {
                    break;
                }

                long available =
                        remaining.getOrDefault(
                                identity,
                                0L
                        );

                if (available <= 0L) {
                    continue;
                }

                long take =
                        Math.min(
                                available,
                                needed
                        );

                long left =
                        available - take;

                if (left <= 0L) {
                    remaining.remove(
                            identity
                    );
                } else {
                    remaining.put(
                            identity,
                            left
                    );
                }

                consumed =
                        safeAdd(
                                consumed,
                                take
                        );

                needed -= take;
            }

            return consumed;
        }

        private static long safeAdd(
                long first,
                long second
        ) {
            if (second > 0L
                    && first > Long.MAX_VALUE - second) {

                return Long.MAX_VALUE;
            }

            return first + second;
        }
    }
}