package io.github.gatiger.craftscope.ui;

import net.minecraft.client.gui.GuiGraphics;

/*
 * Shared visual language for CraftScope.
 *
 * The goal is a dark, opaque application-style interface rather
 * than Minecraft widgets floating directly over the world.
 */
public final class CraftScopeUiTheme {

    /*
     * The world remains faintly visible outside the application
     * window, while the actual CraftScope workspace is opaque.
     */
    public static final int BACKDROP =
            0xB8000000;

    public static final int WINDOW_BACKGROUND =
            0xFF090C0F;

    public static final int HEADER_BACKGROUND =
            0xFF0D1115;

    public static final int TAB_BAR_BACKGROUND =
            0xFF0B0F12;

    public static final int PANEL_BACKGROUND =
            0xFF101519;

    public static final int PANEL_BACKGROUND_ALT =
            0xFF141A1F;

    public static final int SECTION_HEADER_BACKGROUND =
            0xFF171D22;

    public static final int BORDER =
            0xFF2A3138;

    public static final int BORDER_HOVER =
            0xFF46525C;

    public static final int BORDER_SUBTLE =
            0xFF20262C;

    /*
     * Blue accent inspired by the selected Process Diagram tab
     * in the current CraftScope visual reference.
     */
    public static final int ACCENT =
            0xFF0875AA;

    public static final int ACCENT_HOVER =
            0xFF0B8BC8;

    public static final int ACCENT_BACKGROUND =
            0xFF0A3044;

    public static final int BUTTON_BACKGROUND =
            0xFF1A2025;

    public static final int BUTTON_HOVER =
            0xFF252C32;

    public static final int BUTTON_DISABLED =
            0xFF121619;

    public static final int DANGER =
            0xFFC45050;

    public static final int DANGER_BACKGROUND =
            0xFF3A1C1C;

    public static final int TEXT_PRIMARY =
            0xFFF0F0F0;

    public static final int TEXT_SECONDARY =
            0xFFB7BEC5;

    public static final int TEXT_MUTED =
            0xFF7F8992;

    public static final int TEXT_DISABLED =
            0xFF626A71;

    public static final int SUCCESS =
            0xFF58D84B;

    private CraftScopeUiTheme() {
    }

    public static void drawBackdrop(
            GuiGraphics graphics,
            int width,
            int height
    ) {
        graphics.fill(
                0,
                0,
                width,
                height,
                BACKDROP
        );
    }

    public static void drawWindow(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.fill(
                left,
                top,
                right,
                bottom,
                WINDOW_BACKGROUND
        );

        drawBorder(
                graphics,
                left,
                top,
                right,
                bottom,
                BORDER
        );
    }

    public static void drawHeader(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.fill(
                left,
                top,
                right,
                bottom,
                HEADER_BACKGROUND
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                BORDER
        );
    }

    public static void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        drawPanel(
                graphics,
                left,
                top,
                right,
                bottom,
                PANEL_BACKGROUND
        );
    }

    public static void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int background
    ) {
        if (right <= left
                || bottom <= top) {
            return;
        }

        graphics.fill(
                left,
                top,
                right,
                bottom,
                background
        );

        drawBorder(
                graphics,
                left,
                top,
                right,
                bottom,
                BORDER
        );
    }

    public static void drawSectionHeader(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (right <= left
                || bottom <= top) {
            return;
        }

        graphics.fill(
                left,
                top,
                right,
                bottom,
                SECTION_HEADER_BACKGROUND
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                BORDER_SUBTLE
        );
    }

    public static void drawBorder(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        if (right <= left
                || bottom <= top) {
            return;
        }

        graphics.fill(
                left,
                top,
                right,
                top + 1,
                color
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                color
        );

        graphics.fill(
                left,
                top,
                left + 1,
                bottom,
                color
        );

        graphics.fill(
                right - 1,
                top,
                right,
                bottom,
                color
        );
    }

    /*
     * Temporary CraftScope logo placeholder.
     *
     * This deliberately stays simple. Its purpose is only to
     * reserve the correct amount of header space until the real
     * CraftScope logo exists.
     */
    public static void drawPlaceholderLogo(
            GuiGraphics graphics,
            int x,
            int y,
            int size
    ) {
        int right =
                x + size;

        int bottom =
                y + size;

        drawBorder(
                graphics,
                x,
                y,
                right,
                bottom,
                BORDER_HOVER
        );

        int inset =
                Math.max(
                        3,
                        size / 4
                );

        drawBorder(
                graphics,
                x + inset,
                y + inset,
                right - inset,
                bottom - inset,
                ACCENT
        );

        int centerSize =
                Math.max(
                        2,
                        size / 6
                );

        int centerX =
                x + size / 2;

        int centerY =
                y + size / 2;

        graphics.fill(
                centerX - centerSize / 2,
                centerY - centerSize / 2,
                centerX - centerSize / 2 + centerSize,
                centerY - centerSize / 2 + centerSize,
                ACCENT
        );
    }
}