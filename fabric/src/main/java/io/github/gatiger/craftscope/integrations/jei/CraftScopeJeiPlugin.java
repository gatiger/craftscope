package io.github.gatiger.craftscope.integration.jei;

import io.github.gatiger.craftscope.Constants;
import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
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

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
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

    /*
     * JEI is displayed only while Recipe Tree is active.
     *
     * The other CraftScope tabs reclaim the full width of the
     * screen.
     */
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