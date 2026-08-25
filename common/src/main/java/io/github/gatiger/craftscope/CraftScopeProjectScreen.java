package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeResolver;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CraftScopeProjectScreen extends Screen
        implements CraftScopeTargetItemReceiver {

    private static final int TARGET_SLOT_SIZE = 24;

    private static final int TREE_ROW_HEIGHT = 20;
    private static final int TREE_INDENT = 18;

    private static final int TREE_SIDE_MARGIN = 20;
    private static final int TREE_TOP = 175;
    private static final int TREE_BOTTOM_MARGIN = 65;

    private static final int SCROLL_AMOUNT = 20;

    private static final long VARIANT_CYCLE_MS = 1000L;

    private final Screen parent;
    private final CraftScopeProject project;

    /*
     * Expanded node paths.
     *
     * Empty by default, so every project starts with
     * a collapsed tree.
     */
    private final Set<String> expandedNodes =
            new HashSet<>();

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

    private double treeScroll;

    private CraftScopeRecipeTree currentTree;

    public CraftScopeProjectScreen(
            Screen parent,
            CraftScopeProject project
    ) {
        super(Component.literal(project.getName()));

        this.parent = parent;
        this.project = project;
    }

    @Override
    protected void init() {
        super.init();

        targetSlotX =
                width / 2 - TARGET_SLOT_SIZE / 2;

        targetSlotY = 75;

        int centerX = width / 2;

        quantityField = new EditBox(
                font,
                centerX - 25,
                118,
                50,
                20,
                Component.literal("Quantity")
        );

        quantityField.setValue(
                Integer.toString(
                        project.getTargetCount()
                )
        );

        quantityField.setFilter(
                value -> value.isEmpty()
                        || value.matches("\\d+")
        );

        quantityField.setResponder(
                this::craftscope$quantityChanged
        );

        addRenderableWidget(quantityField);

        addRenderableWidget(
                Button.builder(
                                Component.literal("-"),
                                button ->
                                        craftscope$changeQuantity(-1)
                        )
                        .bounds(
                                centerX - 50,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("+"),
                                button ->
                                        craftscope$changeQuantity(1)
                        )
                        .bounds(
                                centerX + 30,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Back"),
                                button ->
                                        minecraft.setScreen(parent)
                        )
                        .bounds(
                                centerX - 50,
                                height - 40,
                                100,
                                20
                        )
                        .build()
        );

        treeScroll = 0;

        rebuildTree();
    }

    private void rebuildTree() {
        ItemStack target =
                getTargetStack();

        if (target.isEmpty()) {
            currentTree = null;
            return;
        }

        currentTree =
                CraftScopeRecipeResolver.resolveTree(
                        target,
                        project.getTargetCount()
                );

        clampTreeScroll();
    }

    private void craftscope$changeQuantity(
            int amount
    ) {
        int current =
                craftscope$getQuantityFromField();

        int updated =
                Math.max(
                        1,
                        current + amount
                );

        quantityField.setValue(
                Integer.toString(updated)
        );
    }

    private int craftscope$getQuantityFromField() {
        try {
            int value =
                    Integer.parseInt(
                            quantityField.getValue()
                    );

            return Math.max(1, value);

        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void craftscope$quantityChanged(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {

            return;
        }

        try {
            int quantity =
                    Integer.parseInt(value);

            if (quantity < 1) {
                return;
            }

            project.setTargetCount(quantity);
            CraftScopeProjectManager.save();

            rebuildTree();

        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                width / 2,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Target Item",
                width / 2,
                55,
                0xCCCCCC
        );

        renderTargetSlot(
                graphics,
                mouseX,
                mouseY
        );

        if (project.getTargetItemId().isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "Drop an item here from JEI/EMI",
                    width / 2,
                    105,
                    0x888888
            );
        }

        graphics.drawCenteredString(
                font,
                "Quantity",
                width / 2,
                105,
                0xCCCCCC
        );

        renderRecipeTree(
                graphics,
                mouseX,
                mouseY
        );
    }

    private void renderTargetSlot(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left = targetSlotX;
        int top = targetSlotY;

        int right =
                left + TARGET_SLOT_SIZE;

        int bottom =
                top + TARGET_SLOT_SIZE;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                0xFF202020
        );

        graphics.fill(
                left,
                top,
                right,
                top + 1,
                0xFFAAAAAA
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                0xFF555555
        );

        graphics.fill(
                left,
                top,
                left + 1,
                bottom,
                0xFFAAAAAA
        );

        graphics.fill(
                right - 1,
                top,
                right,
                bottom,
                0xFF555555
        );

        ItemStack targetStack =
                getTargetStack();

        if (!targetStack.isEmpty()) {

            graphics.renderItem(
                    targetStack,
                    left + 4,
                    top + 4
            );

            if (mouseX >= left
                    && mouseX < right
                    && mouseY >= top
                    && mouseY < bottom) {

                graphics.renderTooltip(
                        font,
                        targetStack,
                        mouseX,
                        mouseY
                );
            }

            graphics.drawCenteredString(
                    font,
                    targetStack.getHoverName(),
                    width / 2,
                    145,
                    0xFFFFFF
            );
        }
    }

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return;
        }

        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        int viewportHeight =
                viewportBottom - viewportTop;

        int treeLeft =
                Math.max(
                        TREE_SIDE_MARGIN,
                        width / 2 - 140
                );

        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                width / 2,
                TREE_TOP,
                0xFFFFFF
        );

        /*
         * Clip everything inside the tree viewport.
         *
         * This is what lets us scroll without drawing over
         * the quantity controls or Back button.
         */
        graphics.enableScissor(
                TREE_SIDE_MARGIN,
                viewportTop,
                width - TREE_SIDE_MARGIN,
                viewportBottom
        );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        renderRecipeNode(
                graphics,
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY,
                viewportTop,
                viewportBottom
        );

        graphics.disableScissor();

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight
        );
    }

    private int renderRecipeNode(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom
    ) {
        boolean hasChildren =
                !node.getChildren().isEmpty();

        boolean expanded =
                expandedNodes.contains(nodePath);

        int indent =
                depth * TREE_INDENT;

        int arrowX =
                treeLeft + indent;

        int iconX =
                arrowX + 12;

        /*
         * Only bother drawing if this row intersects the
         * visible viewport.
         */
        if (rowY + TREE_ROW_HEIGHT >= viewportTop
                && rowY <= viewportBottom) {

            if (hasChildren) {

                graphics.drawString(
                        font,
                        expanded ? "▼" : "▶",
                        arrowX,
                        rowY + 4,
                        0xAAAAAA
                );
            }

            ItemStack displayStack =
                    getDisplayStack(node);

            graphics.renderItem(
                    displayStack,
                    iconX,
                    rowY
            );

            String text =
                    getNodeDisplayName(node)
                            + " x"
                            + node.getRequiredCount();

            graphics.drawString(
                    font,
                    text,
                    iconX + 20,
                    rowY + 4,
                    node.isCraftable()
                            ? 0xFFFFFF
                            : 0xCCCCCC
            );

            /*
             * Tooltip uses whichever variant icon is
             * currently being displayed.
             */
            if (mouseX >= iconX
                    && mouseX < iconX + 16
                    && mouseY >= rowY
                    && mouseY < rowY + 16
                    && mouseY >= viewportTop
                    && mouseY < viewportBottom) {

                graphics.renderTooltip(
                        font,
                        displayStack,
                        mouseX,
                        mouseY
                );
            }
        }

        int nextY =
                rowY + TREE_ROW_HEIGHT;

        if (expanded) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        nodePath
                                + "/"
                                + i
                                + ":"
                                + getItemId(
                                        child.getStack()
                                );

                nextY =
                        renderRecipeNode(
                                graphics,
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY,
                                viewportTop,
                                viewportBottom
                        );
            }
        }

        return nextY;
    }

    private ItemStack getDisplayStack(
            CraftScopeRecipeNode node
    ) {
        List<ItemStack> variants =
                node.getAcceptedVariants();

        if (variants.isEmpty()) {
            return node.getStack();
        }

        if (variants.size() == 1) {
            return variants.getFirst();
        }

        long cycle =
                System.currentTimeMillis()
                        / VARIANT_CYCLE_MS;

        int index =
                (int) (
                        cycle
                                % variants.size()
                );

        return variants
                .get(index)
                .copy();
    }

    private String getNodeDisplayName(
            CraftScopeRecipeNode node
    ) {
        List<ItemStack> variants =
                node.getAcceptedVariants();

        if (variants.size() <= 1) {
            return node
                    .getStack()
                    .getHoverName()
                    .getString();
        }

        String genericName =
                findGenericVariantName(
                        variants
                );

        if (genericName != null) {
            return "Any " + genericName;
        }

        /*
         * We know multiple items are accepted, but we cannot
         * safely infer a human-readable category.
         */
        return "Any Valid Ingredient";
    }

    private String findGenericVariantName(
                List<ItemStack> variants
        ) {
        if (variants.isEmpty()) {
                return null;
        }

        /*
        * Prefer known Minecraft ingredient categories.
        *
        * This handles groups whose item names do not all share
        * the same final word.
        *
        * Example:
        *
        * Oak Log
        * Spruce Log
        * Crimson Stem
        * Warped Stem
        *
        * are all still part of the logs ingredient family.
        */
        boolean allLogs = true;
        boolean allPlanks = true;
        boolean allWool = true;

        for (ItemStack variant : variants) {

                if (!variant.is(ItemTags.LOGS)) {
                allLogs = false;
                }

                if (!variant.is(ItemTags.PLANKS)) {
                allPlanks = false;
                }

                if (!variant.is(ItemTags.WOOL)) {
                allWool = false;
                }
        }

        if (allLogs) {
                return "Log";
        }

        if (allPlanks) {
                return "Planks";
        }

        if (allWool) {
                return "Wool";
        }

        /*
        * Generic fallback for categories whose item names share
        * a common final word.
        *
        * Examples:
        *
        * Oak Slab
        * Spruce Slab
        * Birch Slab
        *
        * -> Any Slab
        */
        String commonSuffix = null;

        for (ItemStack variant : variants) {

                String name =
                        variant.getHoverName()
                                .getString();

                String suffix =
                        extractLastWord(name);

                if (suffix.isEmpty()) {
                return null;
                }

                if (commonSuffix == null) {

                commonSuffix = suffix;

                } else if (!commonSuffix.equalsIgnoreCase(
                        suffix
                )) {

                return null;
                }
        }

        return commonSuffix;
        }

    private String extractLastWord(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String trimmed =
                value.trim();

        if (trimmed.isEmpty()) {
            return "";
        }

        int space =
                trimmed.lastIndexOf(' ');

        if (space < 0) {
            return trimmed;
        }

        return trimmed.substring(
                space + 1
        );
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            int viewportTop,
            int viewportBottom,
            int viewportHeight
    ) {
        int contentHeight =
                getVisibleNodeCount()
                        * TREE_ROW_HEIGHT;

        if (contentHeight <= viewportHeight) {
            return;
        }

        int barX =
                width - TREE_SIDE_MARGIN - 4;

        graphics.fill(
                barX,
                viewportTop,
                barX + 3,
                viewportBottom,
                0xFF303030
        );

        int thumbHeight =
                Math.max(
                        12,
                        viewportHeight
                                * viewportHeight
                                / contentHeight
                );

        int maxScroll =
                Math.max(
                        1,
                        contentHeight - viewportHeight
                );

        int travel =
                viewportHeight - thumbHeight;

        int thumbOffset =
                (int) (
                        (treeScroll / maxScroll)
                                * travel
                );

        graphics.fill(
                barX,
                viewportTop + thumbOffset,
                barX + 3,
                viewportTop
                        + thumbOffset
                        + thumbHeight,
                0xFFAAAAAA
        );
    }

    private int getVisibleNodeCount() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return 0;
        }

        return countVisibleNodes(
                currentTree.getRoot(),
                "root"
        );
    }

    private int countVisibleNodes(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        int count = 1;

        if (!expandedNodes.contains(nodePath)) {
            return count;
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0;
             i < children.size();
             i++) {

            CraftScopeRecipeNode child =
                    children.get(i);

            String childPath =
                    nodePath
                            + "/"
                            + i
                            + ":"
                            + getItemId(
                                    child.getStack()
                            );

            count +=
                    countVisibleNodes(
                            child,
                            childPath
                    );
        }

        return count;
    }

    private void clampTreeScroll() {
        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        int viewportHeight =
                viewportBottom - viewportTop;

        int contentHeight =
                getVisibleNodeCount()
                        * TREE_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight - viewportHeight
                );

        treeScroll =
                Math.max(
                        0,
                        Math.min(
                                treeScroll,
                                maxScroll
                        )
                );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0
                && handleTreeClick(
                        mouseX,
                        mouseY
                )) {

            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private boolean handleTreeClick(
            double mouseX,
            double mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return false;
        }

        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        if (mouseY < viewportTop
                || mouseY >= viewportBottom) {

            return false;
        }

        int treeLeft =
                Math.max(
                        TREE_SIDE_MARGIN,
                        width / 2 - 140
                );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        return handleNodeClick(
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY
        ).handled();
    }

    private ClickResult handleNodeClick(
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            double mouseX,
            double mouseY
    ) {
        int indent =
                depth * TREE_INDENT;

        int arrowX =
                treeLeft + indent;

        boolean hasChildren =
                !node.getChildren().isEmpty();

        /*
         * Make the entire beginning of the row clickable,
         * rather than requiring pixel-perfect clicks on
         * the tiny arrow.
         */
        if (hasChildren
                && mouseY >= rowY
                && mouseY < rowY + TREE_ROW_HEIGHT
                && mouseX >= arrowX - 2
                && mouseX < arrowX + 28) {

            if (expandedNodes.contains(nodePath)) {

                expandedNodes.remove(nodePath);

            } else {

                expandedNodes.add(nodePath);
            }

            clampTreeScroll();

            return new ClickResult(
                    true,
                    rowY + TREE_ROW_HEIGHT
            );
        }

        int nextY =
                rowY + TREE_ROW_HEIGHT;

        if (expandedNodes.contains(nodePath)) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        nodePath
                                + "/"
                                + i
                                + ":"
                                + getItemId(
                                        child.getStack()
                                );

                ClickResult result =
                        handleNodeClick(
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY
                        );

                if (result.handled()) {
                    return result;
                }

                nextY =
                        result.nextY();
            }
        }

        return new ClickResult(
                false,
                nextY
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        if (mouseY >= viewportTop
                && mouseY < viewportBottom) {

            treeScroll -=
                    scrollY
                            * SCROLL_AMOUNT;

            clampTreeScroll();

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    private ItemStack getTargetStack() {
        String itemId =
                project.getTargetItemId();

        if (itemId == null
                || itemId.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ResourceLocation location =
                ResourceLocation.tryParse(itemId);

        if (location == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(location);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private String getItemId(
            ItemStack stack
    ) {
        return BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString();
    }

    @Override
    public void craftscope$setTargetItem(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        project.setTargetItemId(
                itemId.toString()
        );

        CraftScopeProjectManager.save();

        expandedNodes.clear();
        treeScroll = 0;

        rebuildTree();
    }

    @Override
    public int craftscope$getTargetSlotX() {
        return targetSlotX;
    }

    @Override
    public int craftscope$getTargetSlotY() {
        return targetSlotY;
    }

    @Override
    public int craftscope$getTargetSlotWidth() {
        return TARGET_SLOT_SIZE;
    }

    @Override
    public int craftscope$getTargetSlotHeight() {
        return TARGET_SLOT_SIZE;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record ClickResult(
            boolean handled,
            int nextY
    ) {
    }
}