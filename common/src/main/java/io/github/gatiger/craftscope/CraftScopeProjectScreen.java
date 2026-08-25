package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
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
                Integer.toString(project.getTargetCount())
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
                                button -> craftscope$changeQuantity(-1)
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
                                button -> craftscope$changeQuantity(1)
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
                                button -> minecraft.setScreen(parent)
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

    private void craftscope$changeQuantity(int amount) {
        int current =
                craftscope$getQuantityFromField();

        int updated =
                Math.max(1, current + amount);

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
        if (value == null || value.isEmpty()) {
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