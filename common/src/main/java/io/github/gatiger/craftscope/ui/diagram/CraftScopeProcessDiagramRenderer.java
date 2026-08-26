package io.github.gatiger.craftscope.ui.diagram;

import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
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
import java.util.List;

/*
 * Visual renderer for CraftScope production routes.
 *
 * The renderer deliberately knows nothing about Screen widgets
 * or project management. It receives a production route and a
 * rectangular drawing area and turns that route into nodes and
 * connections.
 *
 * Current milestone:
 *
 *     resource -> process -> resource
 *
 * Longer routes automatically wrap into a serpentine layout:
 *
 *     A -> Process -> B -> Process -> C
 *                                  |
 *     E <- Process <- D <- Process <
 *
 * This gives us a scalable base for future Mekanism / Create
 * production chains before pan and zoom are added.
 */
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

    private CraftScopeProcessDiagramRenderer() {
    }

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

        List<DiagramNode> nodes =
                buildNodes(
                        route,
                        requestedTargetCount
                );

        if (nodes.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "This route contains no drawable steps.",
                    (left + right) / 2,
                    top + 34,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
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

        /*
         * Connections are drawn first so node cards remain on
         * top of the lines.
         */
        for (int i = 0;
             i < positions.size() - 1;
             i++) {

            drawConnection(
                    graphics,
                    positions.get(i),
                    positions.get(i + 1)
            );
        }

        for (int i = 0;
             i < nodes.size();
             i++) {

            drawNode(
                    graphics,
                    font,
                    nodes.get(i),
                    positions.get(i)
            );
        }
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

        if (route.steps().isEmpty()) {
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
     * Node drawing
     * ---------------------------------------------------------
     */

    private static void drawNode(
            GuiGraphics graphics,
            Font font,
            DiagramNode node,
            NodePosition position
    ) {
        CraftScopeUiTheme.drawPanel(
                graphics,
                position.x(),
                position.y(),
                position.right(),
                position.bottom(),
                CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

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

        String name =
                getResourceDisplayName(
                        resource
                );

        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        name,
                        position.width() - 8
                ),
                position.x()
                        + position.width() / 2,
                position.y() + 42,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        String amountText =
                formatAmount(
                        resource,
                        node.amount()
                );

        graphics.drawCenteredString(
                font,
                amountText,
                position.x()
                        + position.width() / 2,
                position.y() + 56,
                CraftScopeUiTheme.TEXT_SECONDARY
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

        String methodName =
                getMethodDisplayName(
                        step
                );

        graphics.drawCenteredString(
                font,
                fitText(
                        font,
                        methodName,
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
            NodePosition to
    ) {
        int color =
                CraftScopeUiTheme.TEXT_SECONDARY;

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
         * Same row: horizontal arrow.
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
         * Serpentine row transition.
         *
         * The first node on the next row occupies the same edge
         * as the last node from the previous row, so the route
         * can simply drop vertically before continuing.
         */
        drawDownArrow(
                graphics,
                fromCenterX,
                from.bottom() + 3,
                to.y() - 3,
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
        if (resource.kind()
                != CraftScopeResourceKind.ITEM) {

            return ItemStack.EMPTY;
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

        /*
         * Some production methods do not declare a machine.
         *
         * Vanilla Crafting is the obvious example. Give those
         * common processes a useful visual fallback.
         */
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

    /*
     * Render an item larger than the normal 16x16 GUI item.
     *
     * Minecraft's item texture remains the original item/block
     * texture; this only changes its GUI scale.
     */
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
}