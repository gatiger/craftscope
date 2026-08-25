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

        /*
        * Attach CraftScope like a tab to the right edge of the inventory panel.
        * A few pixels remain over the panel so it looks integrated.
        */
        int x = left + container.craftscope$getImageWidth() - 4;
        int y = top + 62;

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