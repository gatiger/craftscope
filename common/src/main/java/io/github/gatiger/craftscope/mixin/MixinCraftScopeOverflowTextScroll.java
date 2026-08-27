package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Global single-line text-overflow behavior for CraftScopeProjectScreen.
 *
 * Old behavior:
 *
 *     "Player or tamed-wolf k..."
 *
 * New behavior:
 *
 *     The visible text window automatically pans from the beginning
 *     to the end, pauses, then pans back. The returned string always
 *     fits inside the caller's maxWidth, so it remains contained by
 *     the existing panel/row.
 *
 * This applies everywhere CraftScopeProjectScreen uses fitText(),
 * including:
 *
 * - Production Routes
 * - Process detail titles / methods
 * - Machine and requirement rows
 * - Setup rows
 * - Summary rows that use fitText
 * - Future project-screen containers that use fitText
 *
 * Vertical overflow remains handled by the existing panel scrollbars.
 * This mixin is specifically for a single line that is wider than its
 * container.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeOverflowTextScroll {

    @Unique
    private static final long CRAFTSCOPE_TEXT_PAUSE_MS =
            1200L;

    @Unique
    private static final long CRAFTSCOPE_TEXT_MIN_SCROLL_MS =
            1400L;

    @Unique
    private static final long CRAFTSCOPE_TEXT_MAX_SCROLL_MS =
            8000L;

    @Unique
    private static final long CRAFTSCOPE_TEXT_MS_PER_PIXEL =
            28L;

    @Inject(
            method = "fitText",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$scrollOverflowText(
            String text,
            int maxWidth,
            CallbackInfoReturnable<String> cir
    ) {
        if (text == null
                || maxWidth <= 0) {

            cir.setReturnValue("");
            return;
        }

        Font font =
                Minecraft.getInstance().font;

        int fullWidth =
                font.width(text);

        if (fullWidth <= maxWidth) {
            /*
             * Let CraftScope's original fitText() return the normal
             * unmodified string.
             */
            return;
        }

        int overflowPixels =
                Math.max(
                        1,
                        fullWidth - maxWidth
                );

        long oneWayScrollMs =
                craftscope$clampLong(
                        (long) overflowPixels
                                * CRAFTSCOPE_TEXT_MS_PER_PIXEL,
                        CRAFTSCOPE_TEXT_MIN_SCROLL_MS,
                        CRAFTSCOPE_TEXT_MAX_SCROLL_MS
                );

        long cycleMs =
                CRAFTSCOPE_TEXT_PAUSE_MS
                        + oneWayScrollMs
                        + CRAFTSCOPE_TEXT_PAUSE_MS
                        + oneWayScrollMs;

        long now =
                System.currentTimeMillis();

        long cyclePosition =
                Math.floorMod(
                        now,
                        cycleMs
                );

        double progress;

        if (cyclePosition < CRAFTSCOPE_TEXT_PAUSE_MS) {

            progress = 0.0D;

        } else if (cyclePosition
                < CRAFTSCOPE_TEXT_PAUSE_MS
                + oneWayScrollMs) {

            progress =
                    (
                            double
                    ) (
                            cyclePosition
                                    - CRAFTSCOPE_TEXT_PAUSE_MS
                    ) / (
                            double
                    ) oneWayScrollMs;

        } else if (cyclePosition
                < CRAFTSCOPE_TEXT_PAUSE_MS
                + oneWayScrollMs
                + CRAFTSCOPE_TEXT_PAUSE_MS) {

            progress = 1.0D;

        } else {

            long reversePosition =
                    cyclePosition
                            - CRAFTSCOPE_TEXT_PAUSE_MS
                            - oneWayScrollMs
                            - CRAFTSCOPE_TEXT_PAUSE_MS;

            progress =
                    1.0D
                            - (
                            (
                                    double
                            ) reversePosition
                                    / (
                                    double
                            ) oneWayScrollMs
                    );
        }

        int desiredPixelOffset =
                (
                        int
                ) Math.round(
                        overflowPixels
                                * craftscope$smoothStep(
                                progress
                        )
                );

        cir.setReturnValue(
                craftscope$getVisibleWindow(
                        font,
                        text,
                        maxWidth,
                        desiredPixelOffset
                )
        );
    }

    @Unique
    private static String craftscope$getVisibleWindow(
            Font font,
            String text,
            int maxWidth,
            int desiredPixelOffset
    ) {
        if (text.isEmpty()) {
            return "";
        }

        int startIndex = 0;
        int removedWidth = 0;

        while (startIndex < text.length()
                && removedWidth < desiredPixelOffset) {

            int codePoint =
                    text.codePointAt(
                            startIndex
                    );

            String glyph =
                    new String(
                            Character.toChars(
                                    codePoint
                            )
                    );

            int glyphWidth =
                    font.width(
                            glyph
                    );

            if (removedWidth
                    + glyphWidth
                    > desiredPixelOffset) {

                break;
            }

            removedWidth +=
                    glyphWidth;

            startIndex +=
                    Character.charCount(
                            codePoint
                    );
        }

        /*
         * At the far end, force the final characters to become
         * visible even if variable-width glyphs prevented the pixel
         * offset from landing exactly on the ideal start position.
         */
        if (desiredPixelOffset > 0) {
            while (startIndex < text.length()
                    && font.width(
                    text.substring(
                            startIndex
                    )
            ) > maxWidth
                    && craftscope$remainingPixelOverflow(
                    font,
                    text,
                    startIndex,
                    maxWidth
            ) > (
                    font.width(text)
                            - maxWidth
                            - desiredPixelOffset
            )) {

                int codePoint =
                        text.codePointAt(
                                startIndex
                        );

                startIndex +=
                        Character.charCount(
                                codePoint
                        );
            }
        }

        int endIndex =
                startIndex;

        int visibleWidth =
                0;

        while (endIndex < text.length()) {

            int codePoint =
                    text.codePointAt(
                            endIndex
                    );

            String glyph =
                    new String(
                            Character.toChars(
                                    codePoint
                            )
                    );

            int glyphWidth =
                    font.width(
                            glyph
                    );

            if (visibleWidth
                    + glyphWidth
                    > maxWidth) {

                break;
            }

            visibleWidth +=
                    glyphWidth;

            endIndex +=
                    Character.charCount(
                            codePoint
                    );
        }

        if (endIndex <= startIndex) {
            int codePoint =
                    text.codePointAt(
                            startIndex
                    );

            endIndex =
                    Math.min(
                            text.length(),
                            startIndex
                                    + Character.charCount(
                                    codePoint
                            )
                    );
        }

        return text.substring(
                startIndex,
                endIndex
        );
    }

    @Unique
    private static int craftscope$remainingPixelOverflow(
            Font font,
            String text,
            int startIndex,
            int maxWidth
    ) {
        return Math.max(
                0,
                font.width(
                        text.substring(
                                startIndex
                        )
                ) - maxWidth
        );
    }

    @Unique
    private static double craftscope$smoothStep(
            double value
    ) {
        double clamped =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                value
                        )
                );

        return clamped
                * clamped
                * (
                3.0D
                        - 2.0D
                        * clamped
        );
    }

    @Unique
    private static long craftscope$clampLong(
            long value,
            long minimum,
            long maximum
    ) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }
}
