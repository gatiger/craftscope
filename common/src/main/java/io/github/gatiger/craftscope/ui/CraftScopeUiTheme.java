package io.github.gatiger.craftscope.ui;

import net.minecraft.client.gui.GuiGraphics;

/*
 * Central visual theme for CraftScope.
 *
 * Keeping colors and panel drawing here means the entire
 * interface can be restyled later without rewriting every
 * individual screen.
 */
public final class CraftScopeUiTheme {

    /*
     * Darkens the Minecraft world behind CraftScope.
     *
     * The CraftScope window itself is intentionally opaque.
     */
    public static final int BACKDROP =
            0xB8000000;

    public static final int WINDOW_BACKGROUND =
            0xFF090C0F;

    public static final int HEADER_BACKGROUND =
            0xFF0E1216;

    public static final int PANEL_BACKGROUND =
            0xFF101519;

    public static final int PANEL_BACKGROUND_ALT =
            0xFF151B20;

    public static final int SECTION_HEADER_BACKGROUND =
            0xFF171D22;

    public static final int BORDER =
            0xFF2A3138;

    public static final int BORDER_HOVER =
            0xFF48535D;

    public static final int BORDER_SUBTLE =
            0xFF20262C;

    /*
     * Accent based on the blue-highlighted tab from the
     * CraftScope concept image.
     */
    public static final int ACCENT =
            0xFF0875AA;

    public static final int ACCENT_HOVER =
            0xFF0B8BC8;

    public static final int ACCENT_BACKGROUND =
            0xFF0A3044;

    public static final int BUTTON_BACKGROUND =
            0xFF1B2025;

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
     * Temporary logo mark.
     *
     * This reserves the correct header space without locking us
     * into a real logo design yet.
     *
     * When CraftScope gets an actual logo, this method can be
     * replaced by a texture render without changing the layout.
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

        int center =
                Math.max(
                        2,
                        size / 6
                );

        int centerX =
                x + size / 2;

        int centerY =
                y + size / 2;

        graphics.fill(
                centerX - center / 2,
                centerY - center / 2,
                centerX - center / 2 + center,
                centerY - center / 2 + center,
                ACCENT
        );
    }
}