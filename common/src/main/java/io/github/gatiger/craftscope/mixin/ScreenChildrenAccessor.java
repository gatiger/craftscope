package io.github.gatiger.craftscope.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenChildrenAccessor {

    @Invoker("children")
    List<? extends GuiEventListener> craftscope$getChildren();
}
