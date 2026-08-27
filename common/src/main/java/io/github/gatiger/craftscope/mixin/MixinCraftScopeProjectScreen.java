package io.github.gatiger.craftscope.mixin;

import io.github.gatiger.craftscope.CraftScopeProjectScreen;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.ui.CraftScopeRecipeSourceUiModel.Accumulator;
import io.github.gatiger.craftscope.ui.CraftScopeRecipeSourceUiModel.Entry;
import io.github.gatiger.craftscope.ui.CraftScopeRecipeSourceUiModel.Layout;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Adds the Recipe Source selector to CraftScopeProjectScreen
 * without turning source selection into a second recipe-selection
 * system.
 *
 * The control lives in the Recipe Tree section header:
 *
 *   Recipe Tree                   [Source: All Sources ▼]
 *
 * Clicking it opens a scrollable overlay containing every mod that
 * can produce the selected target plus the number of logical
 * Recipe Tree routes contributed by that source.
 *
 * Selecting a source filters only the ROOT target route. Once that
 * route is chosen, intermediate ingredients may use any provider.
 */
@Mixin(CraftScopeProjectScreen.class)
public abstract class MixinCraftScopeProjectScreen {

    @Invoker("getTargetStack")
    protected abstract ItemStack craftscope$invokeGetTargetStack();

    @Invoker("rebuildTree")
    protected abstract void craftscope$invokeRebuildTree();

    @Shadow
    @Final
    private Map<String, List<ResourceLocation>> recipeChoices;

    @Unique
    private static final int CRAFTSCOPE_SOURCE_ROW_HEIGHT = 20;

    @Unique
    private static final int CRAFTSCOPE_SOURCE_SCROLL_AMOUNT = 20;

    @Unique
    private static final int CRAFTSCOPE_SOURCE_MAX_VISIBLE_ROWS = 6;

    @Unique
    private final List<Entry>
            craftscope$recipeSources =
            new ArrayList<>();

    /*
     * null = All Sources
     */
    @Unique
    private String craftscope$selectedRecipeSourceId;

    @Unique
    private boolean craftscope$sourceDropdownOpen;

    @Unique
    private double craftscope$sourceScroll;

    @Unique
    private String craftscope$sourceTargetKey = "";

