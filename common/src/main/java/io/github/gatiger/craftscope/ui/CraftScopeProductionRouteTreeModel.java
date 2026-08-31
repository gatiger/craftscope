package io.github.gatiger.craftscope.ui;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Shared presentation model for CraftScope's Production Routes panel.
 *
 * The panel is a compact PROCESS catalog, not a second material tree.
 *
 * Example:
 *
 *   Minecraft
 *     Crafting
 *     Smelting
 *
 *   Create
 *     Mechanical Crafting
 *     Bulk Blasting
 *     Bulk Washing
 *
 * ProcessOption entries are already deduplicated by the screen's
 * collector. This model only groups and flattens them into visible
 * source/process rows while preserving shared expansion state.
 */
public final class CraftScopeProductionRouteTreeModel {

    private CraftScopeProductionRouteTreeModel() {
    }

    public static List<Row> buildRows(
            List<ProcessOption> options,
            Set<String> expandedSourceIds,
            String selectedSourceId,
            ResourceLocation selectedProcessId
    ) {
        if (options == null
                || options.isEmpty()) {

            return List.of();
        }

        Map<String, GroupAccumulator> groups =
                new LinkedHashMap<>();

        for (ProcessOption option :
                options) {

            if (option == null
                    || option.sourceId() == null
                    || option.sourceId().isBlank()
                    || option.processId() == null) {

                continue;
            }

            GroupAccumulator group =
                    groups.computeIfAbsent(
                            option.sourceId(),
                            ignored ->
                                    new GroupAccumulator(
                                            option.sourceId(),
                                            option.sourceName()
                                    )
                    );

            group.add(
                    option,
                    option.sourceId().equals(
                            selectedSourceId
                    )
                            && option.processId().equals(
                            selectedProcessId
                    )
            );
        }

        List<GroupAccumulator> orderedGroups =
                new ArrayList<>(
                        groups.values()
                );

        orderedGroups.sort(
                Comparator
                        .comparingInt(
                                CraftScopeProductionRouteTreeModel
                                        ::groupPriority
                        )
                        .thenComparing(
                                GroupAccumulator::sourceName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                GroupAccumulator::sourceId
                        )
        );

        List<Row> rows =
                new ArrayList<>();

        for (GroupAccumulator group :
                orderedGroups) {

            boolean expanded =
                    expandedSourceIds != null
                            && expandedSourceIds.contains(
                            group.sourceId()
                    );

            rows.add(
                    Row.source(
                            group.sourceId(),
                            group.sourceName(),
                            group.options().size(),
                            expanded,
                            group.containsSelected()
                    )
            );

            if (!expanded) {
                continue;
            }

            List<ProcessEntry> orderedProcesses =
                    new ArrayList<>(
                            group.options()
                    );

            orderedProcesses.sort(
                    Comparator
                            .comparing(
                                    (ProcessEntry entry) ->
                                            entry
                                                    .option()
                                                    .displayName(),
                                    String.CASE_INSENSITIVE_ORDER
                            )
                            .thenComparing(
                                    (ProcessEntry entry) ->
                                            entry
                                                    .option()
                                                    .processId()
                                                    .toString()
                            )
            );

            for (ProcessEntry process :
                    orderedProcesses) {

                ProcessOption option =
                        process.option();

                rows.add(
                        Row.process(
                                option.sourceId(),
                                option.sourceName(),
                                option.processId(),
                                option.displayName(),
                                option.routeIndex(),
                                process.selected()
                        )
                );
            }
        }

        return List.copyOf(
                rows
        );
    }

    public static String formatSourceName(
            String sourceId
    ) {
        if (sourceId == null
                || sourceId.isBlank()
                || "unknown".equals(
                sourceId
        )) {

            return "Other";
        }

        if ("minecraft".equals(
                sourceId
        )) {
            return "Minecraft";
        }

        if ("craftscope".equals(
                sourceId
        )) {
            return "CraftScope";
        }

        String[] pieces =
                sourceId.split(
                        "[_\\-]"
                );

        StringBuilder result =
                new StringBuilder();

        for (String piece :
                pieces) {

            if (piece == null
                    || piece.isBlank()) {

                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            piece.charAt(0)
                    )
            );

            if (piece.length() > 1) {
                result.append(
                        piece.substring(1)
                );
            }
        }

        return result.isEmpty()
                ? sourceId
                : result.toString();
    }

    private static int groupPriority(
            GroupAccumulator group
    ) {
        if (group == null) {
            return 100;
        }

        return switch (
                group.sourceId()
        ) {
            case "craftscope" -> 0;
            case "minecraft" -> 10;
            default -> 20;
        };
    }

    public record ProcessOption(
            String sourceId,
            String sourceName,
            ResourceLocation processId,
            String displayName,
            int routeIndex
    ) {
    }

    public enum RowKind {
        SOURCE,
        PROCESS
    }

    public record Row(
            RowKind kind,
            String sourceId,
            String sourceName,
            int processCount,
            boolean expanded,
            boolean containsSelected,
            ResourceLocation processId,
            String displayName,
            int routeIndex,
            boolean selected
    ) {
        public static Row source(
                String sourceId,
                String sourceName,
                int processCount,
                boolean expanded,
                boolean containsSelected
        ) {
            return new Row(
                    RowKind.SOURCE,
                    sourceId,
                    sourceName,
                    processCount,
                    expanded,
                    containsSelected,
                    null,
                    "",
                    -1,
                    false
            );
        }

        public static Row process(
                String sourceId,
                String sourceName,
                ResourceLocation processId,
                String displayName,
                int routeIndex,
                boolean selected
        ) {
            return new Row(
                    RowKind.PROCESS,
                    sourceId,
                    sourceName,
                    0,
                    false,
                    false,
                    processId,
                    displayName,
                    routeIndex,
                    selected
            );
        }

        public boolean isSource() {
            return kind
                    == RowKind.SOURCE;
        }

        /*
         * Keep the old method name for the existing screen/mixin
         * click code. A "route" row is now specifically a process
         * selection row.
         */
        public boolean isRoute() {
            return kind
                    == RowKind.PROCESS;
        }
    }

    private static final class GroupAccumulator {

        private final String sourceId;
        private final String sourceName;

        private final List<ProcessEntry> options =
                new ArrayList<>();

        private boolean containsSelected;

        private GroupAccumulator(
                String sourceId,
                String sourceName
        ) {
            this.sourceId =
                    sourceId;

            this.sourceName =
                    sourceName == null
                            || sourceName.isBlank()
                            ? formatSourceName(
                            sourceId
                    )
                            : sourceName;
        }

        private void add(
                ProcessOption option,
                boolean selected
        ) {
            options.add(
                    new ProcessEntry(
                            option,
                            selected
                    )
            );

            if (selected) {
                containsSelected =
                        true;
            }
        }

        private String sourceId() {
            return sourceId;
        }

        private String sourceName() {
            return sourceName;
        }

        private List<ProcessEntry> options() {
            return options;
        }

        private boolean containsSelected() {
            return containsSelected;
        }
    }

    private record ProcessEntry(
            ProcessOption option,
            boolean selected
    ) {
    }
}