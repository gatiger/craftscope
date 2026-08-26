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
 * Converts Minecraft recipe types into generalized CraftScope
 * production routes.
 *
 * These are RAW routes.
 *
 * The normalization layer will later combine equivalent routes
 * such as:
 *
 * Iron Ore smelting
 * Deepslate Iron Ore smelting
 * Iron Ore blasting
 * Deepslate Iron Ore blasting
 *
 * into:
 *
 * Any Iron Ore
 *      ↓
 * Iron Ingot
 *
 * Methods:
 *   Smelting
 *   Blasting
 */
public final class CraftScopeVanillaProductionRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID =
            "craftscope:vanilla";

    private static final ResourceLocation CRAFTING_PROCESS_ID =
            requireId(
                    "craftscope:crafting"
            );

    private static final ResourceLocation SMELTING_PROCESS_ID =
            requireId(
                    "craftscope:smelting"
            );

    private static final ResourceLocation BLASTING_PROCESS_ID =
            requireId(
                    "craftscope:blasting"
            );

    private static final ResourceLocation FURNACE_ID =
            requireId(
                    "minecraft:furnace"
            );

    private static final ResourceLocation BLAST_FURNACE_ID =
            requireId(
                    "minecraft:blast_furnace"
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
        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        RecipeManager recipeManager =
                context.recipeManager();

        RegistryAccess registryAccess =
                context.registryAccess();

        collectRoutes(
                target,
                recipeManager,
                registryAccess,
                RecipeType.CRAFTING,
                VanillaProcessType.CRAFTING,
                routes
        );

        collectRoutes(
                target,
                recipeManager,
                registryAccess,
                RecipeType.SMELTING,
                VanillaProcessType.SMELTING,
                routes
        );

        collectRoutes(
                target,
                recipeManager,
                registryAccess,
                RecipeType.BLASTING,
                VanillaProcessType.BLASTING,
                routes
        );

        return routes;
    }

    private static <I extends RecipeInput, T extends Recipe<I>>
    void collectRoutes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            RecipeType<T> recipeType,
            VanillaProcessType processType,
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

            if (output.isEmpty()
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
                            "minecraft",
                            processType.processId,
                            Component.literal(
                                    processType.displayName
                            ),
                            List.of(
                                    holder.id()
                            ),
                            buildRequirements(
                                    processType
                            )
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            buildStepId(
                                    processType,
                                    holder.id()
                            ),
                            Component.literal(
                                    processType.displayName
                            ),
                            inputs,
                            List.of(
                                    routeOutput
                            ),
                            List.of(
                                    method
                            )
                    );

            CraftScopeProductionRoute route =
                    new CraftScopeProductionRoute(
                            buildRouteId(
                                    processType,
                                    holder.id()
                            ),
                            "minecraft",
                            Component.literal(
                                    "Minecraft"
                            ),
                            Component.literal(
                                    processType.displayName
                            ),
                            routeOutput,
                            List.of(
                                    step
                            ),
                            scoreRoute(
                                    processType,
                                    inputs
                            )
                    );

            routes.add(
                    route
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

            if (ingredient.isEmpty()) {
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

    /*
     * Infrastructure is separate from consumable materials.
     *
     * Fuel is deliberately NOT added.
     *
     * Furnace fuel is an operating requirement rather than an
     * explicit ingredient in the smelting recipe.
     */
    private static List<CraftScopeProcessRequirement>
    buildRequirements(
            VanillaProcessType processType
    ) {
        return switch (processType) {

            case CRAFTING ->
                    List.of();

            case SMELTING ->
                    List.of(
                            new CraftScopeProcessRequirement(
                                    CraftScopeRequirementKind.MACHINE,
                                    FURNACE_ID,
                                    Component.literal(
                                            "Furnace"
                                    ),
                                    1,
                                    ""
                            )
                    );

            case BLASTING ->
                    List.of(
                            new CraftScopeProcessRequirement(
                                    CraftScopeRequirementKind.MACHINE,
                                    BLAST_FURNACE_ID,
                                    Component.literal(
                                            "Blast Furnace"
                                    ),
                                    1,
                                    ""
                            )
                    );
        };
    }

    private static int scoreRoute(
            VanillaProcessType processType,
            List<CraftScopeResourceAmount> inputs
    ) {
        int score =
                switch (processType) {

                    case SMELTING ->
                            3000;

                    case BLASTING ->
                            2900;

                    case CRAFTING ->
                            1000;
                };

        for (CraftScopeResourceAmount input :
                inputs) {

            for (ResourceLocation variantId :
                    input.acceptedVariantIds()) {

                String path =
                        variantId.getPath();

                if (path.endsWith(
                        "_ore"
                )) {

                    score +=
                            50;
                }

                if (path.startsWith(
                        "raw_"
                )) {

                    score +=
                            30;
                }

                if (path.startsWith(
                        "deepslate_"
                )) {

                    score -=
                            5;
                }

                if (path.endsWith(
                        "_nugget"
                )) {

                    score -=
                            75;
                }
            }
        }

        return score;
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

                builder.append(
                        "|"
                );
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

    private static String buildStepId(
            VanillaProcessType processType,
            ResourceLocation recipeId
    ) {
        return "minecraft:"
                + processType.pathName
                + ":"
                + recipeId;
    }

    private static ResourceLocation buildRouteId(
            VanillaProcessType processType,
            ResourceLocation recipeId
    ) {
        return requireId(
                "craftscope:vanilla/"
                        + processType.pathName
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

    private enum VanillaProcessType {

        CRAFTING(
                "crafting",
                "Crafting",
                CRAFTING_PROCESS_ID
        ),

        SMELTING(
                "smelting",
                "Smelting",
                SMELTING_PROCESS_ID
        ),

        BLASTING(
                "blasting",
                "Blasting",
                BLASTING_PROCESS_ID
        );

        private final String pathName;
        private final String displayName;
        private final ResourceLocation processId;

        VanillaProcessType(
                String pathName,
                String displayName,
                ResourceLocation processId
        ) {
            this.pathName =
                    pathName;

            this.displayName =
                    displayName;

            this.processId =
                    processId;
        }
    }
}