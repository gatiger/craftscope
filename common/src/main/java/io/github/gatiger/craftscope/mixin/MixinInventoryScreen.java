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

        int buttonWidth = 20;
        int buttonHeight = 20;

        int x = screen.width - buttonWidth - 5;
        int y = 5;

        Button button = Button.builder(
                Component.literal("C"),
                pressed -> Minecraft.getInstance().setScreen(new CraftScopeScreen(screen))
        ).bounds(
                x,
                y,
                buttonWidth,
                buttonHeight
        ).build();

        ((ScreenAccessor) screen).craftscope$addRenderableWidget(button);
    }
}