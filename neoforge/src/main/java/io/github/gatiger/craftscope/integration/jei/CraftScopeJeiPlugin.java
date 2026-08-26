package io.github.gatiger.craftscope.integration.jei;

import io.github.gatiger.craftscope.Constants;
import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.integration.CraftScopeRecipeViewer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

@JeiPlugin
public class CraftScopeJeiPlugin
        implements IModPlugin {

    private static final ResourceLocation PLUGIN_ID =
            ResourceLocation.fromNamespaceAndPath(
                    Constants.MOD_ID,
                    "jei"
            );

    private static final String VIEWER_ID =
            "jei";

    private IJeiRuntime jeiRuntime;

    private final CraftScopeRecipeViewer.Handler recipeViewerHandler =
            this::openRecipe;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void onRuntimeAvailable(
            IJeiRuntime jeiRuntime
    ) {
        this.jeiRuntime =
                jeiRuntime;

        CraftScopeRecipeViewer.register(
                VIEWER_ID,
                recipeViewerHandler
        );

        Constants.LOG.info(
                "CraftScope JEI recipe viewer integration available"
        );
    }

    @Override
    public void onRuntimeUnavailable() {
        CraftScopeRecipeViewer.unregister(
                VIEWER_ID,
                recipeViewerHandler
        );

        jeiRuntime =
                null;
    }

    private boolean openRecipe(
            ResourceLocation processId,
            List<ResourceLocation> recipeIds
    ) {
        IJeiRuntime runtime =
                jeiRuntime;

        if (runtime == null
                || processId == null
                || recipeIds == null
                || recipeIds.isEmpty()) {

            return false;
        }

        IRecipeManager recipeManager =
                runtime.getRecipeManager();

        Optional<RecipeType<?>> directType =
                recipeManager.getRecipeType(
                        processId
                );

        if (directType.isPresent()
                && showMatchingRecipes(
                runtime,
                recipeManager,
                directType.get(),
                recipeIds
        )) {

            return true;
        }

        List<IRecipeCategory<?>> categories =
                recipeManager
                        .createRecipeCategoryLookup()
                        .get()
                        .toList();

        for (IRecipeCategory<?> category :
                categories) {

            if (directType.isPresent()
                    && category
                    .getRecipeType()
                    .equals(
                            directType.get()
                    )) {

                continue;
            }

            if (showMatchingRecipesInCategory(
                    runtime,
                    recipeManager,
                    category,
                    recipeIds
            )) {

                return true;
            }
        }

        return false;
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private static boolean showMatchingRecipes(
            IJeiRuntime runtime,
            IRecipeManager recipeManager,
            RecipeType<?> recipeType,
            List<ResourceLocation> recipeIds
    ) {
        return showMatchingRecipesTyped(
                runtime,
                recipeManager,
                (RecipeType) recipeType,
                recipeIds
        );
    }

    private static <T> boolean showMatchingRecipesTyped(
            IJeiRuntime runtime,
            IRecipeManager recipeManager,
            RecipeType<T> recipeType,
            List<ResourceLocation> recipeIds
    ) {
        IRecipeCategory<T> category =
                recipeManager.getRecipeCategory(
                        recipeType
                );

        return showMatchingRecipesInCategoryTyped(
                runtime,
                recipeManager,
                category,
                recipeIds
        );
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private static boolean showMatchingRecipesInCategory(
            IJeiRuntime runtime,
            IRecipeManager recipeManager,
            IRecipeCategory<?> category,
            List<ResourceLocation> recipeIds
    ) {
        return showMatchingRecipesInCategoryTyped(
                runtime,
                recipeManager,
                (IRecipeCategory) category,
                recipeIds
        );
    }

    private static <T> boolean showMatchingRecipesInCategoryTyped(
            IJeiRuntime runtime,
            IRecipeManager recipeManager,
            IRecipeCategory<T> category,
            List<ResourceLocation> recipeIds
    ) {
        RecipeType<T> recipeType =
                category.getRecipeType();

        List<T> matchingRecipes =
                recipeManager
                        .createRecipeLookup(
                                recipeType
                        )
                        .get()
                        .filter(
                                recipe -> {

                                    ResourceLocation recipeId =
                                            category.getRegistryName(
                                                    recipe
                                            );

                                    return recipeId != null
                                            && recipeIds.contains(
                                            recipeId
                                    );
                                }
                        )
                        .toList();

        if (matchingRecipes.isEmpty()) {
            return false;
        }

        runtime
                .getRecipesGui()
                .showRecipes(
                        category,
                        matchingRecipes,
                        List.of()
                );

        return true;
    }

    @Override
    public void registerGuiHandlers(
            IGuiHandlerRegistration registration
    ) {
        Constants.LOG.info(
                "CraftScope JEI plugin: registering GUI handlers"
        );

        registration.addGuiScreenHandler(
                CraftScopeProjectScreen.class,
                this::getGuiProperties
        );

        registration.addGhostIngredientHandler(
                CraftScopeProjectScreen.class,
                new CraftScopeGhostIngredientHandler()
        );
    }

    private IGuiProperties getGuiProperties(
            CraftScopeProjectScreen screen
    ) {
        if (!screen.craftscope$isRecipeTreeView()) {
            return null;
        }

        final int guiLeft =
                screen.craftscope$getWindowLeft();

        final int guiTop =
                screen.craftscope$getWindowTop();

        final int guiWidth =
                Math.max(
                        1,
                        screen.craftscope$getWindowRight()
                                - guiLeft
                );

        final int guiHeight =
                Math.max(
                        1,
                        screen.craftscope$getWindowBottom()
                                - guiTop
                );

        return new IGuiProperties() {

            @Override
            public Class<? extends Screen> screenClass() {
                return CraftScopeProjectScreen.class;
            }

            @Override
            public int guiLeft() {
                return guiLeft;
            }

            @Override
            public int guiTop() {
                return guiTop;
            }

            @Override
            public int guiXSize() {
                return guiWidth;
            }

            @Override
            public int guiYSize() {
                return guiHeight;
            }

            @Override
            public int screenWidth() {
                return screen.width;
            }

            @Override
            public int screenHeight() {
                return screen.height;
            }
        };
    }

    private static class CraftScopeGhostIngredientHandler
            implements IGhostIngredientHandler<CraftScopeProjectScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(
                CraftScopeProjectScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart
        ) {
            if (!screen.craftscope$isRecipeTreeView()) {
                return List.of();
            }

            Optional<ItemStack> optionalStack =
                    ingredient.getItemStack();

            if (optionalStack.isEmpty()) {
                return List.of();
            }

            ItemStack stack =
                    optionalStack
                            .get()
                            .copy();

            Rect2i targetArea =
                    new Rect2i(
                            screen.craftscope$getTargetSlotX(),
                            screen.craftscope$getTargetSlotY(),
                            screen.craftscope$getTargetSlotWidth(),
                            screen.craftscope$getTargetSlotHeight()
                    );

            Target<I> target =
                    new Target<>() {

                        @Override
                        public Rect2i getArea() {
                            return targetArea;
                        }

                        @Override
                        public void accept(
                                I ignored
                        ) {
                            screen.craftscope$setTargetItem(
                                    stack.copy()
                            );
                        }
                    };

            return List.of(
                    target
            );
        }

        @Override
        public void onComplete() {

            /*
             * CraftScope saves the project when the target item
             * is accepted.
             */
        }
    }
}