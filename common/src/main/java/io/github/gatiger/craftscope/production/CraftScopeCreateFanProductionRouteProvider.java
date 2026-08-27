package io.github.gatiger.craftscope.production;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Adds Create fan-processing methods that are derived from
 * Minecraft's loaded cooking recipes rather than separate Create
 * recipe JSON.
 *
 * Create Bulk Blasting uses compatible vanilla smelting/blasting
 * recipes. That means the material transformation already exists
 * in Minecraft's RecipeManager; CraftScope only needs to expose a
 * second way to perform that same transformation.
 *
 * Example:
 *
 * Nether Quartz Ore -> Quartz
 *
 * Material route:
 *   Nether Quartz Ore -> Quartz
 *
 * Methods:
 *   Smelting
 *   Blasting
 *   Bulk Blasting
 *
 * The normalizer merges this provider's route with the equivalent
 * vanilla route, so Recipe Tree stays focused on WHAT material
 * route is selected while Process Diagram controls HOW it is run.
 *
 * This provider intentionally has no compile-time Create
 * dependency. It becomes active only when Create's Encased Fan is
 * present in the runtime item registry.
 */
public final class CraftScopeCreateFanProductionRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:create_fan";

    private static final String SOURCE_MOD_ID =
            "create";

    private static final Component SOURCE_MOD_NAME =
            Component.literal("Create");

    private static final ResourceLocation ENCASED_FAN_ID =
            requireId(
                    "create:encased_fan"
            );

    private static final ResourceLocation LAVA_ID =
            requireId(
                    "minecraft:lava"
            );

    private static final ResourceLocation BULK_BLASTING_PROCESS_ID =
            requireId(
                    "create:bulk_blasting"
            );

    @Override
    public String getProviderId() {

        return PROVIDER_ID;
    }

    @Override
    public List<CraftScopeProductionRoute> findRoutes(
            ItemStack target,
            CraftScopeProductionContext context
    ) {
        if (target == null
                || target.isEmpty()
                || context == null
                || !BuiltInRegistries.ITEM.containsKey(
                        ENCASED_FAN_ID
                )) {

            return List.of();
        }

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        collectBulkBlastingRoutes(
                target,
                context.recipeManager(),
                context.registryAccess(),
                RecipeType.SMELTING,
                "smelting",
                routes
        );

        collectBulkBlastingRoutes(
                target,
                context.recipeManager(),
                context.registryAccess(),
                RecipeType.BLASTING,
                "blasting",
                routes
        );

        return List.copyOf(routes);
    }

    private static <I extends RecipeInput, T extends Recipe<I>>
    void collectBulkBlastingRoutes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            RecipeType<T> recipeType,
            String sourceType,
            List<CraftScopeProductionRoute> routes
    ) {
        for (RecipeHolder<T> holder :
                recipeManager.getAllRecipesFor(
                        recipeType
                )) {

            T recipe =
                    holder.value();

            ItemStack output =
                    recipe.getResultItem(
                            registryAccess
                    );

            if (output == null
                    || output.isEmpty()
                    || !ItemStack.isSameItem(
                            output,
                            target
                    )) {

                continue;
            }

            List<CraftScopeResourceAmount> inputs =
                    buildInputs(
                            recipe
                    );

            if (inputs.isEmpty()) {
                continue;
            }

            CraftScopeResourceAmount routeOutput =
                    CraftScopeResourceAmount.item(
                            output,
                            Math.max(
                                    1,
                                    output.getCount()
                            ),
                            false
                    );

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            BULK_BLASTING_PROCESS_ID,
                            Component.literal(
                                    "Bulk Blasting"
                            ),
                            List.of(
                                    holder.id()
                            ),
                            buildRequirements()
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            "create:bulk_blasting:"
                                    + sourceType
                                    + ":"
                                    + holder.id(),
                            Component.literal(
                                    "Bulk Blasting"
                            ),
                            inputs,
                            List.of(
                                    routeOutput
                            ),
                            List.of(
                                    method
                            )
                    );

            routes.add(
                    new CraftScopeProductionRoute(
                            buildRouteId(
                                    sourceType,
                                    holder.id()
                            ),
                            SOURCE_MOD_ID,
                            SOURCE_MOD_NAME,
                            Component.literal(
                                    "Bulk Blasting"
                            ),
                            routeOutput,
                            List.of(
                                    step
                            ),
                            850
                    )
            );
        }
    }

    private static List<CraftScopeResourceAmount> buildInputs(
            Recipe<?> recipe
    ) {
        Map<String, IngredientAccumulator> grouped =
                new LinkedHashMap<>();

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient == null
                    || ingredient.isEmpty()) {

                continue;
            }

            List<ItemStack> variants =
                    normalizeVariants(
                            ingredient.getItems()
                    );

            if (variants.isEmpty()) {
                continue;
            }

            String groupKey =
                    buildVariantKey(
                            variants
                    );

            IngredientAccumulator existing =
                    grouped.get(
                            groupKey
                    );

            if (existing == null) {

                grouped.put(
                        groupKey,
                        new IngredientAccumulator(
                                variants,
                                1
                        )
                );

            } else {

                grouped.put(
                        groupKey,
                        new IngredientAccumulator(
                                existing.variants(),
                                existing.count() + 1
                        )
                );
            }
        }

        List<CraftScopeResourceAmount> result =
                new ArrayList<>();

        for (IngredientAccumulator accumulator :
                grouped.values()) {

            result.add(
                    CraftScopeResourceAmount.itemVariants(
                            accumulator.variants(),
                            accumulator.count()
                    )
            );
        }

        return result;
    }

    private static List<CraftScopeProcessRequirement>
    buildRequirements() {
        return List.of(
                new CraftScopeProcessRequirement(
                        CraftScopeRequirementKind.MACHINE,
                        ENCASED_FAN_ID,
                        Component.literal(
                                "Encased Fan"
                        ),
                        1,
                        ""
                ),
                new CraftScopeProcessRequirement(
                        CraftScopeRequirementKind.ENVIRONMENT,
                        LAVA_ID,
                        Component.literal(
                                "Lava Airflow"
                        ),
                        1,
                        ""
                )
        );
    }

    private static List<ItemStack> normalizeVariants(
            ItemStack[] possibilities
    ) {
        Map<String, ItemStack> unique =
                new LinkedHashMap<>();

        for (ItemStack stack :
                possibilities) {

            if (stack == null
                    || stack.isEmpty()) {

                continue;
            }

            ResourceLocation id =
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    );

            unique.putIfAbsent(
                    id.toString(),
                    stack.copy()
            );
        }

        List<ItemStack> result =
                new ArrayList<>(
                        unique.values()
                );

        result.sort(
                Comparator.comparing(
                        stack ->
                                BuiltInRegistries.ITEM
                                        .getKey(
                                                stack.getItem()
                                        )
                                        .toString()
                )
        );

        return result;
    }

    private static String buildVariantKey(
            List<ItemStack> variants
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (ItemStack stack :
                variants) {

            if (!builder.isEmpty()) {

                builder.append("|");
            }

            builder.append(
                    BuiltInRegistries.ITEM
                            .getKey(
                                    stack.getItem()
                            )
            );
        }

        return builder.toString();
    }

    private static ResourceLocation buildRouteId(
            String sourceType,
            ResourceLocation recipeId
    ) {
        return requireId(
                "craftscope:create/bulk_blasting/"
                        + sourceType
                        + "/"
                        + recipeId.getNamespace()
                        + "/"
                        + recipeId.getPath()
        );
    }

    private static ResourceLocation requireId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(
                        value
                );

        if (id == null) {

            throw new IllegalArgumentException(
                    "Invalid resource location: "
                            + value
            );
        }

        return id;
    }

    private record IngredientAccumulator(
            List<ItemStack> variants,
            int count
    ) {

        private IngredientAccumulator {
            variants =
                    List.copyOf(
                            variants
                    );
        }
    }
}
