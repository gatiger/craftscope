package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeScreen;
import io.github.gatiger.craftscope.client.CraftScopeButtonPlacement;
import io.github.gatiger.craftscope.client.CraftScopeClientConfig;
import io.github.gatiger.craftscope.client.CraftScopeTabButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @Inject(method = "init", at = @At("TAIL"))
    private void craftscope$addTab(CallbackInfo ci) {

        AbstractContainerScreen<?> screen =
                (AbstractContainerScreen<?>) (Object) this;

        AbstractContainerScreenAccessor container =
                (AbstractContainerScreenAccessor) screen;

        int left = container.craftscope$getLeftPos();
        int top = container.craftscope$getTopPos();

        int buttonWidth = 16;
        int buttonHeight = 22;

        CraftScopeButtonPlacement.Position position;

        if (CraftScopeClientConfig.getPlacementMode()
                == CraftScopeClientConfig.PlacementMode.CUSTOM) {

            position = CraftScopeButtonPlacement.getCustomPosition(
                    left,
                    top
            );

        } else {

            List<? extends GuiEventListener> existingWidgets =
                    ((ScreenChildrenAccessor) screen)
                            .craftscope$getChildren();

            position = CraftScopeButtonPlacement.findBestPosition(
                    left,
                    top,
                    container.craftscope$getImageWidth(),
                    container.craftscope$getImageHeight(),
                    buttonWidth,
                    buttonHeight,
                    existingWidgets
            );
        }

        CraftScopeTabButton button =
                new CraftScopeTabButton(
                        position.x(),
                        position.y(),
                        buttonWidth,
                        buttonHeight,
                        Component.literal("C"),
                        pressed -> Minecraft.getInstance()
                                .setScreen(new CraftScopeScreen(screen)),
                        screen
                );

        ((ScreenAccessor) screen)
                .craftscope$addRenderableWidget(button);
    }
}