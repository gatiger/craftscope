package io.github.gatiger.craftscope.client;

public final class CraftScopeClientConfig {

    public enum PlacementMode {
        AUTO,
        CUSTOM
    }

    private static PlacementMode placementMode = PlacementMode.AUTO;

    private static int customXOffset = 172;
    private static int customYOffset = 62;

    private CraftScopeClientConfig() {
    }

    public static PlacementMode getPlacementMode() {
        return placementMode;
    }

    public static void setPlacementMode(PlacementMode mode) {
        placementMode = mode;
    }

    public static int getCustomXOffset() {
        return customXOffset;
    }

    public static void setCustomXOffset(int value) {
        customXOffset = value;
    }

    public static int getCustomYOffset() {
        return customYOffset;
    }

    public static void setCustomYOffset(int value) {
        customYOffset = value;
    }

    private static boolean moveTabMode = false;

    public static boolean isMoveTabMode() {
        return moveTabMode;
    }

    public static void setMoveTabMode(boolean value) {
        moveTabMode = value;
    }
}
