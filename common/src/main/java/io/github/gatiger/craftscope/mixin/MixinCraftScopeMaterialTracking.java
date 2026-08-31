package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.material.CraftScopeMaterialTreePlan;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.storage.CraftScopeStorageRegistry;
import io.github.gatiger.craftscope.storage.CraftScopeStorageSnapshot;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeMaterialTracking {

    @Shadow
    private CraftScopeRecipeTree currentTree;

    @Unique
    private static final int craftscope$ROW_HEIGHT = 22;

    @Unique
    private static final int craftscope$COLUMN_HEADER_HEIGHT = 18;

    @Unique
    private static final int craftscope$BUTTON_WIDTH = 100;

    @Unique
    private static final int craftscope$BUTTON_HEIGHT = 18;

    @Unique
    private static final int craftscope$INDENT = 16;

    @Unique
    private CraftScopeStorageSnapshot craftscope$storageSnapshot =
            CraftScopeStorageSnapshot.notScanned();

    @Unique
    private final Set<String> craftscope$expandedMaterialPaths =
            new HashSet<>();

    @Unique
    private final List<CraftScopeMaterialRowHit> craftscope$rowHits =
            new ArrayList<>();

    @Unique
    private boolean craftscope$totalMaterialsVisible;

    @Unique
    private double craftscope$materialTreeScroll;

    @Unique
    private int craftscope$lastContentHeight;

    @Unique
    private int craftscope$lastViewportHeight;

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void craftscope$beginFrame(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        craftscope$totalMaterialsVisible =
                false;

        craftscope$rowHits.clear();
    }

    @Inject(
            method = "renderTotalMaterials",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$renderExpandableMaterials(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        craftscope$totalMaterialsVisible =
                true;

        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        Font font =
                Minecraft.getInstance().font;

        int contentLeft =
                screen.craftscope$getWindowLeft()
                        + 10;

        int contentRight =
                screen.craftscope$getWindowRight()
                        - 10;

        int panelTop =
                screen.craftscope$getWindowTop()
                        + 93;

        int sectionBottom =
                screen.craftscope$getWindowTop()
                        + 116;

        int columnHeaderTop =
                sectionBottom;

        int rowsTop =
                columnHeaderTop
                        + craftscope$COLUMN_HEADER_HEIGHT;

        int panelBottom =
                screen.craftscope$getWindowBottom()
                        - 10;

        CraftScopeUiTheme.drawPanel(
                graphics,
                contentLeft,
                panelTop,
                contentRight,
                panelBottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                contentLeft + 1,
                panelTop + 1,
                contentRight - 1,
                sectionBottom
        );

        graphics.drawCenteredString(
                font,
                "Total Materials",
                (contentLeft + contentRight) / 2,
                panelTop + 7,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        craftscope$renderScanButton(
                graphics,
                screen,
                mouseX,
                mouseY,
                panelTop
        );

        if (craftscope$storageSnapshot.isScanned()) {
            String status =
                    "Inventory: "
                            + craftscope$storageSnapshot.totalItemCount()
                            + " items";

            graphics.drawString(
                    font,
                    status,
                    contentLeft + 10,
                    panelTop + 7,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        } else {
            graphics.drawString(
                    font,
                    "Inventory not scanned",
                    contentLeft + 10,
                    panelTop + 7,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }

        graphics.fill(
                contentLeft + 1,
                columnHeaderTop,
                contentRight - 1,
                rowsTop,
                CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

        int[] columns =
                craftscope$getColumns(
                        contentLeft,
                        contentRight
                );

        graphics.drawString(
                font,
                "Material",
                contentLeft + 10,
                columnHeaderTop + 5,
                CraftScopeUiTheme.TEXT_MUTED
        );

        craftscope$drawRightAligned(
                graphics,
                font,
                "Required",
                columns[0],
                columnHeaderTop + 5,
                CraftScopeUiTheme.TEXT_MUTED
        );

        craftscope$drawRightAligned(
                graphics,
                font,
                "Owned",
                columns[1],
                columnHeaderTop + 5,
                CraftScopeUiTheme.TEXT_MUTED
        );

        craftscope$drawRightAligned(
                graphics,
                font,
                "Missing",
                columns[2],
                columnHeaderTop + 5,
                CraftScopeUiTheme.TEXT_MUTED
        );

        CraftScopeMaterialTreePlan.Result plan =
                CraftScopeMaterialTreePlan.build(
                        currentTree,
                        craftscope$storageSnapshot,
                        craftscope$expandedMaterialPaths
                );

        if (plan.rows().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    "No materials to display.",
                    (contentLeft + contentRight) / 2,
                    rowsTop + 14,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            craftscope$lastContentHeight =
                    0;

            craftscope$lastViewportHeight =
                    Math.max(
                            0,
                            panelBottom - rowsTop - 1
                    );

            craftscope$materialTreeScroll =
                    0.0D;

            ci.cancel();
            return;
        }

        int viewportBottom =
                panelBottom - 1;

        int viewportHeight =
                Math.max(
                        0,
                        viewportBottom - rowsTop
                );

        int contentHeight =
                plan.rows().size()
                        * craftscope$ROW_HEIGHT;

        craftscope$lastContentHeight =
                contentHeight;

        craftscope$lastViewportHeight =
                viewportHeight;

        craftscope$clampScroll();

        graphics.enableScissor(
                contentLeft + 1,
                rowsTop,
                contentRight - 1,
                viewportBottom
        );

        int rowY =
                rowsTop
                        - (int) craftscope$materialTreeScroll;

        ItemStack tooltipStack =
                ItemStack.EMPTY;

        for (CraftScopeMaterialTreePlan.Row row :
                plan.rows()) {

            if (rowY + craftscope$ROW_HEIGHT >= rowsTop
                    && rowY <= viewportBottom) {

                ItemStack hovered =
                        craftscope$renderMaterialRow(
                                graphics,
                                font,
                                row,
                                contentLeft,
                                contentRight,
                                rowY,
                                mouseX,
                                mouseY,
                                rowsTop,
                                viewportBottom,
                                columns,
                                plan.scanned()
                        );

                if (!hovered.isEmpty()) {
                    tooltipStack =
                            hovered;
                }
            }

            rowY +=
                    craftscope$ROW_HEIGHT;
        }

        graphics.disableScissor();

        craftscope$renderScrollbar(
                graphics,
                contentRight,
                rowsTop,
                viewportBottom,
                viewportHeight,
                contentHeight
        );

        if (!tooltipStack.isEmpty()) {
            graphics.renderTooltip(
                    font,
                    tooltipStack,
                    mouseX,
                    mouseY
            );
        }

        ci.cancel();
    }

    @Inject(
            method = "mouseClicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$handleMaterialClick(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!craftscope$totalMaterialsVisible
                || button != 0) {

            return;
        }

        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        int panelTop =
                screen.craftscope$getWindowTop()
                        + 93;

        int scanX =
                craftscope$getScanButtonX(
                        screen
                );

        int scanY =
                panelTop + 2;

        if (mouseX >= scanX
                && mouseX < scanX + craftscope$BUTTON_WIDTH
                && mouseY >= scanY
                && mouseY < scanY + craftscope$BUTTON_HEIGHT) {

            craftscope$storageSnapshot =
                    CraftScopeStorageRegistry.capture(
                            Minecraft.getInstance()
                    );

            craftscope$materialTreeScroll =
                    0.0D;

            cir.setReturnValue(
                    true
            );

            return;
        }

        for (CraftScopeMaterialRowHit hit :
                craftscope$rowHits) {

            if (!hit.expandable()) {
                continue;
            }

            if (mouseX < hit.left()
                    || mouseX >= hit.right()
                    || mouseY < hit.top()
                    || mouseY >= hit.bottom()) {

                continue;
            }

            if (craftscope$expandedMaterialPaths.contains(
                    hit.path()
            )) {

                craftscope$expandedMaterialPaths.remove(
                        hit.path()
                );

            } else {

                craftscope$expandedMaterialPaths.add(
                        hit.path()
                );
            }

            craftscope$clampScroll();

            cir.setReturnValue(
                    true
            );

            return;
        }
    }

    @Inject(
            method = "mouseScrolled",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$scrollMaterialTree(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!craftscope$totalMaterialsVisible
                || craftscope$lastContentHeight
                <= craftscope$lastViewportHeight) {

            return;
        }

        craftscope$materialTreeScroll -=
                scrollY
                        * craftscope$ROW_HEIGHT;

        craftscope$clampScroll();

        cir.setReturnValue(
                true
        );
    }

    @Inject(
            method = "rebuildTree",
            at = @At("TAIL")
    )
    private void craftscope$resetMaterialTree(
            CallbackInfo ci
    ) {
        craftscope$expandedMaterialPaths.clear();

        craftscope$materialTreeScroll =
                0.0D;
    }

    @Unique
    private ItemStack craftscope$renderMaterialRow(
            GuiGraphics graphics,
            Font font,
            CraftScopeMaterialTreePlan.Row row,
            int contentLeft,
            int contentRight,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom,
            int[] columns,
            boolean scanned
    ) {
        boolean hovered =
                mouseX >= contentLeft + 2
                        && mouseX < contentRight - 2
                        && mouseY >= rowY
                        && mouseY < rowY + craftscope$ROW_HEIGHT
                        && mouseY >= viewportTop
                        && mouseY < viewportBottom;

        int background =
                hovered
                        ? CraftScopeUiTheme.PANEL_BACKGROUND_ALT
                        : CraftScopeUiTheme.PANEL_BACKGROUND;

        graphics.fill(
                contentLeft + 2,
                rowY,
                contentRight - 2,
                rowY + craftscope$ROW_HEIGHT,
                background
        );

        graphics.fill(
                contentLeft + 8,
                rowY + craftscope$ROW_HEIGHT - 1,
                contentRight - 8,
                rowY + craftscope$ROW_HEIGHT,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        int rowLeft =
                contentLeft
                        + 10
                        + row.depth()
                        * craftscope$INDENT;

        if (row.expandable()) {
            graphics.drawString(
                    font,
                    row.expanded()
                            ? "-"
                            : "+",
                    rowLeft,
                    rowY + 7,
                    row.missing() == 0L
                            && scanned
                            ? CraftScopeUiTheme.TEXT_MUTED
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        int iconX =
                rowLeft + 12;

        ItemStack displayStack =
                craftscope$getDisplayStack(
                        row
                );

        graphics.renderItem(
                displayStack,
                iconX,
                rowY + 3
        );

        String name =
                craftscope$getDisplayName(
                        row
                );

        int nameX =
                iconX + 20;

        int nameRight =
                columns[0] - 18;

        int maxNameWidth =
                Math.max(
                        20,
                        nameRight - nameX
                );

        String visibleName =
                craftscope$trimToWidth(
                        font,
                        name,
                        maxNameWidth
                );

        int nameColor =
                scanned
                        && row.required() == 0L
                        ? CraftScopeUiTheme.TEXT_MUTED
                        : CraftScopeUiTheme.TEXT_PRIMARY;

        graphics.drawString(
                font,
                visibleName,
                nameX,
                rowY + 7,
                nameColor
        );

        craftscope$drawRightAligned(
                graphics,
                font,
                Long.toString(
                        row.required()
                ),
                columns[0],
                rowY + 7,
                row.required() == 0L
                        ? CraftScopeUiTheme.TEXT_MUTED
                        : CraftScopeUiTheme.TEXT_PRIMARY
        );

        String ownedText =
                scanned
                        ? Long.toString(
                        row.owned()
                )
                        : "--";

        craftscope$drawRightAligned(
                graphics,
                font,
                ownedText,
                columns[1],
                rowY + 7,
                scanned
                        ? CraftScopeUiTheme.TEXT_SECONDARY
                        : CraftScopeUiTheme.TEXT_MUTED
        );

        String missingText =
                scanned
                        ? Long.toString(
                        row.missing()
                )
                        : "--";

        int missingColor =
                !scanned
                        ? CraftScopeUiTheme.TEXT_MUTED
                        : row.missing() == 0L
                        ? CraftScopeUiTheme.SUCCESS
                        : CraftScopeUiTheme.TEXT_SECONDARY;

        craftscope$drawRightAligned(
                graphics,
                font,
                missingText,
                columns[2],
                rowY + 7,
                missingColor
        );

        int hitRight =
                Math.max(
                        nameRight,
                        nameX + font.width(visibleName) + 6
                );

        craftscope$rowHits.add(
                new CraftScopeMaterialRowHit(
                        row.path(),
                        contentLeft + 2,
                        rowY,
                        Math.min(
                                contentRight - 2,
                                hitRight
                        ),
                        rowY + craftscope$ROW_HEIGHT,
                        row.expandable()
                )
        );

        if (mouseX >= iconX
                && mouseX < iconX + 16
                && mouseY >= rowY + 3
                && mouseY < rowY + 19
                && mouseY >= viewportTop
                && mouseY < viewportBottom) {

            return displayStack;
        }

        return ItemStack.EMPTY;
    }

    @Unique
    private void craftscope$renderScanButton(
            GuiGraphics graphics,
            CraftScopeProjectScreen screen,
            int mouseX,
            int mouseY,
            int panelTop
    ) {
        Font font =
                Minecraft.getInstance().font;

        int buttonX =
                craftscope$getScanButtonX(
                        screen
                );

        int buttonY =
                panelTop + 2;

        boolean hovered =
                mouseX >= buttonX
                        && mouseX < buttonX
                        + craftscope$BUTTON_WIDTH
                        && mouseY >= buttonY
                        && mouseY < buttonY
                        + craftscope$BUTTON_HEIGHT;

        graphics.fill(
                buttonX,
                buttonY,
                buttonX + craftscope$BUTTON_WIDTH,
                buttonY + craftscope$BUTTON_HEIGHT,
                hovered
                        ? CraftScopeUiTheme.BUTTON_HOVER
                        : CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                buttonX,
                buttonY,
                buttonX + craftscope$BUTTON_WIDTH,
                buttonY + craftscope$BUTTON_HEIGHT,
                hovered
                        ? CraftScopeUiTheme.ACCENT
                        : CraftScopeUiTheme.BORDER
        );

        graphics.drawCenteredString(
                font,
                craftscope$storageSnapshot.isScanned()
                        ? "Recalculate"
                        : "Scan Inventory",
                buttonX
                        + craftscope$BUTTON_WIDTH / 2,
                buttonY + 5,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    @Unique
    private static int craftscope$getScanButtonX(
            CraftScopeProjectScreen screen
    ) {
        return screen.craftscope$getWindowRight()
                - 14
                - craftscope$BUTTON_WIDTH;
    }

    @Unique
    private static int[] craftscope$getColumns(
            int contentLeft,
            int contentRight
    ) {
        int missingRight =
                contentRight - 14;

        int ownedRight =
                missingRight - 84;

        int requiredRight =
                ownedRight - 84;

        int minimumRequired =
                contentLeft + 235;

        if (requiredRight < minimumRequired) {
            requiredRight =
                    minimumRequired;

            ownedRight =
                    requiredRight + 76;

            missingRight =
                    ownedRight + 76;
        }

        return new int[]{
                requiredRight,
                ownedRight,
                missingRight
        };
    }

    @Unique
    private static void craftscope$drawRightAligned(
            GuiGraphics graphics,
            Font font,
            String text,
            int right,
            int y,
            int color
    ) {
        graphics.drawString(
                font,
                text,
                right - font.width(text),
                y,
                color
        );
    }

    @Unique
    private static ItemStack craftscope$getDisplayStack(
            CraftScopeMaterialTreePlan.Row row
    ) {
        List<ItemStack> variants =
                row.acceptedVariants();

        if (row.selectableIngredientAlternatives()
                && row.explicitIngredientVariantSelection()) {

            return row.stack();
        }

        if (!variants.isEmpty()) {
            /*
             * Rotate through every accepted option instead of
             * permanently showing the first registry entry
             * (which often happened to be Acacia).
             */
            long cycle =
                    System.currentTimeMillis()
                            / 1000L;

            long offset =
                    row.path() == null
                            ? 0L
                            : row.path().hashCode();

            int index =
                    (int) Math.floorMod(
                            cycle + offset,
                            (long) variants.size()
                    );

            return variants
                    .get(index)
                    .copy();
        }

        return row.stack();
    }
    @Unique
    private static String craftscope$getDisplayName(
            CraftScopeMaterialTreePlan.Row row
    ) {
        List<ItemStack> variants =
                row.acceptedVariants();

        ItemStack fallback =
                row.stack();

        if (row.selectableIngredientAlternatives()) {

            if (row.explicitIngredientVariantSelection()) {

                return fallback
                        .getHoverName()
                        .getString();
            }

            return craftscope$getAlternativeSummary(
                    variants
            );
        }

        if (variants.size() <= 1) {
            return fallback
                    .getHoverName()
                    .getString();
        }

        boolean allLogs =
                true;

        boolean allPlanks =
                true;

        boolean allWool =
                true;

        for (ItemStack variant : variants) {
            if (!variant.is(
                    ItemTags.LOGS
            )) {
                allLogs =
                        false;
            }

            if (!variant.is(
                    ItemTags.PLANKS
            )) {
                allPlanks =
                        false;
            }

            if (!variant.is(
                    ItemTags.WOOL
            )) {
                allWool =
                        false;
            }
        }

        if (allLogs) {
            return "Any Log";
        }

        if (allPlanks) {
            return "Any Planks";
        }

        if (allWool) {
            return "Any Wool";
        }

        String suffix =
                craftscope$findCommonSuffix(
                        variants
                );

        if (suffix != null
                && !suffix.isBlank()) {

            return "Any " + suffix;
        }

        return "Any "
                + fallback
                .getHoverName()
                .getString();
    }

    @Unique
    private static String craftscope$getAlternativeSummary(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return "Choose Ingredient";
        }

        if (variants.size() == 1) {

            return variants
                    .getFirst()
                    .getHoverName()
                    .getString();
        }

        if (variants.size() == 2) {

            return variants
                    .get(0)
                    .getHoverName()
                    .getString()
                    + " OR "
                    + variants
                    .get(1)
                    .getHoverName()
                    .getString();
        }

        return "Choose Ingredient ("
                + variants.size()
                + " options)";
    }
    @Unique
    private static String craftscope$findCommonSuffix(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return null;
        }

        String[] common =
                variants
                        .getFirst()
                        .getHoverName()
                        .getString()
                        .split("\\s+");

        int commonCount =
                common.length;

        for (int i = 1;
             i < variants.size()
                     && commonCount > 0;
             i++) {

            String[] words =
                    variants
                            .get(i)
                            .getHoverName()
                            .getString()
                            .split("\\s+");

            int matches =
                    0;

            while (matches < commonCount
                    && matches < words.length) {

                String first =
                        common[
                                common.length
                                        - 1
                                        - matches
                                ];

                String other =
                        words[
                                words.length
                                        - 1
                                        - matches
                                ];

                if (!first.equalsIgnoreCase(
                        other
                )) {
                    break;
                }

                matches++;
            }

            commonCount =
                    matches;
        }

        if (commonCount <= 0) {
            return null;
        }

        StringBuilder builder =
                new StringBuilder();

        for (int i =
             common.length - commonCount;
             i < common.length;
             i++) {

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(
                    common[i]
            );
        }

        return builder.toString();
    }

    @Unique
    private static String craftscope$trimToWidth(
            Font font,
            String text,
            int maxWidth
    ) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis =
                "...";

        int ellipsisWidth =
                font.width(
                        ellipsis
                );

        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        String working =
                text;

        while (!working.isEmpty()
                && font.width(working)
                + ellipsisWidth
                > maxWidth) {

            working =
                    working.substring(
                            0,
                            working.length() - 1
                    );
        }

        return working
                + ellipsis;
    }

    @Unique
    private void craftscope$renderScrollbar(
            GuiGraphics graphics,
            int contentRight,
            int viewportTop,
            int viewportBottom,
            int viewportHeight,
            int contentHeight
    ) {
        if (contentHeight <= viewportHeight
                || viewportHeight <= 0) {

            return;
        }

        int trackX =
                contentRight - 5;

        graphics.fill(
                trackX,
                viewportTop + 2,
                trackX + 2,
                viewportBottom - 2,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        int trackHeight =
                Math.max(
                        1,
                        viewportHeight - 4
                );

        int thumbHeight =
                Math.max(
                        18,
                        (int) (
                                (long) trackHeight
                                        * viewportHeight
                                        / contentHeight
                        )
                );

        thumbHeight =
                Math.min(
                        trackHeight,
                        thumbHeight
                );

        double maxScroll =
                Math.max(
                        1.0D,
                        contentHeight - viewportHeight
                );

        double fraction =
                craftscope$materialTreeScroll
                        / maxScroll;

        int thumbTravel =
                Math.max(
                        0,
                        trackHeight - thumbHeight
                );

        int thumbTop =
                viewportTop
                        + 2
                        + (int) Math.round(
                        fraction
                                * thumbTravel
                );

        graphics.fill(
                trackX - 1,
                thumbTop,
                trackX + 3,
                thumbTop + thumbHeight,
                CraftScopeUiTheme.BORDER_HOVER
        );
    }

    @Unique
    private void craftscope$clampScroll() {
        double max =
                Math.max(
                        0.0D,
                        craftscope$lastContentHeight
                                - craftscope$lastViewportHeight
                );

        craftscope$materialTreeScroll =
                Math.max(
                        0.0D,
                        Math.min(
                                max,
                                craftscope$materialTreeScroll
                        )
                );
    }

    @Unique
    private record CraftScopeMaterialRowHit(
            String path,
            int left,
            int top,
            int right,
            int bottom,
            boolean expandable
    ) {
    }
}