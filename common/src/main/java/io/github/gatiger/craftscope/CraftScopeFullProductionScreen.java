package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.production.CraftScopeProductionGraph;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/*
 * Dedicated scrollable viewer for complete production chains.
 *
 * The normal Project screen is an overview and intentionally keeps
 * production diagrams bounded.
 *
 * This screen instead gives the production graph a virtual canvas
 * that may be larger than the visible Minecraft window.
 *
 * Vertical canvas size:
 *
 *     determined by the largest number of parallel production
 *     branches at one dependency depth.
 *
 * Horizontal canvas size:
 *
 *     determined by production-chain depth and convergence columns.
 *
 * Controls:
 *
 *     Mouse wheel          -> vertical scroll
 *     Shift + mouse wheel  -> horizontal scroll
 *     Middle mouse drag    -> free pan
 *     Scrollbar drag       -> horizontal / vertical scroll
 */
public final class CraftScopeFullProductionScreen
        extends CraftScopeBaseScreen {

    private static final int WINDOW_MARGIN =
            8;

    private static final int HEADER_HEIGHT =
            30;

    private static final int DIAGRAM_MARGIN =
            8;

    private static final int SCROLLBAR_GUTTER =
            12;

    private static final int SCROLLBAR_THICKNESS =
            6;

    private static final int MIN_SCROLLBAR_THUMB =
            24;

    private static final double WHEEL_SCROLL_AMOUNT =
            46.0D;

    /*
     * Keep these synchronized with the Process Diagram renderer.
     *
     * They are used only to calculate the natural virtual-canvas
     * dimensions. The renderer remains authoritative for drawing.
     */
    private static final int DIAGRAM_NODE_WIDTH =
            82;

    private static final int DIAGRAM_NODE_HEIGHT =
            74;

    private static final int DIAGRAM_HORIZONTAL_GAP =
            22;

    private static final int DIAGRAM_VERTICAL_GAP =
            28;

    private static final int DIAGRAM_TITLE_HEIGHT =
            22;

    /*
     * Full Production should never enter the embedded adaptive
     * shrinking mode.
     */
    private static final int MIN_FULL_CANVAS_WIDTH =
            780;

    private static final int MIN_FULL_CANVAS_HEIGHT =
            360;

    private static final int CANVAS_PADDING =
            44;

    private final Screen parent;

    private final CraftScopeProductionRoute route;

    private final long requestedTargetCount;

    private int selectedNodeIndex =
            -1;

    private double scrollX =
            0.0D;

    private double scrollY =
            0.0D;

    private boolean draggingHorizontalScrollbar =
            false;

    private boolean draggingVerticalScrollbar =
            false;

    private boolean panningCanvas =
            false;

    private double scrollbarDragOffset =
            0.0D;

    public CraftScopeFullProductionScreen(
            Screen parent,
            CraftScopeProductionRoute route,
            long requestedTargetCount
    ) {
        super(
                Component.literal(
                        "Full Production"
                )
        );

        this.parent =
                Objects.requireNonNull(
                        parent,
                        "parent"
                );

        this.route =
                Objects.requireNonNull(
                        route,
                        "route"
                );

        this.requestedTargetCount =
                Math.max(
                        1L,
                        requestedTargetCount
                );
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth =
                50;

        int buttonHeight =
                18;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        width
                                - WINDOW_MARGIN
                                - buttonWidth
                                - 8,
                        WINDOW_MARGIN + 6,
                        buttonWidth,
                        buttonHeight,
                        Component.literal(
                                "Back"
                        ),
                        this::onClose
                )
        );

        clampScroll();
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int windowLeft =
                WINDOW_MARGIN;

        int windowTop =
                WINDOW_MARGIN;

        int windowRight =
                width
                        - WINDOW_MARGIN;

        int windowBottom =
                height
                        - WINDOW_MARGIN;

        CraftScopeUiTheme.drawBackdrop(
                graphics,
                width,
                height
        );

        CraftScopeUiTheme.drawWindow(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowBottom
        );

        CraftScopeUiTheme.drawHeader(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowTop + HEADER_HEIGHT
        );

        CraftScopeUiTheme.drawPlaceholderLogo(
                graphics,
                windowLeft + 8,
                windowTop + 6,
                18
        );

        graphics.drawString(
                font,
                "CraftScope",
                windowLeft + 32,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawString(
                font,
                "Full Production",
                windowLeft + 102,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        Viewport viewport =
                getViewport();

        CraftScopeUiTheme.drawPanel(
                graphics,
                viewport.left() - 4,
                viewport.top() - 4,
                viewport.right()
                        + SCROLLBAR_GUTTER
                        + 4,
                viewport.bottom()
                        + SCROLLBAR_GUTTER
                        + 4
        );

        CanvasMetrics canvas =
                getCanvasMetrics(
                        viewport
                );

        clampScroll(
                viewport,
                canvas
        );

        /*
         * Only Full Production owns this scissor.
         *
         * The Process Diagram renderer itself no longer manipulates
         * scissor state, so this push/pop remains balanced here.
         */
        graphics.enableScissor(
                viewport.left(),
                viewport.top(),
                viewport.right(),
                viewport.bottom()
        );

        graphics.pose()
                .pushPose();

        graphics.pose()
                .translate(
                        (float) (
                                viewport.left()
                                        - scrollX
                        ),
                        (float) (
                                viewport.top()
                                        - scrollY
                        ),
                        0.0F
                );

        CraftScopeProcessDiagramRenderer.render(
                graphics,
                font,
                route,
                0,
                0,
                canvas.width(),
                canvas.height(),
                requestedTargetCount,
                selectedNodeIndex
        );

        graphics.pose()
                .popPose();

        graphics.disableScissor();

        drawScrollbars(
                graphics,
                viewport,
                canvas
        );

        drawControlHint(
                graphics,
                viewport,
                canvas
        );

        /*
         * Render Back and any future widgets last.
         */
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    /*
     * ---------------------------------------------------------
     * Interaction
     * ---------------------------------------------------------
     */

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (super.mouseClicked(
                mouseX,
                mouseY,
                button
        )) {

            return true;
        }

        Viewport viewport =
                getViewport();

        CanvasMetrics canvas =
                getCanvasMetrics(
                        viewport
                );

        clampScroll(
                viewport,
                canvas
        );

        Scrollbars scrollbars =
                getScrollbars(
                        viewport,
                        canvas
                );

        /*
         * Left-click scrollbar interaction.
         */
        if (button == 0) {

            if (scrollbars.horizontalVisible()) {

                if (contains(
                        mouseX,
                        mouseY,
                        scrollbars.horizontalThumbLeft(),
                        scrollbars.horizontalTrackTop(),
                        scrollbars.horizontalThumbRight(),
                        scrollbars.horizontalTrackBottom()
                )) {

                    draggingHorizontalScrollbar =
                            true;

                    scrollbarDragOffset =
                            mouseX
                                    - scrollbars.horizontalThumbLeft();

                    return true;
                }

                if (contains(
                        mouseX,
                        mouseY,
                        scrollbars.horizontalTrackLeft(),
                        scrollbars.horizontalTrackTop(),
                        scrollbars.horizontalTrackRight(),
                        scrollbars.horizontalTrackBottom()
                )) {

                    setHorizontalScrollFromTrack(
                            mouseX,
                            scrollbars,
                            viewport,
                            canvas
                    );

                    return true;
                }
            }

            if (scrollbars.verticalVisible()) {

                if (contains(
                        mouseX,
                        mouseY,
                        scrollbars.verticalTrackLeft(),
                        scrollbars.verticalThumbTop(),
                        scrollbars.verticalTrackRight(),
                        scrollbars.verticalThumbBottom()
                )) {

                    draggingVerticalScrollbar =
                            true;

                    scrollbarDragOffset =
                            mouseY
                                    - scrollbars.verticalThumbTop();

                    return true;
                }

                if (contains(
                        mouseX,
                        mouseY,
                        scrollbars.verticalTrackLeft(),
                        scrollbars.verticalTrackTop(),
                        scrollbars.verticalTrackRight(),
                        scrollbars.verticalTrackBottom()
                )) {

                    setVerticalScrollFromTrack(
                            mouseY,
                            scrollbars,
                            viewport,
                            canvas
                    );

                    return true;
                }
            }
        }

        /*
         * Middle mouse drag pans the canvas.
         */
        if (button == 2
                && viewport.contains(
                mouseX,
                mouseY
        )) {

            panningCanvas =
                    true;

            return true;
        }

        if (button != 0
                || !viewport.contains(
                mouseX,
                mouseY
        )) {

            return false;
        }

        double logicalMouseX =
                mouseX
                        - viewport.left()
                        + scrollX;

        double logicalMouseY =
                mouseY
                        - viewport.top()
                        + scrollY;

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.hitTest(
                        route,
                        0,
                        0,
                        canvas.width(),
                        canvas.height(),
                        requestedTargetCount,
                        logicalMouseX,
                        logicalMouseY
                );

        if (selection == null) {

            selectedNodeIndex =
                    -1;

            return false;
        }

        selectedNodeIndex =
                selection.nodeIndex();

        return true;
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean handled =
                draggingHorizontalScrollbar
                        || draggingVerticalScrollbar
                        || panningCanvas;

        draggingHorizontalScrollbar =
                false;

        draggingVerticalScrollbar =
                false;

        panningCanvas =
                false;

        if (handled) {
            return true;
        }

        return super.mouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        Viewport viewport =
                getViewport();

        CanvasMetrics canvas =
                getCanvasMetrics(
                        viewport
                );

        Scrollbars scrollbars =
                getScrollbars(
                        viewport,
                        canvas
                );

        if (draggingHorizontalScrollbar
                && button == 0) {

            double thumbLeft =
                    mouseX
                            - scrollbarDragOffset;

            setHorizontalScrollFromThumb(
                    thumbLeft,
                    scrollbars,
                    viewport,
                    canvas
            );

            return true;
        }

        if (draggingVerticalScrollbar
                && button == 0) {

            double thumbTop =
                    mouseY
                            - scrollbarDragOffset;

            setVerticalScrollFromThumb(
                    thumbTop,
                    scrollbars,
                    viewport,
                    canvas
            );

            return true;
        }

        if (panningCanvas
                && button == 2) {

            scrollX -=
                    dragX;

            scrollY -=
                    dragY;

            clampScroll(
                    viewport,
                    canvas
            );

            return true;
        }

        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        Viewport viewport =
                getViewport();

        if (!viewport.contains(
                mouseX,
                mouseY
        )) {

            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    horizontalAmount,
                    verticalAmount
            );
        }

        CanvasMetrics canvas =
                getCanvasMetrics(
                        viewport
                );

        /*
         * Trackpads may supply real horizontal wheel movement.
         *
         * Shift + ordinary wheel provides the same behavior for a
         * standard mouse.
         */
        if (Math.abs(
                horizontalAmount
        ) > 0.0001D) {

            scrollX -=
                    horizontalAmount
                            * WHEEL_SCROLL_AMOUNT;

        } else if (Screen.hasShiftDown()) {

            scrollX -=
                    verticalAmount
                            * WHEEL_SCROLL_AMOUNT;

        } else {

            scrollY -=
                    verticalAmount
                            * WHEEL_SCROLL_AMOUNT;
        }

        clampScroll(
                viewport,
                canvas
        );

        return true;
    }

    /*
     * ---------------------------------------------------------
     * Virtual production canvas
     * ---------------------------------------------------------
     */

    private Viewport getViewport() {
        int left =
                WINDOW_MARGIN
                        + DIAGRAM_MARGIN
                        + 4;

        int top =
                WINDOW_MARGIN
                        + HEADER_HEIGHT
                        + DIAGRAM_MARGIN
                        + 4;

        int right =
                width
                        - WINDOW_MARGIN
                        - DIAGRAM_MARGIN
                        - 4
                        - SCROLLBAR_GUTTER;

        int bottom =
                height
                        - WINDOW_MARGIN
                        - DIAGRAM_MARGIN
                        - 4
                        - SCROLLBAR_GUTTER;

        /*
         * Defensive minimum for very small GUI sizes.
         */
        right =
                Math.max(
                        left + 40,
                        right
                );

        bottom =
                Math.max(
                        top + 40,
                        bottom
                );

        return new Viewport(
                left,
                top,
                right,
                bottom
        );
    }

    private CanvasMetrics getCanvasMetrics(
            Viewport viewport
    ) {
        CraftScopeProductionGraph graph =
                CraftScopeProductionGraph.fromRoute(
                        route
                );

        Map<Integer, Integer> depthByStep =
                new LinkedHashMap<>();

        Map<Integer, Integer> countByDepth =
                new LinkedHashMap<>();

        Map<Integer, Boolean> convergenceByDepth =
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

                int producerDepth =
                        depthByStep.getOrDefault(
                                edge.producerStepIndex(),
                                0
                        );

                depth =
                        Math.max(
                                depth,
                                producerDepth + 1
                        );
            }

            depthByStep.put(
                    node.index(),
                    depth
            );

            countByDepth.merge(
                    depth,
                    1,
                    Integer::sum
            );

            if (graph.incomingEdges(
                    node.index()
            ).size() > 1) {

                convergenceByDepth.put(
                        depth,
                        true
                );
            }

            maximumDepth =
                    Math.max(
                            maximumDepth,
                            depth
                    );
        }

        int maximumParallelSteps =
                1;

        for (Integer count :
                countByDepth.values()) {

            maximumParallelSteps =
                    Math.max(
                            maximumParallelSteps,
                            count
                    );
        }

        /*
         * Natural graph width:
         *
         * ordinary depth:
         *     Process | Output
         *
         * convergence depth:
         *     Inputs | Process | Output
         */
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

        int interDepthGapSlots =
                maximumDepth * 2;

        int totalGapSlots =
                internalGapSlots
                        + interDepthGapSlots;

        int naturalWidth =
                CANVAS_PADDING
                        + totalColumns
                        * DIAGRAM_NODE_WIDTH
                        + totalGapSlots
                        * DIAGRAM_HORIZONTAL_GAP;

        /*
         * Natural graph height is driven by the busiest dependency
         * depth -- effectively the number of parallel branches that
         * must coexist vertically.
         */
        int naturalBranchHeight =
                maximumParallelSteps
                        * DIAGRAM_NODE_HEIGHT
                        + Math.max(
                        0,
                        maximumParallelSteps - 1
                )
                        * DIAGRAM_VERTICAL_GAP;

        int naturalHeight =
                CANVAS_PADDING
                        + DIAGRAM_TITLE_HEIGHT
                        + naturalBranchHeight;

        int canvasWidth =
                Math.max(
                        Math.max(
                                viewport.width(),
                                MIN_FULL_CANVAS_WIDTH
                        ),
                        naturalWidth
                );

        int canvasHeight =
                Math.max(
                        Math.max(
                                viewport.height(),
                                MIN_FULL_CANVAS_HEIGHT
                        ),
                        naturalHeight
                );

        return new CanvasMetrics(
                canvasWidth,
                canvasHeight
        );
    }

    /*
     * ---------------------------------------------------------
     * Scroll state
     * ---------------------------------------------------------
     */

    private void clampScroll() {
        Viewport viewport =
                getViewport();

        clampScroll(
                viewport,
                getCanvasMetrics(
                        viewport
                )
        );
    }

    private void clampScroll(
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        scrollX =
                clamp(
                        scrollX,
                        0.0D,
                        getMaximumScrollX(
                                viewport,
                                canvas
                        )
                );

        scrollY =
                clamp(
                        scrollY,
                        0.0D,
                        getMaximumScrollY(
                                viewport,
                                canvas
                        )
                );
    }

    private static double getMaximumScrollX(
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        return Math.max(
                0.0D,
                canvas.width()
                        - viewport.width()
        );
    }

    private static double getMaximumScrollY(
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        return Math.max(
                0.0D,
                canvas.height()
                        - viewport.height()
        );
    }

    /*
     * ---------------------------------------------------------
     * Scrollbars
     * ---------------------------------------------------------
     */

    private Scrollbars getScrollbars(
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        double maximumScrollX =
                getMaximumScrollX(
                        viewport,
                        canvas
                );

        double maximumScrollY =
                getMaximumScrollY(
                        viewport,
                        canvas
                );

        boolean horizontalVisible =
                maximumScrollX > 0.001D;

        boolean verticalVisible =
                maximumScrollY > 0.001D;

        int horizontalTrackLeft =
                viewport.left();

        int horizontalTrackRight =
                viewport.right();

        int horizontalTrackTop =
                viewport.bottom() + 3;

        int horizontalTrackBottom =
                horizontalTrackTop
                        + SCROLLBAR_THICKNESS;

        int horizontalThumbWidth =
                horizontalVisible
                        ? Math.max(
                        MIN_SCROLLBAR_THUMB,
                        (int) Math.round(
                                (
                                        double
                                ) viewport.width()
                                        / canvas.width()
                                        * viewport.width()
                        )
                )
                        : viewport.width();

        horizontalThumbWidth =
                Math.min(
                        viewport.width(),
                        horizontalThumbWidth
                );

        int horizontalTravel =
                Math.max(
                        0,
                        viewport.width()
                                - horizontalThumbWidth
                );

        int horizontalThumbLeft =
                horizontalTrackLeft;

        if (horizontalVisible
                && horizontalTravel > 0) {

            horizontalThumbLeft +=
                    (int) Math.round(
                            scrollX
                                    / maximumScrollX
                                    * horizontalTravel
                    );
        }

        int horizontalThumbRight =
                horizontalThumbLeft
                        + horizontalThumbWidth;

        int verticalTrackLeft =
                viewport.right() + 3;

        int verticalTrackRight =
                verticalTrackLeft
                        + SCROLLBAR_THICKNESS;

        int verticalTrackTop =
                viewport.top();

        int verticalTrackBottom =
                viewport.bottom();

        int verticalThumbHeight =
                verticalVisible
                        ? Math.max(
                        MIN_SCROLLBAR_THUMB,
                        (int) Math.round(
                                (
                                        double
                                ) viewport.height()
                                        / canvas.height()
                                        * viewport.height()
                        )
                )
                        : viewport.height();

        verticalThumbHeight =
                Math.min(
                        viewport.height(),
                        verticalThumbHeight
                );

        int verticalTravel =
                Math.max(
                        0,
                        viewport.height()
                                - verticalThumbHeight
                );

        int verticalThumbTop =
                verticalTrackTop;

        if (verticalVisible
                && verticalTravel > 0) {

            verticalThumbTop +=
                    (int) Math.round(
                            scrollY
                                    / maximumScrollY
                                    * verticalTravel
                    );
        }

        int verticalThumbBottom =
                verticalThumbTop
                        + verticalThumbHeight;

        return new Scrollbars(
                horizontalVisible,
                verticalVisible,
                horizontalTrackLeft,
                horizontalTrackTop,
                horizontalTrackRight,
                horizontalTrackBottom,
                horizontalThumbLeft,
                horizontalThumbRight,
                verticalTrackLeft,
                verticalTrackTop,
                verticalTrackRight,
                verticalTrackBottom,
                verticalThumbTop,
                verticalThumbBottom
        );
    }

    private void drawScrollbars(
            GuiGraphics graphics,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        Scrollbars scrollbars =
                getScrollbars(
                        viewport,
                        canvas
                );

        if (scrollbars.horizontalVisible()) {

            graphics.fill(
                    scrollbars.horizontalTrackLeft(),
                    scrollbars.horizontalTrackTop(),
                    scrollbars.horizontalTrackRight(),
                    scrollbars.horizontalTrackBottom(),
                    CraftScopeUiTheme.BORDER_SUBTLE
            );

            graphics.fill(
                    scrollbars.horizontalThumbLeft(),
                    scrollbars.horizontalTrackTop(),
                    scrollbars.horizontalThumbRight(),
                    scrollbars.horizontalTrackBottom(),
                    CraftScopeUiTheme.ACCENT
            );
        }

        if (scrollbars.verticalVisible()) {

            graphics.fill(
                    scrollbars.verticalTrackLeft(),
                    scrollbars.verticalTrackTop(),
                    scrollbars.verticalTrackRight(),
                    scrollbars.verticalTrackBottom(),
                    CraftScopeUiTheme.BORDER_SUBTLE
            );

            graphics.fill(
                    scrollbars.verticalTrackLeft(),
                    scrollbars.verticalThumbTop(),
                    scrollbars.verticalTrackRight(),
                    scrollbars.verticalThumbBottom(),
                    CraftScopeUiTheme.ACCENT
            );
        }
    }

    private void setHorizontalScrollFromTrack(
            double mouseX,
            Scrollbars scrollbars,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        double thumbCenter =
                mouseX
                        - (
                        scrollbars.horizontalThumbRight()
                                - scrollbars.horizontalThumbLeft()
                ) / 2.0D;

        setHorizontalScrollFromThumb(
                thumbCenter,
                scrollbars,
                viewport,
                canvas
        );
    }

    private void setHorizontalScrollFromThumb(
            double thumbLeft,
            Scrollbars scrollbars,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        int thumbWidth =
                scrollbars.horizontalThumbRight()
                        - scrollbars.horizontalThumbLeft();

        double travel =
                Math.max(
                        1.0D,
                        viewport.width()
                                - thumbWidth
                );

        double normalized =
                (
                        thumbLeft
                                - scrollbars.horizontalTrackLeft()
                ) / travel;

        normalized =
                clamp(
                        normalized,
                        0.0D,
                        1.0D
                );

        scrollX =
                normalized
                        * getMaximumScrollX(
                        viewport,
                        canvas
                );
    }

    private void setVerticalScrollFromTrack(
            double mouseY,
            Scrollbars scrollbars,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        double thumbCenter =
                mouseY
                        - (
                        scrollbars.verticalThumbBottom()
                                - scrollbars.verticalThumbTop()
                ) / 2.0D;

        setVerticalScrollFromThumb(
                thumbCenter,
                scrollbars,
                viewport,
                canvas
        );
    }

    private void setVerticalScrollFromThumb(
            double thumbTop,
            Scrollbars scrollbars,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        int thumbHeight =
                scrollbars.verticalThumbBottom()
                        - scrollbars.verticalThumbTop();

        double travel =
                Math.max(
                        1.0D,
                        viewport.height()
                                - thumbHeight
                );

        double normalized =
                (
                        thumbTop
                                - scrollbars.verticalTrackTop()
                ) / travel;

        normalized =
                clamp(
                        normalized,
                        0.0D,
                        1.0D
                );

        scrollY =
                normalized
                        * getMaximumScrollY(
                        viewport,
                        canvas
                );
    }

    private void drawControlHint(
            GuiGraphics graphics,
            Viewport viewport,
            CanvasMetrics canvas
    ) {
        boolean horizontal =
                getMaximumScrollX(
                        viewport,
                        canvas
                ) > 0.001D;

        boolean vertical =
                getMaximumScrollY(
                        viewport,
                        canvas
                ) > 0.001D;

        if (!horizontal
                && !vertical) {

            return;
        }

        String hint;

        if (horizontal
                && vertical) {

            hint =
                    "Wheel: vertical  •  Shift+Wheel: horizontal  •  Middle-drag: pan";

        } else if (horizontal) {

            hint =
                    "Shift+Wheel or scrollbar: horizontal";

        } else {

            hint =
                    "Wheel or scrollbar: vertical";
        }

        /*
         * Keep the control hint on the right side of the header.
         *
         * The CraftScope logo/title occupies the left side and the
         * Back button occupies the far right. Only render the hint
         * when enough space exists between them.
         */
        int hintWidth =
                font.width(
                        hint
                );

        int hintLeftBoundary =
                WINDOW_MARGIN + 220;

        int hintRightBoundary =
                width
                        - WINDOW_MARGIN
                        - 82;

        int hintX =
                hintRightBoundary
                        - hintWidth;

        if (hintX
                >= hintLeftBoundary) {

            graphics.drawString(
                    font,
                    hint,
                    hintX,
                    WINDOW_MARGIN + 11,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------
     */

    private static boolean contains(
            double mouseX,
            double mouseY,
            int left,
            int top,
            int right,
            int bottom
    ) {
        return mouseX >= left
                && mouseX < right
                && mouseY >= top
                && mouseY < bottom;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(
                parent
        );
    }

    private record Viewport(
            int left,
            int top,
            int right,
            int bottom
    ) {

        private int width() {
            return Math.max(
                    1,
                    right - left
            );
        }

        private int height() {
            return Math.max(
                    1,
                    bottom - top
            );
        }

        private boolean contains(
                double x,
                double y
        ) {
            return x >= left
                    && x < right
                    && y >= top
                    && y < bottom;
        }
    }

    private record CanvasMetrics(
            int width,
            int height
    ) {
    }

    private record Scrollbars(
            boolean horizontalVisible,
            boolean verticalVisible,

            int horizontalTrackLeft,
            int horizontalTrackTop,
            int horizontalTrackRight,
            int horizontalTrackBottom,

            int horizontalThumbLeft,
            int horizontalThumbRight,

            int verticalTrackLeft,
            int verticalTrackTop,
            int verticalTrackRight,
            int verticalTrackBottom,

            int verticalThumbTop,
            int verticalThumbBottom
    ) {
    }
}