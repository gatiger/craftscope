package io.github.gatiger.craftscope.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Preserves broad recipe ingredient families while CraftScope expands
 * a production chain.
 *
 * Example:
 *
 *     Lectern
 *
 * accepts:
 *
 *     any wooden slab
 *
 * The route expander may choose Acacia Slab as a representative
 * upstream route. That does NOT mean the production chain truly
 * requires Acacia.
 *
 * Instead, the logical chain is:
 *
 *     Any Log
 *         ↓
 *     Any Planks
 *         ↓
 *     Any Slabs
 *
 * Individual icons can rotate through the actual accepted items.
 *
 * This class currently handles the vanilla wood families where
 * equivalent recipe routes naturally form a material family:
 *
 *     logs -> planks -> wooden slabs
 *
 * The same architecture can later support other tag-backed recipe
 * families without adding UI-specific hacks.
 */
public final class CraftScopeRecipeVariantFamilyPolicy {

    private CraftScopeRecipeVariantFamilyPolicy() {
    }

    /*
     * Normalize an individual recipe resource into its logical family.
     *
     * This is used by UI flow/convergence nodes that may receive the
     * original consumer-side ingredient rather than the adapted
     * producer-side resource.
     *
     * Examples:
     *
     *     Acacia Planks + all accepted plank variants
     *         -> Planks + all accepted plank variants
     *
     *     Acacia Slab + all accepted slab variants
     *         -> Slabs + all accepted slab variants
     */
    public static CraftScopeResourceAmount normalizeFamilyResource(
            CraftScopeResourceAmount resource
    ) {
        if (resource == null
                || resource.kind()
                != CraftScopeResourceKind.ITEM
                || !resource.hasVariants()) {

            return resource;
        }

        Family family =
                classify(
                        resource
                );

        if (family == null) {
            return resource;
        }

        return withFamily(
                resource,
                family
        );
    }
    public static CraftScopeProductionRoute adaptRoute(
            CraftScopeProductionRoute route,
            CraftScopeResourceAmount requestedFamily
    ) {
        if (route == null
                || requestedFamily == null
                || route.steps().size() != 1
                || requestedFamily.kind()
                != CraftScopeResourceKind.ITEM
                || !requestedFamily.hasVariants()) {

            return route;
        }

        Family outputFamily =
                classify(
                        requestedFamily
                );

        if (outputFamily == null) {
            return route;
        }

        CraftScopeProductionStep originalStep =
                route.steps()
                        .getFirst();

        CraftScopeResourceAmount familyTarget =
                withFamily(
                        route.targetOutput(),
                        outputFamily
                );

        List<CraftScopeResourceAmount> outputs =
                new ArrayList<>();

        for (CraftScopeResourceAmount output :
                originalStep.outputs()) {

            if (resourceBelongsToFamily(
                    output,
                    outputFamily
            )) {

                outputs.add(
                        withFamily(
                                output,
                                outputFamily
                        )
                );

            } else {

                outputs.add(
                        output
                );
            }
        }

        /*
         * Preserve the upstream family relationship.
         *
         *     any slabs
         *         requires
         *     any planks
         *
         *     any planks
         *         requires
         *     any logs
         */
        Family inputFamily =
                switch (outputFamily) {

                    case WOODEN_SLABS ->
                            Family.PLANKS;

                    case PLANKS ->
                            Family.LOGS;

                    case LOGS ->
                            null;
                };

        List<CraftScopeResourceAmount> inputs =
                new ArrayList<>();

        for (CraftScopeResourceAmount input :
                originalStep.inputs()) {

            if (inputFamily != null
                    && resourceBelongsToFamily(
                    input,
                    inputFamily
            )) {

                inputs.add(
                        withFamily(
                                input,
                                inputFamily
                        )
                );

            } else {

                inputs.add(
                        input
                );
            }
        }

        CraftScopeProductionStep adaptedStep =
                new CraftScopeProductionStep(
                        originalStep.id(),
                        originalStep.displayName(),
                        inputs,
                        outputs,
                        originalStep.methods()
                );

        return new CraftScopeProductionRoute(
                route.id(),
                route.sourceModId(),
                route.sourceModName(),
                route.displayName(),
                familyTarget,
                List.of(
                        adaptedStep
                ),
                route.priority()
        );
    }

