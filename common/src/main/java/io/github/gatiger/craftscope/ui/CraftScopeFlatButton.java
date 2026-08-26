package io.github.gatiger.craftscope.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/*
 * CraftScope's application-style button.
 *
 * This replaces the normal textured Minecraft button where we
 * want the flatter, darker interface used by CraftScope.
 */
public final class CraftScopeFlatButton
        extends AbstractButton {

    private final Runnable action;

    private boolean selected;
    private boolean danger;

    public CraftScopeFlatButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Runnable action
    ) {
        super(
                x,
                y,
                width,
                height,
                message
        );

        this.action =
                Objects.requireNonNull(
                        action,
                        "action"
                );
    }

    public CraftScopeFlatButton setSelected(
            boolean selected
    ) {
        this.selected =
                selected;

        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public CraftScopeFlatButton setDanger(
            boolean danger
    ) {
        this.danger =
                danger;

        return this;
    }

    @Override
    public void onPress() {
        if (!active) {
            return;
        }

        action.run();
    }

    @Override
    protected void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        boolean highlighted =
                isHoveredOrFocused();

        int background;
        int border;
        int textColor;

        if (!active) {

            background =
                    CraftScopeUiTheme.BUTTON_DISABLED;

            border =
                    CraftScopeUiTheme.BORDER_SUBTLE;

            textColor =
                    CraftScopeUiTheme.TEXT_DISABLED;

        } else if (danger) {

            background =
                    highlighted
                            ? CraftScopeUiTheme.DANGER_BACKGROUND
                            : CraftScopeUiTheme.BUTTON_BACKGROUND;

            border =
                    highlighted
                            ? CraftScopeUiTheme.DANGER
                            : CraftScopeUiTheme.BORDER;

            textColor =
                    CraftScopeUiTheme.TEXT_PRIMARY;

        } else if (selected) {

            background =
                    CraftScopeUiTheme.ACCENT_BACKGROUND;

            border =
                    highlighted
                            ? CraftScopeUiTheme.ACCENT_HOVER
                            : CraftScopeUiTheme.ACCENT;

            textColor =
                    CraftScopeUiTheme.TEXT_PRIMARY;

        } else if (highlighted) {

            background =
                    CraftScopeUiTheme.BUTTON_HOVER;

            border =
                    CraftScopeUiTheme.BORDER_HOVER;

            textColor =
                    CraftScopeUiTheme.TEXT_PRIMARY;

        } else {

            background =
                    CraftScopeUiTheme.BUTTON_BACKGROUND;

            border =
                    CraftScopeUiTheme.BORDER;

            textColor =
                    CraftScopeUiTheme.TEXT_SECONDARY;
        }

        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                background
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                border
        );

        Font font =
                Minecraft.getInstance().font;

        /*
         * Minecraft's visible glyph body is effectively about
         * one pixel shorter than Font#lineHeight.
         *
         * The +1 gives the text visually equal space above and
         * below it instead of making it look slightly high.
         */
        int textY =
                getY()
                        + (
                        getHeight()
                                - font.lineHeight
                ) / 2
                        + 1;

        graphics.drawCenteredString(
                font,
                getMessage(),
                getX()
                        + getWidth() / 2,
                textY,
                textColor
        );
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput output
    ) {
        defaultButtonNarrationText(
                output
        );
    }
}