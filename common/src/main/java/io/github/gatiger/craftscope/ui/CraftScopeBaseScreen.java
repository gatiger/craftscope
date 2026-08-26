package io.github.gatiger.craftscope.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/*
 * Base screen shared by every CraftScope screen.
 *
 * Minecraft 1.21 normally applies its menu blur from
 * Screen#renderBackground().
 *
 * CraftScope deliberately disables that behavior and renders its
 * own consistent dark application-style background instead.
 */
public abstract class CraftScopeBaseScreen
        extends Screen {

    protected static final int STANDARD_MARGIN =
            12;

    protected static final int STANDARD_HEADER_HEIGHT =
            34;

    protected CraftScopeBaseScreen(
            Component title
    ) {
        super(
                title
        );
    }

    /*
     * IMPORTANT:
     *
     * Screen#render() calls this automatically.
     *
     * Leaving it empty prevents Minecraft's built-in blur and
     * menu-background texture from being rendered over CraftScope.
     */
    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // CraftScope renders its own background.
    }

    protected int craftscope$getStandardWindowWidth() {
        return Math.min(
                520,
                Math.max(
                        220,
                        width - 32
                )
        );
    }

    protected int craftscope$getStandardWindowHeight() {
        return Math.min(
                340,
                Math.max(
                        180,
                        height - 32
                )
        );
    }

    protected int craftscope$getStandardWindowLeft() {
        return Math.max(
                8,
                (
                        width
                                - craftscope$getStandardWindowWidth()
                ) / 2
        );
    }

    protected int craftscope$getStandardWindowRight() {
        return craftscope$getStandardWindowLeft()
                + craftscope$getStandardWindowWidth();
    }

    protected int craftscope$getStandardWindowTop() {
        return Math.max(
                8,
                (
                        height
                                - craftscope$getStandardWindowHeight()
                ) / 2
        );
    }

    protected int craftscope$getStandardWindowBottom() {
        return craftscope$getStandardWindowTop()
                + craftscope$getStandardWindowHeight();
    }

    protected int craftscope$getStandardCenterX() {
        return (
                craftscope$getStandardWindowLeft()
                        + craftscope$getStandardWindowRight()
        ) / 2;
    }

    protected void craftscope$renderStandardShell(
            GuiGraphics graphics,
            String heading,
            String subtitle
    ) {
        int left =
                craftscope$getStandardWindowLeft();

        int right =
                craftscope$getStandardWindowRight();

        int top =
                craftscope$getStandardWindowTop();

        int bottom =
                craftscope$getStandardWindowBottom();

        CraftScopeUiTheme.drawBackdrop(
                graphics,
                width,
                height
        );

        CraftScopeUiTheme.drawWindow(
                graphics,
                left,
                top,
                right,
                bottom
        );

        CraftScopeUiTheme.drawHeader(
                graphics,
                left,
                top,
                right,
                top + STANDARD_HEADER_HEIGHT
        );

        CraftScopeUiTheme.drawPlaceholderLogo(
                graphics,
                left + 8,
                top + 8,
                18
        );

        graphics.drawString(
                font,
                "CraftScope",
                left + 32,
                top + 11,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (heading != null
                && !heading.isEmpty()) {

            graphics.drawString(
                    font,
                    heading,
                    left + 110,
                    top + 11,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        if (subtitle != null
                && !subtitle.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    subtitle,
                    craftscope$getStandardCenterX(),
                    top + 50,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    protected void craftscope$drawContentPanel(
            GuiGraphics graphics,
            int top,
            int bottom
    ) {
        CraftScopeUiTheme.drawPanel(
                graphics,
                craftscope$getStandardWindowLeft() + 12,
                top,
                craftscope$getStandardWindowRight() - 12,
                bottom
        );
    }

    protected void craftscope$drawFieldBackground(
            GuiGraphics graphics,
            int x,
            int y,
            int fieldWidth,
            int fieldHeight
    ) {
        graphics.fill(
                x,
                y,
                x + fieldWidth,
                y + fieldHeight,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                x,
                y,
                x + fieldWidth,
                y + fieldHeight,
                CraftScopeUiTheme.BORDER
        );
    }
}