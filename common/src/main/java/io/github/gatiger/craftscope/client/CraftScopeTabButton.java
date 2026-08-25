package io.github.gatiger.craftscope.client;

import io.github.gatiger.craftscope.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class CraftScopeTabButton extends Button {

    private static final int SNAP_DISTANCE = 10;
    private static final int RELEASE_DISTANCE = 18;

    // Keeps the same slight overhang style as the default tab.
    private static final int EDGE_OVERHANG = 4;

    private final AbstractContainerScreen<?> containerScreen;

    private int xOffset;
    private int yOffset;

    private boolean repositioning = false;

    private int grabOffsetX;
    private int grabOffsetY;

    private boolean snappedLeft = false;
    private boolean snappedRight = false;
    private boolean snappedTop = false;
    private boolean snappedBottom = false;

    public CraftScopeTabButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            OnPress onPress,
            AbstractContainerScreen<?> containerScreen
    ) {
        super(
                x,
                y,
                width,
                height,
                message,
                onPress,
                DEFAULT_NARRATION
        );

        this.containerScreen = containerScreen;

        AbstractContainerScreenAccessor container =
                (AbstractContainerScreenAccessor) containerScreen;

        this.xOffset = x - container.craftscope$getLeftPos();
        this.yOffset = y - container.craftscope$getTopPos();
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        boolean moveMode = CraftScopeClientConfig.isMoveTabMode();
        boolean leftMouseDown = craftscope$isLeftMouseDown();

        AbstractContainerScreenAccessor container =
                (AbstractContainerScreenAccessor) containerScreen;

        int containerLeft = container.craftscope$getLeftPos();
        int containerTop = container.craftscope$getTopPos();
        int containerRight =
                containerLeft + container.craftscope$getImageWidth();
        int containerBottom =
                containerTop + container.craftscope$getImageHeight();

        if (moveMode) {

            if (!repositioning
                    && leftMouseDown
                    && isMouseOver(mouseX, mouseY)) {

                repositioning = true;

                grabOffsetX = mouseX - getX();
                grabOffsetY = mouseY - getY();

                snappedLeft = false;
                snappedRight = false;
                snappedTop = false;
                snappedBottom = false;
            }

            if (repositioning && leftMouseDown) {

                int freeX = mouseX - grabOffsetX;
                int freeY = mouseY - grabOffsetY;

                int snappedX = craftscope$applyHorizontalSnap(
                        freeX,
                        containerLeft,
                        containerRight
                );

                int snappedY = craftscope$applyVerticalSnap(
                        freeY,
                        containerTop,
                        containerBottom
                );

                setX(snappedX);
                setY(snappedY);

                xOffset = getX() - containerLeft;
                yOffset = getY() - containerTop;

                CraftScopeClientConfig.setPlacementMode(
                        CraftScopeClientConfig.PlacementMode.CUSTOM
                );

                CraftScopeClientConfig.setCustomXOffset(xOffset);
                CraftScopeClientConfig.setCustomYOffset(yOffset);
            }

            if (repositioning && !leftMouseDown) {

                repositioning = false;

                snappedLeft = false;
                snappedRight = false;
                snappedTop = false;
                snappedBottom = false;

                CraftScopeClientConfig.setMoveTabMode(false);
                CraftScopeClientConfigManager.save();
            }

        } else {

            setX(containerLeft + xOffset);
            setY(containerTop + yOffset);
        }

        super.renderWidget(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (CraftScopeClientConfig.isMoveTabMode()
                && button == 0
                && isMouseOver(mouseX, mouseY)) {

            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int craftscope$applyHorizontalSnap(
            int freeX,
            int containerLeft,
            int containerRight
    ) {
        int leftSnapX =
                containerLeft - getWidth() + EDGE_OVERHANG;

        int rightSnapX =
                containerRight - EDGE_OVERHANG;

        if (snappedLeft) {
            if (Math.abs(freeX - leftSnapX) <= RELEASE_DISTANCE) {
                return leftSnapX;
            }

            snappedLeft = false;
        }

        if (snappedRight) {
            if (Math.abs(freeX - rightSnapX) <= RELEASE_DISTANCE) {
                return rightSnapX;
            }

            snappedRight = false;
        }

        if (Math.abs(freeX - leftSnapX) <= SNAP_DISTANCE) {
            snappedLeft = true;
            snappedRight = false;
            return leftSnapX;
        }

        if (Math.abs(freeX - rightSnapX) <= SNAP_DISTANCE) {
            snappedRight = true;
            snappedLeft = false;
            return rightSnapX;
        }

        return freeX;
    }

    private int craftscope$applyVerticalSnap(
            int freeY,
            int containerTop,
            int containerBottom
    ) {
        int topSnapY =
                containerTop - getHeight() + EDGE_OVERHANG;

        int bottomSnapY =
                containerBottom - EDGE_OVERHANG;

        if (snappedTop) {
            if (Math.abs(freeY - topSnapY) <= RELEASE_DISTANCE) {
                return topSnapY;
            }

            snappedTop = false;
        }

        if (snappedBottom) {
            if (Math.abs(freeY - bottomSnapY) <= RELEASE_DISTANCE) {
                return bottomSnapY;
            }

            snappedBottom = false;
        }

        if (Math.abs(freeY - topSnapY) <= SNAP_DISTANCE) {
            snappedTop = true;
            snappedBottom = false;
            return topSnapY;
        }

        if (Math.abs(freeY - bottomSnapY) <= SNAP_DISTANCE) {
            snappedBottom = true;
            snappedTop = false;
            return bottomSnapY;
        }

        return freeY;
    }

    private boolean craftscope$isLeftMouseDown() {
        long window = Minecraft.getInstance()
                .getWindow()
                .getWindow();

        return GLFW.glfwGetMouseButton(
                window,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;
    }
}