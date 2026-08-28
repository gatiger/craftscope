package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Prevent CraftScope's Quantity EditBox from retaining keyboard
 * focus when switching between project views.
 *
 * JEI's search field is an overlay and is not one of
 * CraftScopeProjectScreen's normal child widgets. If Quantity keeps
 * Minecraft's screen focus after changing tabs, JEI can appear to
 * receive a mouse click while keyboard input is still being routed
 * to CraftScope.
 *
 * updateViewButtons() is called whenever the project view changes,
 * so releasing Quantity focus here gives Recipe Tree / JEI a clean
 * input state when the player returns to that view.
 *
 * Clicking Quantity again still focuses it normally.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeQuantityFocus {

    @Shadow
    private EditBox quantityField;

    @Inject(
            method = "updateViewButtons",
            at = @At("TAIL")
    )
    private void craftscope$releaseQuantityFocus(
            CallbackInfo ci
    ) {
        if (quantityField == null) {
            return;
        }

        quantityField.setFocused(
                false
        );

        Screen screen =
                (Screen) (Object) this;

        if (screen.getFocused()
                == quantityField) {

            screen.setFocused(
                    null
            );
        }
    }
}