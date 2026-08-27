package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Completes the first CraftScope overflow-scrolling UI pass for
 * the Process Diagram detail pane.
 *
 * Global UI rule:
 *
 * When useful information exceeds the available panel space,
 * preserve it and make the panel scrollable instead of clipping,
 * truncating the list, or replacing entries with "+N more".
 *
 * This mixin covers:
 *
 * - Selected Resource details
 *   - all accepted variants are visible by scrolling
 *
 * - Selected Process details
 *   - every available process method remains selectable
 *   - every machine requirement remains visible
 *   - inputs / outputs / recipe counts remain reachable
 *
 * The View Recipe button stays pinned at the bottom because it is
 * an action, not detail content.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeScrollableDetails {

    @Unique
    private static final int CRAFTSCOPE_SCROLL_AMOUNT = 20;

    @Unique
    private static final int CRAFTSCOPE_CONTENT_SIDE_MARGIN = 10;

    @Unique
    private static final int CRAFTSCOPE_CONTENT_BOTTOM_MARGIN = 10;

    @Unique
    private static final int CRAFTSCOPE_MAIN_TOP_OFFSET = 93;

    @Unique
    private static final int CRAFTSCOPE_SUMMARY_HEIGHT = 74;

    @Unique
    private static final int CRAFTSCOPE_MAIN_GAP = 6;

    @Unique
    private double craftscope$detailScroll;

    @Unique
    private int craftscope$detailMaxScroll;

    @Unique
    private boolean craftscope$processDiagramActive;

    @Unique
    private String craftscope$detailIdentity = "";

    @Shadow
    @Final
    private CraftScopeProject project;

    @Shadow
    private int selectedDiagramNodeIndex;

    @Invoker("getSelectedProductionRoute")
    protected abstract CraftScopeProductionRoute
    craftscope$invokeGetSelectedProductionRoute();

    @Invoker("getSelectedMethodIndex")
    protected abstract int craftscope$invokeGetSelectedMethodIndex(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step
    );

    @Invoker("getSelectedMethod")
    protected abstract CraftScopeProductionMethod craftscope$invokeGetSelectedMethod(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step
    );

    @Invoker("selectMethod")
    protected abstract void craftscope$invokeSelectMethod(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step,
            int methodIndex
    );

    @Invoker("fitText")
    protected abstract String craftscope$invokeFitText(
            String text,
            int maxWidth
    );

    @Invoker("formatResourceKind")
    protected abstract String craftscope$invokeFormatResourceKind(
            CraftScopeResourceAmount resource
    );

    @Invoker("renderDetailItem")
    protected abstract void craftscope$invokeRenderDetailItem(
            GuiGraphics graphics,
            ItemStack stack,
            int centerX,
            int y
    );

    @Invoker("renderViewRecipeButton")
    protected abstract void craftscope$invokeRenderViewRecipeButton(
            GuiGraphics graphics,
            CraftScopeProductionMethod method,
            int detailsLeft,
            int detailsRight,
            int bottom
    );

    /*
     * ---------------------------------------------------------
     * Active-view tracking
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderProcessDiagram",
            at = @At("HEAD")
    )
    private void craftscope$markProcessDiagram(
            GuiGraphics graphics,
            CallbackInfo ci
    ) {
        craftscope$processDiagramActive =
                true;

        craftscope$refreshDetailIdentity();
    }

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
        craftscope$processDiagramActive =
                false;
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
        craftscope$processDiagramActive =
                false;
    }

    @Inject(
            method = "renderSetup",
            at = @At("HEAD")
    )
    private void craftscope$markSetup(
            GuiGraphics graphics,
            CallbackInfo ci
    ) {
        craftscope$processDiagramActive =
                false;
    }

    @Inject(
            method = "craftscope$setTargetItem",
            at = @At("HEAD")
    )
    private void craftscope$resetDetailScrollForNewTarget(
            ItemStack stack,
            CallbackInfo ci
    ) {
        craftscope$detailScroll =
                0.0D;

        craftscope$detailMaxScroll =
                0;

        craftscope$detailIdentity =
                "";
    }

    /*
     * ---------------------------------------------------------
     * Selected Resource
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderResourceSelectionDetails",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableResourceDetails(
            GuiGraphics graphics,
            CraftScopeProcessDiagramRenderer.Selection selection,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        if (selection == null
                || selection.resource() == null) {

            return;
        }

        craftscope$refreshDetailIdentity();

        Font font =
                Minecraft.getInstance().font;

        CraftScopeResourceAmount resource =
                selection.resource();

        int bodyTop =
                top + 23;

        int bodyBottom =
                Math.max(
                        bodyTop,
                        bottom - 5
                );

        int viewportHeight =
                bodyBottom - bodyTop;

        int variantCount =
                resource.hasVariants()
                        ? resource
                        .acceptedVariantIds()
                        .size()
                        : 0;

        int contentHeight =
                168
                        + (
                        variantCount > 0
                                ? 18
                                + variantCount * 13
                                : 0
                );

        craftscope$detailMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$detailScroll =
                craftscope$clamp(
                        craftscope$detailScroll,
                        craftscope$detailMaxScroll
                );

        graphics.enableScissor(
                detailsLeft + 2,
                bodyTop,
                detailsRight - 2,
                bodyBottom
        );

        int offset =
                -(int) craftscope$detailScroll;

        int centerX =
                (detailsLeft + detailsRight) / 2;

        ItemStack displayStack =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayStack(
                                selection
                        );

        if (!displayStack.isEmpty()) {

            craftscope$invokeRenderDetailItem(
                    graphics,
                    displayStack,
                    centerX,
                    top + 31 + offset
            );
        }

        String displayName =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayName(
                                selection
                        );

        graphics.drawCenteredString(
                font,
                craftscope$invokeFitText(
                        displayName,
                        detailsRight
                                - detailsLeft
                                - 18
                ),
                centerX,
                top + 67 + offset,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        int textX =
                detailsLeft + 9;

        int y =
                top + 88 + offset;

        graphics.drawString(
                font,
                "Required",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y +=
                14;

        String amountText =
                resource.hasUnit()
                        ? selection.amount()
                        + " "
                        + resource.unit()
                        : "x"
                        + selection.amount();

        graphics.drawString(
                font,
                amountText,
                textX,
                y,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        y +=
                22;

        graphics.drawString(
                font,
                "Type",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y +=
                14;

        graphics.drawString(
                font,
                craftscope$invokeFormatResourceKind(
                        resource
                ),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y +=
                22;

        graphics.drawString(
                font,
                "Consumed: "
                        + (
                        resource.consumed()
                                ? "Yes"
                                : "No"
                ),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y +=
                18;

        if (resource.hasVariants()) {

            graphics.drawString(
                    font,
                    "Variants: "
                            + resource
                            .acceptedVariantIds()
                            .size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            y +=
                    15;

            for (ResourceLocation variant :
                    resource.acceptedVariantIds()) {

                graphics.drawString(
                        font,
                        craftscope$invokeFitText(
                                "• "
                                        + variant
                                        .getPath(),
                                detailsRight
                                        - textX
                                        - 11
                        ),
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_MUTED
                );

                y +=
                        13;
            }
        }

        graphics.disableScissor();

        craftscope$drawDetailScrollbar(
                graphics,
                detailsRight - 4,
                bodyTop,
                bodyBottom,
                contentHeight,
                craftscope$detailScroll,
                craftscope$detailMaxScroll
        );
    }

    /*
     * ---------------------------------------------------------
     * Selected Process
     * ---------------------------------------------------------
     */

    @Inject(
            method = "renderProcessSelectionDetails",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderScrollableProcessDetails(
            GuiGraphics graphics,
            CraftScopeProcessDiagramRenderer.Selection selection,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom,
            CallbackInfo ci
    ) {
        ci.cancel();

        CraftScopeProductionRoute route =
                craftscope$invokeGetSelectedProductionRoute();

        CraftScopeProductionStep step =
                selection == null
                        ? null
                        : selection.step();

        if (route == null
                || step == null) {

            return;
        }

        craftscope$refreshDetailIdentity();

        Font font =
                Minecraft.getInstance().font;

        int centerX =
                (detailsLeft + detailsRight) / 2;

        CraftScopeProductionMethod selectedMethod =
                craftscope$invokeGetSelectedMethod(
                        route,
                        step
                );

        boolean hasViewRecipe =
                selectedMethod != null
                        && selectedMethod.hasRecipes();

        int bodyTop =
                top + 23;

        int bodyBottom =
                hasViewRecipe
                        ? bottom - 34
                        : bottom - 5;

        bodyBottom =
                Math.max(
                        bodyTop,
                        bodyBottom
                );

        int viewportHeight =
                bodyBottom - bodyTop;

        int contentHeight =
                craftscope$getProcessDetailContentHeight(
                        step,
                        selectedMethod
                );

        craftscope$detailMaxScroll =
                craftscope$maxScroll(
                        contentHeight,
                        viewportHeight
                );

        craftscope$detailScroll =
                craftscope$clamp(
                        craftscope$detailScroll,
                        craftscope$detailMaxScroll
                );

        CraftScopeProductionStep displayStep =
                step;

        if (selectedMethod != null) {

            displayStep =
                    new CraftScopeProductionStep(
                            step.id(),
                            step.displayName(),
                            step.inputs(),
                            step.outputs(),
                            java.util.List.of(
                                    selectedMethod
                            )
                    );
        }

        CraftScopeProcessDiagramRenderer.Selection displaySelection =
                new CraftScopeProcessDiagramRenderer.Selection(
                        selection.nodeIndex(),
                        selection.kind(),
                        selection.resource(),
                        displayStep,
                        selection.amount(),
                        selection.extraCount()
                );

        graphics.enableScissor(
                detailsLeft + 2,
                bodyTop,
                detailsRight - 2,
                bodyBottom
        );

        int offset =
                -(int) craftscope$detailScroll;

        ItemStack displayStack =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayStack(
                                displaySelection
                        );

        if (!displayStack.isEmpty()) {

            craftscope$invokeRenderDetailItem(
                    graphics,
                    displayStack,
                    centerX,
                    top + 31 + offset
            );
        }

        graphics.drawCenteredString(
                font,
                craftscope$invokeFitText(
                        step
                                .displayName()
                                .getString(),
                        detailsRight
                                - detailsLeft
                                - 18
                ),
                centerX,
                top + 67 + offset,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        int textX =
                detailsLeft + 9;

        int methodWidth =
                detailsRight
                        - detailsLeft
                        - 18;

        int y =
                top + 88 + offset;

        graphics.drawString(
                font,
                "Method",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y +=
                14;

        int methodRowHeight =
                18;

        int selectedMethodIndex =
                craftscope$invokeGetSelectedMethodIndex(
                        route,
                        step
                );

        for (int i = 0;
             i < step.methods().size();
             i++) {

            CraftScopeProductionMethod method =
                    step.methods().get(i);

            boolean selected =
                    i == selectedMethodIndex;

            if (selected) {

                graphics.fill(
                        textX,
                        y,
                        textX + methodWidth,
                        y + methodRowHeight,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

                CraftScopeUiTheme.drawBorder(
                        graphics,
                        textX,
                        y,
                        textX + methodWidth,
                        y + methodRowHeight,
                        CraftScopeUiTheme.ACCENT
                );
            }

            graphics.drawString(
                    font,
                    craftscope$invokeFitText(
                            method
                                    .displayName()
                                    .getString(),
                            methodWidth - 12
                    ),
                    textX + 6,
                    y + 5,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );

            y +=
                    methodRowHeight + 3;
        }

        y +=
                4;

        if (selectedMethod != null) {

            graphics.drawString(
                    font,
                    "Machine",
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            y +=
                    14;

            boolean foundMachine =
                    false;

            for (CraftScopeProcessRequirement requirement :
                    selectedMethod.requirements()) {

                if (requirement.kind()
                        != CraftScopeRequirementKind.MACHINE) {

                    continue;
                }

                foundMachine =
                        true;

                graphics.drawString(
                        font,
                        craftscope$invokeFitText(
                                "• "
                                        + requirement
                                        .displayName()
                                        .getString(),
                                detailsRight
                                        - textX
                                        - 11
                        ),
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_SECONDARY
                );

                y +=
                        14;
            }

            if (!foundMachine) {

                graphics.drawString(
                        font,
                        "None required",
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_MUTED
                );

                y +=
                        14;
            }
        }

        y +=
                5;

        graphics.drawString(
                font,
                "Inputs: "
                        + step
                        .inputs()
                        .size(),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y +=
                14;

        graphics.drawString(
                font,
                "Outputs: "
                        + step
                        .outputs()
                        .size(),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y +=
                14;

        if (selectedMethod != null
                && selectedMethod.hasRecipes()) {

            graphics.drawString(
                    font,
                    "Recipes: "
                            + selectedMethod
                            .recipeIds()
                            .size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        graphics.disableScissor();

        craftscope$drawDetailScrollbar(
                graphics,
                detailsRight - 4,
                bodyTop,
                bodyBottom,
                contentHeight,
                craftscope$detailScroll,
                craftscope$detailMaxScroll
        );

        /*
         * Keep the action fixed in place while only the detail
         * content scrolls.
         */
        if (hasViewRecipe) {

            craftscope$invokeRenderViewRecipeButton(
                    graphics,
                    selectedMethod,
                    detailsLeft,
                    detailsRight,
                    bottom
            );
        }
    }

    /*
     * Existing method-click logic assumes the method rows never
     * move. Once the detail pane scrolls, map clicks through the
     * current scroll offset before the original handler runs.
     */
    @Inject(
            method = "handleProcessMethodClick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$handleScrollableProcessMethodClick(
            double mouseX,
            double mouseY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!craftscope$processDiagramActive) {
            return;
        }

        CraftScopeProductionRoute route =
                craftscope$invokeGetSelectedProductionRoute();

        if (route == null) {
            return;
        }

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.getSelection(
                        route,
                        project.getTargetCount(),
                        selectedDiagramNodeIndex
                );

        if (selection == null
                || !selection.isProcess()
                || selection.step() == null) {

            return;
        }

        CraftScopeProductionStep step =
                selection.step();

        if (step.methods().size() <= 1) {
            return;
        }

        int detailsLeft =
                craftscope$getDetailsLeft();

        int detailsRight =
                craftscope$getContentRight();

        int top =
                craftscope$getMainTop();

        CraftScopeProductionMethod selectedMethod =
                craftscope$invokeGetSelectedMethod(
                        route,
                        step
                );

        boolean hasViewRecipe =
                selectedMethod != null
                        && selectedMethod.hasRecipes();

        int bodyTop =
                top + 23;

        int bodyBottom =
                hasViewRecipe
                        ? craftscope$getMainBottom()
                        - 34
                        : craftscope$getMainBottom()
                        - 5;

        if (mouseX < detailsLeft + 9
                || mouseX >= detailsRight - 9
                || mouseY < bodyTop
                || mouseY >= bodyBottom) {

            return;
        }

        double unscrolledY =
                mouseY
                        + craftscope$detailScroll;

        int firstMethodY =
                top + 102;

        if (unscrolledY < firstMethodY) {
            if (craftscope$detailScroll > 0.0D) {
                cir.setReturnValue(
                        false
                );
            }

            return;
        }

        int rowStride =
                21;

        int relative =
                (int) (
                        unscrolledY
                                - firstMethodY
                );

        int methodIndex =
                relative
                        / rowStride;

        int withinRow =
                relative
                        % rowStride;

        if (methodIndex < 0
                || methodIndex >= step.methods().size()
                || withinRow >= 18) {

            /*
             * Once the detail body has moved, the original
             * CraftScope handler's fixed row coordinates are no
             * longer valid. Cancel only that old method handler
             * while still returning false to the screen's outer
             * click chain.
             */
            if (craftscope$detailScroll > 0.0D) {
                cir.setReturnValue(
                        false
                );
            }

            return;
        }

        craftscope$invokeSelectMethod(
                route,
                step,
                methodIndex
        );

        cir.setReturnValue(
                true
        );
    }

    /*
     * ---------------------------------------------------------
     * Wheel routing
     * ---------------------------------------------------------
     */

    @Inject(
            method = "mouseScrolled",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$scrollProcessDetails(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!craftscope$processDiagramActive
                || scrollY == 0.0D
                || craftscope$detailMaxScroll <= 0) {

            return;
        }

        int detailsLeft =
                craftscope$getDetailsLeft();

        int detailsRight =
                craftscope$getContentRight();

        int top =
                craftscope$getMainTop();

        int bottom =
                craftscope$getMainBottom();

        if (!craftscope$isInside(
                mouseX,
                mouseY,
                detailsLeft + 1,
                top + 23,
                detailsRight - 1,
                bottom
        )) {

            return;
        }

        craftscope$detailScroll =
                craftscope$clamp(
                        craftscope$detailScroll
                                - scrollY
                                * CRAFTSCOPE_SCROLL_AMOUNT,
                        craftscope$detailMaxScroll
                );

        cir.setReturnValue(
                true
        );
    }

    /*
     * ---------------------------------------------------------
     * Detail identity / reset behavior
     * ---------------------------------------------------------
     */

    @Unique
    private void craftscope$refreshDetailIdentity() {
        CraftScopeProductionRoute route =
                craftscope$invokeGetSelectedProductionRoute();

        String identity;

        if (route == null) {

            identity =
                    "none";

        } else {

            identity =
                    route.id()
                            + "|"
                            + selectedDiagramNodeIndex;
        }

        if (!identity.equals(
                craftscope$detailIdentity
        )) {

            craftscope$detailIdentity =
                    identity;

            craftscope$detailScroll =
                    0.0D;

            craftscope$detailMaxScroll =
                    0;
        }
    }

    /*
     * ---------------------------------------------------------
     * Content sizing
     * ---------------------------------------------------------
     */

    @Unique
    private int craftscope$getProcessDetailContentHeight(
            CraftScopeProductionStep step,
            CraftScopeProductionMethod selectedMethod
    ) {
        /*
         * Content begins at top + 31 while the viewport begins at
         * top + 23, leaving the same padding as the original pane.
         */
        int height =
                79;

        height +=
                14;

        height +=
                step.methods().size()
                        * 21;

        height +=
                4;

        if (selectedMethod != null) {

            height +=
                    14;

            int machineCount =
                    0;

            for (CraftScopeProcessRequirement requirement :
                    selectedMethod.requirements()) {

                if (requirement.kind()
                        == CraftScopeRequirementKind.MACHINE) {

                    machineCount++;
                }
            }

            height +=
                    Math.max(
                            1,
                            machineCount
                    )
                            * 14;
        }

        height +=
                5;

        height +=
                14;

        height +=
                14;

        if (selectedMethod != null
                && selectedMethod.hasRecipes()) {

            height +=
                    14;
        }

        /*
         * Bottom padding keeps the final line from touching the
         * scissor boundary.
         */
        height +=
                8;

        return height;
    }

    /*
     * ---------------------------------------------------------
     * Process layout geometry
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

    @Unique
    private int craftscope$getMainBottom() {
        return craftscope$getSummaryTop()
                - CRAFTSCOPE_MAIN_GAP;
    }

    @Unique
    private int craftscope$getDetailsLeft() {
        int left =
                craftscope$getContentLeft();

        int right =
                craftscope$getContentRight();

        int availableWidth =
                right - left;

        int gap =
                CRAFTSCOPE_MAIN_GAP;

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

        return centerRight
                + gap;
    }

    /*
     * ---------------------------------------------------------
     * Generic scrolling helpers
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
    private void craftscope$drawDetailScrollbar(
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
}
