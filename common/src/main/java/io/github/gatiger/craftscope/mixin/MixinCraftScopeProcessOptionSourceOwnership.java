package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * Resolve the ecosystem shown for Process Diagram production options.
 *
 * The key distinction is:
 *
 *     WHAT performs the recipe
 *         vs.
 *     WHAT authored / registered the recipe
 *
 * KubeJS, CraftTweaker, datapacks, and similar systems can define a
 * recipe whose actual process still belongs to Create, Mekanism,
 * Thermal, Minecraft, or another processing ecosystem.
 *
 * CraftScope should therefore display:
 *
 *     kubejs recipe + Create Crushing
 *         -> Create: Crushing
 *
 *     kubejs recipe + Mekanism Enriching
 *         -> Mekanism: Enriching
 *
 *     kubejs recipe + vanilla crafting
 *         -> Minecraft: Crafting
 *
 * while a normal Create-owned vanilla crafting recipe can still be
 * presented as:
 *
 *     Create: Crafting
 *
 * Source resolution is generic. It does NOT contain a list of
 * supported processing mods. Any mod can win automatically through
 * its provider source, process ID, registered recipe type, registered
 * recipe serializer, or (last) ordinary recipe namespace.
 *
 * IMPORTANT MIXIN IMPLEMENTATION NOTE:
 *
 * Do not declare helper records/classes inside this mixin. Sponge
 * Mixin treats nested classes in a defined mixin package as mixin
 * package classes and transformed target code cannot reference them
 * directly. Ownership inspection therefore returns a simple String[]
 * instead of a nested RecipeOwnership record.
 */
@Mixin(CraftScopeProductionRouteQuery.class)
public abstract class MixinCraftScopeProcessOptionSourceOwnership {

    /*
     * These namespaces commonly author/replace recipes without owning
     * the machine/process that executes them.
     *
     * This list is intentionally about AUTHORING LAYERS, not process
     * mods. No Create/Mekanism/Thermal/etc. allow-list is required.
     */
    @Unique
    private static final Set<String>
            CRAFTSCOPE$TRANSPARENT_AUTHOR_NAMESPACES =
            Set.of(
                    "kubejs",
                    "crafttweaker"
            );

    @Unique
    private static final int CRAFTSCOPE$RECIPE_NAMESPACE_INDEX =
            0;

    @Unique
    private static final int CRAFTSCOPE$TYPE_NAMESPACE_INDEX =
            1;

    @Unique
    private static final int CRAFTSCOPE$SERIALIZER_NAMESPACE_INDEX =
            2;

