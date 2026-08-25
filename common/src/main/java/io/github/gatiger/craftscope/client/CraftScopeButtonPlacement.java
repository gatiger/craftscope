package io.github.gatiger.craftscope.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

public final class CraftScopeButtonPlacement {

    private CraftScopeButtonPlacement() {
    }

    public record Position(int x, int y) {
    }

    public static List<Position> getCandidatePositions(
            int left,
            int top,
            int imageWidth,
            int imageHeight
    ) {
        return List.of(
                // Preferred default
                new Position(left + imageWidth - 4, top + 62),

                // Right edge, lower
                new Position(left + imageWidth - 4, top + 88),

                // Right edge, higher
                new Position(left + imageWidth - 4, top + 36),

                // Left edge
                new Position(left - 12, top + 62),

                // Bottom edge
                new Position(
                        left + imageWidth - 28,
                        top + imageHeight - 4
                )
        );
    }

    public static Position findBestPosition(
            int left,
            int top,
            int imageWidth,
            int imageHeight,
            int buttonWidth,
            int buttonHeight,
            List<? extends GuiEventListener> existingWidgets
    ) {
        List<Position> candidates = getCandidatePositions(
                left,
                top,
                imageWidth,
                imageHeight
        );

        for (Position candidate : candidates) {
            if (!overlapsExistingWidget(
                    candidate,
                    buttonWidth,
                    buttonHeight,
                    existingWidgets
            )) {
                return candidate;
            }
        }

        // If every candidate is occupied, preserve our preferred position.
        return candidates.getFirst();
    }

    private static boolean overlapsExistingWidget(
            Position candidate,
            int buttonWidth,
            int buttonHeight,
            List<? extends GuiEventListener> existingWidgets
    ) {
        int leftA = candidate.x();
        int topA = candidate.y();
        int rightA = leftA + buttonWidth;
        int bottomA = topA + buttonHeight;

        for (GuiEventListener listener : existingWidgets) {
            if (!(listener instanceof AbstractWidget widget)) {
                continue;
            }

            int leftB = widget.getX();
            int topB = widget.getY();
            int rightB = leftB + widget.getWidth();
            int bottomB = topB + widget.getHeight();

            boolean overlaps =
                    leftA < rightB &&
                    rightA > leftB &&
                    topA < bottomB &&
                    bottomA > topB;

            if (overlaps) {
                return true;
            }
        }

        return false;
    }
}