    private static Family classify(
            CraftScopeResourceAmount resource
    ) {
        if (resourceMatchesTag(
                resource,
                ItemTags.WOODEN_SLABS
        )) {

            return Family.WOODEN_SLABS;
        }

        if (resourceMatchesTag(
                resource,
                ItemTags.PLANKS
        )) {

            return Family.PLANKS;
        }

        if (resourceMatchesTag(
                resource,
                ItemTags.LOGS
        )) {

            return Family.LOGS;
        }

        return null;
    }

    private static boolean resourceBelongsToFamily(
            CraftScopeResourceAmount resource,
            Family family
    ) {
        if (resource == null
                || resource.kind()
                != CraftScopeResourceKind.ITEM) {

            return false;
        }

        TagKey<Item> tag =
                family.tag();

        /*
         * A specific representative resource still belongs to the
         * family if every accepted variant belongs to that tag.
         */
        return resourceMatchesTag(
                resource,
                tag
        );
    }

    private static boolean resourceMatchesTag(
            CraftScopeResourceAmount resource,
            TagKey<Item> tag
    ) {
        if (resource == null
                || tag == null
                || resource.kind()
                != CraftScopeResourceKind.ITEM
                || resource.acceptedVariantIds().isEmpty()) {

            return false;
        }

        boolean found =
                false;

        for (ResourceLocation id :
                resource.acceptedVariantIds()) {

            Item item =
                    BuiltInRegistries.ITEM
                            .getOptional(
                                    id
                            )
                            .orElse(
                                    null
                            );

            if (item == null) {
                return false;
            }

            ItemStack stack =
                    new ItemStack(
                            item
                    );

            if (stack.isEmpty()
                    || !stack.is(
                    tag
            )) {

                return false;
            }

            found =
                    true;
        }

        return found;
    }

    private static CraftScopeResourceAmount withFamily(
            CraftScopeResourceAmount resource,
            Family family
    ) {
        List<ResourceLocation> variants =
                getFamilyVariants(
                        family
                );

        if (variants.isEmpty()) {
            return resource;
        }

        ResourceLocation representativeId =
                resource.id();

        if (!variants.contains(
                representativeId
        )) {

            representativeId =
                    variants.getFirst();
        }

        return new CraftScopeResourceAmount(
                resource.kind(),
                representativeId,
                Component.literal(
                        family.displayName()
                ),
                resource.amount(),
                resource.unit(),
                resource.consumed(),
                resource.chance(),
                variants,
                resource.minimumAmount(),
                resource.maximumAmount(),
                resource.expectedAmount(),
                null
        );
    }

    /*
     * Read the live registry/tag contents instead of caching them.
     *
     * Tags may change after a datapack/resource reload, and CraftScope
     * should reflect the currently loaded recipe environment.
     */
    private static List<ResourceLocation> getFamilyVariants(
            Family family
    ) {
        List<ResourceLocation> result =
                new ArrayList<>();

        BuiltInRegistries.ITEM
                .stream()
                .forEach(
                        item -> {

                            ItemStack stack =
                                    new ItemStack(
                                            item
                                    );

                            if (stack.isEmpty()
                                    || !stack.is(
                                    family.tag()
                            )) {

                                return;
                            }

                            ResourceLocation id =
                                    BuiltInRegistries.ITEM
                                            .getKey(
                                                    item
                                            );

                            if (id != null
                                    && !result.contains(
                                    id
                            )) {

                                result.add(
                                        id
                                );
                            }
                        }
                );

        result.sort(
                Comparator.comparing(
                        ResourceLocation::toString
                )
        );

        return List.copyOf(
                result
        );
    }

    private enum Family {

        LOGS(
                ItemTags.LOGS,
                "Log"
        ),

        PLANKS(
                ItemTags.PLANKS,
                "Planks"
        ),

        WOODEN_SLABS(
                ItemTags.WOODEN_SLABS,
                "Slabs"
        );

        private final TagKey<Item> tag;

        private final String displayName;

        Family(
                TagKey<Item> tag,
                String displayName
        ) {
            this.tag =
                    tag;

            this.displayName =
                    displayName;
        }

        private TagKey<Item> tag() {
            return tag;
        }

        private String displayName() {
            return displayName;
        }
    }
}