    @Inject(
            method = "buildProcessOptions",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void craftscope$resolveProcessSources(
            List<CraftScopeProductionRoute> routes,
            CallbackInfoReturnable<List<CraftScopeProductionRoute>> cir
    ) {
        List<CraftScopeProductionRoute> original =
                cir.getReturnValue();

        if (original == null
                || original.isEmpty()) {

            return;
        }

        List<CraftScopeProductionRoute> corrected =
                new ArrayList<>(
                        original.size()
                );

        boolean changed =
                false;

        for (CraftScopeProductionRoute route :
                original) {

            CraftScopeProductionRoute replacement =
                    craftscope$resolveRouteSource(
                            route
                    );

            corrected.add(
                    replacement
            );

            if (replacement != route) {
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(
                    List.copyOf(
                            corrected
                    )
            );
        }
    }

    @Unique
    private static CraftScopeProductionRoute craftscope$resolveRouteSource(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().size() != 1) {

            return route;
        }

        CraftScopeProductionStep step =
                route.steps()
                        .getFirst();

        CraftScopeProductionMethod method =
                step.getPrimaryMethod();

        if (method == null) {
            return route;
        }

        String resolvedSource =
                craftscope$resolveSourceModId(
                        route,
                        method
                );

        if (resolvedSource == null
                || resolvedSource.isBlank()) {

            return route;
        }

        boolean routeAlreadyMatches =
                resolvedSource.equals(
                        route.sourceModId()
                );

        boolean methodAlreadyMatches =
                resolvedSource.equals(
                        method.sourceModId()
                );

        if (routeAlreadyMatches
                && methodAlreadyMatches) {

            return route;
        }

        CraftScopeProductionMethod correctedMethod =
                new CraftScopeProductionMethod(
                        resolvedSource,
                        method.processId(),
                        method.displayName(),
                        method.recipeIds(),
                        method.requirements()
                );

        List<CraftScopeProductionMethod> correctedMethods =
                new ArrayList<>(
                        step.methods()
                );

        /*
         * Process options created by CraftScopeProductionRouteQuery
         * normally contain exactly one method. Replace the primary
         * method while preserving any unexpected additional methods.
         */
        if (!correctedMethods.isEmpty()) {
            correctedMethods.set(
                    0,
                    correctedMethod
            );
        }

        CraftScopeProductionStep correctedStep =
                new CraftScopeProductionStep(
                        step.id(),
                        step.displayName(),
                        step.inputs(),
                        step.outputs(),
                        correctedMethods
                );

        return new CraftScopeProductionRoute(
                route.id(),
                resolvedSource,
                Component.literal(
                        craftscope$formatSourceName(
                                resolvedSource
                        )
                ),
                route.displayName(),
                route.targetOutput(),
                List.of(
                        correctedStep
                ),
                route.priority()
        );
    }

    @Unique
    private static String craftscope$resolveSourceModId(
            CraftScopeProductionRoute route,
            CraftScopeProductionMethod method
    ) {
        String methodSource =
                craftscope$normalizeNamespace(
                        method.sourceModId()
                );

        ResourceLocation processId =
                method.processId();

        String processNamespace =
                processId == null
                        ? ""
                        : craftscope$normalizeNamespace(
                        processId.getNamespace()
                );

        /*
         * 1. A real, explicit provider source is strongest.
         *
         * Adapters for Create, Mekanism, Thermal, etc. already know
         * which ecosystem owns their process. A scripting/authoring
         * layer is not treated as authoritative here.
         */
        if (craftscope$isRealProcessNamespace(
                methodSource
        )) {

            return methodSource;
        }

        /*
         * 2. A mod-owned process ID is also authoritative.
         *
         * Example:
         *     create:crushing
         *     mekanism:enriching
         *
         * This makes a kubejs:<recipe> still display as the actual
         * processing mod without any process-mod allow-list.
         */
        if (craftscope$isRealProcessNamespace(
                processNamespace
        )) {

            return processNamespace;
        }

        String[] ownership =
                craftscope$inspectLoadedRecipes(
                        method.recipeIds()
                );

        String recipeNamespace =
                ownership[
                        CRAFTSCOPE$RECIPE_NAMESPACE_INDEX
                ];

        String typeNamespace =
                ownership[
                        CRAFTSCOPE$TYPE_NAMESPACE_INDEX
                ];

        String serializerNamespace =
                ownership[
                        CRAFTSCOPE$SERIALIZER_NAMESPACE_INDEX
                ];

        /*
         * 3. Registered RecipeType ownership.
         *
         * This survives arbitrary recipe IDs because the loaded recipe
         * object itself reports the type of process it belongs to.
         */
        if (craftscope$isRealProcessNamespace(
                typeNamespace
        )) {

            return typeNamespace;
        }

        /*
         * 4. Registered RecipeSerializer ownership.
         *
         * For many modded recipes, the serializer is the most direct
         * reflection of the JSON recipe type such as:
         *
         *     create:crushing
         *     thermal:pulverizer
         *
         * A kubejs recipe using that serializer still resolves to the
         * machine/process mod.
         */
        if (craftscope$isRealProcessNamespace(
                serializerNamespace
        )) {

            return serializerNamespace;
        }

        /*
         * 5. Recipe ID namespace is a useful final ownership signal
         * for a normal mod-contributed vanilla process.
         *
         * Example:
         *     create:<ordinary crafting recipe>
         *         -> Create: Crafting
         *
         * Transparent authoring namespaces do NOT win here. Thus:
         *
         *     kubejs:<ordinary crafting recipe>
         *         -> Minecraft: Crafting
         */
        if (craftscope$isOrdinaryRecipeOwnerNamespace(
                recipeNamespace
        )) {

            return recipeNamespace;
        }

        /*
         * 6. Vanilla process fallback.
         *
         * CraftScope's generic vanilla provider intentionally uses
         * craftscope:* process IDs such as craftscope:crafting, so a
         * Minecraft method source remains the correct fallback after
         * all stronger real-process signals have been checked.
         */
        if ("minecraft".equals(
                methodSource
        )
                || "minecraft".equals(
                typeNamespace
        )
                || "minecraft".equals(
                serializerNamespace
        )) {

            return "minecraft";
        }

        /*
         * 7. Keep the route's existing source if nothing better can
         * be proven. This avoids inventing ownership.
         */
        String routeSource =
                craftscope$normalizeNamespace(
                        route.sourceModId()
                );

        if (!routeSource.isBlank()
                && !craftscope$isTransparentAuthorNamespace(
                routeSource
        )) {

            return routeSource;
        }

        /*
         * A transparent authoring source with no detectable external
         * process is most safely treated as vanilla when the process
         * ID is one of CraftScope's generic process IDs.
         */
        if ("craftscope".equals(
                processNamespace
        )) {

            return "minecraft";
        }

        return methodSource;
    }

    /*
     * Returns exactly three strings:
     *
     * [0] single recipe-ID namespace
     * [1] single registered RecipeType namespace
     * [2] single registered RecipeSerializer namespace
     *
     * Empty string means either "not known" or "mixed ownership".
     *
     * A plain array is used deliberately so transformed target code
     * never references a nested helper type from the mixin package.
     */
    @Unique
    private static String[] craftscope$inspectLoadedRecipes(
            List<ResourceLocation> recipeIds
    ) {
        if (recipeIds == null
                || recipeIds.isEmpty()) {

            return new String[]{
                    "",
                    "",
                    ""
            };
        }

        Set<String> recipeNamespaces =
                new LinkedHashSet<>();

        Set<String> typeNamespaces =
                new LinkedHashSet<>();

        Set<String> serializerNamespaces =
                new LinkedHashSet<>();

        RecipeManager recipeManager =
                craftscope$getClientRecipeManager();

        for (ResourceLocation recipeId :
                recipeIds) {

            if (recipeId == null) {
                continue;
            }

            recipeNamespaces.add(
                    craftscope$normalizeNamespace(
                            recipeId.getNamespace()
                    )
            );

            if (recipeManager == null) {
                continue;
            }

            RecipeHolder<?> holder =
                    recipeManager
                            .byKey(
                                    recipeId
                            )
                            .orElse(
                                    null
                            );

            if (holder == null
                    || holder.value() == null) {

                continue;
            }

            ResourceLocation typeId =
                    BuiltInRegistries.RECIPE_TYPE.getKey(
                            holder.value()
                                    .getType()
                    );

            if (typeId != null) {
                typeNamespaces.add(
                        craftscope$normalizeNamespace(
                                typeId.getNamespace()
                        )
                );
            }

            ResourceLocation serializerId =
                    BuiltInRegistries.RECIPE_SERIALIZER.getKey(
                            holder.value()
                                    .getSerializer()
                    );

            if (serializerId != null) {
                serializerNamespaces.add(
                        craftscope$normalizeNamespace(
                                serializerId.getNamespace()
                        )
                );
            }
        }

        return new String[]{
                craftscope$singleMeaningfulNamespace(
                        recipeNamespaces
                ),
                craftscope$singleMeaningfulNamespace(
                        typeNamespaces
                ),
                craftscope$singleMeaningfulNamespace(
                        serializerNamespaces
                )
        };
    }

    @Unique
    private static RecipeManager craftscope$getClientRecipeManager() {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft == null
                || minecraft.level == null) {

            return null;
        }

        return minecraft
                .level
                .getRecipeManager();
    }

