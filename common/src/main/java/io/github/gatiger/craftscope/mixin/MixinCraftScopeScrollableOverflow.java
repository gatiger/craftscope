package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionSummary;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.ui.CraftScopeMarqueeContext;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
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
 * First general overflow-scrolling pass for CraftScope.
 *
 * UI rule:
 *
 * If a list contains more information than its panel can display,
 * keep every entry available and make the panel scrollable.
 *
 * This replaces the old "+N more" behavior in:
 *
 * - Process Diagram -> Production Routes
 * - Setup -> Required Machines
 * - Setup -> Operating Requirements
 * - Setup -> Selected Methods
 * - Process/Setup summary -> Required Machines
 * - Process/Setup summary -> Required Resources
 * - Process/Setup summary -> Outputs
 *
 * Recipe Tree, Total Materials, and Recipe Source already have
 * their own scrolling implementations.
 *
 * A later pass can use the same rule for detail-pane content that
 * is currently vertically clipped rather than summarized with
 * "+N more".
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeScrollableOverflow {

    @Unique
    private static final int CRAFTSCOPE_SCROLL_AMOUNT = 20;

    @Unique
    private static final int CRAFTSCOPE_MODE_OTHER = 0;

    @Unique
    private static final int CRAFTSCOPE_MODE_PROCESS = 1;

    @Unique
    private static final int CRAFTSCOPE_MODE_SETUP = 2;

    /*
     * CraftScopeProjectScreen constants used to derive panel
     * geometry from its public window bounds.
     *
     * CONTENT_SIDE_MARGIN = 10
     * CONTENT_BOTTOM_MARGIN = 10
     * CONTENT_TITLE_Y - 4 = windowTop + 93
     */
    @Unique
    private static final int CRAFTSCOPE_CONTENT_SIDE_MARGIN = 10;

    @Unique
    private static final int CRAFTSCOPE_CONTENT_BOTTOM_MARGIN = 10;

    @Unique
    private static final int CRAFTSCOPE_MAIN_TOP_OFFSET = 93;

    @Unique
    private static final int CRAFTSCOPE_SUMMARY_HEIGHT = 74;

    @Shadow
    private List<CraftScopeProductionRoute> productionRoutes;

    @Shadow
    private int selectedProductionRouteIndex;

    @Shadow
    private int selectedDiagramNodeIndex;

    /*
     * Which view was rendered most recently.
     *
     * We deliberately do not shadow CraftScopeProjectScreen's
     * private ViewMode enum. Marking the view from the existing
     * render methods keeps this mixin independent of that private
     * nested type.
     */
    @Unique
    private int craftscope$overflowMode =
            CRAFTSCOPE_MODE_OTHER;

    @Unique
    private double craftscope$productionRouteScroll;

    @Unique
    private double craftscope$setupMachineScroll;

    @Unique
    private double craftscope$setupRequirementScroll;

    @Unique
    private double craftscope$setupMethodScroll;

    @Unique
    private double craftscope$summaryMachineScroll;

    @Unique
    private double craftscope$summaryResourceScroll;

    @Unique
    private double craftscope$summaryOutputScroll;

    @Unique
    private int craftscope$productionRouteMaxScroll;

    @Unique
    private int craftscope$setupMachineMaxScroll;

    @Unique
    private int craftscope$setupRequirementMaxScroll;

    @Unique
    private int craftscope$setupMethodMaxScroll;

    @Unique
    private int craftscope$summaryMachineMaxScroll;

    @Unique
    private int craftscope$summaryResourceMaxScroll;

    @Unique
    private int craftscope$summaryOutputMaxScroll;

    @Invoker("getTargetStack")
    protected abstract ItemStack craftscope$invokeGetTargetStack();

    @Invoker("fitText")
    protected abstract String craftscope$invokeFitText(
            String text,
            int maxWidth
    );

    @Invoker("getProductionRouteLabel")
    protected abstract String craftscope$invokeGetProductionRouteLabel(
            CraftScopeProductionRoute route
    );

    @Invoker("renderSetupEmptyMessage")
    protected abstract void craftscope$invokeRenderSetupEmptyMessage(
            GuiGraphics graphics,
            int left,
            int right,
            int top,
            String text
    );

    @Invoker("getSummaryRequirementStack")
    protected abstract ItemStack craftscope$invokeGetSummaryRequirementStack(
            CraftScopeProcessRequirement requirement
    );

    @Invoker("formatSummaryRequirementAmount")
    protected abstract String craftscope$invokeFormatSummaryRequirementAmount(
            CraftScopeProcessRequirement requirement
    );

    @Invoker("formatSetupRequirementKind")
    protected abstract String craftscope$invokeFormatSetupRequirementKind(
            CraftScopeRequirementKind kind
    );

    @Invoker("renderSummaryEmptyText")
    protected abstract void craftscope$invokeRenderSummaryEmptyText(
            GuiGraphics graphics,
            int left,
            int right,
            int top,
            String text
    );

    @Invoker("renderSummaryRequirementRow")
    protected abstract void craftscope$invokeRenderSummaryRequirementRow(
            GuiGraphics graphics,
            CraftScopeProcessRequirement requirement,
            int left,
            int right,
            int y
    );

    @Invoker("renderSummaryResourceRow")
    protected abstract void craftscope$invokeRenderSummaryResourceRow(
            GuiGraphics graphics,
            CraftScopeResourceAmount resource,
            int left,
            int right,
            int y
    );

    /*
     * ---------------------------------------------------------
     * Track the active view without accessing the private enum.
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderRecipeTree",
            at = @At("HEAD")
    )
    private void craftscope$markRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        craftscope$overflowMode =
                CRAFTSCOPE_MODE_OTHER;
    }

    @Inject(
            method = "renderTotalMaterials",
            at = @At("HEAD")
    )
    private void craftscope$markTotalMaterials(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        craftscope$overflowMode =
                CRAFTSCOPE_MODE_OTHER;
    }

    @Inject(
            method = "renderProcessDiagram",
            at = @At("HEAD")
    )
    private void craftscope$markProcessDiagram(
            GuiGraphics graphics,
            CallbackInfo ci
    ) {
        craftscope$overflowMode =
                CRAFTSCOPE_MODE_PROCESS;
    }

    @Inject(
            method = "renderSetup",
            at = @At("HEAD")
    )
    private void craftscope$markSetup(
            GuiGraphics graphics,
            CallbackInfo ci
    ) {
        craftscope$overflowMode =
                CRAFTSCOPE_MODE_SETUP;
    }

    /*
     * ---------------------------------------------------------
     * Process Diagram -> Production Routes
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderProductionRoutes",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableProductionRoutes(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        Font font =
                Minecraft.getInstance().font;

        int rowTop =
                top + 25;

        int rowHeight =
                22;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 4
                );

        int viewportHeight =
                Math.max(
                        0,
                        viewportBottom - rowTop
                );

        int contentHeight =
                productionRoutes == null
                        ? 0
                        : productionRoutes.size()
                        * rowHeight;

        craftscope$productionRouteMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$productionRouteScroll =
                craftscope$clamp(
                        craftscope$productionRouteScroll,
                        craftscope$productionRouteMaxScroll
                );

        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            graphics.drawString(
                    font,
                    "Select a target item.",
                    left + 8,
                    rowTop + 6,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        if (productionRoutes == null
                || productionRoutes.isEmpty()) {

            graphics.drawString(
                    font,
                    "No production routes",
                    left + 8,
                    rowTop + 6,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            graphics.drawString(
                    font,
                    "were found.",
                    left + 8,
                    rowTop + 20,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        graphics.enableScissor(
                left + 2,
                rowTop,
                right - 2,
                viewportBottom
        );

        int y =
                rowTop
                        - (int) craftscope$productionRouteScroll;

        for (int i = 0;
             i < productionRoutes.size();
             i++) {

            int rowY =
                    y + i * rowHeight;

            if (rowY + rowHeight < rowTop
                    || rowY > viewportBottom) {

                continue;
            }

            CraftScopeProductionRoute route =
                    productionRoutes.get(i);

            boolean selected =
                    i == selectedProductionRouteIndex;

            if (selected) {

                graphics.fill(
                        left + 4,
                        rowY,
                        right - 5,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

                CraftScopeUiTheme.drawBorder(
                        graphics,
                        left + 4,
                        rowY,
                        right - 5,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT
                );
            }

            String fullLabel =
                    craftscope$invokeGetProductionRouteLabel(
                            route
                    );

            String label;

            if (selected) {

                CraftScopeMarqueeContext.begin();

                try {

                    label =
                            craftscope$invokeFitText(
                                    fullLabel,
                                    right - left - 20
                            );

                } finally {

                    CraftScopeMarqueeContext.end();
                }

            } else {

                /*
                 * Non-selected rows remain still and use CraftScope's
                 * ordinary ellipsis behavior.
                 */
                label =
                        craftscope$invokeFitText(
                                fullLabel,
                                right - left - 20
                        );
            }

            graphics.drawString(
                    font,
                    label,
                    left + 9,
                    rowY + 6,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 5,
                rowTop,
                viewportBottom,
                contentHeight,
                craftscope$productionRouteScroll,
                craftscope$productionRouteMaxScroll
        );
    }

    /*
     * The original click handler assumes row zero is always the
     * first visible route. Account for scroll before it gets a
     * chance to use that old mapping.
     */
    @Inject(
            method = "handleProductionRouteClick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$handleScrollableProductionRouteClick(
            double mouseX,
            double mouseY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (productionRoutes == null
                || productionRoutes.isEmpty()) {

            return;
        }

        int left =
                craftscope$getContentLeft();

        int right =
                craftscope$getContentRight();

        int top =
                craftscope$getMainTop();

        int summaryTop =
                craftscope$getSummaryTop();

        int mainBottom =
                summaryTop - 6;

        int availableWidth =
                right - left;

        int leftColumnWidth =
                Math.min(
                        145,
                        Math.max(
                                110,
                                availableWidth / 6
                        )
                );

        int leftHeight =
                mainBottom - top;

        int routesBottom =
                top
                        + Math.max(
                        76,
                        leftHeight * 43 / 100
                );

        int rowTop =
                top + 25;

        int rowHeight =
                22;

        int viewportBottom =
                Math.max(
                        rowTop,
                        routesBottom - 4
                );

        if (mouseX < left + 4
                || mouseX >= left
                + leftColumnWidth
                - 4
                || mouseY < rowTop
                || mouseY >= viewportBottom) {

            return;
        }

        int clickedIndex =
                (int) (
                        (
                                mouseY
                                        - rowTop
                                        + craftscope$productionRouteScroll
                        )
                                / rowHeight
                );

        if (clickedIndex < 0
                || clickedIndex >= productionRoutes.size()) {

            cir.setReturnValue(false);
            return;
        }

        selectedProductionRouteIndex =
                clickedIndex;

        selectedDiagramNodeIndex =
                -1;

        /*
         * Keep the selected route visible even if its row was only
         * partially inside the viewport.
         */
        craftscope$ensureRowVisible(
                clickedIndex,
                rowHeight,
                viewportBottom - rowTop,
                true
        );

        cir.setReturnValue(true);
    }

    /*
     * ---------------------------------------------------------
     * Setup -> Required Machines
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderSetupMachines",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSetupMachines(
            GuiGraphics graphics,
            List<CraftScopeProcessRequirement> machines,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        if (machines == null
                || machines.isEmpty()) {

            craftscope$setupMachineMaxScroll =
                    0;

            craftscope$setupMachineScroll =
                    0;

            craftscope$invokeRenderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No machine required"
            );

            return;
        }

        Font font =
                Minecraft.getInstance().font;

        int rowTop =
                top + 28;

        int rowHeight =
                20;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 6
                );

        int viewportHeight =
                viewportBottom - rowTop;

        int contentHeight =
                machines.size()
                        * rowHeight;

        craftscope$setupMachineMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$setupMachineScroll =
                craftscope$clamp(
                        craftscope$setupMachineScroll,
                        craftscope$setupMachineMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                rowTop,
                right - 2,
                viewportBottom
        );

        int startY =
                rowTop
                        - (int) craftscope$setupMachineScroll;

        for (int i = 0;
             i < machines.size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < rowTop
                    || y > viewportBottom) {

                continue;
            }

            CraftScopeProcessRequirement requirement =
                    machines.get(i);

            ItemStack stack =
                    craftscope$invokeGetSummaryRequirementStack(
                            requirement
                    );

            int iconX =
                    left + 9;

            int textX =
                    iconX;

            if (stack != null
                    && !stack.isEmpty()) {

                graphics.renderItem(
                        stack,
                        iconX,
                        y
                );

                textX +=
                        20;
            }

            String amountText =
                    craftscope$invokeFormatSummaryRequirementAmount(
                            requirement
                    );

            String label =
                    requirement
                            .displayName()
                            .getString();

            if (!amountText.isEmpty()) {

                label +=
                        " "
                                + amountText;
            }

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            label,
                            right - textX - 11
                    ),
                    textX,
                    y + 4,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 5,
                rowTop,
                viewportBottom,
                contentHeight,
                craftscope$setupMachineScroll,
                craftscope$setupMachineMaxScroll
        );
    }

    /*
     * ---------------------------------------------------------
     * Setup -> Operating Requirements
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderSetupOperatingRequirements",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSetupRequirements(
            GuiGraphics graphics,
            List<CraftScopeProcessRequirement> requirements,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        if (requirements == null
                || requirements.isEmpty()) {

            craftscope$setupRequirementMaxScroll =
                    0;

            craftscope$setupRequirementScroll =
                    0;

            craftscope$invokeRenderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No special requirements"
            );

            return;
        }

        Font font =
                Minecraft.getInstance().font;

        int rowTop =
                top + 28;

        int rowHeight =
                28;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 6
                );

        int viewportHeight =
                viewportBottom - rowTop;

        int contentHeight =
                requirements.size()
                        * rowHeight;

        craftscope$setupRequirementMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$setupRequirementScroll =
                craftscope$clamp(
                        craftscope$setupRequirementScroll,
                        craftscope$setupRequirementMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                rowTop,
                right - 2,
                viewportBottom
        );

        int startY =
                rowTop
                        - (int) craftscope$setupRequirementScroll;

        for (int i = 0;
             i < requirements.size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < rowTop
                    || y > viewportBottom) {

                continue;
            }

            CraftScopeProcessRequirement requirement =
                    requirements.get(i);

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            craftscope$invokeFormatSetupRequirementKind(
                                    requirement.kind()
                            ),
                            right - left - 20
                    ),
                    left + 9,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            String amount =
                    craftscope$invokeFormatSummaryRequirementAmount(
                            requirement
                    );

            String label =
                    requirement
                            .displayName()
                            .getString();

            if (!amount.isEmpty()) {

                label +=
                        " "
                                + amount;
            }

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            label,
                            right - left - 20
                    ),
                    left + 9,
                    y + 13,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 5,
                rowTop,
                viewportBottom,
                contentHeight,
                craftscope$setupRequirementScroll,
                craftscope$setupRequirementMaxScroll
        );
    }

    /*
     * ---------------------------------------------------------
     * Setup -> Selected Methods
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderSetupSelectedMethods",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSetupMethods(
            GuiGraphics graphics,
            CraftScopeProductionRoute route,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        if (route == null
                || route.steps().isEmpty()) {

            craftscope$setupMethodMaxScroll =
                    0;

            craftscope$setupMethodScroll =
                    0;

            craftscope$invokeRenderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No production steps"
            );

            return;
        }

        Font font =
                Minecraft.getInstance().font;

        int rowTop =
                top + 28;

        int rowHeight =
                28;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 6
                );

        int viewportHeight =
                viewportBottom - rowTop;

        int contentHeight =
                route.steps().size()
                        * rowHeight;

        craftscope$setupMethodMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$setupMethodScroll =
                craftscope$clamp(
                        craftscope$setupMethodScroll,
                        craftscope$setupMethodMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                rowTop,
                right - 2,
                viewportBottom
        );

        int startY =
                rowTop
                        - (int) craftscope$setupMethodScroll;

        for (int i = 0;
             i < route.steps().size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < rowTop
                    || y > viewportBottom) {

                continue;
            }

            var step =
                    route.steps().get(i);

            var method =
                    step.getPrimaryMethod();

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            step.displayName()
                                    .getString(),
                            right - left - 20
                    ),
                    left + 9,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            String methodName =
                    method == null
                            ? "No method"
                            : method
                            .displayName()
                            .getString();

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            methodName,
                            right - left - 20
                    ),
                    left + 9,
                    y + 13,
                    method == null
                            ? CraftScopeUiTheme.TEXT_MUTED
                            : CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 5,
                rowTop,
                viewportBottom,
                contentHeight,
                craftscope$setupMethodScroll,
                craftscope$setupMethodMaxScroll
        );
    }

    /*
     * ---------------------------------------------------------
     * Bottom summary panels
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderProductionSummaryMachines",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSummaryMachines(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        List<CraftScopeProcessRequirement> machines =
                summary.machines();

        if (machines.isEmpty()) {

            craftscope$summaryMachineMaxScroll =
                    0;

            craftscope$summaryMachineScroll =
                    0;

            craftscope$invokeRenderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No machine required"
            );

            return;
        }

        int bodyTop =
                top + 27;

        int bodyBottom =
                Math.max(
                        bodyTop,
                        bottom - 4
                );

        int rowHeight =
                18;

        int viewportHeight =
                bodyBottom - bodyTop;

        int contentHeight =
                machines.size()
                        * rowHeight;

        craftscope$summaryMachineMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$summaryMachineScroll =
                craftscope$clamp(
                        craftscope$summaryMachineScroll,
                        craftscope$summaryMachineMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                bodyTop,
                right - 2,
                bodyBottom
        );

        int startY =
                bodyTop
                        - (int) craftscope$summaryMachineScroll;

        for (int i = 0;
             i < machines.size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < bodyTop
                    || y > bodyBottom) {

                continue;
            }

            craftscope$invokeRenderSummaryRequirementRow(
                    graphics,
                    machines.get(i),
                    left,
                    right - 3,
                    y
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 4,
                bodyTop,
                bodyBottom,
                contentHeight,
                craftscope$summaryMachineScroll,
                craftscope$summaryMachineMaxScroll
        );
    }

    @Inject(
            method = "renderProductionSummaryResources",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSummaryResources(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        List<CraftScopeResourceAmount> resources =
                summary.resources();

        if (resources.isEmpty()) {

            craftscope$summaryResourceMaxScroll =
                    0;

            craftscope$summaryResourceScroll =
                    0;

            craftscope$invokeRenderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No external resources"
            );

            return;
        }

        int bodyTop =
                top + 27;

        int bodyBottom =
                Math.max(
                        bodyTop,
                        bottom - 4
                );

        int rowHeight =
                18;

        int viewportHeight =
                bodyBottom - bodyTop;

        int contentHeight =
                resources.size()
                        * rowHeight;

        craftscope$summaryResourceMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$summaryResourceScroll =
                craftscope$clamp(
                        craftscope$summaryResourceScroll,
                        craftscope$summaryResourceMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                bodyTop,
                right - 2,
                bodyBottom
        );

        int startY =
                bodyTop
                        - (int) craftscope$summaryResourceScroll;

        for (int i = 0;
             i < resources.size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < bodyTop
                    || y > bodyBottom) {

                continue;
            }

            craftscope$invokeRenderSummaryResourceRow(
                    graphics,
                    resources.get(i),
                    left,
                    right - 3,
                    y
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 4,
                bodyTop,
                bodyBottom,
                contentHeight,
                craftscope$summaryResourceScroll,
                craftscope$summaryResourceMaxScroll
        );
    }

    @Inject(
            method = "renderProductionSummaryOutputs",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableSummaryOutputs(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        List<CraftScopeResourceAmount> outputs =
                summary.outputs();

        if (outputs.isEmpty()) {

            craftscope$summaryOutputMaxScroll =
                    0;

            craftscope$summaryOutputScroll =
                    0;

            craftscope$invokeRenderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No output data"
            );

            return;
        }

        int bodyTop =
                top + 27;

        int bodyBottom =
                Math.max(
                        bodyTop,
                        bottom - 4
                );

        int rowHeight =
                18;

        int viewportHeight =
                bodyBottom - bodyTop;

        int contentHeight =
                outputs.size()
                        * rowHeight;

        craftscope$summaryOutputMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$summaryOutputScroll =
                craftscope$clamp(
                        craftscope$summaryOutputScroll,
                        craftscope$summaryOutputMaxScroll
                );

        graphics.enableScissor(
                left + 2,
                bodyTop,
                right - 2,
                bodyBottom
        );

        int startY =
                bodyTop
                        - (int) craftscope$summaryOutputScroll;

        for (int i = 0;
             i < outputs.size();
             i++) {

            int y =
                    startY
                            + i * rowHeight;

            if (y + rowHeight < bodyTop
                    || y > bodyBottom) {

                continue;
            }

            craftscope$invokeRenderSummaryResourceRow(
                    graphics,
                    outputs.get(i),
                    left,
                    right - 3,
                    y
            );
        }

        graphics.disableScissor();

        craftscope$drawScrollbar(
                graphics,
                right - 4,
                bodyTop,
                bodyBottom,
                contentHeight,
                craftscope$summaryOutputScroll,
                craftscope$summaryOutputMaxScroll
        );
    }

    /*
     * ---------------------------------------------------------
     * Wheel routing
     * ---------------------------------------------------------
     *
     * Only the panel under the cursor consumes the mouse wheel.
     * This keeps nested scroll areas predictable.
     */

    @Inject(
            method = "mouseScrolled",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$scrollOverflowPanel(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollY == 0.0D
                || craftscope$overflowMode
                == CRAFTSCOPE_MODE_OTHER) {

            return;
        }

        if (craftscope$handleSummaryScroll(
                mouseX,
                mouseY,
                scrollY
        )) {

            cir.setReturnValue(true);
            return;
        }

        if (craftscope$overflowMode
                == CRAFTSCOPE_MODE_PROCESS
                && craftscope$handleProductionRouteScroll(
                mouseX,
                mouseY,
                scrollY
        )) {

            cir.setReturnValue(true);
            return;
        }

        if (craftscope$overflowMode
                == CRAFTSCOPE_MODE_SETUP
                && craftscope$handleSetupScroll(
                mouseX,
                mouseY,
                scrollY
        )) {

            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean craftscope$handleProductionRouteScroll(
            double mouseX,
            double mouseY,
            double scrollY
    ) {
        int left =
                craftscope$getContentLeft();

        int right =
                craftscope$getContentRight();

        int top =
                craftscope$getMainTop();

        int summaryTop =
                craftscope$getSummaryTop();

        int mainBottom =
                summaryTop - 6;

        int availableWidth =
                right - left;

        int leftColumnWidth =
                Math.min(
                        145,
                        Math.max(
                                110,
                                availableWidth / 6
                        )
                );

        int leftHeight =
                mainBottom - top;

        int routesBottom =
                top
                        + Math.max(
                        76,
                        leftHeight * 43 / 100
                );

        int bodyTop =
                top + 25;

        int bodyBottom =
                routesBottom - 4;

        if (!craftscope$isInside(
                mouseX,
                mouseY,
                left + 2,
                bodyTop,
                left + leftColumnWidth - 2,
                bodyBottom
        )) {

            return false;
        }

        if (craftscope$productionRouteMaxScroll <= 0) {
            return false;
        }

        craftscope$productionRouteScroll =
                craftscope$scroll(
                        craftscope$productionRouteScroll,
                        scrollY,
                        craftscope$productionRouteMaxScroll
                );

        return true;
    }

    @Unique
    private boolean craftscope$handleSetupScroll(
            double mouseX,
            double mouseY,
            double scrollY
    ) {
        int left =
                craftscope$getContentLeft();

        int right =
                craftscope$getContentRight();

        int top =
                craftscope$getMainTop();

        int summaryTop =
                craftscope$getSummaryTop();

        int mainBottom =
                summaryTop - 6;

        int gap =
                6;

        int panelsTop =
                top + 60 + gap;

        int bodyTop =
                panelsTop + 23;

        int bodyBottom =
                mainBottom - 3;

        int availableWidth =
                right - left - gap * 2;

        int machinesWidth =
                availableWidth / 3;

        int requirementsWidth =
                availableWidth / 3;

        int machinesLeft =
                left;

        int machinesRight =
                machinesLeft + machinesWidth;

        int requirementsLeft =
                machinesRight + gap;

        int requirementsRight =
                requirementsLeft + requirementsWidth;

        int methodsLeft =
                requirementsRight + gap;

        int methodsRight =
                right;

        if (craftscope$isInside(
                mouseX,
                mouseY,
                machinesLeft,
                bodyTop,
                machinesRight,
                bodyBottom
        )) {

            if (craftscope$setupMachineMaxScroll <= 0) {
                return false;
            }

            craftscope$setupMachineScroll =
                    craftscope$scroll(
                            craftscope$setupMachineScroll,
                            scrollY,
                            craftscope$setupMachineMaxScroll
                    );

            return true;
        }

        if (craftscope$isInside(
                mouseX,
                mouseY,
                requirementsLeft,
                bodyTop,
                requirementsRight,
                bodyBottom
        )) {

            if (craftscope$setupRequirementMaxScroll <= 0) {
                return false;
            }

            craftscope$setupRequirementScroll =
                    craftscope$scroll(
                            craftscope$setupRequirementScroll,
                            scrollY,
                            craftscope$setupRequirementMaxScroll
                    );

            return true;
        }

        if (craftscope$isInside(
                mouseX,
                mouseY,
                methodsLeft,
                bodyTop,
                methodsRight,
                bodyBottom
        )) {

            if (craftscope$setupMethodMaxScroll <= 0) {
                return false;
            }

            craftscope$setupMethodScroll =
                    craftscope$scroll(
                            craftscope$setupMethodScroll,
                            scrollY,
                            craftscope$setupMethodMaxScroll
                    );

            return true;
        }

        return false;
    }

    @Unique
    private boolean craftscope$handleSummaryScroll(
            double mouseX,
            double mouseY,
            double scrollY
    ) {
        int left =
                craftscope$getContentLeft();

        int right =
                craftscope$getContentRight();

        int top =
                craftscope$getSummaryTop();

        int bottom =
                top
                        + CRAFTSCOPE_SUMMARY_HEIGHT;

        int bodyTop =
                top + 23;

        int gap =
                6;

        int totalWidth =
                right - left - gap * 2;

        int machinesWidth =
                totalWidth * 36 / 100;

        int resourcesWidth =
                totalWidth * 36 / 100;

        int machinesLeft =
                left;

        int machinesRight =
                machinesLeft + machinesWidth;

        int resourcesLeft =
                machinesRight + gap;

        int resourcesRight =
                resourcesLeft + resourcesWidth;

        int outputsLeft =
                resourcesRight + gap;

        int outputsRight =
                right;

        if (craftscope$isInside(
                mouseX,
                mouseY,
                machinesLeft,
                bodyTop,
                machinesRight,
                bottom
        )) {

            if (craftscope$summaryMachineMaxScroll <= 0) {
                return false;
            }

            craftscope$summaryMachineScroll =
                    craftscope$scroll(
                            craftscope$summaryMachineScroll,
                            scrollY,
                            craftscope$summaryMachineMaxScroll
                    );

            return true;
        }

        if (craftscope$isInside(
                mouseX,
                mouseY,
                resourcesLeft,
                bodyTop,
                resourcesRight,
                bottom
        )) {

            if (craftscope$summaryResourceMaxScroll <= 0) {
                return false;
            }

            craftscope$summaryResourceScroll =
                    craftscope$scroll(
                            craftscope$summaryResourceScroll,
                            scrollY,
                            craftscope$summaryResourceMaxScroll
                    );

            return true;
        }

        if (craftscope$isInside(
                mouseX,
                mouseY,
                outputsLeft,
                bodyTop,
                outputsRight,
                bottom
        )) {

            if (craftscope$summaryOutputMaxScroll <= 0) {
                return false;
            }

            craftscope$summaryOutputScroll =
                    craftscope$scroll(
                            craftscope$summaryOutputScroll,
                            scrollY,
                            craftscope$summaryOutputMaxScroll
                    );

            return true;
        }

        return false;
    }

    /*
     * ---------------------------------------------------------
     * Geometry helpers
     * ---------------------------------------------------------
     */

    @Unique
    private int craftscope$getContentLeft() {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        return screen.craftscope$getWindowLeft()
                + CRAFTSCOPE_CONTENT_SIDE_MARGIN;
    }

    @Unique
    private int craftscope$getContentRight() {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        return screen.craftscope$getWindowRight()
                - CRAFTSCOPE_CONTENT_SIDE_MARGIN;
    }

    @Unique
    private int craftscope$getMainTop() {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        return screen.craftscope$getWindowTop()
                + CRAFTSCOPE_MAIN_TOP_OFFSET;
    }

    @Unique
    private int craftscope$getSummaryTop() {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        return screen.craftscope$getWindowBottom()
                - CRAFTSCOPE_CONTENT_BOTTOM_MARGIN
                - CRAFTSCOPE_SUMMARY_HEIGHT;
    }

    /*
     * ---------------------------------------------------------
     * Generic scroll helpers
     * ---------------------------------------------------------
     */

    @Unique
    private int craftscope$maxScroll(
            int contentHeight,
            int viewportHeight
    ) {
        return Math.max(
                0,
                contentHeight
                        - Math.max(
                        0,
                        viewportHeight
                )
        );
    }

    @Unique
    private double craftscope$clamp(
            double scroll,
            int maxScroll
    ) {
        return Math.max(
                0.0D,
                Math.min(
                        scroll,
                        Math.max(
                                0,
                                maxScroll
                        )
                )
        );
    }

    @Unique
    private double craftscope$scroll(
            double current,
            double wheelDelta,
            int maxScroll
    ) {
        return craftscope$clamp(
                current
                        - wheelDelta
                        * CRAFTSCOPE_SCROLL_AMOUNT,
                maxScroll
        );
    }

    @Unique
    private boolean craftscope$isInside(
            double mouseX,
            double mouseY,
            int left,
            int top,
            int right,
            int bottom
    ) {
        return mouseX >= left
                && mouseX < right
                && mouseY >= top
                && mouseY < bottom;
    }

    @Unique
    private void craftscope$drawScrollbar(
            GuiGraphics graphics,
            int barX,
            int viewportTop,
            int viewportBottom,
            int contentHeight,
            double scroll,
            int maxScroll
    ) {
        int viewportHeight =
                viewportBottom
                        - viewportTop;

        if (viewportHeight <= 0
                || contentHeight <= viewportHeight
                || maxScroll <= 0) {

            return;
        }

        graphics.fill(
                barX,
                viewportTop,
                barX + 2,
                viewportBottom,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        int thumbHeight =
                Math.max(
                        10,
                        viewportHeight
                                * viewportHeight
                                / contentHeight
                );

        thumbHeight =
                Math.min(
                        viewportHeight,
                        thumbHeight
                );

        int travel =
                Math.max(
                        0,
                        viewportHeight
                                - thumbHeight
                );

        int thumbOffset =
                (int) (
                        craftscope$clamp(
                                scroll,
                                maxScroll
                        )
                                / maxScroll
                                * travel
                );

        graphics.fill(
                barX,
                viewportTop + thumbOffset,
                barX + 2,
                viewportTop
                        + thumbOffset
                        + thumbHeight,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    @Unique
    private void craftscope$ensureRowVisible(
            int rowIndex,
            int rowHeight,
            int viewportHeight,
            boolean productionRoutesPanel
    ) {
        if (!productionRoutesPanel
                || rowIndex < 0
                || rowHeight <= 0
                || viewportHeight <= 0) {

            return;
        }

        double current =
                craftscope$productionRouteScroll;

        int rowTop =
                rowIndex
                        * rowHeight;

        int rowBottom =
                rowTop
                        + rowHeight;

        if (rowTop < current) {

            current =
                    rowTop;

        } else if (rowBottom
                > current
                + viewportHeight) {

            current =
                    rowBottom
                            - viewportHeight;
        }

        craftscope$productionRouteScroll =
                craftscope$clamp(
                        current,
                        craftscope$productionRouteMaxScroll
                );
    }
}
