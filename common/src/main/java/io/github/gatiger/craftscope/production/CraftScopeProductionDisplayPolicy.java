package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/*
 * Central rules for how production routes should be presented.
 *
 * Planning and presentation are deliberately separate concepts.
 *
 * Example:
 *
 *     Project target:
 *         Glowstone Dust x1
 *
 *     Planner:
 *         Expected Witch kills: 4
 *
 *     Process Diagram:
 *         One Witch kill
 *
 * This lets the player understand what ONE execution of a process
 * produces without losing expected-attempt calculations elsewhere.
 */
public final class CraftScopeProductionDisplayPolicy {

    private static final String CRAFTSCOPE_NAMESPACE =
            "craftscope";

    private static final String MOB_DROP_PREFIX =
            "mob_drop/";

    private CraftScopeProductionDisplayPolicy() {
    }

    /*
     * Direct mob-drop routes describe one mob kill.
     *
     * Only one-step routes qualify here. A larger production chain
     * that happens to contain a mob-drop step is still a project
     * planning chain and must remain quantity-scaled.
     */
    public static boolean isSingleExecutionRoute(
            CraftScopeProductionRoute route
    ) {
        return getMobEntityTypeId(
                route
        ) != null;
    }

    /*
     * Formats the planner's execution count using terminology that
     * matches the actual process.
     *
     * Mob drop:
     *
     *     1 Witch kill
     *     4 Witch kills
     *
     * Other production:
     *
     *     1 run
     *     4 runs
     */
    public static String formatExecutionCount(
            CraftScopeProductionRoute route,
            long count
    ) {
        long safeCount =
                Math.max(
                        0L,
                        count
                );

        ResourceLocation entityTypeId =
                getMobEntityTypeId(
                        route
                );

        if (entityTypeId == null) {

            return safeCount
                    + (
                    safeCount == 1L
                            ? " run"
                            : " runs"
            );
        }

        EntityType<?> entityType =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(
                                entityTypeId
                        )
                        .orElse(
                                null
                        );

        String mobName;

        if (entityType != null) {

            mobName =
                    entityType
                            .getDescription()
                            .getString();

        } else {

            mobName =
                    formatPathName(
                            entityTypeId.getPath()
                    );
        }

        return safeCount
                + " "
                + mobName
                + (
                safeCount == 1L
                        ? " kill"
                        : " kills"
        );
    }

    /*
     * Mob process IDs are generated as:
     *
     *     craftscope:mob_drop/<mod namespace>/<entity path>
     *
     * Recovering the EntityType from that ID keeps UI wording generic
     * for vanilla and modded mobs without hard-coded mob names.
     */
    private static ResourceLocation getMobEntityTypeId(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return null;
        }

        CraftScopeProductionStep step =
                route
                        .steps()
                        .getFirst();

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null) {
            return null;
        }

        ResourceLocation processId =
                method.processId();

        if (processId == null
                || !CRAFTSCOPE_NAMESPACE.equals(
                processId.getNamespace()
        )
                || !processId
                .getPath()
                .startsWith(
                        MOB_DROP_PREFIX
                )) {

            return null;
        }

        String value =
                processId
                        .getPath()
                        .substring(
                                MOB_DROP_PREFIX.length()
                        );

        int separator =
                value.indexOf(
                        '/'
                );

        if (separator <= 0
                || separator >= value.length() - 1) {

            return null;
        }

        String namespace =
                value.substring(
                        0,
                        separator
                );

        String path =
                value.substring(
                        separator + 1
                );

        return ResourceLocation.tryParse(
                namespace
                        + ":"
                        + path
        );
    }

    private static String formatPathName(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "Mob";
        }

        String[] parts =
                value.split(
                        "[_\\-/]"
                );

        StringBuilder result =
                new StringBuilder();

        for (String part :
                parts) {

            if (part == null
                    || part.isBlank()) {

                continue;
            }

            if (!result.isEmpty()) {

                result.append(
                        ' '
                );
            }

            result.append(
                    Character.toUpperCase(
                            part.charAt(
                                    0
                            )
                    )
            );

            if (part.length() > 1) {

                result.append(
                        part.substring(
                                1
                        )
                );
            }
        }

        return result.isEmpty()
                ? "Mob"
                : result.toString();
    }
}