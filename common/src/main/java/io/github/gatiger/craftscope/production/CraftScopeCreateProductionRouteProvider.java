package io.github.gatiger.craftscope.production;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Optional Create production-route provider.
 *
 * This class intentionally has NO compile-time dependency on
 * Create. It discovers Create recipe types through Minecraft's
 * RecipeType registry and uses Create's public ProcessingRecipe
 * methods reflectively when the mod is present.
 *
 * If Create is not installed, this provider simply returns no
 * routes. That keeps CraftScope portable across both loaders.
 *
 * Create route priorities intentionally sit below vanilla's
 * normal material-production defaults. A recycling route such as
 * crushing Iron Horse Armor must never silently become the
 * default way to obtain Iron Ingots. Players can still select any
 * Create alternative explicitly in Recipe Tree.
 */
public final class CraftScopeCreateProductionRouteProvider
        implements CraftScopeProductionRouteProvider {

    private static final String PROVIDER_ID = "craftscope:create";
    private static final String SOURCE_MOD_ID = "create";

    private static final Component SOURCE_MOD_NAME =
            Component.literal("Create");

    private static final ResourceLocation SEQUENCED_ASSEMBLY_ID =
            requireId("create:sequenced_assembly");

    private static final ResourceLocation ITEM_APPLICATION_ID =
            requireId("create:item_application");

    private static final ResourceLocation SANDPAPER_POLISHING_ID =
            requireId("create:sandpaper_polishing");

    private static final List<ProcessDefinition> PROCESS_TYPES =
            List.of(
                    process(
                            "create:crushing",
                            "Crushing",
                            900,
                            machine(
                                    "create:crushing_wheel",
                                    "Crushing Wheels",
                                    2
                            )
                    ),
                    process(
                            "create:pressing",
                            "Pressing",
                            880,
                            machine(
                                    "create:mechanical_press",
                                    "Mechanical Press",
                                    1
                            )
                    ),
                    process(
                            "create:milling",
                            "Milling",
                            860,
                            machine(
                                    "create:millstone",
                                    "Millstone",
                                    1
                            )
                    ),
                    process(
                            "create:mixing",
                            "Mixing",
                            840,
                            machine(
                                    "create:mechanical_mixer",
                                    "Mechanical Mixer",
                                    1
                            ),
                            machine(
                                    "create:basin",
                                    "Basin",
                                    1
                            )
                    ),
                    process(
                            "create:compacting",
                            "Compacting",
                            830,
                            machine(
                                    "create:mechanical_press",
                                    "Mechanical Press",
                                    1
                            ),
                            machine(
                                    "create:basin",
                                    "Basin",
                                    1
                            )
                    ),
                    process(
                            "create:cutting",
                            "Cutting",
                            820,
                            machine(
                                    "create:mechanical_saw",
                                    "Mechanical Saw",
                                    1
                            )
                    ),
                    process(
                            "create:splashing",
                            "Washing",
                            810,
                            machine(
                                    "create:encased_fan",
                                    "Encased Fan",
                                    1
                            ),
                            requirement(
                                    CraftScopeRequirementKind.ENVIRONMENT,
                                    "minecraft:water",
                                    "Water Airflow",
                                    1,
                                    ""
                            )
                    ),
                    process(
                            "create:haunting",
                            "Haunting",
                            800,
                            machine(
                                    "create:encased_fan",
                                    "Encased Fan",
                                    1
                            ),
                            requirement(
                                    CraftScopeRequirementKind.ENVIRONMENT,
                                    "minecraft:soul_fire",
                                    "Soul Fire Airflow",
                                    1,
                                    ""
                            )
                    ),
                    process(
                            "create:item_application",
                            "Item Application",
                            795
                    ),
                    process(
                            "create:deploying",
                            "Deploying",
                            790,
                            machine(
                                    "create:deployer",
                                    "Deployer",
                                    1
                            )
                    ),
                    process(
                            "create:mechanical_crafting",
                            "Mechanical Crafting",
                            780,
                            machine(
                                    "create:mechanical_crafter",
                                    "Mechanical Crafter Array",
                                    1
                            )
                    ),
                    process(
                            "create:sandpaper_polishing",
                            "Sandpaper Polishing",
                            770,
                            requirement(
                                    CraftScopeRequirementKind.TOOL,
                                    "create:sand_paper",
                                    "Sand Paper (wears with use)",
                                    1,
                                    ""
                            )
                    )
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
        if (target == null || target.isEmpty() || context == null) {
            return List.of();
        }

        List<CraftScopeProductionRoute> routes =
                new ArrayList<>();

        collectSequencedAssemblyRoutes(
                target,
                context.recipeManager(),
                context.registryAccess(),
                routes
        );

        for (ProcessDefinition process : PROCESS_TYPES) {
            collectProcessRoutes(
                    target,
                    context.recipeManager(),
                    context.registryAccess(),
                    process,
                    routes
            );
        }

        return List.copyOf(routes);
    }

    /*
     * Create Sequenced Assembly is not a normal ProcessingRecipe.
     * Its outer recipe contains:
     *
     * - one starting ingredient,
     * - a list of processing recipes,
     * - a loop count,
     * - a transitional item,
     * - and a weighted result pool.
     *
     * CraftScope currently represents this as one summarized
     * production step. The consumed item inputs from every
     * sequence operation are multiplied by the loop count, while
     * the transitional item itself is excluded because it is an
     * internal work-in-progress item rather than an external
     * material requirement.
     *
     * Example: Precision Mechanism
     *
     * Gold Sheet x1
     * Cogwheel x5
     * Large Cogwheel x5
     * Iron Nugget x5
     *       ↓
     * Sequenced Assembly (5 loops)
     *       ↓
     * Precision Mechanism
     *
     * The output chance is preserved on the route. Recipe Tree,
     * Process Diagram, Setup, and multi-step expansion now share
     * CraftScopeChancePlanner for expected-value quantity planning.
     */
    private static void collectSequencedAssemblyRoutes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            List<CraftScopeProductionRoute> routes
    ) {
        if (!BuiltInRegistries.RECIPE_TYPE.containsKey(
                SEQUENCED_ASSEMBLY_ID
        )) {
            return;
        }

        RecipeType<?> recipeType =
                BuiltInRegistries.RECIPE_TYPE.get(
                        SEQUENCED_ASSEMBLY_ID
                );

        if (recipeType == null) {
            return;
        }

        for (RecipeHolder<?> holder :
                getRecipes(recipeManager, recipeType)) {

            Recipe<?> recipe = holder.value();

            ItemStack result =
                    recipe.getResultItem(
                            registryAccess
                    );

            if (result == null
                    || result.isEmpty()
                    || !ItemStack.isSameItem(
                            result,
                            target
                    )) {

                continue;
            }

            SequencedAssemblyData data =
                    readSequencedAssembly(
                            recipe
                    );

            if (data == null
                    || data.inputs().isEmpty()) {

                continue;
            }

            ResourceLocation targetId =
                    BuiltInRegistries.ITEM.getKey(
                            result.getItem()
                    );

            double chance =
                    data.outputChance();

            CraftScopeResourceAmount routeOutput =
                    new CraftScopeResourceAmount(
                            CraftScopeResourceKind.ITEM,
                            targetId,
                            result.getHoverName().copy(),
                            Math.max(1, result.getCount()),
                            "",
                            false,
                            chance,
                            List.of(targetId)
                    );

            String displayName =
                    buildSequencedDisplayName(
                            data.loops(),
                            chance
                    );

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            SEQUENCED_ASSEMBLY_ID,
                            Component.literal(displayName),
                            List.of(holder.id()),
                            data.requirements()
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            "create:sequenced_assembly:"
                                    + holder.id(),
                            Component.literal(displayName),
                            data.inputs(),
                            List.of(routeOutput),
                            List.of(method)
                    );

            routes.add(
                    new CraftScopeProductionRoute(
                            requireId(
                                    "craftscope:create/sequenced_assembly/"
                                            + holder.id().getNamespace()
                                            + "/"
                                            + holder.id().getPath()
                                            + "/"
                                            + targetId.getNamespace()
                                            + "/"
                                            + targetId.getPath()
                            ),
                            SOURCE_MOD_ID,
                            SOURCE_MOD_NAME,
                            Component.literal(displayName),
                            routeOutput,
                            List.of(step),
                            910
                    )
            );

            CraftScopeProductionRoute visualRoute =
                    buildSequencedVisualRoute(
                            holder.id(),
                            targetId,
                            routeOutput,
                            data
                    );

            if (visualRoute != null) {
                routes.add(visualRoute);
            }
        }
    }

    private static SequencedAssemblyData readSequencedAssembly(
            Recipe<?> recipe
    ) {
        try {
            Method getIngredient =
                    recipe.getClass().getMethod(
                            "getIngredient"
                    );

            Method getSequence =
                    recipe.getClass().getMethod(
                            "getSequence"
                    );

            Method getLoops =
                    recipe.getClass().getMethod(
                            "getLoops"
                    );

            Method getTransitionalItem =
                    recipe.getClass().getMethod(
                            "getTransitionalItem"
                    );

            Object ingredientValue =
                    getIngredient.invoke(recipe);

            Object sequenceValue =
                    getSequence.invoke(recipe);

            Object loopsValue =
                    getLoops.invoke(recipe);

            Object transitionalValue =
                    getTransitionalItem.invoke(recipe);

            if (!(ingredientValue instanceof Ingredient ingredient)
                    || !(sequenceValue instanceof Iterable<?> sequence)
                    || !(loopsValue instanceof Number loopsNumber)
                    || !(transitionalValue instanceof ItemStack transitional)) {

                return null;
            }

            int loops =
                    Math.max(
                            1,
                            loopsNumber.intValue()
                    );

            Map<String, IngredientAccumulator> grouped =
                    new LinkedHashMap<>();

            addIngredient(
                    grouped,
                    ingredient,
                    1
            );

            Map<String, IngredientAccumulator> startingGrouped =
                    new LinkedHashMap<>();

            addIngredient(
                    startingGrouped,
                    ingredient,
                    1
            );

            List<CraftScopeResourceAmount> startingInputs =
                    toInputResources(startingGrouped);

            CraftScopeResourceAmount startingInput =
                    startingInputs.isEmpty()
                            ? null
                            : startingInputs.getFirst();

            List<SequencedStepData> sequenceSteps =
                    new ArrayList<>();

            Map<String, CraftScopeProcessRequirement> requirements =
                    new LinkedHashMap<>();

            for (Object sequencedRecipe : sequence) {
                if (sequencedRecipe == null) {
                    continue;
                }

                Object nestedValue =
                        invokeNoArg(
                                sequencedRecipe,
                                "getRecipe"
                        );

                if (!(nestedValue instanceof Recipe<?> nestedRecipe)) {
                    continue;
                }

                ResourceLocation nestedTypeId =
                        BuiltInRegistries.RECIPE_TYPE.getKey(
                                nestedRecipe.getType()
                        );

                ProcessDefinition definition =
                        findProcessDefinition(
                                nestedTypeId
                        );

                List<CraftScopeProcessRequirement> stepRequirements =
                        definition == null
                                ? List.of()
                                : definition.requirements();

                if (definition != null) {
                    for (CraftScopeProcessRequirement requirement :
                            stepRequirements) {

                        mergeRequirement(
                                requirements,
                                requirement
                        );
                    }
                }

                Map<String, IngredientAccumulator> stepGrouped =
                        new LinkedHashMap<>();

                List<Ingredient> nestedIngredients =
                        nestedRecipe.getIngredients();

                for (int ingredientIndex = 0;
                     ingredientIndex < nestedIngredients.size();
                     ingredientIndex++) {

                    Ingredient nestedIngredient =
                            nestedIngredients.get(ingredientIndex);

                    /*
                     * In Create Sequenced Assembly the first item
                     * ingredient is the in-progress/transitional
                     * workpiece. On the first operation Create may
                     * expose it as a compound ingredient containing
                     * both the transitional item and the starting
                     * ingredient. The starting ingredient was already
                     * added once above, so never count this first slot
                     * as a per-loop external material.
                     */
                    if (ingredientIndex == 0) {
                        continue;
                    }

                    if (nestedIngredient == null
                            || nestedIngredient.isEmpty()
                            || isTransitionalIngredient(
                                    nestedIngredient,
                                    transitional
                            )) {

                        continue;
                    }

                    addIngredient(
                            grouped,
                            nestedIngredient,
                            loops
                    );

                    addIngredient(
                            stepGrouped,
                            nestedIngredient,
                            loops
                    );
                }

                String stepDisplayName =
                        definition == null
                                ? formatProcessName(nestedTypeId)
                                : definition.displayName();

                sequenceSteps.add(
                        new SequencedStepData(
                                nestedTypeId,
                                stepDisplayName,
                                toInputResources(stepGrouped),
                                stepRequirements
                        )
                );
            }

            List<CraftScopeResourceAmount> inputs =
                    toInputResources(
                            grouped
                    );

            return new SequencedAssemblyData(
                    loops,
                    inputs,
                    List.copyOf(
                            requirements.values()
                    ),
                    readOutputChance(recipe),
                    startingInput,
                    transitional.copy(),
                    sequenceSteps
            );

        } catch (NoSuchMethodException ignored) {
            return null;

        } catch (ReflectiveOperationException
                 | RuntimeException ignored) {

            return null;
        }
    }

    private static CraftScopeProductionRoute buildSequencedVisualRoute(
            ResourceLocation recipeId,
            ResourceLocation targetId,
            CraftScopeResourceAmount routeOutput,
            SequencedAssemblyData data
    ) {
        if (data == null
                || data.startingInput() == null
                || data.transitionalItem() == null
                || data.transitionalItem().isEmpty()
                || data.sequenceSteps().isEmpty()) {

            return null;
        }

        List<CraftScopeProductionStep> steps =
                new ArrayList<>();

        for (int index = 0;
             index < data.sequenceSteps().size();
             index++) {

            SequencedStepData sequenceStep =
                    data.sequenceSteps().get(index);

            List<CraftScopeResourceAmount> stepInputs =
                    new ArrayList<>();

            if (index == 0) {
                stepInputs.add(data.startingInput());
            } else {
                stepInputs.add(
                        CraftScopeResourceAmount.item(
                                data.transitionalItem(),
                                1,
                                true
                        )
                );
            }

            stepInputs.addAll(
                    sequenceStep.externalInputs()
            );

            boolean last =
                    index == data.sequenceSteps().size() - 1;

            List<CraftScopeResourceAmount> stepOutputs =
                    last
                            ? List.of(routeOutput)
                            : List.of(
                            CraftScopeResourceAmount.item(
                                    data.transitionalItem(),
                                    1,
                                    false
                            )
                    );

            String loopSuffix =
                    data.loops() <= 1
                            ? ""
                            : " - repeat x" + data.loops();

            String stepName =
                    sequenceStep.displayName()
                            + loopSuffix;

            CraftScopeProductionMethod method =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            sequenceStep.processId(),
                            Component.literal(stepName),
                            List.of(recipeId),
                            sequenceStep.requirements()
                    );

            steps.add(
                    new CraftScopeProductionStep(
                            "create:sequenced_flow:"
                                    + recipeId
                                    + ":"
                                    + index,
                            Component.literal(stepName),
                            stepInputs,
                            stepOutputs,
                            List.of(method)
                    )
            );
        }

        String displayName =
                buildSequencedDisplayName(
                        data.loops(),
                        data.outputChance()
                );

        return new CraftScopeProductionRoute(
                requireId(
                        "craftscope:create/sequenced_assembly_flow/"
                                + recipeId.getNamespace()
                                + "/"
                                + recipeId.getPath()
                                + "/"
                                + targetId.getNamespace()
                                + "/"
                                + targetId.getPath()
                ),
                SOURCE_MOD_ID,
                SOURCE_MOD_NAME,
                Component.literal(displayName),
                routeOutput,
                List.copyOf(steps),
                920
        );
    }

    private static String formatProcessName(
            ResourceLocation processId
    ) {
        if (processId == null) {
            return "Processing";
        }

        String[] words =
                processId
                        .getPath()
                        .split("_");

        StringBuilder builder =
                new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(
                    Character.toUpperCase(word.charAt(0))
            );

            if (word.length() > 1) {
                builder.append(
                        word.substring(1)
                );
            }
        }

        return builder.isEmpty()
                ? "Processing"
                : builder.toString();
    }

    private static Object invokeNoArg(
            Object target,
            String methodName
    ) throws ReflectiveOperationException {
        Method method;

        try {
            method =
                    target
                            .getClass()
                            .getMethod(
                                    methodName
                            );
        } catch (NoSuchMethodException e) {
            method =
                    target
                            .getClass()
                            .getDeclaredMethod(
                                    methodName
                            );

            method.setAccessible(true);
        }

        return method.invoke(
                target
        );
    }

    private static double readOutputChance(
            Recipe<?> recipe
    ) {
        try {
            Method getOutputChance =
                    recipe
                            .getClass()
                            .getMethod(
                                    "getOutputChance"
                            );

            Object value =
                    getOutputChance.invoke(
                            recipe
                    );

            if (value instanceof Number number) {
                return clampChance(
                        number.doubleValue()
                );
            }
        } catch (ReflectiveOperationException
                 | RuntimeException ignored) {
        }

        return 1.0;
    }

    private static double clampChance(
            double chance
    ) {
        if (Double.isNaN(chance)
                || Double.isInfinite(chance)) {

            return 1.0;
        }

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        chance
                )
        );
    }

    private static String buildChanceAwareProcessName(
            String processName,
            double chance
    ) {
        String base =
                processName == null
                        || processName.isBlank()
                        ? "Processing"
                        : processName;

        double normalizedChance =
                clampChance(
                        chance
                );

        if (normalizedChance >= 0.999999D) {
            return base;
        }

        double percent =
                normalizedChance
                        * 100.0D;

        String percentText;

        if (Math.abs(
                percent - Math.rint(percent)
        ) < 0.000001D) {

            percentText =
                    Long.toString(
                            Math.round(percent)
                    );

        } else {

            percentText =
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            percent
                    )
                            .replaceAll(
                                    "0+$",
                                    ""
                            )
                            .replaceAll(
                                    "\\.$",
                                    ""
                            );
        }

        return base
                + " ("
                + percentText
                + "% target chance)";
    }

    private static String buildSequencedDisplayName(
            int loops,
            double chance
    ) {
        String base =
                "Sequenced Assembly ("
                        + loops
                        + (loops == 1 ? " loop" : " loops")
                        + ")";

        if (chance >= 0.999999) {
            return base;
        }

        int percent =
                (int) Math.round(
                        chance * 100.0
                );

        return base
                + " - "
                + percent
                + "% output chance";
    }

    private static void addIngredient(
            Map<String, IngredientAccumulator> grouped,
            Ingredient ingredient,
            int multiplier
    ) {
        if (ingredient == null
                || ingredient.isEmpty()
                || multiplier <= 0) {

            return;
        }

        List<ItemStack> variants =
                normalizeVariants(
                        ingredient.getItems()
                );

        if (variants.isEmpty()) {
            return;
        }

        String key =
                buildVariantKey(
                        variants
                );

        IngredientAccumulator existing =
                grouped.get(
                        key
                );

        if (existing == null) {
            grouped.put(
                    key,
                    new IngredientAccumulator(
                            variants,
                            multiplier
                    )
            );
        } else {
            grouped.put(
                    key,
                    new IngredientAccumulator(
                            existing.variants(),
                            safeAddInt(
                                    existing.count(),
                                    multiplier
                            )
                    )
            );
        }
    }

    private static List<CraftScopeResourceAmount> toInputResources(
            Map<String, IngredientAccumulator> grouped
    ) {
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

        return List.copyOf(
                result
        );
    }

    private static boolean isTransitionalIngredient(
            Ingredient ingredient,
            ItemStack transitional
    ) {
        if (transitional == null
                || transitional.isEmpty()) {

            return false;
        }

        ItemStack[] possibilities =
                ingredient.getItems();

        if (possibilities == null
                || possibilities.length == 0) {

            return false;
        }

        boolean sawUsable = false;

        for (ItemStack stack : possibilities) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            sawUsable = true;

            if (!ItemStack.isSameItem(
                    stack,
                    transitional
            )) {
                return false;
            }
        }

        return sawUsable;
    }

    private static ProcessDefinition findProcessDefinition(
            ResourceLocation recipeTypeId
    ) {
        if (recipeTypeId == null) {
            return null;
        }

        for (ProcessDefinition definition : PROCESS_TYPES) {
            if (definition.recipeTypeId().equals(
                    recipeTypeId
            )) {
                return definition;
            }
        }

        return null;
    }

    private static void mergeRequirement(
            Map<String, CraftScopeProcessRequirement> requirements,
            CraftScopeProcessRequirement requirement
    ) {
        if (requirement == null) {
            return;
        }

        String key =
                requirement.kind()
                        + "|"
                        + (
                        requirement.id() == null
                                ? ""
                                : requirement.id().toString()
                )
                        + "|"
                        + requirement.displayName().getString()
                        + "|"
                        + requirement.unit();

        CraftScopeProcessRequirement existing =
                requirements.get(
                        key
                );

        if (existing == null
                || requirement.amount()
                > existing.amount()) {

            requirements.put(
                    key,
                    requirement
            );
        }
    }

    private static void collectProcessRoutes(
            ItemStack target,
            RecipeManager recipeManager,
            RegistryAccess registryAccess,
            ProcessDefinition process,
            List<CraftScopeProductionRoute> routes
    ) {
        if (!BuiltInRegistries.RECIPE_TYPE.containsKey(
                process.recipeTypeId()
        )) {
            return;
        }

        RecipeType<?> recipeType =
                BuiltInRegistries.RECIPE_TYPE.get(
                        process.recipeTypeId()
                );

        if (recipeType == null) {
            return;
        }

        for (RecipeHolder<?> holder :
                getRecipes(recipeManager, recipeType)) {

            Recipe<?> recipe = holder.value();

            /*
             * Fluid support is intentionally deferred. We skip any
             * Create recipe that declares fluid inputs or outputs
             * so required-resource totals remain correct.
             */
            if (hasFluidData(recipe)) {
                continue;
            }

            List<CraftScopeResourceAmount> inputs =
                    buildInputs(recipe);

            if (inputs.isEmpty()) {
                continue;
            }

            /*
             * Preserve BOTH guaranteed and probabilistic item
             * outputs. CraftScopeChancePlanner handles expected-value
             * quantity planning for the selected target.
             */
            List<ProcessingOutput> outputs =
                    getProcessingOutputs(
                            recipe,
                            registryAccess
                    );

            if (outputs.isEmpty()) {
                continue;
            }

            ProcessingOutput targetOutput =
                    findTargetOutput(
                            outputs,
                            target
                    );

            if (targetOutput == null) {
                continue;
            }

            List<CraftScopeResourceAmount> stepOutputs =
                    new ArrayList<>();

            for (ProcessingOutput output : outputs) {
                stepOutputs.add(
                        toResource(
                                output
                        )
                );
            }

            CraftScopeResourceAmount routeOutput =
                    toResource(
                            targetOutput
                    );

            String routeDisplayName =
                    buildChanceAwareProcessName(
                            process.displayName(),
                            targetOutput.chance()
                    );

            List<CraftScopeProductionMethod> methods =
                    buildMethods(
                            process,
                            holder.id()
                    );

            CraftScopeProductionStep step =
                    new CraftScopeProductionStep(
                            buildStepId(
                                    process,
                                    holder.id()
                            ),
                            Component.literal(
                                    routeDisplayName
                            ),
                            inputs,
                            stepOutputs,
                            methods
                    );

            routes.add(
                    new CraftScopeProductionRoute(
                            buildRouteId(
                                    process,
                                    holder.id(),
                                    targetOutput.id()
                            ),
                            SOURCE_MOD_ID,
                            SOURCE_MOD_NAME,
                            Component.literal(
                                    routeDisplayName
                            ),
                            routeOutput,
                            List.of(
                                    step
                            ),
                            process.priority()
                    )
            );
        }
    }

    private static List<CraftScopeProductionMethod> buildMethods(
            ProcessDefinition process,
            ResourceLocation recipeId
    ) {
        if (process.recipeTypeId().equals(
                SANDPAPER_POLISHING_ID
        )) {
            CraftScopeProcessRequirement sandPaper =
                    requirement(
                            CraftScopeRequirementKind.TOOL,
                            "create:sand_paper",
                            "Sand Paper (wears with use)",
                            1,
                            ""
                    );

            CraftScopeProductionMethod manual =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            process.recipeTypeId(),
                            Component.literal(
                                    "Manual Sandpaper"
                            ),
                            List.of(recipeId),
                            List.of(sandPaper)
                    );

            CraftScopeProductionMethod deployer =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            process.recipeTypeId(),
                            Component.literal(
                                    "Deployer + Sandpaper"
                            ),
                            List.of(recipeId),
                            List.of(
                                    machine(
                                            "create:deployer",
                                            "Deployer",
                                            1
                                    ),
                                    sandPaper
                            )
                    );

            return List.of(
                    manual,
                    deployer
            );
        }

        if (process.recipeTypeId().equals(
                ITEM_APPLICATION_ID
        )) {
            CraftScopeProductionMethod manual =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            process.recipeTypeId(),
                            Component.literal(
                                    "Manual Application"
                            ),
                            List.of(recipeId),
                            List.of()
                    );

            CraftScopeProductionMethod deployer =
                    new CraftScopeProductionMethod(
                            SOURCE_MOD_ID,
                            process.recipeTypeId(),
                            Component.literal(
                                    "Deployer Application"
                            ),
                            List.of(recipeId),
                            List.of(
                                    machine(
                                            "create:deployer",
                                            "Deployer",
                                            1
                                    )
                            )
                    );

            return List.of(
                    manual,
                    deployer
            );
        }

        return List.of(
                new CraftScopeProductionMethod(
                        SOURCE_MOD_ID,
                        process.recipeTypeId(),
                        Component.literal(
                                process.displayName()
                        ),
                        List.of(recipeId),
                        process.requirements()
                )
        );
    }

    private static CraftScopeResourceAmount toResource(
            ProcessingOutput output
    ) {
        return new CraftScopeResourceAmount(
                CraftScopeResourceKind.ITEM,
                output.id(),
                output.stack()
                        .getHoverName()
                        .copy(),
                output.stack()
                        .getCount(),
                "",
                false,
                clampChance(
                        output.chance()
                ),
                List.of(
                        output.id()
                )
        );
    }

    private static List<CraftScopeResourceAmount> buildInputs(
            Recipe<?> recipe
    ) {
        Map<String, IngredientAccumulator> grouped =
                new LinkedHashMap<>();

        for (Ingredient ingredient :
                recipe.getIngredients()) {

            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            List<ItemStack> variants =
                    normalizeVariants(
                            ingredient.getItems()
                    );

            if (variants.isEmpty()) {
                continue;
            }

            String key =
                    buildVariantKey(
                            variants
                    );

            IngredientAccumulator existing =
                    grouped.get(
                            key
                    );

            if (existing == null) {
                grouped.put(
                        key,
                        new IngredientAccumulator(
                                variants,
                                1
                        )
                );
            } else {
                grouped.put(
                        key,
                        new IngredientAccumulator(
                                existing.variants(),
                                existing.count() + 1
                        )
                );
            }
        }

        List<CraftScopeResourceAmount> inputs =
                new ArrayList<>();

        for (IngredientAccumulator accumulator :
                grouped.values()) {

            inputs.add(
                    CraftScopeResourceAmount.itemVariants(
                            accumulator.variants(),
                            accumulator.count()
                    )
            );
        }

        return List.copyOf(inputs);
    }

    private static List<ItemStack> normalizeVariants(
            ItemStack[] possibilities
    ) {
        if (possibilities == null) {
            return List.of();
        }

        Map<String, ItemStack> unique =
                new LinkedHashMap<>();

        for (ItemStack stack : possibilities) {
            if (stack == null || stack.isEmpty()) {
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

        for (ItemStack stack : variants) {
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

    /*
     * Create ProcessingOutput exposes public getStack() and
     * getChance() methods. We read those without importing Create.
     *
     * Every positive-probability item output is preserved. The
     * production model stores the chance on CraftScopeResourceAmount
     * and CraftScopeChancePlanner converts that into expected-value
     * run counts.
     *
     * Outputs for the same item are grouped ONLY when they share the
     * same probability. Two separate rolls for the same item at
     * different chances must remain distinct or their expected yield
     * would be misrepresented.
     */
    private static List<ProcessingOutput> getProcessingOutputs(
            Recipe<?> recipe,
            RegistryAccess registryAccess
    ) {
        ReflectionResult reflected =
                getProcessingOutputsReflectively(
                        recipe
                );

        if (reflected.supported()) {
            return reflected.outputs();
        }

        /*
         * Fallback for a compatible custom recipe implementation
         * that does not expose Create's rollable-result API. The
         * normal recipe result is deterministic.
         */
        ItemStack result =
                recipe.getResultItem(
                        registryAccess
                );

        if (result == null || result.isEmpty()) {
            return List.of();
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        result.getItem()
                );

        return List.of(
                new ProcessingOutput(
                        id,
                        result.copy(),
                        1.0D
                )
        );
    }

    private static ReflectionResult
    getProcessingOutputsReflectively(
            Recipe<?> recipe
    ) {
        try {
            Method getRollableResults =
                    recipe
                            .getClass()
                            .getMethod(
                                    "getRollableResults"
                            );

            Object value =
                    getRollableResults.invoke(
                            recipe
                    );

            if (!(value instanceof Iterable<?> iterable)) {
                return new ReflectionResult(
                        true,
                        List.of()
                );
            }

            Map<OutputKey, ItemStack> grouped =
                    new LinkedHashMap<>();

            for (Object output : iterable) {
                if (output == null) {
                    continue;
                }

                Method getStack =
                        output
                                .getClass()
                                .getMethod(
                                        "getStack"
                                );

                Method getChance =
                        output
                                .getClass()
                                .getMethod(
                                        "getChance"
                                );

                Object stackValue =
                        getStack.invoke(
                                output
                        );

                Object chanceValue =
                        getChance.invoke(
                                output
                        );

                if (!(stackValue instanceof ItemStack stack)
                        || stack.isEmpty()) {
                    continue;
                }

                double chance =
                        chanceValue instanceof Number number
                                ? clampChance(
                                number.doubleValue()
                        )
                                : 0.0D;

                if (chance <= 0.0D) {
                    continue;
                }

                ResourceLocation id =
                        BuiltInRegistries.ITEM.getKey(
                                stack.getItem()
                        );

                OutputKey key =
                        new OutputKey(
                                id,
                                Double.doubleToLongBits(
                                        chance
                                )
                        );

                ItemStack existing =
                        grouped.get(key);

                if (existing == null) {
                    grouped.put(
                            key,
                            stack.copy()
                    );
                } else {
                    ItemStack combined =
                            existing.copy();

                    combined.setCount(
                            safeAddInt(
                                    existing.getCount(),
                                    stack.getCount()
                            )
                    );

                    grouped.put(
                            key,
                            combined
                    );
                }
            }

            List<ProcessingOutput> result =
                    new ArrayList<>();

            for (Map.Entry<OutputKey, ItemStack> entry :
                    grouped.entrySet()) {

                result.add(
                        new ProcessingOutput(
                                entry.getKey().id(),
                                entry.getValue(),
                                Double.longBitsToDouble(
                                        entry.getKey().chanceBits()
                                )
                        )
                );
            }

            return new ReflectionResult(
                    true,
                    List.copyOf(result)
            );

        } catch (NoSuchMethodException ignored) {
            return new ReflectionResult(
                    false,
                    List.of()
            );

        } catch (ReflectiveOperationException
                 | RuntimeException ignored) {

            /*
             * The API shape was recognized but could not be read.
             * Be conservative and expose no outputs instead of
             * fabricating probabilities.
             */
            return new ReflectionResult(
                    true,
                    List.of()
            );
        }
    }

    private static boolean hasFluidData(
            Recipe<?> recipe
    ) {
        return hasNonEmptyReflectiveCollection(
                recipe,
                "getFluidIngredients"
        )
                || hasNonEmptyReflectiveCollection(
                recipe,
                "getFluidResults"
        );
    }

    private static boolean hasNonEmptyReflectiveCollection(
            Recipe<?> recipe,
            String methodName
    ) {
        try {
            Method method =
                    recipe
                            .getClass()
                            .getMethod(
                                    methodName
                            );

            Object value =
                    method.invoke(
                            recipe
                    );

            if (value instanceof Iterable<?> iterable) {
                return iterable
                        .iterator()
                        .hasNext();
            }

            return false;

        } catch (NoSuchMethodException ignored) {
            return false;

        } catch (ReflectiveOperationException
                 | RuntimeException ignored) {

            /*
             * If a known fluid accessor exists but fails, skipping
             * the route is safer than showing incomplete totals.
             */
            return true;
        }
    }

    private static ProcessingOutput findTargetOutput(
            List<ProcessingOutput> outputs,
            ItemStack target
    ) {
        ProcessingOutput best =
                null;

        double bestExpected =
                -1.0D;

        for (ProcessingOutput output : outputs) {
            if (!ItemStack.isSameItem(
                    output.stack(),
                    target
            )) {
                continue;
            }

            double expected =
                    output.stack().getCount()
                            * output.chance();

            if (best == null
                    || expected > bestExpected) {

                best =
                        output;

                bestExpected =
                        expected;
            }
        }

        return best;
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private static List<RecipeHolder<?>> getRecipes(
            RecipeManager recipeManager,
            RecipeType<?> recipeType
    ) {
        List raw =
                recipeManager.getAllRecipesFor(
                        (RecipeType) recipeType
                );

        List<RecipeHolder<?>> result =
                new ArrayList<>();

        for (Object value : raw) {
            if (value instanceof RecipeHolder<?> holder) {
                result.add(holder);
            }
        }

        return result;
    }

    private static ProcessDefinition process(
            String recipeTypeId,
            String displayName,
            int priority,
            CraftScopeProcessRequirement... requirements
    ) {
        return new ProcessDefinition(
                requireId(recipeTypeId),
                displayName,
                priority,
                List.of(requirements)
        );
    }

    private static CraftScopeProcessRequirement machine(
            String id,
            String displayName,
            long amount
    ) {
        return requirement(
                CraftScopeRequirementKind.MACHINE,
                id,
                displayName,
                amount,
                ""
        );
    }

    private static CraftScopeProcessRequirement requirement(
            CraftScopeRequirementKind kind,
            String id,
            String displayName,
            long amount,
            String unit
    ) {
        return new CraftScopeProcessRequirement(
                kind,
                requireId(id),
                Component.literal(displayName),
                amount,
                unit
        );
    }

    private static String buildStepId(
            ProcessDefinition process,
            ResourceLocation recipeId
    ) {
        return "create:"
                + process.recipeTypeId().getPath()
                + ":"
                + recipeId;
    }

    private static ResourceLocation buildRouteId(
            ProcessDefinition process,
            ResourceLocation recipeId,
            ResourceLocation targetId
    ) {
        return requireId(
                "craftscope:create/"
                        + process.recipeTypeId().getPath()
                        + "/"
                        + recipeId.getNamespace()
                        + "/"
                        + recipeId.getPath()
                        + "/"
                        + targetId.getNamespace()
                        + "/"
                        + targetId.getPath()
        );
    }

    private static ResourceLocation requireId(
            String value
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(value);

        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid resource location: "
                            + value
            );
        }

        return id;
    }

    private static int safeAddInt(
            int left,
            int right
    ) {
        long result =
                (long) left + right;

        return result > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) result;
    }

    private record ProcessDefinition(
            ResourceLocation recipeTypeId,
            String displayName,
            int priority,
            List<CraftScopeProcessRequirement> requirements
    ) {
        private ProcessDefinition {
            requirements =
                    requirements == null
                            ? List.of()
                            : List.copyOf(requirements);
        }
    }

    private record IngredientAccumulator(
            List<ItemStack> variants,
            int count
    ) {
        private IngredientAccumulator {
            variants =
                    List.copyOf(variants);
        }
    }

    private record ProcessingOutput(
            ResourceLocation id,
            ItemStack stack,
            double chance
    ) {
        private ProcessingOutput {
            chance =
                    clampChance(
                            chance
                    );

            stack =
                    stack == null
                            ? ItemStack.EMPTY
                            : stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    private record OutputKey(
            ResourceLocation id,
            long chanceBits
    ) {
    }

    private record SequencedAssemblyData(
            int loops,
            List<CraftScopeResourceAmount> inputs,
            List<CraftScopeProcessRequirement> requirements,
            double outputChance,
            CraftScopeResourceAmount startingInput,
            ItemStack transitionalItem,
            List<SequencedStepData> sequenceSteps
    ) {
        private SequencedAssemblyData {
            inputs =
                    inputs == null
                            ? List.of()
                            : List.copyOf(inputs);

            requirements =
                    requirements == null
                            ? List.of()
                            : List.copyOf(requirements);

            transitionalItem =
                    transitionalItem == null
                            ? ItemStack.EMPTY
                            : transitionalItem.copy();

            sequenceSteps =
                    sequenceSteps == null
                            ? List.of()
                            : List.copyOf(sequenceSteps);
        }

        @Override
        public ItemStack transitionalItem() {
            return transitionalItem.copy();
        }
    }

    private record SequencedStepData(
            ResourceLocation processId,
            String displayName,
            List<CraftScopeResourceAmount> externalInputs,
            List<CraftScopeProcessRequirement> requirements
    ) {
        private SequencedStepData {
            processId =
                    processId == null
                            ? requireId("craftscope:processing")
                            : processId;

            displayName =
                    displayName == null || displayName.isBlank()
                            ? "Processing"
                            : displayName;

            externalInputs =
                    externalInputs == null
                            ? List.of()
                            : List.copyOf(externalInputs);

            requirements =
                    requirements == null
                            ? List.of()
                            : List.copyOf(requirements);
        }
    }

    private record ReflectionResult(
            boolean supported,
            List<ProcessingOutput> outputs
    ) {
        private ReflectionResult {
            outputs =
                    outputs == null
                            ? List.of()
                            : List.copyOf(outputs);
        }
    }
}
