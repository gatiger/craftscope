package io.github.gatiger.craftscope.production;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/*
 * One material transformation inside a production route.
 *
 * Example:
 *
 * Any Iron Ore
 *      ↓
 * Iron Ingot
 *
 * The transformation itself is the step.
 *
 * HOW that transformation can be performed is represented by
 * CraftScopeProductionMethod.
 *
 * That lets one step support:
 *
 *   Smelting
 *   Blasting
 *
 * without treating them as different material routes.
 */
public record CraftScopeProductionStep(
        String id,
        Component displayName,
        List<CraftScopeResourceAmount> inputs,
        List<CraftScopeResourceAmount> outputs,
        List<CraftScopeProductionMethod> methods
) {

    public CraftScopeProductionStep {
        Objects.requireNonNull(
                id,
                "id"
        );

        Objects.requireNonNull(
                displayName,
                "displayName"
        );

        inputs =
                inputs == null
                        ? List.of()
                        : List.copyOf(
                                inputs
                        );

        outputs =
                outputs == null
                        ? List.of()
                        : List.copyOf(
                                outputs
                        );

        methods =
                methods == null
                        ? List.of()
                        : List.copyOf(
                                methods
                        );

        if (methods.isEmpty()) {

            throw new IllegalArgumentException(
                    "Production step must contain at least one method"
            );
        }
    }

    public CraftScopeProductionMethod getPrimaryMethod() {

        return methods.getFirst();
    }

    public boolean hasAlternativeMethods() {

        return methods.size() > 1;
    }

    public boolean hasRecipe() {

        for (CraftScopeProductionMethod method :
                methods) {

            if (method.hasRecipes()) {
                return true;
            }
        }

        return false;
    }

    public boolean hasMachineRequirement() {

        for (CraftScopeProductionMethod method :
                methods) {

            if (method.hasMachineRequirement()) {
                return true;
            }
        }

        return false;
    }
}