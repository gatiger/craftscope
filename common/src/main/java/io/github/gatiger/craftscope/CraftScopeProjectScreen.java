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

public class CraftScopeProjectScreen extends Screen
        implements CraftScopeTargetItemReceiver {

    private static final int TARGET_SLOT_SIZE = 24;

    private static final int TREE_START_Y = 175;
    private static final int TREE_ROW_HEIGHT = 20;
    private static final int TREE_INDENT = 18;

    private final Screen parent;
    private final CraftScopeProject project;

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

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
        ItemStack targetStack =
                getTargetStack();

        if (targetStack.isEmpty()) {
            return;
        }

        CraftScopeRecipeTree tree =
                CraftScopeRecipeResolver.resolveTree(
                        targetStack,
                        project.getTargetCount()
                );

        if (tree == null
                || tree.getRoot() == null) {

            return;
        }

        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                width / 2,
                TREE_START_Y,
                0xFFFFFF
        );

        int treeLeft =
                Math.max(
                        20,
                        width / 2 - 120
                );

        int firstRowY =
                TREE_START_Y + 18;

        renderRecipeNode(
                graphics,
                tree.getRoot(),
                0,
                treeLeft,
                firstRowY,
                mouseX,
                mouseY
        );
    }

    private int renderRecipeNode(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            int depth,
            int treeLeft,
            int rowY,
            int mouseX,
            int mouseY
    ) {
        /*
         * Leave room at the bottom for the Back button.
         *
         * Scrolling will be added after we confirm the
         * recursive tree itself is resolving correctly.
         */
        if (rowY > height - 65) {
            return rowY;
        }

        ItemStack stack =
                node.getStack();

        int indent =
                depth * TREE_INDENT;

        int iconX =
                treeLeft + indent;

        /*
         * Draw a simple tree branch indicator.
         *
         * This is intentionally basic for the first tree
         * implementation. Later we'll replace this with
         * proper expandable/collapsible tree controls.
         */
        if (depth > 0) {

            graphics.drawString(
                    font,
                    "└",
                    iconX - 10,
                    rowY + 4,
                    0x777777
            );
        }

        graphics.renderItem(
                stack,
                iconX,
                rowY
        );

        String text =
                stack.getHoverName().getString()
                        + " x"
                        + node.getRequiredCount();

        int textColor =
                node.isCraftable()
                        ? 0xFFFFFF
                        : 0xCCCCCC;

        graphics.drawString(
                font,
                text,
                iconX + 20,
                rowY + 4,
                textColor
        );

        /*
         * Tooltip for every item in the tree.
         */
        if (mouseX >= iconX
                && mouseX < iconX + 16
                && mouseY >= rowY
                && mouseY < rowY + 16) {

            graphics.renderTooltip(
                    font,
                    stack,
                    mouseX,
                    mouseY
            );
        }

        int nextY =
                rowY + TREE_ROW_HEIGHT;

        for (CraftScopeRecipeNode child :
                node.getChildren()) {

            nextY =
                    renderRecipeNode(
                            graphics,
                            child,
                            depth + 1,
                            treeLeft,
                            nextY,
                            mouseX,
                            mouseY
                    );

            if (nextY > height - 65) {
                break;
            }
        }

        return nextY;
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
}