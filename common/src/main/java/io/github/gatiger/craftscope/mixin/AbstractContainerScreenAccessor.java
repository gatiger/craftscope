package io.github.gatiger.craftscope.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int craftscope$getLeftPos();

    @Accessor("topPos")
    int craftscope$getTopPos();

    @Accessor("imageWidth")
    int craftscope$getImageWidth();

    @Accessor("imageHeight")
    int craftscope$getImageHeight();
}