    @Unique
    private static String craftscope$singleMeaningfulNamespace(
            Set<String> namespaces
    ) {
        if (namespaces == null
                || namespaces.isEmpty()) {

            return "";
        }

        String only =
                null;

        for (String namespace :
                namespaces) {

            String normalized =
                    craftscope$normalizeNamespace(
                            namespace
                    );

            if (normalized.isBlank()) {
                continue;
            }

            if (only == null) {
                only =
                        normalized;

                continue;
            }

            if (!only.equals(
                    normalized
            )) {

                return "";
            }
        }

        return only == null
                ? ""
                : only;
    }

    @Unique
    private static boolean craftscope$isRealProcessNamespace(
            String namespace
    ) {
        String normalized =
                craftscope$normalizeNamespace(
                        namespace
                );

        return !normalized.isBlank()
                && !"minecraft".equals(
                normalized
        )
                && !"craftscope".equals(
                normalized
        )
                && !craftscope$isTransparentAuthorNamespace(
                normalized
        );
    }

    @Unique
    private static boolean craftscope$isOrdinaryRecipeOwnerNamespace(
            String namespace
    ) {
        String normalized =
                craftscope$normalizeNamespace(
                        namespace
                );

        return !normalized.isBlank()
                && !"minecraft".equals(
                normalized
        )
                && !"craftscope".equals(
                normalized
        )
                && !craftscope$isTransparentAuthorNamespace(
                normalized
        );
    }

    @Unique
    private static boolean craftscope$isTransparentAuthorNamespace(
            String namespace
    ) {
        String normalized =
                craftscope$normalizeNamespace(
                        namespace
                );

        return CRAFTSCOPE$TRANSPARENT_AUTHOR_NAMESPACES.contains(
                normalized
        );
    }

    @Unique
    private static String craftscope$normalizeNamespace(
            String namespace
    ) {
        if (namespace == null) {
            return "";
        }

        return namespace
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    @Unique
    private static String craftscope$formatSourceName(
            String sourceModId
    ) {
        if (sourceModId == null
                || sourceModId.isBlank()) {

            return "";
        }

        if ("minecraft".equals(
                sourceModId
        )) {

            return "Minecraft";
        }

        if ("craftscope".equals(
                sourceModId
        )) {

            return "CraftScope";
        }

        String[] pieces =
                sourceModId.split(
                        "[_\\-]"
                );

        StringBuilder result =
                new StringBuilder();

        for (String piece :
                pieces) {

            if (piece == null
                    || piece.isBlank()) {

                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            piece.charAt(0)
                    )
            );

            if (piece.length() > 1) {
                result.append(
                        piece.substring(
                                1
                        )
                );
            }
        }

        return result.isEmpty()
                ? sourceModId
                : result.toString();
    }
}
