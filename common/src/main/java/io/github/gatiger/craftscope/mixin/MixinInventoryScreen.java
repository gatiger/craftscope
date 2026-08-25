package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.gatiger.craftscope.client.CraftScopeButtonPlacement;
import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {

    @Inject(method = "init", at = @At("TAIL"))
    private void craftscope$addButton(CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;

        AbstractContainerScreenAccessor container =
                (AbstractContainerScreenAccessor) screen;

        int left = container.craftscope$getLeftPos();
        int top = container.craftscope$getTopPos();

        int buttonWidth = 16;
        int buttonHeight = 22;
        
        
        List<? extends GuiEventListener> existingWidgets =
                ((ScreenChildrenAccessor) screen).craftscope$getChildren();

        CraftScopeButtonPlacement.Position position =
                CraftScopeButtonPlacement.findBestPosition(
                        left,
                        top,
                        container.craftscope$getImageWidth(),
                        container.craftscope$getImageHeight(),
                        buttonWidth,
                        buttonHeight,
                        existingWidgets
                );

        int x = position.x();
        int y = position.y();

        Button button = Button.builder(
                Component.literal("C"),
                pressed -> Minecraft.getInstance()
                        .setScreen(new CraftScopeScreen(screen))
        ).bounds(
                x,
                y,
                buttonWidth,
                buttonHeight
        ).build();

        ((ScreenAccessor) screen).craftscope$addRenderableWidget(button);
    }
}