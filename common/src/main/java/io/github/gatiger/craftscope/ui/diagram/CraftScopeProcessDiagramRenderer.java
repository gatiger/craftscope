package io.github.gatiger.craftscope.ui.diagram;

import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionGraph;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeRecipeVariantFamilyPolicy;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftScopeProcessDiagramRenderer {

    private static final int NODE_WIDTH =
            82;

    private static final int NODE_HEIGHT =
            74;

    private static final int HORIZONTAL_GAP =
            22;

    private static final int VERTICAL_GAP =
            28;

    private static final int ROUTE_TITLE_HEIGHT =
            22;

    private static final long VARIANT_CYCLE_MS =
            1000L;

    /*
     * Multiple independently produced resources feeding one process
     * rotate through the shared convergence/input node at the same
     * relaxed speed used by the older grouped-resource UI.
     */
    private static final long GRAPH_INPUT_CYCLE_MS =
            1600L;

    /*
     * Large graphs may outgrow the embedded Process Diagram panel.
     *
     * Scale the entire diagram proportionally instead of shrinking
     * individual UI pieces independently.
     *
     * Never go below 72%. Beyond that point the dedicated
     * "View Full Production" screen is the better presentation.
     */
    /*
     * 78% is the smallest embedded diagram size CraftScope should
     * use. Smaller than this becomes noticeably harder to read.
     *
     * Large routes continue in the dedicated Full Production view
     * rather than shrinking further.
     */
    private static final float MIN_ADAPTIVE_DIAGRAM_SCALE =
            0.78F;

    private static final int LARGE_DIAGRAM_STEP_THRESHOLD =
            5;

    /*
     * Six production steps can still be displayed at the minimum
     * readable embedded scale.
     *
     * Larger routes keep the readable scale and use a continuation
     * marker rather than attempting to squeeze the entire graph into
     * the Project screen.
     */
    private static final int EMBEDDED_COMPLETE_STEP_LIMIT =
            6;

    private static final int CONTINUATION_RESERVE_WIDTH =
            142;

    private static final int CONTINUATION_PANEL_WIDTH =
            126;

    private static final int CONTINUATION_PANEL_HEIGHT =
            62;

    /*
     * A diagram this large already has enough room and does not need
     * compact rendering. These values are Minecraft GUI coordinates,
     * not physical monitor pixels.
     */
    private static final int COMFORTABLE_DIAGRAM_WIDTH =
            760;

    private static final int COMFORTABLE_DIAGRAM_HEIGHT =
            320;

    private CraftScopeProcessDiagramRenderer() {
    }

    /*
     * ---------------------------------------------------------
     * Public rendering API
     * ---------------------------------------------------------
     */

    public static void render(
            GuiGraphics graphics,
            Font font,
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom,
            long requestedTargetCount
    ) {
        render(
                graphics,
                font,
                route,
                left,
                top,
                right,
                bottom,
                requestedTargetCount,
                -1
        );
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom,
            long requestedTargetCount,
            int selectedNodeIndex
    ) {
        if (route == null) {

            graphics.drawCenteredString(
                    font,
                    "Select a production route.",
                    (left + right) / 2,
                    top + 20,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        float renderScale =
                getAdaptiveDiagramScale(
                        route,
                        left,
                        top,
                        right,
                        bottom
                );

        boolean scaled =
                renderScale < 0.999F;

        /*
         * Determine this once for the entire render pass so both the
         * graph clipping and final continuation marker can use it.
         */
        boolean continuedInFullProduction =
                shouldUseEmbeddedContinuation(
                        route,
                        renderScale
                );

        int continuationLeft =
                getContinuationLeft(
                        left,
                        right
                );

        float scaleCenterX =
                (left + right) / 2.0F;

        float scaleCenterY =
                (top + bottom) / 2.0F;

        if (scaled) {
            graphics.pose()
                    .pushPose();

            graphics.pose()
                    .translate(
                            scaleCenterX,
                            scaleCenterY,
                            0.0F
                    );

            graphics.pose()
                    .scale(
                            renderScale,
                            renderScale,
                            1.0F
                    );

            graphics.pose()
                    .translate(
                            -scaleCenterX,
                            -scaleCenterY,
                            0.0F
                    );
        }

        String routeTitle =
                route.sourceModName()
                        .getString()
                        + ": "
                        + route.displayName()
                        .getString();

        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        routeTitle,
                        right - left - 20
                ),
                (left + right) / 2,
                top + 5,
                CraftScopeUiTheme.SUCCESS
        );

        DiagramLayout layout =
                buildDiagramLayout(
                        route,
                        left,
                        top,
                        right,
                        bottom,
                        requestedTargetCount
                );

        if (layout.nodes().isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "This route contains no drawable steps.",
                    (left + right) / 2,
                    top + 34,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            if (scaled) {
                graphics.pose()
                        .popPose();
            }

            return;
        }

        /*
         * Connections first so nodes remain above them.
         *
         * Connections are explicit rather than inferred from list
         * order. Linear diagrams still use sequential connections,
         * while the upcoming graph layout can supply branching and
         * converging edges.
         */
        for (DiagramConnection connection :
                layout.connections()) {

            int fromIndex =
                    connection.fromNodeIndex();

            int toIndex =
                    connection.toNodeIndex();

            NodePosition fromPosition =
                    layout.positions().get(
                            fromIndex
                    );

            NodePosition toPosition =
                    layout.positions().get(
                            toIndex
                    );

            if (!isVisibleBeforeContinuation(
                    fromPosition,
                    continuedInFullProduction,
                    renderScale,
                    left,
                    right
            )
                    || !isVisibleBeforeContinuation(
                    toPosition,
                    continuedInFullProduction,
                    renderScale,
                    left,
                    right
            )) {

                continue;
            }

            boolean highlighted =
                    selectedNodeIndex == fromIndex
                            || selectedNodeIndex == toIndex;

            drawConnection(
                    graphics,
                    fromPosition,
                    toPosition,
                    highlighted
            );
        }

        for (int i = 0;
             i < layout.nodes().size();
             i++) {

            NodePosition position =
                    layout.positions().get(
                            i
                    );

            if (!isVisibleBeforeContinuation(
                    position,
                    continuedInFullProduction,
                    renderScale,
                    left,
                    right
            )) {

                continue;
            }

            drawNode(
                    graphics,
                    font,
                    layout.nodes().get(i),
                    position,
                    selectedNodeIndex == i
            );
        }

        if (scaled) {
            graphics.pose()
                    .popPose();
        }

        if (continuedInFullProduction) {

            drawContinuationMarker(
                    graphics,
                    font,
                    left,
                    top,
                    right,
                    bottom
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * Public interaction API
     * ---------------------------------------------------------
     */

    public static Selection hitTest(
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom,
            long requestedTargetCount,
            double mouseX,
            double mouseY
    ) {
        if (route == null) {
            return null;
        }

        DiagramLayout layout =
                buildDiagramLayout(
                        route,
                        left,
                        top,
                        right,
                        bottom,
                        requestedTargetCount
                );

        float renderScale =
                getAdaptiveDiagramScale(
                        route,
                        left,
                        top,
                        right,
                        bottom
                );

        boolean continuedInFullProduction =
                shouldUseEmbeddedContinuation(
                        route,
                        renderScale
                );

        if (continuedInFullProduction) {

            int continuationLeft =
                    getContinuationLeft(
                            left,
                            right
                    );

            if (mouseX
                    >= continuationLeft - 8) {

                return null;
            }
        }

        double logicalMouseX =
                mouseX;

        double logicalMouseY =
                mouseY;

        if (renderScale < 0.999F) {

            double centerX =
                    (left + right) / 2.0D;

            double centerY =
                    (top + bottom) / 2.0D;

            logicalMouseX =
                    centerX
                            + (
                            mouseX - centerX
                    ) / renderScale;

            logicalMouseY =
                    centerY
                            + (
                            mouseY - centerY
                    ) / renderScale;
        }

        for (int i = 0;
             i < layout.positions().size();
             i++) {

            NodePosition position =
                    layout.positions().get(i);

            if (!isVisibleBeforeContinuation(
                    position,
                    continuedInFullProduction,
                    renderScale,
                    left,
                    right
            )) {

                continue;
            }

            if (logicalMouseX >= position.x()
                    && logicalMouseX < position.right()
                    && logicalMouseY >= position.y()
                    && logicalMouseY < position.bottom()) {

                return layout.nodes()
                        .get(i)
                        .toSelection(
                                i
                        );
            }
        }

        return null;
    }

    public static Selection getSelection(
            CraftScopeProductionRoute route,
            long requestedTargetCount,
            int nodeIndex
    ) {
        if (route == null
                || nodeIndex < 0) {

            return null;
        }

        List<DiagramNode> nodes =
                buildNodes(
                        route,
                        requestedTargetCount
                );

        if (nodeIndex >= nodes.size()) {
            return null;
        }

        return nodes
                .get(nodeIndex)
                .toSelection(
                        nodeIndex
                );
    }

    public static ItemStack getSelectionDisplayStack(
            Selection selection
    ) {
        if (selection == null) {
            return ItemStack.EMPTY;
        }

        if (selection.kind()
                == SelectionKind.RESOURCE) {

            return getResourceStack(
                    selection.resource()
            );
        }

        return getProcessStack(
                selection.step()
        );
    }

    public static String getSelectionDisplayName(
            Selection selection
    ) {
        if (selection == null) {
            return "";
        }

        if (selection.kind()
                == SelectionKind.RESOURCE) {

            return getResourceDisplayName(
                    selection.resource()
            );
        }

        return selection.step()
                .displayName()
                .getString();
    }

    /*
     * ---------------------------------------------------------
     * Adaptive embedded-diagram scaling
     * ---------------------------------------------------------
     */

    private static float getAdaptiveDiagramScale(
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (route == null) {
            return 1.0F;
        }

        int stepCount =
                route.steps().size();

        if (stepCount
                < LARGE_DIAGRAM_STEP_THRESHOLD) {

            return 1.0F;
        }

        int availableWidth =
                Math.max(
                        1,
                        right - left
                );

        int availableHeight =
                Math.max(
                        1,
                        bottom - top
                );

        /*
         * Full Production normally lands here: it already has enough
         * room, so preserve the standard 100% diagram size.
         */
        if (availableWidth
                >= COMFORTABLE_DIAGRAM_WIDTH
                && availableHeight
                >= COMFORTABLE_DIAGRAM_HEIGHT) {

            return 1.0F;
        }

        /*
         * Embedded compact levels.
         *
         * 5 steps:
         *     modest reduction
         *
         * 6 steps:
         *     larger reduction -- this is the size of the current
         *     Enchanting Table test graph.
         *
         * 7+:
         *     minimum embedded size. Use Full Production for anything
         *     that still needs more room.
         */
        float scale;

        if (stepCount == 5) {

            scale =
                    0.84F;

        } else {

            /*
             * Six or more steps have reached CraftScope's minimum
             * readable embedded size.
             *
             * Routes larger than the embedded view can hold use the
             * continuation marker instead of shrinking further.
             */
            scale =
                    MIN_ADAPTIVE_DIAGRAM_SCALE;
        }

        return Math.max(
                MIN_ADAPTIVE_DIAGRAM_SCALE,
                Math.min(
                        1.0F,
                        scale
                )
        );
    }
    private static boolean isVisibleBeforeContinuation(
            NodePosition position,
            boolean continuedInFullProduction,
            float renderScale,
            int left,
            int right
    ) {
        if (!continuedInFullProduction) {
            return true;
        }

        double centerX =
                (
                        left
                                + right
                ) / 2.0D;

        double visualLeftCutoff =
                left + 4.0D;

        double visualRightCutoff =
                getContinuationLeft(
                        left,
                        right
                )
                        - 8.0D;

        /*
         * Undo the render transform so both cutoffs can be compared
         * against logical NodePosition coordinates.
         */
        double logicalLeftCutoff =
                centerX
                        + (
                        visualLeftCutoff
                                - centerX
                ) / Math.max(
                        0.001F,
                        renderScale
                );

        double logicalRightCutoff =
                centerX
                        + (
                        visualRightCutoff
                                - centerX
                ) / Math.max(
                        0.001F,
                        renderScale
                );

        /*
         * Never show a partially clipped node.
         */
        return position.x()
                >= logicalLeftCutoff
                && position.right()
                <= logicalRightCutoff;
    }

    /*
     * Public interaction helper for the embedded continuation card.
     *
     * The Project screen can use this without duplicating the
     * continuation-policy thresholds or marker dimensions.
     */
    public static boolean isContinuationMarkerHit(
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom,
            double mouseX,
            double mouseY
    ) {
        if (route == null) {
            return false;
        }

        float renderScale =
                getAdaptiveDiagramScale(
                        route,
                        left,
                        top,
                        right,
                        bottom
                );

        if (!shouldUseEmbeddedContinuation(
                route,
                renderScale
        )) {

            return false;
        }

        int markerRight =
                right - 8;

        int markerLeft =
                markerRight
                        - CONTINUATION_PANEL_WIDTH;

        int usableTop =
                top
                        + ROUTE_TITLE_HEIGHT;

        int markerTop =
                usableTop
                        + Math.max(
                        4,
                        (
                                bottom
                                        - usableTop
                                        - CONTINUATION_PANEL_HEIGHT
                        ) / 2
                );

        int markerBottom =
                markerTop
                        + CONTINUATION_PANEL_HEIGHT;

        return mouseX >= markerLeft
                && mouseX < markerRight
                && mouseY >= markerTop
                && mouseY < markerBottom;
    }
    private static boolean shouldUseEmbeddedContinuation(
            CraftScopeProductionRoute route,
            float renderScale
    ) {
        if (route == null) {
            return false;
        }

        /*
         * Full Production normally renders at 100%, so it never
         * enters this path.
         *
         * The embedded Project view reaches the minimum scale first.
         */
        return route.steps().size()
                > EMBEDDED_COMPLETE_STEP_LIMIT
                && renderScale
                <= MIN_ADAPTIVE_DIAGRAM_SCALE + 0.001F;
    }

    private static int getContinuationLeft(
            int left,
            int right
    ) {
        return Math.max(
                left + 40,
                right
                        - CONTINUATION_RESERVE_WIDTH
        );
    }

    private static void drawContinuationMarker(
            GuiGraphics graphics,
            Font font,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int markerRight =
                right - 8;

        int markerLeft =
                markerRight
                        - CONTINUATION_PANEL_WIDTH;

        int usableTop =
                top
                        + ROUTE_TITLE_HEIGHT;

        int markerTop =
                usableTop
                        + Math.max(
                        4,
                        (
                                bottom
                                        - usableTop
                                        - CONTINUATION_PANEL_HEIGHT
                        ) / 2
                );

        int markerBottom =
                markerTop
                        + CONTINUATION_PANEL_HEIGHT;

        int centerX =
                markerLeft
                        + CONTINUATION_PANEL_WIDTH / 2;

        int centerY =
                markerTop
                        + CONTINUATION_PANEL_HEIGHT / 2;

        /*
         * Small arrow leading into the continuation marker.
         */
        drawRightArrow(
                graphics,
                Math.max(
                        left + 4,
                        markerLeft - 22
                ),
                markerLeft - 4,
                centerY,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        CraftScopeUiTheme.drawPanel(
                graphics,
                markerLeft,
                markerTop,
                markerRight,
                markerBottom,
                CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                markerLeft,
                markerTop,
                markerRight,
                markerBottom,
                CraftScopeUiTheme.ACCENT
        );

        graphics.drawCenteredString(
                font,
                "...",
                centerX,
                markerTop + 7,
                CraftScopeUiTheme.ACCENT_HOVER
        );

        graphics.drawCenteredString(
                font,
                "Continued in",
                centerX,
                markerTop + 20,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawCenteredString(
                font,
                "Full Production",
                centerX,
                markerTop + 33,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawCenteredString(
                font,
                "View",
                centerX,
                markerTop + 46,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }
    /*
     * ---------------------------------------------------------
     * Shared diagram layout
     * ---------------------------------------------------------
     */

    private static DiagramLayout buildDiagramLayout(
            CraftScopeProductionRoute route,
            int left,
            int top,
            int right,
            int bottom,
            long requestedTargetCount
    ) {
        /*
         * Ordinary routes retain the existing sequential/serpentine
         * layout.
         *
         * Only a route with a real convergence point switches to the
         * graph layout.
         */
        if (route != null
                && route.steps().size() > 1) {

            CraftScopeProductionGraph graph =
                    CraftScopeProductionGraph.fromRoute(
                            route
                    );

            if (graph.hasBranchingInputs()) {

                DiagramLayout graphLayout =
                        buildGraphDiagramLayout(
                                route,
                                graph,
                                left,
                                top,
                                right,
                                bottom,
                                requestedTargetCount
                        );

                if (graphLayout != null) {
                    return graphLayout;
                }
            }
        }

        List<DiagramNode> nodes =
                buildNodes(
                        route,
                        requestedTargetCount
                );

        if (nodes.isEmpty()) {

            return new DiagramLayout(
                    nodes,
                    List.of(),
                    List.of()
            );
        }

        int diagramTop =
                top
                        + ROUTE_TITLE_HEIGHT;

        int diagramHeight =
                bottom
                        - diagramTop;

        int diagramWidth =
                right
                        - left;

        int maxColumns =
                Math.max(
                        1,
                        (
                                diagramWidth
                                        + HORIZONTAL_GAP
                        )
                                / (
                                NODE_WIDTH
                                        + HORIZONTAL_GAP
                        )
                );

        maxColumns =
                Math.min(
                        maxColumns,
                        nodes.size()
                );

        int rowCount =
                (
                        nodes.size()
                                + maxColumns
                                - 1
                )
                        / maxColumns;

        int requiredHeight =
                rowCount
                        * NODE_HEIGHT
                        + Math.max(
                        0,
                        rowCount - 1
                )
                        * VERTICAL_GAP;

        int startY =
                diagramTop
                        + Math.max(
                        4,
                        (
                                diagramHeight
                                        - requiredHeight
                        ) / 2
                );

        int usedWidth =
                maxColumns
                        * NODE_WIDTH
                        + Math.max(
                        0,
                        maxColumns - 1
                )
                        * HORIZONTAL_GAP;

        int startX =
                left
                        + Math.max(
                        4,
                        (
                                diagramWidth
                                        - usedWidth
                        ) / 2
                );

        List<NodePosition> positions =
                buildPositions(
                        nodes.size(),
                        maxColumns,
                        startX,
                        startY
                );

        return new DiagramLayout(
                nodes,
                positions,
                buildSequentialConnections(
                        nodes.size()
                )
        );
    }

    private static List<DiagramConnection>
    buildSequentialConnections(
            int nodeCount
    ) {
        if (nodeCount <= 1) {
            return List.of();
        }

        List<DiagramConnection> result =
                new ArrayList<>();

        for (int i = 0;
             i < nodeCount - 1;
             i++) {

            result.add(
                    new DiagramConnection(
                            i,
                            i + 1
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    /*
     * ---------------------------------------------------------
     * Branching graph layout
     * ---------------------------------------------------------
     *
     * Every production step is represented by:
     *
     *     Process -> Produced Resource
     *
     * The produced-resource node then connects to whichever later
     * process consumes it.
     *
     * Several resource nodes may therefore converge on one process.
     */
    private static DiagramLayout buildGraphDiagramLayout(
            CraftScopeProductionRoute route,
            CraftScopeProductionGraph graph,
            int left,
            int top,
            int right,
            int bottom,
            long requestedTargetCount
    ) {
        if (route == null
                || graph == null
                || graph.steps().isEmpty()) {

            return null;
        }

        long routeOutputAmount =
                Math.max(
                        1L,
                        route.targetOutput().amount()
                );

        long runs =
                ceilDiv(
                        Math.max(
                                1L,
                                requestedTargetCount
                        ),
                        routeOutputAmount
                );

        /*
         * Determine the dependency depth of every step.
         *
         * Because CraftScopeProductionGraph is topologically ordered,
         * parent depths are already known when the consumer is
         * visited.
         */
        Map<Integer, Integer> depthByStep =
                new LinkedHashMap<>();

        int maximumDepth =
                0;

        for (CraftScopeProductionGraph.StepNode node :
                graph.steps()) {

            int depth =
                    0;

            for (CraftScopeProductionGraph.Edge edge :
                    graph.incomingEdges(
                            node.index()
                    )) {

                int parentDepth =
                        depthByStep.getOrDefault(
                                edge.producerStepIndex(),
                                0
                        );

                depth =
                        Math.max(
                                depth,
                                parentDepth + 1
                        );
            }

            depthByStep.put(
                    node.index(),
                    depth
            );

            maximumDepth =
                    Math.max(
                            maximumDepth,
                            depth
                    );
        }

        Map<Integer, Integer> stepCountByDepth =
                new LinkedHashMap<>();

        for (Integer depth :
                depthByStep.values()) {

            stepCountByDepth.merge(
                    depth,
                    1,
                    Integer::sum
            );
        }

        int diagramTop =
                top
                        + ROUTE_TITLE_HEIGHT;

        int diagramWidth =
                Math.max(
                        1,
                        right - left
                );

        int diagramHeight =
                Math.max(
                        1,
                        bottom - diagramTop
                );

        /*
         * Determine which dependency depths require a dedicated
         * convergence/input column.
         *
         * Normal depth:
         *
         *     Process | Output
         *
         * Branching depth:
         *
         *     Inputs | Process | Output
         *
         * Keeping convergence horizontal prevents the rotating shared
         * input node from colliding with branches above or below it.
         */
        Map<Integer, Boolean> convergenceByDepth =
                new LinkedHashMap<>();

        for (CraftScopeProductionGraph.StepNode node :
                graph.steps()) {

            if (graph.incomingEdges(
                    node.index()
            ).size() <= 1) {

                continue;
            }

            int depth =
                    depthByStep.getOrDefault(
                            node.index(),
                            0
                    );

            convergenceByDepth.put(
                    depth,
                    true
            );
        }

        int depthCount =
                maximumDepth + 1;

        int totalColumns =
                0;

        int internalGapSlots =
                0;

        for (int depth = 0;
             depth <= maximumDepth;
             depth++) {

            int columns =
                    convergenceByDepth.getOrDefault(
                            depth,
                            false
                    )
                            ? 3
                            : 2;

            totalColumns +=
                    columns;

            internalGapSlots +=
                    columns - 1;
        }

        /*
         * Two additional gap-widths separate each dependency depth.
         */
        int interDepthGapSlots =
                maximumDepth * 2;

        int gapSlots =
                internalGapSlots
                        + interDepthGapSlots;

        int totalNodeWidth =
                totalColumns
                        * NODE_WIDTH;

        int availableGapWidth =
                Math.max(
                        0,
                        diagramWidth
                                - totalNodeWidth
                                - 8
                );

        int graphGap =
                Math.min(
                        HORIZONTAL_GAP,
                        Math.max(
                                4,
                                availableGapWidth
                                        / Math.max(
                                        1,
                                        gapSlots
                                )
                        )
                );

        int requiredWidth =
                totalNodeWidth
                        + gapSlots
                        * graphGap;
        float adaptiveScale =
                getAdaptiveDiagramScale(
                        route,
                        left,
                        top,
                        right,
                        bottom
                );

        boolean continuedInFullProduction =
                shouldUseEmbeddedContinuation(
                        route,
                        adaptiveScale
                );

        int startX;

        if (continuedInFullProduction) {

            double centerX =
                    (
                            left
                                    + right
                    ) / 2.0D;

            double desiredVisualLeft =
                    left + 8.0D;

            /*
             * Rendering scales around centerX:
             *
             * visual = center + (logical - center) * scale
             *
             * Solve that equation backward so the first logical
             * graph column appears at left + 8 after scaling.
             */
            startX =
                    (int) Math.round(
                            centerX
                                    + (
                                    desiredVisualLeft
                                            - centerX
                            ) / Math.max(
                                    0.001F,
                                    adaptiveScale
                            )
                    );

        } else {

            startX =
                    left
                            + (
                            diagramWidth
                                    - requiredWidth
                    ) / 2;
        }

        /*
         * Precalculate the beginning of every dependency depth.
         *
         * This supports variable-width depths:
         *
         *     2 columns for ordinary flow
         *     3 columns when several resources converge
         */
        Map<Integer, Integer> depthStartXByDepth =
                new LinkedHashMap<>();

        int depthCursorX =
                startX;

        for (int depth = 0;
             depth <= maximumDepth;
             depth++) {

            depthStartXByDepth.put(
                    depth,
                    depthCursorX
            );

            int columns =
                    convergenceByDepth.getOrDefault(
                            depth,
                            false
                    )
                            ? 3
                            : 2;

            depthCursorX +=
                    columns
                            * NODE_WIDTH
                            + (
                            columns - 1
                    )
                            * graphGap;

            if (depth
                    < maximumDepth) {

                depthCursorX +=
                        graphGap * 2;
            }
        }

        List<DiagramNode> nodes =
                new ArrayList<>();

        List<NodePosition> positions =
                new ArrayList<>();

        List<DiagramConnection> connections =
                new ArrayList<>();

        Map<Integer, Integer> processNodeByStep =
                new LinkedHashMap<>();

        Map<Integer, Integer> outputNodeByStep =
                new LinkedHashMap<>();

        /*
         * A process with several independently produced inputs gets
         * one shared resource node immediately before the process.
         *
         * That node is where grouped-input rotation belongs.
         */
        Map<Integer, Integer> convergenceNodeByStep =
                new LinkedHashMap<>();

        Map<Integer, Integer> depthOffset =
                new LinkedHashMap<>();

        for (CraftScopeProductionGraph.StepNode graphNode :
                graph.steps()) {

            int stepIndex =
                    graphNode.index();

            CraftScopeProductionStep step =
                    graphNode.step();

            int depth =
                    depthByStep.getOrDefault(
                            stepIndex,
                            0
                    );

            int countAtDepth =
                    stepCountByDepth.getOrDefault(
                            depth,
                            1
                    );

            int offset =
                    depthOffset.getOrDefault(
                            depth,
                            0
                    );

            depthOffset.put(
                    depth,
                    offset + 1
            );

            int groupHeight =
                    countAtDepth
                            * NODE_HEIGHT
                            + Math.max(
                            0,
                            countAtDepth - 1
                    )
                            * VERTICAL_GAP;

            int depthStartY =
                    diagramTop
                            + (
                            diagramHeight
                                    - groupHeight
                    ) / 2;

            int y =
                    depthStartY
                            + offset
                            * (
                            NODE_HEIGHT
                                    + VERTICAL_GAP
                    );

            int depthStartX =
                    depthStartXByDepth.getOrDefault(
                            depth,
                            startX
                    );

            boolean depthHasConvergence =
                    convergenceByDepth.getOrDefault(
                            depth,
                            false
                    );

            int inputX =
                    depthStartX;

            int processX =
                    depthStartX
                            + (
                            depthHasConvergence
                                    ? NODE_WIDTH
                                    + graphGap
                                    : 0
                    );

            int outputX =
                    processX
                            + NODE_WIDTH
                            + graphGap;

            int processNodeIndex =
                    nodes.size();

            nodes.add(
                    DiagramNode.process(
                            step
                    )
            );

            positions.add(
                    new NodePosition(
                            processX,
                            y,
                            NODE_WIDTH,
                            NODE_HEIGHT
                    )
            );

            processNodeByStep.put(
                    stepIndex,
                    processNodeIndex
            );

            /*
             * When several produced resources converge on this
             * process, add a shared input node.
             *
             * Producer output nodes remain truthful:
             *
             *     Blaze Powder remains Blaze Powder.
             *     Slimeball remains Slimeball.
             *
             * The shared node represents the process input group and
             * rotates between those actual consumed resources.
             */
            List<CraftScopeProductionGraph.Edge> incomingEdges =
                    graph.incomingEdges(
                            stepIndex
                    );

            if (incomingEdges.size() > 1) {

                long cycle =
                        System.currentTimeMillis()
                                / GRAPH_INPUT_CYCLE_MS;

                int resourceIndex =
                        (int) (
                                cycle
                                        % incomingEdges.size()
                        );

                CraftScopeResourceAmount displayedInput =
                        CraftScopeRecipeVariantFamilyPolicy
                                .normalizeFamilyResource(
                                        incomingEdges
                                                .get(
                                                        resourceIndex
                                                )
                                                .consumedResource()
                                );

                int convergenceNodeIndex =
                        nodes.size();

                nodes.add(
                        DiagramNode.resource(
                                displayedInput,
                                safeMultiply(
                                        displayedInput.amount(),
                                        runs
                                ),
                                0
                        )
                );

                /*
                 * Dedicated horizontal convergence column.
                 *
                 * The rotating shared-input node now occupies the
                 * column immediately before its consuming process.
                 * It cannot collide with branches above or below.
                 */
                positions.add(
                        new NodePosition(
                                inputX,
                                y,
                                NODE_WIDTH,
                                NODE_HEIGHT
                        )
                );

                convergenceNodeByStep.put(
                        stepIndex,
                        convergenceNodeIndex
                );

                connections.add(
                        new DiagramConnection(
                                convergenceNodeIndex,
                                processNodeIndex
                        )
                );
            }
            CraftScopeResourceAmount flowOutput =
                    getGraphFlowOutput(
                            route,
                            graph,
                            stepIndex,
                            step
                    );

            if (flowOutput == null) {

                /*
                 * A production step without a drawable output cannot
                 * participate safely in this graph renderer.
                 *
                 * Fall back to the proven sequential renderer.
                 */
                return null;
            }

            int outputNodeIndex =
                    nodes.size();

            nodes.add(
                    DiagramNode.resource(
                            flowOutput,
                            safeMultiply(
                                    flowOutput.amount(),
                                    runs
                            ),
                            Math.max(
                                    0,
                                    step.outputs().size() - 1
                            )
                    )
            );

            positions.add(
                    new NodePosition(
                            outputX,
                            y,
                            NODE_WIDTH,
                            NODE_HEIGHT
                    )
            );

            outputNodeByStep.put(
                    stepIndex,
                    outputNodeIndex
            );

            connections.add(
                    new DiagramConnection(
                            processNodeIndex,
                            outputNodeIndex
                    )
            );
        }

        /*
         * Connect produced-resource nodes to their real consumers.
         *
         * This is the part that creates convergence:
         *
         *     resource A ─┐
         *                 ├─> process C
         *     resource B ─┘
         */
        for (CraftScopeProductionGraph.Edge edge :
                graph.edges()) {

            Integer from =
                    outputNodeByStep.get(
                            edge.producerStepIndex()
                    );

            Integer to =
                    convergenceNodeByStep.get(
                            edge.consumerStepIndex()
                    );

            if (to == null) {

                to =
                        processNodeByStep.get(
                                edge.consumerStepIndex()
                        );
            }

            if (from == null
                    || to == null) {

                return null;
            }

            DiagramConnection connection =
                    new DiagramConnection(
                            from,
                            to
                    );

            if (!connections.contains(
                    connection
            )) {

                connections.add(
                        connection
                );
            }
        }

        return new DiagramLayout(
                nodes,
                positions,
                connections
        );
    }

    /*
     * Place the grouped-input node vertically so adding it does not
     * consume another horizontal graph column.
     *
     * This is especially important on narrower Process Diagram
     * panels where another full-width column would force the route
     * outside its bounds.
     */
    private static int findConvergenceNodeY(
            int processY,
            int diagramTop,
            int diagramBottom
    ) {
        int spacing =
                8;

        int above =
                processY
                        - NODE_HEIGHT
                        - spacing;

        if (above >= diagramTop + 4) {
            return above;
        }

        int below =
                processY
                        + NODE_HEIGHT
                        + spacing;

        if (below + NODE_HEIGHT
                <= diagramBottom - 4) {

            return below;
        }

        return -1;
    }
    /*
     * Choose the resource that represents flow out of a step.
     *
     * For an upstream step, prefer the exact resource consumed by its
     * downstream edge.
     *
     * For a terminal step, prefer the route target.
     */
    private static CraftScopeResourceAmount getGraphFlowOutput(
            CraftScopeProductionRoute route,
            CraftScopeProductionGraph graph,
            int stepIndex,
            CraftScopeProductionStep step
    ) {
        List<CraftScopeProductionGraph.Edge> outgoing =
                graph.outgoingEdges(
                        stepIndex
                );

        if (!outgoing.isEmpty()) {

            CraftScopeResourceAmount first =
                    outgoing
                            .getFirst()
                            .producedResource();

            /*
             * First graph-rendering pass remains conservative when one
             * process feeds several consumers with materially different
             * output resources.
             */
            for (CraftScopeProductionGraph.Edge edge :
                    outgoing) {

                if (!CraftScopeProductionGraph.resourcesMatch(
                        first,
                        edge.producedResource()
                )) {

                    return null;
                }
            }

            return first;
        }

        if (route != null
                && step
                == route.steps().getLast()) {

            for (CraftScopeResourceAmount output :
                    step.outputs()) {

                if (CraftScopeProductionGraph.resourcesMatch(
                        output,
                        route.targetOutput()
                )) {

                    return output;
                }
            }

            return route.targetOutput();
        }

        if (!step.outputs().isEmpty()) {
            return step.outputs().getFirst();
        }

        return null;
    }

    /*
     * ---------------------------------------------------------
     * Diagram model
     * ---------------------------------------------------------
     */

    private static List<DiagramNode> buildNodes(
            CraftScopeProductionRoute route,
            long requestedTargetCount
    ) {
        List<DiagramNode> nodes =
                new ArrayList<>();

        if (route == null
                || route.steps().isEmpty()) {

            return nodes;
        }

        long routeOutputAmount =
                Math.max(
                        1,
                        route.targetOutput().amount()
                );

        long runs =
                ceilDiv(
                        Math.max(
                                1,
                                requestedTargetCount
                        ),
                        routeOutputAmount
                );

        CraftScopeProductionStep firstStep =
                route.steps()
                        .getFirst();

        if (!firstStep.inputs().isEmpty()) {

            CraftScopeResourceAmount input =
                    firstStep.inputs()
                            .getFirst();

            nodes.add(
                    DiagramNode.resource(
                            input,
                            safeMultiply(
                                    input.amount(),
                                    runs
                            ),
                            Math.max(
                                    0,
                                    firstStep.inputs().size() - 1
                            )
                    )
            );
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            nodes.add(
                    DiagramNode.process(
                            step
                    )
            );

            CraftScopeResourceAmount output =
                    null;

            if (!step.outputs().isEmpty()) {

                output =
                        step.outputs()
                                .getFirst();

            } else if (step
                    == route.steps()
                    .getLast()) {

                output =
                        route.targetOutput();
            }

            if (output != null) {

                nodes.add(
                        DiagramNode.resource(
                                output,
                                safeMultiply(
                                        output.amount(),
                                        runs
                                ),
                                Math.max(
                                        0,
                                        step.outputs().size() - 1
                                )
                        )
                );
            }
        }

        return nodes;
    }

    private static List<NodePosition> buildPositions(
            int nodeCount,
            int maxColumns,
            int startX,
            int startY
    ) {
        List<NodePosition> result =
                new ArrayList<>();

        for (int i = 0;
             i < nodeCount;
             i++) {

            int row =
                    i / maxColumns;

            int offset =
                    i % maxColumns;

            boolean reverse =
                    row % 2 == 1;

            int column =
                    reverse
                            ? maxColumns - 1 - offset
                            : offset;

            int x =
                    startX
                            + column
                            * (
                            NODE_WIDTH
                                    + HORIZONTAL_GAP
                    );

            int y =
                    startY
                            + row
                            * (
                            NODE_HEIGHT
                                    + VERTICAL_GAP
                    );

            result.add(
                    new NodePosition(
                            x,
                            y,
                            NODE_WIDTH,
                            NODE_HEIGHT
                    )
            );
        }

        return result;
    }

    /*
     * ---------------------------------------------------------
     * Node rendering
     * ---------------------------------------------------------
     */

    private static void drawNode(
            GuiGraphics graphics,
            Font font,
            DiagramNode node,
            NodePosition position,
            boolean selected
    ) {
        CraftScopeUiTheme.drawPanel(
                graphics,
                position.x(),
                position.y(),
                position.right(),
                position.bottom(),
                selected
                        ? CraftScopeUiTheme.ACCENT_BACKGROUND
                        : CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

        if (selected) {

            CraftScopeUiTheme.drawBorder(
                    graphics,
                    position.x(),
                    position.y(),
                    position.right(),
                    position.bottom(),
                    CraftScopeUiTheme.ACCENT_HOVER
            );
        }

        if (node.kind()
                == DiagramNodeKind.RESOURCE) {

            drawResourceNode(
                    graphics,
                    font,
                    node,
                    position
            );

        } else {

            drawProcessNode(
                    graphics,
                    font,
                    node,
                    position
            );
        }
    }

    private static void drawResourceNode(
            GuiGraphics graphics,
            Font font,
            DiagramNode node,
            NodePosition position
    ) {
        CraftScopeResourceAmount resource =
                node.resource();

        ItemStack stack =
                getResourceStack(
                        resource
                );

        if (!stack.isEmpty()) {

            renderLargeItem(
                    graphics,
                    stack,
                    position.x()
                            + position.width() / 2,
                    position.y() + 8
            );

        } else {

            drawResourcePlaceholder(
                    graphics,
                    font,
                    resource,
                    position
            );
        }

        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        getResourceDisplayName(
                                resource
                        ),
                        position.width() - 8
                ),
                position.x()
                        + position.width() / 2,
                position.y() + 42,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        drawBoundedResourceAmount(
                graphics,
                font,
                resource,
                node.amount(),
                position
        );

        if (node.extraCount() > 0) {

            String extra =
                    "+"
                            + node.extraCount();

            graphics.drawString(
                    font,
                    extra,
                    position.right()
                            - font.width(
                            extra
                    )
                            - 4,
                    position.y() + 4,
                    CraftScopeUiTheme.ACCENT_HOVER
            );
        }
    }

    /*
     * Resource quantity/yield text can become fairly long once a
     * probabilistic variable yield contains:
     *
     *     range
     *     expected amount
     *     probability
     *
     * Example:
     *
     *     x0-6 ≈0.3 @17.9%
     *
     * Never allow that text to escape the resource node.
     *
     * Prefer two readable lines when probability information is
     * present:
     *
     *     x0-6 ≈0.3
     *     @17.9%
     *
     * Otherwise fall back to the normal width-fitting behavior.
     */
    private static void drawBoundedResourceAmount(
            GuiGraphics graphics,
            Font font,
            CraftScopeResourceAmount resource,
            long amount,
            NodePosition position
    ) {
        String text =
                formatAmount(
                        resource,
                        amount
                );

        int maxWidth =
                Math.max(
                        1,
                        position.width() - 8
                );

        int centerX =
                position.x()
                        + position.width() / 2;

        if (font.width(
                text
        ) <= maxWidth) {

            graphics.drawCenteredString(
                    font,
                    text,
                    centerX,
                    position.y() + 56,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            return;
        }

        /*
         * Probability is the natural split point for variable/
         * probabilistic yield text.
         */
        int probabilitySplit =
                text.lastIndexOf(
                        " @"
                );

        if (probabilitySplit > 0) {

            String firstLine =
                    text.substring(
                            0,
                            probabilitySplit
                    );

            String secondLine =
                    text.substring(
                            probabilitySplit + 1
                    );

            graphics.drawCenteredString(
                    font,
                    fitText(
                            font,
                            firstLine,
                            maxWidth
                    ),
                    centerX,
                    position.y() + 52,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            graphics.drawCenteredString(
                    font,
                    fitText(
                            font,
                            secondLine,
                            maxWidth
                    ),
                    centerX,
                    position.y() + 63,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        /*
         * Non-probabilistic text still receives a hard width limit.
         */
        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        text,
                        maxWidth
                ),
                centerX,
                position.y() + 56,
                CraftScopeUiTheme.TEXT_SECONDARY
        );
    }
    private static void drawProcessNode(
            GuiGraphics graphics,
            Font font,
            DiagramNode node,
            NodePosition position
    ) {
        CraftScopeProductionStep step =
                node.step();

        ItemStack processStack =
                getProcessStack(
                        step
                );

        if (!processStack.isEmpty()) {

            renderLargeItem(
                    graphics,
                    processStack,
                    position.x()
                            + position.width() / 2,
                    position.y() + 8
            );

        } else {

            int boxSize =
                    24;

            int boxX =
                    position.x()
                            + (
                            position.width()
                                    - boxSize
                    ) / 2;

            int boxY =
                    position.y() + 8;

            graphics.fill(
                    boxX,
                    boxY,
                    boxX + boxSize,
                    boxY + boxSize,
                    CraftScopeUiTheme.ACCENT_BACKGROUND
            );

            CraftScopeUiTheme.drawBorder(
                    graphics,
                    boxX,
                    boxY,
                    boxX + boxSize,
                    boxY + boxSize,
                    CraftScopeUiTheme.ACCENT
            );

            graphics.drawCenteredString(
                    font,
                    "P",
                    boxX + boxSize / 2,
                    boxY + 8,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        getMethodDisplayName(
                                step
                        ),
                        position.width() - 8
                ),
                position.x()
                        + position.width() / 2,
                position.y() + 42,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        String detail =
                getProcessDetail(
                        step
                );

        if (!detail.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    fitText(
                            font,
                            detail,
                            position.width() - 8
                    ),
                    position.x()
                            + position.width() / 2,
                    position.y() + 56,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * Connections
     * ---------------------------------------------------------
     */

    private static void drawConnection(
            GuiGraphics graphics,
            NodePosition from,
            NodePosition to,
            boolean highlighted
    ) {
        int color =
                highlighted
                        ? CraftScopeUiTheme.ACCENT_HOVER
                        : CraftScopeUiTheme.TEXT_SECONDARY;

        int fromCenterX =
                from.x()
                        + from.width() / 2;

        int fromCenterY =
                from.y()
                        + from.height() / 2;

        int toCenterX =
                to.x()
                        + to.width() / 2;

        int toCenterY =
                to.y()
                        + to.height() / 2;

        /*
         * Simple horizontal connection.
         */
        if (fromCenterY
                == toCenterY) {

            if (toCenterX
                    > fromCenterX) {

                drawRightArrow(
                        graphics,
                        from.right() + 3,
                        to.x() - 3,
                        fromCenterY,
                        color
                );

            } else {

                drawLeftArrow(
                        graphics,
                        from.x() - 3,
                        to.right() + 3,
                        fromCenterY,
                        color
                );
            }

            return;
        }

        /*
         * Pure vertical connection.
         */
        if (fromCenterX
                == toCenterX) {

            if (toCenterY
                    > fromCenterY) {

                drawDownArrow(
                        graphics,
                        fromCenterX,
                        from.bottom() + 3,
                        to.y() - 3,
                        color
                );

            } else {

                drawUpArrow(
                        graphics,
                        fromCenterX,
                        from.y() - 3,
                        to.bottom() + 3,
                        color
                );
            }

            return;
        }

        /*
         * Branching graph connection.
         *
         * Route through an orthogonal elbow:
         *
         *     source ─────┐
         *                 │
         *                 └────> target
         *
         * The vertical segment may travel either upward or downward.
         */
        if (toCenterX
                > fromCenterX) {

            int startX =
                    from.right() + 3;

            int endX =
                    to.x() - 3;

            if (endX <= startX) {
                return;
            }

            int elbowX =
                    startX
                            + (
                            endX - startX
                    ) / 2;

            graphics.fill(
                    startX,
                    fromCenterY,
                    elbowX + 1,
                    fromCenterY + 1,
                    color
            );

            drawVerticalLine(
                    graphics,
                    elbowX,
                    fromCenterY,
                    toCenterY,
                    color
            );

            drawRightArrow(
                    graphics,
                    elbowX,
                    endX,
                    toCenterY,
                    color
            );

            return;
        }

        int startX =
                from.x() - 3;

        int endX =
                to.right() + 3;

        if (startX <= endX) {
            return;
        }

        int elbowX =
                endX
                        + (
                        startX - endX
                ) / 2;

        graphics.fill(
                elbowX,
                fromCenterY,
                startX,
                fromCenterY + 1,
                color
        );

        drawVerticalLine(
                graphics,
                elbowX,
                fromCenterY,
                toCenterY,
                color
        );

        drawLeftArrow(
                graphics,
                elbowX,
                endX,
                toCenterY,
                color
        );
    }

    private static void drawRightArrow(
            GuiGraphics graphics,
            int startX,
            int endX,
            int y,
            int color
    ) {
        if (endX <= startX) {
            return;
        }

        int headX =
                endX - 4;

        graphics.fill(
                startX,
                y,
                headX,
                y + 1,
                color
        );

        graphics.fill(
                headX,
                y - 2,
                headX + 1,
                y + 3,
                color
        );

        graphics.fill(
                headX + 1,
                y - 1,
                headX + 2,
                y + 2,
                color
        );

        graphics.fill(
                headX + 2,
                y,
                endX,
                y + 1,
                color
        );
    }

    private static void drawLeftArrow(
            GuiGraphics graphics,
            int startX,
            int endX,
            int y,
            int color
    ) {
        if (startX <= endX) {
            return;
        }

        int headX =
                endX + 4;

        graphics.fill(
                headX,
                y,
                startX,
                y + 1,
                color
        );

        graphics.fill(
                headX - 1,
                y - 2,
                headX,
                y + 3,
                color
        );

        graphics.fill(
                headX - 2,
                y - 1,
                headX - 1,
                y + 2,
                color
        );

        graphics.fill(
                endX,
                y,
                headX - 2,
                y + 1,
                color
        );
    }

    private static void drawVerticalLine(
            GuiGraphics graphics,
            int x,
            int fromY,
            int toY,
            int color
    ) {
        int top =
                Math.min(
                        fromY,
                        toY
                );

        int bottom =
                Math.max(
                        fromY,
                        toY
                );

        if (bottom <= top) {
            return;
        }

        graphics.fill(
                x,
                top,
                x + 1,
                bottom + 1,
                color
        );
    }

    private static void drawUpArrow(
            GuiGraphics graphics,
            int x,
            int startY,
            int endY,
            int color
    ) {
        if (startY <= endY) {
            return;
        }

        int headY =
                endY + 4;

        graphics.fill(
                x,
                headY,
                x + 1,
                startY,
                color
        );

        graphics.fill(
                x - 2,
                headY - 1,
                x + 3,
                headY,
                color
        );

        graphics.fill(
                x - 1,
                headY - 2,
                x + 2,
                headY - 1,
                color
        );

        graphics.fill(
                x,
                endY,
                x + 1,
                headY - 2,
                color
        );
    }
    private static void drawDownArrow(
            GuiGraphics graphics,
            int x,
            int startY,
            int endY,
            int color
    ) {
        if (endY <= startY) {
            return;
        }

        int headY =
                endY - 4;

        graphics.fill(
                x,
                startY,
                x + 1,
                headY,
                color
        );

        graphics.fill(
                x - 2,
                headY,
                x + 3,
                headY + 1,
                color
        );

        graphics.fill(
                x - 1,
                headY + 1,
                x + 2,
                headY + 2,
                color
        );

        graphics.fill(
                x,
                headY + 2,
                x + 1,
                endY,
                color
        );
    }

    /*
     * ---------------------------------------------------------
     * Resource helpers
     * ---------------------------------------------------------
     */

    private static ItemStack getResourceStack(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null
                || resource.kind()
                != CraftScopeResourceKind.ITEM) {

            return ItemStack.EMPTY;
        }

        /*
         * Preserve exact Minecraft item components when CraftScope
         * knows the resource identity.
         *
         * This is required for tipped arrows, Ominous Bottle levels,
         * and future component-bearing vanilla/modded resources.
         */
        /*
         * Exact component-bearing resources must preserve their exact
         * ItemStack:
         *
         *     Poison Tipped Arrow
         *     Ominous Bottle III
         *
         * Ordinary componentless resources, however, should continue
         * into the acceptedVariantIds logic below so tag-backed
         * families can rotate their icons.
         */
        if (resource.hasCustomItemComponents()) {

            ItemStack identityStack =
                    resource.createDisplayStack();

            if (!identityStack.isEmpty()) {

                return identityStack;
            }
        }

        List<ResourceLocation> variants =
                resource.acceptedVariantIds();

        ResourceLocation id =
                resource.id();

        if (variants != null
                && !variants.isEmpty()) {

            if (variants.size() == 1) {

                id =
                        variants.getFirst();

            } else {

                long cycle =
                        System.currentTimeMillis()
                                / VARIANT_CYCLE_MS;

                int index =
                        (int) (
                                cycle
                                        % variants.size()
                        );

                id =
                        variants.get(index);
            }
        }

        return getItemStack(
                id
        );
    }

    private static ItemStack getProcessStack(
            CraftScopeProductionStep step
    ) {
        if (step == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation machineId =
                findPrimaryMachineId(
                        step
                );

        if (machineId != null) {

            ItemStack stack =
                    getItemStack(
                            machineId
                    );

            if (!stack.isEmpty()) {
                return stack;
            }
        }

        if (!step.methods().isEmpty()) {

            ResourceLocation processId =
                    step.getPrimaryMethod()
                            .processId();

            String processPath =
                    processId.getPath();

            if (processPath.contains(
                    "crafting"
            )) {

                return new ItemStack(
                        Items.CRAFTING_TABLE
                );
            }

            if (processPath.contains(
                    "blasting"
            )) {

                return new ItemStack(
                        Items.BLAST_FURNACE
                );
            }

            if (processPath.contains(
                    "smelting"
            )) {

                return new ItemStack(
                        Items.FURNACE
                );
            }
        }

        return ItemStack.EMPTY;
    }

    private static ResourceLocation findPrimaryMachineId(
            CraftScopeProductionStep step
    ) {
        for (CraftScopeProductionMethod method :
                step.methods()) {

            for (CraftScopeProcessRequirement requirement :
                    method.requirements()) {

                if (requirement.kind()
                        == CraftScopeRequirementKind.MACHINE
                        && requirement.id() != null) {

                    return requirement.id();
                }
            }
        }

        return null;
    }

    private static ItemStack getItemStack(
            ResourceLocation id
    ) {
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        id
                );

        if (item == null
                || item == Items.AIR) {

            return ItemStack.EMPTY;
        }

        return new ItemStack(
                item
        );
    }

    private static String getResourceDisplayName(
            CraftScopeResourceAmount resource
    ) {
        String name =
                resource.displayName()
                        .getString();

        if (resource.hasVariants()) {

            return "Any "
                    + name;
        }

        return name;
    }

    private static String formatAmount(
            CraftScopeResourceAmount resource,
            long amount
    ) {
        if (resource.hasUnit()) {

            return amount
                    + " "
                    + resource.unit();
        }

        return "x"
                + amount;
    }

    private static void drawResourcePlaceholder(
            GuiGraphics graphics,
            Font font,
            CraftScopeResourceAmount resource,
            NodePosition position
    ) {
        int boxSize =
                24;

        int boxX =
                position.x()
                        + (
                        position.width()
                                - boxSize
                ) / 2;

        int boxY =
                position.y() + 8;

        graphics.fill(
                boxX,
                boxY,
                boxX + boxSize,
                boxY + boxSize,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                boxX,
                boxY,
                boxX + boxSize,
                boxY + boxSize,
                CraftScopeUiTheme.BORDER_HOVER
        );

        String letter =
                switch (resource.kind()) {

                    case FLUID ->
                            "F";

                    case CHEMICAL ->
                            "C";

                    case ITEM ->
                            "I";

                    case OTHER ->
                            "?";
                };

        graphics.drawCenteredString(
                font,
                letter,
                boxX + boxSize / 2,
                boxY + 8,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    /*
     * ---------------------------------------------------------
     * Process helpers
     * ---------------------------------------------------------
     */

    private static String getMethodDisplayName(
            CraftScopeProductionStep step
    ) {
        if (step.methods().isEmpty()) {

            return step.displayName()
                    .getString();
        }

        if (step.methods().size() == 1) {

            return step.methods()
                    .getFirst()
                    .displayName()
                    .getString();
        }

        if (step.methods().size() == 2) {

            return step.methods()
                    .get(0)
                    .displayName()
                    .getString()
                    + " / "
                    + step.methods()
                    .get(1)
                    .displayName()
                    .getString();
        }

        return step.methods()
                .getFirst()
                .displayName()
                .getString()
                + " +"
                + (
                step.methods().size()
                        - 1
        );
    }

    private static String getProcessDetail(
            CraftScopeProductionStep step
    ) {
        int methodCount =
                step.methods().size();

        int inputCount =
                step.inputs().size();

        if (methodCount > 1
                && inputCount > 1) {

            return methodCount
                    + " methods, "
                    + inputCount
                    + " inputs";
        }

        if (methodCount > 1) {

            return methodCount
                    + " methods";
        }

        if (inputCount > 1) {

            return inputCount
                    + " inputs";
        }

        return "";
    }

    private static void renderLargeItem(
            GuiGraphics graphics,
            ItemStack stack,
            int centerX,
            int y
    ) {
        float scale =
                1.5F;

        int renderedSize =
                24;

        graphics.pose()
                .pushPose();

        graphics.pose()
                .translate(
                        centerX
                                - renderedSize / 2.0F,
                        y,
                        0.0F
                );

        graphics.pose()
                .scale(
                        scale,
                        scale,
                        1.0F
                );

        graphics.renderItem(
                stack,
                0,
                0
        );

        graphics.pose()
                .popPose();
    }

    /*
     * ---------------------------------------------------------
     * General helpers
     * ---------------------------------------------------------
     */

    private static String fitText(
            Font font,
            String text,
            int maxWidth
    ) {
        if (text == null
                || text.isEmpty()
                || maxWidth <= 0) {

            return "";
        }

        if (font.width(
                text
        ) <= maxWidth) {

            return text;
        }

        String ellipsis =
                "...";

        int availableWidth =
                Math.max(
                        0,
                        maxWidth
                                - font.width(
                                ellipsis
                        )
                );

        String result =
                text;

        while (!result.isEmpty()
                && font.width(
                result
        ) > availableWidth) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result
                + ellipsis;
    }

    private static long ceilDiv(
            long value,
            long divisor
    ) {
        if (divisor <= 0) {
            return 0;
        }

        return value / divisor
                + (
                value % divisor == 0
                        ? 0
                        : 1
        );
    }

    private static long safeMultiply(
            long left,
            long right
    ) {
        if (left == 0
                || right == 0) {

            return 0;
        }

        if (left > Long.MAX_VALUE / right) {

            return Long.MAX_VALUE;
        }

        return left
                * right;
    }

    /*
     * ---------------------------------------------------------
     * Public selection model
     * ---------------------------------------------------------
     */

    public enum SelectionKind {

        RESOURCE,

        PROCESS
    }

    public record Selection(
            int nodeIndex,
            SelectionKind kind,
            CraftScopeResourceAmount resource,
            CraftScopeProductionStep step,
            long amount,
            int extraCount
    ) {

        public boolean isResource() {
            return kind
                    == SelectionKind.RESOURCE;
        }

        public boolean isProcess() {
            return kind
                    == SelectionKind.PROCESS;
        }
    }

    /*
     * ---------------------------------------------------------
     * Internal models
     * ---------------------------------------------------------
     */

    private enum DiagramNodeKind {

        RESOURCE,

        PROCESS
    }

    private record DiagramNode(
            DiagramNodeKind kind,
            CraftScopeResourceAmount resource,
            CraftScopeProductionStep step,
            long amount,
            int extraCount
    ) {

        private static DiagramNode resource(
                CraftScopeResourceAmount resource,
                long amount,
                int extraCount
        ) {
            return new DiagramNode(
                    DiagramNodeKind.RESOURCE,
                    resource,
                    null,
                    amount,
                    extraCount
            );
        }

        private static DiagramNode process(
                CraftScopeProductionStep step
        ) {
            return new DiagramNode(
                    DiagramNodeKind.PROCESS,
                    null,
                    step,
                    0,
                    0
            );
        }

        private Selection toSelection(
                int nodeIndex
        ) {
            return new Selection(
                    nodeIndex,
                    kind == DiagramNodeKind.RESOURCE
                            ? SelectionKind.RESOURCE
                            : SelectionKind.PROCESS,
                    resource,
                    step,
                    amount,
                    extraCount
            );
        }
    }

    private record NodePosition(
            int x,
            int y,
            int width,
            int height
    ) {

        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }

    private record DiagramConnection(
            int fromNodeIndex,
            int toNodeIndex
    ) {
        private DiagramConnection {
            if (fromNodeIndex < 0
                    || toNodeIndex < 0) {

                throw new IllegalArgumentException(
                        "Diagram connection indexes cannot be negative"
                );
            }

            if (fromNodeIndex == toNodeIndex) {

                throw new IllegalArgumentException(
                        "Diagram connection cannot point to itself"
                );
            }
        }
    }

    private record DiagramLayout(
            List<DiagramNode> nodes,
            List<NodePosition> positions,
            List<DiagramConnection> connections
    ) {
        private DiagramLayout {
            nodes =
                    nodes == null
                            ? List.of()
                            : List.copyOf(
                            nodes
                    );

            positions =
                    positions == null
                            ? List.of()
                            : List.copyOf(
                            positions
                    );

            connections =
                    connections == null
                            ? List.of()
                            : List.copyOf(
                            connections
                    );

            if (nodes.size()
                    != positions.size()) {

                throw new IllegalArgumentException(
                        "Every diagram node must have a position"
                );
            }

            for (DiagramConnection connection :
                    connections) {

                if (connection.fromNodeIndex()
                        >= nodes.size()
                        || connection.toNodeIndex()
                        >= nodes.size()) {

                    throw new IllegalArgumentException(
                            "Diagram connection references a missing node"
                    );
                }
            }
        }
    }
}