    /*
     * Redirect the existing Recipe Tree builder call so the screen
     * can supply the optional root-source filter without replacing
     * CraftScopeProjectScreen itself.
     */
    @Redirect(
            method = "rebuildTree",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/github/gatiger/craftscope/recipe/CraftScopeProductionRecipeTreeBuilder;resolveTree(Lnet/minecraft/world/item/ItemStack;ILjava/util/Map;)Lio/github/gatiger/craftscope/recipe/CraftScopeRecipeTree;"
            )
    )
    private CraftScopeRecipeTree craftscope$resolveTreeWithRecipeSource(
            ItemStack target,
            int targetCount,
            Map<String, ResourceLocation> overrides
    ) {
        return CraftScopeProductionRecipeTreeBuilder.resolveTree(
                target,
                targetCount,
                overrides,
                craftscope$selectedRecipeSourceId
        );
    }

    /*
     * Keep the selector's source list synchronized with the actual
     * normalized direct routes that are loaded at runtime.
     */
    @Inject(
            method = "rebuildTree",
            at = @At("RETURN")
    )
    private void craftscope$refreshRecipeSourcesAfterTreeBuild(
            CallbackInfo ci
    ) {
        craftscope$refreshRecipeSources();
    }

    /*
     * A new target always starts at All Sources. This avoids an old
     * source filter temporarily turning an unrelated new target
     * into a leaf before the source list has had a chance to
     * refresh.
     */
    @Inject(
            method = "craftscope$setTargetItem",
            at = @At("HEAD")
    )
    private void craftscope$resetRecipeSourceForNewTarget(
            ItemStack stack,
            CallbackInfo ci
    ) {
        craftscope$selectedRecipeSourceId = null;
        craftscope$sourceDropdownOpen = false;
        craftscope$sourceScroll = 0.0D;
        craftscope$sourceTargetKey = "";
    }

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void craftscope$renderRecipeSourceSelector(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        if (!screen.craftscope$isRecipeTreeView()) {
            craftscope$sourceDropdownOpen = false;
            return;
        }

        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            craftscope$sourceDropdownOpen = false;
            return;
        }

        craftscope$ensureSourceListCurrent();

        Layout layout =
                craftscope$getLayout();

        Font font =
                Minecraft
                        .getInstance()
                        .font;

        boolean controlHovered =
                craftscope$isInside(
                        mouseX,
                        mouseY,
                        layout.controlLeft(),
                        layout.controlTop(),
                        layout.controlRight(),
                        layout.controlBottom()
                );

        graphics.fill(
                layout.controlLeft(),
                layout.controlTop(),
                layout.controlRight(),
                layout.controlBottom(),
                controlHovered
                        || craftscope$sourceDropdownOpen
                        ? CraftScopeUiTheme.BUTTON_HOVER
                        : CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                layout.controlLeft(),
                layout.controlTop(),
                layout.controlRight(),
                layout.controlBottom(),
                craftscope$sourceDropdownOpen
                        ? CraftScopeUiTheme.ACCENT
                        : CraftScopeUiTheme.BORDER
        );

        String selectedName =
                craftscope$getSelectedSourceDisplayName();

        String controlText =
                "Source: "
                        + selectedName
                        + (craftscope$sourceDropdownOpen
                        ? " ▲"
                        : " ▼");

        graphics.drawString(
                font,
                craftscope$fitText(
                        font,
                        controlText,
                        layout.controlRight()
                                - layout.controlLeft()
                                - 10
                ),
                layout.controlLeft() + 5,
                layout.controlTop() + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (craftscope$sourceDropdownOpen) {
            craftscope$renderSourceDropdown(
                    graphics,
                    mouseX,
                    mouseY,
                    layout,
                    font
            );
        }
    }

    @Inject(
            method = "mouseClicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$handleRecipeSourceClick(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (button != 0) {
            return;
        }

        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        if (!screen.craftscope$isRecipeTreeView()) {
            craftscope$sourceDropdownOpen = false;
            return;
        }

        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            craftscope$sourceDropdownOpen = false;
            return;
        }

        craftscope$ensureSourceListCurrent();

        Layout layout =
                craftscope$getLayout();

        if (craftscope$isInside(
                mouseX,
                mouseY,
                layout.controlLeft(),
                layout.controlTop(),
                layout.controlRight(),
                layout.controlBottom()
        )) {
            craftscope$sourceDropdownOpen =
                    !craftscope$sourceDropdownOpen;

            craftscope$clampSourceScroll(
                    layout
            );

            cir.setReturnValue(true);
            return;
        }

        if (!craftscope$sourceDropdownOpen) {
            return;
        }

        if (craftscope$isInside(
                mouseX,
                mouseY,
                layout.dropdownLeft(),
                layout.dropdownTop(),
                layout.dropdownRight(),
                layout.dropdownBottom()
        )) {
            int index =
                    (int) (
                            (
                                    mouseY
                                            - layout.dropdownTop()
                                            + craftscope$sourceScroll
                            )
                                    / CRAFTSCOPE_SOURCE_ROW_HEIGHT
                    );

            int totalRows =
                    craftscope$getSourceRowCount();

            if (index >= 0
                    && index < totalRows) {

                String selectedSource =
                        index == 0
                                ? null
                                : craftscope$recipeSources
                                .get(index - 1)
                                .modId();

                boolean changed =
                        !craftscope$equalsNullable(
                                selectedSource,
                                craftscope$selectedRecipeSourceId
                        );

                craftscope$selectedRecipeSourceId =
                        selectedSource;

                craftscope$sourceDropdownOpen = false;

                if (changed) {
                    /*
                     * Recipe choices are rebuilt for the newly
                     * filtered root route set. Persisted recipe
                     * overrides remain intact; only the session
                     * choice cache is cleared.
                     */
                    recipeChoices.clear();
                    craftscope$invokeRebuildTree();
                }

                cir.setReturnValue(true);
                return;
            }

            cir.setReturnValue(true);
            return;
        }

        /*
         * Clicking elsewhere closes the overlay but does not eat
         * the click, allowing the normal Recipe Tree/tabs to handle
         * it.
         */
        craftscope$sourceDropdownOpen = false;
    }

    @Inject(
            method = "mouseScrolled",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftscope$handleRecipeSourceScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!craftscope$sourceDropdownOpen) {
            return;
        }

        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        if (!screen.craftscope$isRecipeTreeView()) {
            return;
        }

        Layout layout =
                craftscope$getLayout();

        if (!craftscope$isInside(
                mouseX,
                mouseY,
                layout.dropdownLeft(),
                layout.dropdownTop(),
                layout.dropdownRight(),
                layout.dropdownBottom()
        )) {
            return;
        }

        craftscope$sourceScroll -=
                scrollY
                        * CRAFTSCOPE_SOURCE_SCROLL_AMOUNT;

        craftscope$clampSourceScroll(
                layout
        );

        cir.setReturnValue(true);
    }

    @Unique
    private void craftscope$renderSourceDropdown(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            Layout layout,
            Font font
    ) {
        graphics.fill(
                layout.dropdownLeft(),
                layout.dropdownTop(),
                layout.dropdownRight(),
                layout.dropdownBottom(),
                CraftScopeUiTheme.PANEL_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                layout.dropdownLeft(),
                layout.dropdownTop(),
                layout.dropdownRight(),
                layout.dropdownBottom(),
                CraftScopeUiTheme.BORDER_HOVER
        );

        craftscope$clampSourceScroll(
                layout
        );

        graphics.enableScissor(
                layout.dropdownLeft() + 1,
                layout.dropdownTop() + 1,
                layout.dropdownRight() - 1,
                layout.dropdownBottom() - 1
        );

        int totalRows =
                craftscope$getSourceRowCount();

        int y =
                layout.dropdownTop()
                        - (int) craftscope$sourceScroll;

        for (int rowIndex = 0;
             rowIndex < totalRows;
             rowIndex++) {

            int rowTop = y;
            int rowBottom =
                    rowTop
                            + CRAFTSCOPE_SOURCE_ROW_HEIGHT;

            if (rowBottom >= layout.dropdownTop()
                    && rowTop <= layout.dropdownBottom()) {

                Entry entry =
                        rowIndex == 0
                                ? null
                                : craftscope$recipeSources
                                .get(rowIndex - 1);

                String modId =
                        entry == null
                                ? null
                                : entry.modId();

                String label =
                        entry == null
                                ? "All Sources"
                                : entry.displayName();

                int routeCount =
                        entry == null
                                ? craftscope$getAllSourceRouteCount()
                                : entry.routeCount();

                boolean selected =
                        craftscope$equalsNullable(
                                modId,
                                craftscope$selectedRecipeSourceId
                        );

                boolean hovered =
                        craftscope$isInside(
                                mouseX,
                                mouseY,
                                layout.dropdownLeft() + 2,
                                rowTop,
                                layout.dropdownRight() - 2,
                                rowBottom
                        )
                                && mouseY >= layout.dropdownTop()
                                && mouseY < layout.dropdownBottom();

                if (selected) {
                    graphics.fill(
                            layout.dropdownLeft() + 3,
                            rowTop + 1,
                            layout.dropdownRight() - 5,
                            rowBottom - 1,
                            CraftScopeUiTheme.ACCENT_BACKGROUND
                    );

                    CraftScopeUiTheme.drawBorder(
                            graphics,
                            layout.dropdownLeft() + 3,
                            rowTop + 1,
                            layout.dropdownRight() - 5,
                            rowBottom - 1,
                            CraftScopeUiTheme.ACCENT
                    );

                } else if (hovered) {

                    graphics.fill(
                            layout.dropdownLeft() + 3,
                            rowTop + 1,
                            layout.dropdownRight() - 5,
                            rowBottom - 1,
                            CraftScopeUiTheme.BUTTON_HOVER
                    );
                }

                String countText =
                        Integer.toString(
                                routeCount
                        );

                int countX =
                        layout.dropdownRight()
                                - 10
                                - font.width(
                                countText
                        );

                graphics.drawString(
                        font,
                        craftscope$fitText(
                                font,
                                label,
                                Math.max(
                                        20,
                                        countX
                                                - layout.dropdownLeft()
                                                - 14
                                )
                        ),
                        layout.dropdownLeft() + 7,
                        rowTop + 6,
                        selected
                                ? CraftScopeUiTheme.TEXT_PRIMARY
                                : CraftScopeUiTheme.TEXT_SECONDARY
                );

                graphics.drawString(
                        font,
                        countText,
                        countX,
                        rowTop + 6,
                        CraftScopeUiTheme.TEXT_MUTED
                );
            }

            y +=
                    CRAFTSCOPE_SOURCE_ROW_HEIGHT;
        }

        graphics.disableScissor();

        craftscope$renderSourceScrollbar(
                graphics,
                layout
        );
    }

    @Unique
    private void craftscope$renderSourceScrollbar(
            GuiGraphics graphics,
            Layout layout
    ) {
        int contentHeight =
                craftscope$getSourceRowCount()
                        * CRAFTSCOPE_SOURCE_ROW_HEIGHT;

        int viewportHeight =
                layout.dropdownBottom()
                        - layout.dropdownTop();

        if (contentHeight <= viewportHeight) {
            return;
        }

        int trackLeft =
                layout.dropdownRight() - 4;

        int trackTop =
                layout.dropdownTop() + 2;

        int trackBottom =
                layout.dropdownBottom() - 2;

        graphics.fill(
                trackLeft,
                trackTop,
                trackLeft + 2,
                trackBottom,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        int trackHeight =
                trackBottom - trackTop;

        int thumbHeight =
                Math.max(
                        12,
                        viewportHeight
                                * trackHeight
                                / contentHeight
                );

        int maxScroll =
                contentHeight
                        - viewportHeight;

        int maxThumbTravel =
                Math.max(
                        0,
                        trackHeight
                                - thumbHeight
                );

        int thumbOffset =
                maxScroll <= 0
                        ? 0
                        : (int) (
                        craftscope$sourceScroll
                                * maxThumbTravel
                                / maxScroll
                );

        graphics.fill(
                trackLeft,
                trackTop + thumbOffset,
                trackLeft + 2,
                trackTop
                        + thumbOffset
                        + thumbHeight,
                CraftScopeUiTheme.ACCENT
        );
    }

    @Unique
    private void craftscope$refreshRecipeSources() {
        ItemStack target =
                craftscope$invokeGetTargetStack();

        craftscope$recipeSources.clear();

        if (target == null
                || target.isEmpty()) {

            craftscope$selectedRecipeSourceId = null;
            craftscope$sourceScroll = 0.0D;
            craftscope$sourceTargetKey = "";
            return;
        }

        ResourceLocation targetId =
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(
                                target.getItem()
                        );

        craftscope$sourceTargetKey =
                targetId == null
                        ? ""
                        : targetId.toString();

        List<CraftScopeProductionRoute> directRoutes =
                CraftScopeProductionRouteQuery.findDirectRoutes(
                        target
                );

        Map<String, Accumulator> sources =
                new LinkedHashMap<>();

        for (CraftScopeProductionRoute route :
                directRoutes) {

            if (!CraftScopeProductionRecipeTreeBuilder
                    .isRecipeTreeCandidate(route)) {

                continue;
            }

            Set<String> routeSources =
                    new LinkedHashSet<>();

            String routeSource =
                    route.sourceModId();

            if (routeSource != null
                    && !routeSource.isBlank()) {

                routeSources.add(
                        routeSource
                );

                Accumulator accumulator =
                        sources.computeIfAbsent(
                                routeSource,
                                ignored ->
                                        new Accumulator(
                                                routeSource,
                                                craftscope$displayNameForRouteSource(
                                                        route
                                                )
                                        )
                        );

                accumulator.setDisplayNameIfBetter(
                        craftscope$displayNameForRouteSource(
                                route
                        )
                );
            }

            for (CraftScopeProductionStep step :
                    route.steps()) {

                for (CraftScopeProductionMethod method :
                        step.methods()) {

                    String methodSource =
                            method.sourceModId();

                    if (methodSource == null
                            || methodSource.isBlank()) {

                        continue;
                    }

                    routeSources.add(
                            methodSource
                    );

                    sources.computeIfAbsent(
                            methodSource,
                            ignored ->
                                    new Accumulator(
                                            methodSource,
                                            craftscope$formatModId(
                                                    methodSource
                                            )
                                    )
                    );
                }
            }

            for (String sourceId :
                    routeSources) {

                Accumulator accumulator =
                        sources.get(
                                sourceId
                        );

                if (accumulator != null) {
                    accumulator.increment();
                }
            }
        }

        for (Accumulator accumulator :
                sources.values()) {

            craftscope$recipeSources.add(
                    accumulator.build()
            );
        }

        craftscope$recipeSources.sort(
                Comparator
                        .comparingInt(
                                (Entry entry) ->
                                        "minecraft".equals(
                                                entry.modId()
                                        )
                                                ? 0
                                                : 1
                        )
                        .thenComparing(
                                entry ->
                                        entry
                                                .displayName()
                                                .toLowerCase()
                        )
                        .thenComparing(
                                Entry::modId
                        )
        );

        if (craftscope$selectedRecipeSourceId != null) {
            boolean stillAvailable =
                    false;

            for (Entry entry :
                    craftscope$recipeSources) {

                if (entry.modId().equals(
                        craftscope$selectedRecipeSourceId
                )) {

                    stillAvailable = true;
                    break;
                }
            }

            if (!stillAvailable) {
                craftscope$selectedRecipeSourceId = null;
            }
        }

        Layout layout =
                craftscope$getLayout();

        craftscope$clampSourceScroll(
                layout
        );
    }

    @Unique
    private void craftscope$ensureSourceListCurrent() {
        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            if (!craftscope$sourceTargetKey.isEmpty()) {
                craftscope$refreshRecipeSources();
            }

            return;
        }

        ResourceLocation targetId =
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(
                                target.getItem()
                        );

        String targetKey =
                targetId == null
                        ? ""
                        : targetId.toString();

        if (!targetKey.equals(
                craftscope$sourceTargetKey
        )) {

            craftscope$refreshRecipeSources();
        }
    }

    @Unique
    private int craftscope$getAllSourceRouteCount() {
        ItemStack target =
                craftscope$invokeGetTargetStack();

        if (target == null
                || target.isEmpty()) {

            return 0;
        }

        int count = 0;

        for (CraftScopeProductionRoute route :
                CraftScopeProductionRouteQuery.findDirectRoutes(
                        target
                )) {

            if (CraftScopeProductionRecipeTreeBuilder
                    .isRecipeTreeCandidate(route)) {

                count++;
            }
        }

        return count;
    }

    @Unique
    private String craftscope$getSelectedSourceDisplayName() {
        if (craftscope$selectedRecipeSourceId == null) {
            return "All Sources";
        }

        for (Entry entry :
                craftscope$recipeSources) {

            if (entry.modId().equals(
                    craftscope$selectedRecipeSourceId
            )) {

                return entry.displayName();
            }
        }

        return craftscope$formatModId(
                craftscope$selectedRecipeSourceId
        );
    }

    @Unique
    private String craftscope$displayNameForRouteSource(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.sourceModName() == null) {

            return route == null
                    ? "Unknown"
                    : craftscope$formatModId(
                    route.sourceModId()
            );
        }

        String name =
                route
                        .sourceModName()
                        .getString();

        if (name == null
                || name.isBlank()) {

            return craftscope$formatModId(
                    route.sourceModId()
            );
        }

        return name;
    }

    @Unique
    private String craftscope$formatModId(
            String modId
    ) {
        if (modId == null
                || modId.isBlank()) {

            return "Unknown";
        }

        if ("minecraft".equals(modId)) {
            return "Minecraft";
        }

        String normalized =
                modId
                        .replace('_', ' ')
                        .replace('-', ' ');

        StringBuilder result =
                new StringBuilder();

        boolean uppercaseNext = true;

        for (int i = 0;
             i < normalized.length();
             i++) {

            char c =
                    normalized.charAt(i);

            if (Character.isWhitespace(c)) {
                uppercaseNext = true;

                if (!result.isEmpty()
                        && result.charAt(
                        result.length() - 1
                ) != ' ') {

                    result.append(' ');
                }

                continue;
            }

            result.append(
                    uppercaseNext
                            ? Character.toUpperCase(c)
                            : c
            );

            uppercaseNext = false;
        }

        return result.toString();
    }

    @Unique
    private Layout craftscope$getLayout() {
        CraftScopeProjectScreen screen =
                (CraftScopeProjectScreen) (Object) this;

        int panelLeft =
                screen.craftscope$getWindowLeft()
                        + 10;

        int panelRight =
                screen.craftscope$getWindowRight()
                        - 10;

        /*
         * CraftScopeProjectScreen's Recipe Tree section title is
         * 97 pixels below the window top.
         */
        int contentTitleY =
                screen.craftscope$getWindowTop()
                        + 97;

        int controlHeight = 16;

        int availableWidth =
                Math.max(
                        1,
                        panelRight - panelLeft
                );

        int controlWidth =
                Math.min(
                        150,
                        Math.max(
                                110,
                                availableWidth / 3
                        )
                );

        int controlRight =
                panelRight - 5;

        int controlLeft =
                Math.max(
                        panelLeft + 5,
                        controlRight
                                - controlWidth
                );

        int controlTop =
                contentTitleY - 1;

        int controlBottom =
                controlTop
                        + controlHeight;

        int dropdownLeft =
                controlLeft;

        int dropdownRight =
                controlRight;

        int dropdownTop =
                controlBottom + 2;

        int availableHeight =
                Math.max(
                        CRAFTSCOPE_SOURCE_ROW_HEIGHT,
                        screen.craftscope$getWindowBottom()
                                - dropdownTop
                                - 12
                );

        int visibleRows =
                Math.max(
                        1,
                        Math.min(
                                CRAFTSCOPE_SOURCE_MAX_VISIBLE_ROWS,
                                availableHeight
                                        / CRAFTSCOPE_SOURCE_ROW_HEIGHT
                        )
                );

        int dropdownHeight =
                visibleRows
                        * CRAFTSCOPE_SOURCE_ROW_HEIGHT;

        int dropdownBottom =
                dropdownTop
                        + dropdownHeight;

        return new Layout(
                controlLeft,
                controlTop,
                controlRight,
                controlBottom,
                dropdownLeft,
                dropdownTop,
                dropdownRight,
                dropdownBottom
        );
    }

    @Unique
    private void craftscope$clampSourceScroll(
            Layout layout
    ) {
        int contentHeight =
                craftscope$getSourceRowCount()
                        * CRAFTSCOPE_SOURCE_ROW_HEIGHT;

        int viewportHeight =
                layout.dropdownBottom()
                        - layout.dropdownTop();

        int maxScroll =
                Math.max(
                        0,
                        contentHeight
                                - viewportHeight
                );

        craftscope$sourceScroll =
                Math.max(
                        0.0D,
                        Math.min(
                                craftscope$sourceScroll,
                                maxScroll
                        )
                );
    }

    @Unique
    private int craftscope$getSourceRowCount() {
        return 1
                + craftscope$recipeSources.size();
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
    private boolean craftscope$equalsNullable(
            String left,
            String right
    ) {
        if (left == null) {
            return right == null;
        }

        return left.equals(right);
    }

    @Unique
    private String craftscope$fitText(
            Font font,
            String text,
            int maxWidth
    ) {
        if (text == null
                || maxWidth <= 0) {

            return "";
        }

        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";

        int available =
                Math.max(
                        0,
                        maxWidth
                                - font.width(ellipsis)
                );

        String result =
                text;

        while (!result.isEmpty()
                && font.width(result)
                > available) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result + ellipsis;
    }

}
