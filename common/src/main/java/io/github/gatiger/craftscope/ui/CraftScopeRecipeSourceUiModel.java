package io.github.gatiger.craftscope.ui;

/*
 * Stable helper types used by the Recipe Source screen mixin.
 *
 * These intentionally live OUTSIDE the mixin class.
 *
 * Sponge Mixin copies methods/fields from a mixin into the target
 * class and may uniquely rename mixin-owned nested classes. When a
 * nested helper class appears in another nested helper's method
 * descriptor, those generated names can disagree at runtime and
 * cause NoSuchMethodError.
 *
 * Keeping these types in a normal, non-mixin class gives their
 * binary names a stable identity on both Fabric and NeoForge.
 */
public final class CraftScopeRecipeSourceUiModel {

    private CraftScopeRecipeSourceUiModel() {
    }

    public record Entry(
            String modId,
            String displayName,
            int routeCount
    ) {
        public Entry {
            modId =
                    modId == null
                            ? ""
                            : modId;

            displayName =
                    displayName == null
                            || displayName.isBlank()
                            ? modId
                            : displayName;

            routeCount =
                    Math.max(
                            0,
                            routeCount
                    );
        }
    }

    public static final class Accumulator {

        private final String modId;

        private String displayName;

        private int routeCount;

        public Accumulator(
                String modId,
                String displayName
        ) {
            this.modId =
                    modId == null
                            ? ""
                            : modId;

            this.displayName =
                    displayName == null
                            ? ""
                            : displayName;
        }

        public void increment() {
            routeCount++;
        }

        public void setDisplayNameIfBetter(
                String candidate
        ) {
            if (candidate == null
                    || candidate.isBlank()) {

                return;
            }

            if (displayName.isBlank()
                    || displayName.equals(modId)) {

                displayName =
                        candidate;
            }
        }

        public Entry build() {
            return new Entry(
                    modId,
                    displayName,
                    routeCount
            );
        }
    }

    public record Layout(
            int controlLeft,
            int controlTop,
            int controlRight,
            int controlBottom,
            int dropdownLeft,
            int dropdownTop,
            int dropdownRight,
            int dropdownBottom
    ) {
    }
}
