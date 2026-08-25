package io.github.gatiger.craftscope.material;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CraftScopeMaterialSummary {

    private final List<Entry> entries =
            new ArrayList<>();

    public CraftScopeMaterialSummary(
            List<Entry> entries
    ) {
        if (entries == null) {
            return;
        }

        for (Entry entry : entries) {

            if (entry == null) {
                continue;
            }

            this.entries.add(
                    new Entry(
                            entry.getStack(),
                            entry.getRequiredCount(),
                            entry.getAcceptedVariants()
                    )
            );
        }
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(
                entries
        );
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public static final class Entry {

        private final ItemStack stack;
        private final int requiredCount;

        private final List<ItemStack> acceptedVariants =
                new ArrayList<>();

        public Entry(
                ItemStack stack,
                int requiredCount,
                List<ItemStack> acceptedVariants
        ) {
            this.stack =
                    stack == null
                            ? ItemStack.EMPTY
                            : stack.copy();

            this.requiredCount =
                    Math.max(
                            0,
                            requiredCount
                    );

            if (acceptedVariants != null) {

                for (ItemStack variant :
                        acceptedVariants) {

                    if (variant == null
                            || variant.isEmpty()) {

                        continue;
                    }

                    this.acceptedVariants.add(
                            variant.copy()
                    );
                }
            }

            if (this.acceptedVariants.isEmpty()
                    && !this.stack.isEmpty()) {

                this.acceptedVariants.add(
                        this.stack.copy()
                );
            }
        }

        public ItemStack getStack() {
            return stack.copy();
        }

        public int getRequiredCount() {
            return requiredCount;
        }

        public List<ItemStack> getAcceptedVariants() {

            List<ItemStack> result =
                    new ArrayList<>();

            for (ItemStack variant :
                    acceptedVariants) {

                result.add(
                        variant.copy()
                );
            }

            return Collections.unmodifiableList(
                    result
            );
        }

        public boolean hasVariants() {
            return acceptedVariants.size() > 1;
        }
    }
}