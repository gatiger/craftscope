package io.github.gatiger.craftscope.ui;

/*
 * Small render-time context used by CraftScope's text marquee.
 *
 * Most overflowing text should remain still and use the normal
 * ellipsis behavior.
 *
 * A renderer can temporarily enable this context for the one piece
 * of text that is currently selected. The marquee mixin will then
 * animate only that text.
 */
public final class CraftScopeMarqueeContext {

    private static final ThreadLocal<Integer> DEPTH =
            ThreadLocal.withInitial(
                    () -> 0
            );

    private CraftScopeMarqueeContext() {
    }

    public static void begin() {
        DEPTH.set(
                DEPTH.get() + 1
        );
    }

    public static void end() {
        int depth =
                DEPTH.get() - 1;

        if (depth <= 0) {
            DEPTH.remove();
            return;
        }

        DEPTH.set(
                depth
        );
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}