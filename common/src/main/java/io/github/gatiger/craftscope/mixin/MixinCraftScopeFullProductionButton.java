package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeFullProductionScreen;
import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/*
 * Adds the "View Full Production" action to large Process Diagrams.
 *
 * Medium and small diagrams stay uncluttered.
 *
 * Large routes can open a dedicated screen where the graph receives
 * nearly the entire Minecraft window.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeFullProductionButton {

    /*
     * Five production steps is the first point where the normal
     * embedded Process Diagram commonly becomes crowded once branch
     * resource/process nodes are included.
     */
    @Unique
    private static final int CRAFTSCOPE_FULL_VIEW_STEP_THRESHOLD =
            5;

    @Shadow
    @Final
    private CraftScopeProject project;

    @Shadow
    private List<CraftScopeProductionRoute> productionRoutes;

    @Shadow
    private int selectedProductionRouteIndex;

    @Shadow
    private CraftScopeFlatButton processDiagramButton;

    @Shadow
    private CraftScopeFlatButton setupButton;

    @Unique
    private CraftScopeFlatButton craftscope$fullProductionButton;

    @Invoker("getDisplayProductionRoute")
    protected abstract CraftScopeProductionRoute
    craftscope$invokeGetDisplayProductionRoute(
            CraftScopeProductionRoute route
    );

    @Invoker("getContentLeft")
    protected abstract int craftscope$invokeGetContentLeft();

    @Invoker("getContentRight")
    protected abstract int craftscope$invokeGetContentRight();

    @Invoker("getWindowBottom")
    protected abstract int craftscope$invokeGetWindowBottom();

    /*
     * Add the button immediately after the normal Project screen has
     * created its Recipe Tree / Total Materials / Process Diagram /
     * Setup tab buttons.
     */
    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void craftscope$addFullProductionButton(
            CallbackInfo ci
    ) {
        if (setupButton == null) {
            return;
        }

        int buttonWidth =
                116;

        int buttonX =
                setupButton.getX()
                        + setupButton.getWidth()
                        + 6;

        int buttonY =
                setupButton.getY();

        craftscope$fullProductionButton =
                new CraftScopeFlatButton(
                        buttonX,
                        buttonY,
                        buttonWidth,
                        setupButton.getHeight(),
                        Component.literal(
                                "View Full Production"
                        ),
                        this::craftscope$openFullProduction
                );

        /*
         * Visibility is recalculated every render because switching
         * tabs or routes does not recreate the screen.
         */
        craftscope$fullProductionButton.visible =
                false;

        craftscope$fullProductionButton.active =
                false;

        ((ScreenAccessor) (Object) this)
                .craftscope$addRenderableWidget(
                        craftscope$fullProductionButton
                );
    }

    /*
     * Keep the button synchronized with the current tab and route.
     */
    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void craftscope$updateFullProductionButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        if (craftscope$fullProductionButton == null) {
            return;
        }

        CraftScopeProductionRoute route =
                craftscope$getSelectedDisplayRoute();

        boolean visible =
                processDiagramButton != null
                        && processDiagramButton.isSelected()
                        && route != null
                        && route.steps().size()
                        >= CRAFTSCOPE_FULL_VIEW_STEP_THRESHOLD;

        craftscope$fullProductionButton.visible =
                visible;

        craftscope$fullProductionButton.active =
                visible;
    }

    /*
     * The continuation card itself behaves like a shortcut to the
     * dedicated Full Production screen.
     */
    @Inject(
            method = "mouseClicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$clickFullProductionContinuation(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (button != 0
                || processDiagramButton == null
                || !processDiagramButton.isSelected()) {

            return;
        }

        CraftScopeProductionRoute route =
                craftscope$getSelectedDisplayRoute();

        if (route == null) {
            return;
        }

        int left =
                craftscope$invokeGetContentLeft();

        int right =
                craftscope$invokeGetContentRight();

        /*
         * Process panel top:
         *
         * CONTENT_TITLE_Y - 4
         *
         * CONTENT_TITLE_Y is 27 pixels below the Process Diagram tab.
         */
        int processTop =
                processDiagramButton.getY()
                        + 27;

        int summaryHeight =
                74;

        int summaryTop =
                craftscope$invokeGetWindowBottom()
                        - 10
                        - summaryHeight;

        int mainBottom =
                summaryTop - 6;

        int availableWidth =
                right - left;

        int gap =
                6;

        int leftColumnWidth =
                Math.min(
                        145,
                        Math.max(
                                110,
                                availableWidth / 6
                        )
                );

        int rightColumnWidth =
                Math.min(
                        165,
                        Math.max(
                                130,
                                availableWidth / 6
                        )
                );

        int centerLeft =
                left
                        + leftColumnWidth
                        + gap;

        int centerRight =
                right
                        - rightColumnWidth
                        - gap;

        /*
         * These are the exact bounds passed by
         * renderSelectedProductionRoute() to the renderer.
         */
        int diagramLeft =
                centerLeft + 6;

        int diagramTop =
                processTop + 25;

        int diagramRight =
                centerRight - 6;

        int diagramBottom =
                mainBottom - 6;

        if (!CraftScopeProcessDiagramRenderer
                .isContinuationMarkerHit(
                        route,
                        diagramLeft,
                        diagramTop,
                        diagramRight,
                        diagramBottom,
                        mouseX,
                        mouseY
                )) {

            return;
        }

        craftscope$openFullProduction();

        cir.setReturnValue(
                true
        );
    }
    @Unique
    private CraftScopeProductionRoute
    craftscope$getSelectedDisplayRoute() {

        if (productionRoutes == null
                || selectedProductionRouteIndex < 0
                || selectedProductionRouteIndex
                >= productionRoutes.size()) {

            return null;
        }

        CraftScopeProductionRoute route =
                productionRoutes.get(
                        selectedProductionRouteIndex
                );

        return craftscope$invokeGetDisplayProductionRoute(
                route
        );
    }

    @Unique
    private void craftscope$openFullProduction() {
        CraftScopeProductionRoute route =
                craftscope$getSelectedDisplayRoute();

        if (route == null) {
            return;
        }

        Screen currentScreen =
                (Screen) (Object) this;

        Minecraft
                .getInstance()
                .setScreen(
                        new CraftScopeFullProductionScreen(
                                currentScreen,
                                route,
                                project.getTargetCount()
                        )
                );
    }
}