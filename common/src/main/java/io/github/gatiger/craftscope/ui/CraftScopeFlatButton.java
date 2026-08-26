package io.github.gatiger.craftscope.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/*
 * Modern CraftScope-specific button.
 *
 * Unlike Minecraft's normal textured button, this uses the
 * flatter panel styling from the CraftScope UI concept.
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
                    CraftScopeUiTheme.ACCENT;

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

        int textY =
                getY()
                        + (
                        getHeight()
                                - 8
                ) / 2;

        graphics.drawCenteredString(
                Minecraft.getInstance().font,
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