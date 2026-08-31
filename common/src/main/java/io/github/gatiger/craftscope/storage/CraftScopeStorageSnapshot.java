package io.github.gatiger.craftscope.storage;

import io.github.gatiger.craftscope.production.CraftScopeItemIdentity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftScopeStorageSnapshot {

    private final boolean scanned;

    private final Map<CraftScopeItemIdentity, Long> counts;

    private final List<String> sourceNames;

    private final long totalItemCount;

    private CraftScopeStorageSnapshot(
            boolean scanned,
            Map<CraftScopeItemIdentity, Long> counts,
            List<String> sourceNames
    ) {
        this.scanned = scanned;

        this.counts =
                Map.copyOf(
                        counts == null
                                ? Map.of()
                                : counts
                );

        this.sourceNames =
                List.copyOf(
                        sourceNames == null
                                ? List.of()
                                : sourceNames
                );

        long total = 0L;

        for (long count : this.counts.values()) {
            total =
                    safeAdd(
                            total,
                            count
                    );
        }

        this.totalItemCount =
                total;
    }

    public static CraftScopeStorageSnapshot notScanned() {
        return new CraftScopeStorageSnapshot(
                false,
                Map.of(),
                List.of()
        );
    }

    static CraftScopeStorageSnapshot scanned(
            List<String> sourceNames,
            List<ItemStack> stacks
    ) {
        Map<CraftScopeItemIdentity, Long> counts =
                new LinkedHashMap<>();

        if (stacks != null) {

            for (ItemStack stack : stacks) {

                if (stack == null
                        || stack.isEmpty()
                        || stack.getCount() <= 0) {

                    continue;
                }

                CraftScopeItemIdentity identity =
                        CraftScopeItemIdentity.fromStack(
                                stack
                        );

                long existing =
                        counts.getOrDefault(
                                identity,
                                0L
                        );

                counts.put(
                        identity,
                        safeAdd(
                                existing,
                                stack.getCount()
                        )
                );
            }
        }

        List<String> copiedSources =
                new ArrayList<>();

        if (sourceNames != null) {

            for (String sourceName : sourceNames) {

                if (sourceName == null
                        || sourceName.isBlank()) {

                    continue;
                }

                copiedSources.add(
                        sourceName
                );
            }
        }

        return new CraftScopeStorageSnapshot(
                true,
                counts,
                copiedSources
        );
    }

    public boolean isScanned() {
        return scanned;
    }

    public List<String> sourceNames() {
        return sourceNames;
    }

    public long totalItemCount() {
        return totalItemCount;
    }

    public int distinctIdentityCount() {
        return counts.size();
    }

    public Map<CraftScopeItemIdentity, Long> countsCopy() {
        return new LinkedHashMap<>(
                counts
        );
    }

    public long countAcceptedVariants(
            List<ItemStack> acceptedVariants,
            ItemStack fallback
    ) {
        if (!scanned) {
            return 0L;
        }

        Set<CraftScopeItemIdentity> accepted =
                new LinkedHashSet<>();

        if (acceptedVariants != null) {

            for (ItemStack variant :
                    acceptedVariants) {

                if (variant == null
                        || variant.isEmpty()) {

                    continue;
                }

                accepted.add(
                        CraftScopeItemIdentity.fromStack(
                                variant
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

        long total = 0L;

        for (CraftScopeItemIdentity identity :
                accepted) {

            total =
                    safeAdd(
                            total,
                            counts.getOrDefault(
                                    identity,
                                    0L
                            )
                    );
        }

        return total;
    }

    private static long safeAdd(
            long first,
            long second
    ) {
        if (second > 0
                && first > Long.MAX_VALUE - second) {

            return Long.MAX_VALUE;
        }

        return first + second;
    }
}