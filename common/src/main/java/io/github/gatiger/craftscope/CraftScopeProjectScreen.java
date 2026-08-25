package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeResolver;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummary;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummarizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftScopeProjectScreen extends Screen
        implements CraftScopeTargetItemReceiver {

    private static final int TARGET_SLOT_SIZE = 24;

    private static final int TREE_ROW_HEIGHT = 20;
    private static final int TREE_INDENT = 18;

    private static final int TREE_SIDE_MARGIN = 20;
    private static final int TREE_TOP = 175;
    private static final int TREE_BOTTOM_MARGIN = 65;

    private static final int SCROLL_AMOUNT = 20;

    private static final long VARIANT_CYCLE_MS = 1000L;

    private final Screen parent;
    private final CraftScopeProject project;

    /*
     * Nodes start collapsed.
     *
     * Expanded state currently lasts only while this project
     * screen remains open.
     */
    private final Set<String> expandedNodes =
            new HashSet<>();

    /*
     * Active per-node recipe overrides.
     *
     * These are loaded from the CraftScopeProject when this
     * screen is created and saved whenever the player changes
     * a recipe.
     */
    private final Map<String, ResourceLocation> recipeOverrides =
            new HashMap<>();

    /*
     * Stable ranked recipe choices for each node.
     *
     * The resolver reports the currently selected recipe as
     * the preferred recipe for the rebuilt node. This cache
     * preserves the original order so the selector remains:
     *
     * [1/2] -> [2/2] -> [1/2]
     */
    private final Map<String, List<ResourceLocation>> recipeChoices =
            new HashMap<>();

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

    private double treeScroll;

    private CraftScopeRecipeTree currentTree;

        private CraftScopeMaterialSummary currentMaterialSummary =
                new CraftScopeMaterialSummary(
                        List.of()
                );

        public CraftScopeProjectScreen(
                Screen parent,
                CraftScopeProject project
        ) {
        super(Component.literal(project.getName()));

        this.parent = parent;
        this.project = project;

        loadRecipeOverrides();
        }

    private void loadRecipeOverrides() {
        recipeOverrides.clear();

        for (Map.Entry<String, String> entry :
                project.getRecipeOverrides().entrySet()) {

            ResourceLocation recipeId =
                    ResourceLocation.tryParse(
                            entry.getValue()
                    );

            if (recipeId != null) {
                recipeOverrides.put(
                        entry.getKey(),
                        recipeId
                );
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        targetSlotX =
                width / 2 - TARGET_SLOT_SIZE / 2;

        targetSlotY = 75;

        int centerX = width / 2;

        quantityField = new EditBox(
                font,
                centerX - 25,
                118,
                50,
                20,
                Component.literal("Quantity")
        );

        quantityField.setValue(
                Integer.toString(
                        project.getTargetCount()
                )
        );

        quantityField.setFilter(
                value -> value.isEmpty()
                        || value.matches("\\d+")
        );

        quantityField.setResponder(
                this::craftscope$quantityChanged
        );

        addRenderableWidget(quantityField);

        addRenderableWidget(
                Button.builder(
                                Component.literal("-"),
                                button ->
                                        craftscope$changeQuantity(-1)
                        )
                        .bounds(
                                centerX - 50,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("+"),
                                button ->
                                        craftscope$changeQuantity(1)
                        )
                        .bounds(
                                centerX + 30,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Back"),
                                button ->
                                        minecraft.setScreen(parent)
                        )
                        .bounds(
                                centerX - 50,
                                height - 40,
                                100,
                                20
                        )
                        .build()
        );

        treeScroll = 0;

        rebuildTree();
    }

    private void logMaterialSummary() {

        Constants.LOG.info(
                "CraftScope Total Materials for project '{}':",
                project.getName()
        );

        if (currentMaterialSummary == null
                || currentMaterialSummary.isEmpty()) {

                Constants.LOG.info(
                        "  No materials found."
                );

                return;
        }

        for (CraftScopeMaterialSummary.Entry entry :
                currentMaterialSummary.getEntries()) {

                ItemStack stack =
                        entry.getStack();

                String itemName =
                        stack.getHoverName()
                                .getString();

                List<ItemStack> variants =
                        entry.getAcceptedVariants();

                if (variants.size() <= 1) {

                Constants.LOG.info(
                        "  {} x{} ({})",
                        itemName,
                        entry.getRequiredCount(),
                        getItemId(stack)
                );

                continue;
                }

                StringBuilder variantIds =
                        new StringBuilder();

                for (ItemStack variant : variants) {

                if (!variantIds.isEmpty()) {
                        variantIds.append(", ");
                }

                variantIds.append(
                        getItemId(variant)
                );
                }

                Constants.LOG.info(
                        "  {} x{} [variants: {}]",
                        itemName,
                        entry.getRequiredCount(),
                        variantIds
                );
        }
        }

    private void rebuildTree() {
        ItemStack target =
                getTargetStack();

        if (target.isEmpty()) {
            currentTree = null;
            return;
        }

        currentTree =
                CraftScopeRecipeResolver.resolveTree(
                        target,
                        project.getTargetCount(),
                        recipeOverrides
                );

        populateRecipeChoices();

        clampTreeScroll();
    }

    /*
     * Store the ranked recipe order the first time a node is
     * encountered.
     */
    private void populateRecipeChoices() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return;
        }

        populateRecipeChoices(
                currentTree.getRoot(),
                "root"
        );
    }

    private void populateRecipeChoices(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        if (node.getPreferredRecipeId() != null
                && node.getTotalRecipeCount() > 1
                && !recipeChoices.containsKey(nodePath)) {

            List<ResourceLocation> choices =
                    new ArrayList<>();

            /*
             * If this project already has an override, the
             * resolver reports that selected recipe first.
             *
             * We want the normal automatically preferred
             * recipe to remain position 1 in the UI.
             *
             * The resolver's alternatives list contains every
             * other recipe, so reconstruct the list and, when
             * possible, put the saved override after the first
             * normal alternative.
             */
            ResourceLocation selected =
                    node.getPreferredRecipeId();

            String savedOverrideString =
                    project.getRecipeOverride(
                            nodePath
                    );

            ResourceLocation savedOverride =
                    savedOverrideString == null
                            ? null
                            : ResourceLocation.tryParse(
                                    savedOverrideString
                            );

            if (savedOverride == null) {

                choices.add(selected);

                choices.addAll(
                        node.getAlternativeRecipeIds()
                );

            } else {

                List<ResourceLocation> allRecipes =
                        new ArrayList<>(
                                node.getAlternativeRecipeIds()
                        );

                /*
                 * The selected override is omitted from
                 * alternativeRecipeIds, so put it back into
                 * the complete set.
                 */
                allRecipes.add(savedOverride);

                /*
                 * The resolver's alternatives are ranked.
                 * The first alternative is therefore normally
                 * CraftScope's automatic/default choice.
                 */
                ResourceLocation defaultRecipe = null;

                for (ResourceLocation id :
                        node.getAlternativeRecipeIds()) {

                    if (!id.equals(savedOverride)) {
                        defaultRecipe = id;
                        break;
                    }
                }

                if (defaultRecipe != null) {
                    choices.add(defaultRecipe);
                }

                for (ResourceLocation id : allRecipes) {

                    if (!choices.contains(id)) {
                        choices.add(id);
                    }
                }
            }

            recipeChoices.put(
                    nodePath,
                    choices
            );
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0;
             i < children.size();
             i++) {

            CraftScopeRecipeNode child =
                    children.get(i);

            String childPath =
                    buildChildPath(
                            nodePath,
                            i,
                            child
                    );

            populateRecipeChoices(
                    child,
                    childPath
            );
        }
    }

    private void craftscope$changeQuantity(
            int amount
    ) {
        int current =
                craftscope$getQuantityFromField();

        int updated =
                Math.max(
                        1,
                        current + amount
                );

        quantityField.setValue(
                Integer.toString(updated)
        );
    }

    private int craftscope$getQuantityFromField() {
        try {
            int value =
                    Integer.parseInt(
                            quantityField.getValue()
                    );

            return Math.max(1, value);

        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void craftscope$quantityChanged(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {

            return;
        }

        try {
            int quantity =
                    Integer.parseInt(value);

            if (quantity < 1) {
                return;
            }

            project.setTargetCount(quantity);

            CraftScopeProjectManager.save();

            rebuildTree();

        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                width / 2,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Target Item",
                width / 2,
                55,
                0xCCCCCC
        );

        renderTargetSlot(
                graphics,
                mouseX,
                mouseY
        );

        if (project.getTargetItemId().isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "Drop an item here from JEI/EMI",
                    width / 2,
                    105,
                    0x888888
            );
        }

        graphics.drawCenteredString(
                font,
                "Quantity",
                width / 2,
                105,
                0xCCCCCC
        );

        renderRecipeTree(
                graphics,
                mouseX,
                mouseY
        );
    }

    private void renderTargetSlot(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left = targetSlotX;
        int top = targetSlotY;

        int right =
                left + TARGET_SLOT_SIZE;

        int bottom =
                top + TARGET_SLOT_SIZE;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                0xFF202020
        );

        graphics.fill(
                left,
                top,
                right,
                top + 1,
                0xFFAAAAAA
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                0xFF555555
        );

        graphics.fill(
                left,
                top,
                left + 1,
                bottom,
                0xFFAAAAAA
        );

        graphics.fill(
                right - 1,
                top,
                right,
                bottom,
                0xFF555555
        );

        ItemStack targetStack =
                getTargetStack();

        if (!targetStack.isEmpty()) {

            graphics.renderItem(
                    targetStack,
                    left + 4,
                    top + 4
            );

            if (mouseX >= left
                    && mouseX < right
                    && mouseY >= top
                    && mouseY < bottom) {

                graphics.renderTooltip(
                        font,
                        targetStack,
                        mouseX,
                        mouseY
                );
            }

            graphics.drawCenteredString(
                    font,
                    targetStack.getHoverName(),
                    width / 2,
                    145,
                    0xFFFFFF
            );
        }
    }

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return;
        }

        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        int viewportHeight =
                viewportBottom - viewportTop;

        int treeLeft =
                Math.max(
                        TREE_SIDE_MARGIN,
                        width / 2 - 140
                );

        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                width / 2,
                TREE_TOP,
                0xFFFFFF
        );

        graphics.enableScissor(
                TREE_SIDE_MARGIN,
                viewportTop,
                width - TREE_SIDE_MARGIN,
                viewportBottom
        );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        renderRecipeNode(
                graphics,
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY,
                viewportTop,
                viewportBottom
        );

        graphics.disableScissor();

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight
        );
    }

    private int renderRecipeNode(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom
    ) {
        boolean hasChildren =
                !node.getChildren().isEmpty();

        boolean expanded =
                expandedNodes.contains(nodePath);

        int indent =
                depth * TREE_INDENT;

        int arrowX =
                treeLeft + indent;

        int iconX =
                arrowX + 12;

        if (rowY + TREE_ROW_HEIGHT >= viewportTop
                && rowY <= viewportBottom) {

            if (hasChildren) {

                graphics.drawString(
                        font,
                        expanded ? "▼" : "▶",
                        arrowX,
                        rowY + 4,
                        0xAAAAAA
                );
            }

            ItemStack displayStack =
                    getDisplayStack(node);

            graphics.renderItem(
                    displayStack,
                    iconX,
                    rowY
            );

            String text =
                    getNodeDisplayName(node)
                            + " x"
                            + node.getRequiredCount();

            graphics.drawString(
                    font,
                    text,
                    iconX + 20,
                    rowY + 4,
                    node.isCraftable()
                            ? 0xFFFFFF
                            : 0xCCCCCC
            );

            renderRecipeSelector(
                    graphics,
                    node,
                    nodePath,
                    iconX,
                    rowY,
                    text
            );

            if (mouseX >= iconX
                    && mouseX < iconX + 16
                    && mouseY >= rowY
                    && mouseY < rowY + 16
                    && mouseY >= viewportTop
                    && mouseY < viewportBottom) {

                graphics.renderTooltip(
                        font,
                        displayStack,
                        mouseX,
                        mouseY
                );
            }
        }

        int nextY =
                rowY + TREE_ROW_HEIGHT;

        if (expanded) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        buildChildPath(
                                nodePath,
                                i,
                                child
                        );

                nextY =
                        renderRecipeNode(
                                graphics,
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY,
                                viewportTop,
                                viewportBottom
                        );
            }
        }

        return nextY;
    }

    private void renderRecipeSelector(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null
                || choices.size() <= 1
                || node.getPreferredRecipeId() == null) {

            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(itemText)
                        + 8;

        int maxX =
                width
                        - TREE_SIDE_MARGIN
                        - font.width(selectorText)
                        - 8;

        int selectorX =
                Math.min(
                        desiredX,
                        maxX
                );

        graphics.drawString(
                font,
                selectorText,
                selectorX,
                rowY + 4,
                0x55FFFF
        );
    }

    private int getCurrentRecipeIndex(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null
                || choices.isEmpty()) {

            return 0;
        }

        ResourceLocation selected =
                node.getPreferredRecipeId();

        for (int i = 0;
             i < choices.size();
             i++) {

            if (choices.get(i)
                    .equals(selected)) {

                return i;
            }
        }

        return 0;
    }

    private ItemStack getDisplayStack(
            CraftScopeRecipeNode node
    ) {
        List<ItemStack> variants =
                node.getAcceptedVariants();

        if (variants.isEmpty()) {
            return node.getStack();
        }

        if (variants.size() == 1) {
            return variants.getFirst();
        }

        long cycle =
                System.currentTimeMillis()
                        / VARIANT_CYCLE_MS;

        int index =
                (int) (
                        cycle
                                % variants.size()
                );

        return variants
                .get(index)
                .copy();
    }

    private String getNodeDisplayName(
            CraftScopeRecipeNode node
    ) {
        List<ItemStack> variants =
                node.getAcceptedVariants();

        if (variants.size() <= 1) {
            return node
                    .getStack()
                    .getHoverName()
                    .getString();
        }

        String genericName =
                findGenericVariantName(
                        variants
                );

        if (genericName != null) {
            return "Any " + genericName;
        }

        return "Any Valid Ingredient";
    }

    private String findGenericVariantName(
            List<ItemStack> variants
    ) {
        if (variants.isEmpty()) {
            return null;
        }

        boolean allLogs = true;
        boolean allPlanks = true;
        boolean allWool = true;

        for (ItemStack variant : variants) {

            if (!variant.is(ItemTags.LOGS)) {
                allLogs = false;
            }

            if (!variant.is(ItemTags.PLANKS)) {
                allPlanks = false;
            }

            if (!variant.is(ItemTags.WOOL)) {
                allWool = false;
            }
        }

        if (allLogs) {
            return "Log";
        }

        if (allPlanks) {
            return "Planks";
        }

        if (allWool) {
            return "Wool";
        }

        String commonSuffix = null;

        for (ItemStack variant : variants) {

            String name =
                    variant.getHoverName()
                            .getString();

            String suffix =
                    extractLastWord(name);

            if (suffix.isEmpty()) {
                return null;
            }

            if (commonSuffix == null) {

                commonSuffix = suffix;

            } else if (!commonSuffix.equalsIgnoreCase(
                    suffix
            )) {

                return null;
            }
        }

        return commonSuffix;
    }

    private String extractLastWord(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String trimmed =
                value.trim();

        if (trimmed.isEmpty()) {
            return "";
        }

        int space =
                trimmed.lastIndexOf(' ');

        if (space < 0) {
            return trimmed;
        }

        return trimmed.substring(
                space + 1
        );
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            int viewportTop,
            int viewportBottom,
            int viewportHeight
    ) {
        int contentHeight =
                getVisibleNodeCount()
                        * TREE_ROW_HEIGHT;

        if (contentHeight <= viewportHeight) {
            return;
        }

        int barX =
                width - TREE_SIDE_MARGIN - 4;

        graphics.fill(
                barX,
                viewportTop,
                barX + 3,
                viewportBottom,
                0xFF303030
        );

        int thumbHeight =
                Math.max(
                        12,
                        viewportHeight
                                * viewportHeight
                                / contentHeight
                );

        int maxScroll =
                Math.max(
                        1,
                        contentHeight - viewportHeight
                );

        int travel =
                viewportHeight - thumbHeight;

        int thumbOffset =
                (int) (
                        (treeScroll / maxScroll)
                                * travel
                );

        graphics.fill(
                barX,
                viewportTop + thumbOffset,
                barX + 3,
                viewportTop
                        + thumbOffset
                        + thumbHeight,
                0xFFAAAAAA
        );
    }

    private int getVisibleNodeCount() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return 0;
        }

        return countVisibleNodes(
                currentTree.getRoot(),
                "root"
        );
    }

    private int countVisibleNodes(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        int count = 1;

        if (!expandedNodes.contains(nodePath)) {
            return count;
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0;
             i < children.size();
             i++) {

            CraftScopeRecipeNode child =
                    children.get(i);

            String childPath =
                    buildChildPath(
                            nodePath,
                            i,
                            child
                    );

            count +=
                    countVisibleNodes(
                            child,
                            childPath
                    );
        }

        return count;
    }

    private void clampTreeScroll() {
        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        int viewportHeight =
                viewportBottom - viewportTop;

        int contentHeight =
                getVisibleNodeCount()
                        * TREE_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight - viewportHeight
                );

        treeScroll =
                Math.max(
                        0,
                        Math.min(
                                treeScroll,
                                maxScroll
                        )
                );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0
                && handleTreeClick(
                        mouseX,
                        mouseY
                )) {

            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private boolean handleTreeClick(
            double mouseX,
            double mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return false;
        }

        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        if (mouseY < viewportTop
                || mouseY >= viewportBottom) {

            return false;
        }

        int treeLeft =
                Math.max(
                        TREE_SIDE_MARGIN,
                        width / 2 - 140
                );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        return handleNodeClick(
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY
        ).handled();
    }

    private ClickResult handleNodeClick(
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            double mouseX,
            double mouseY
    ) {
        int indent =
                depth * TREE_INDENT;

        int arrowX =
                treeLeft + indent;

        int iconX =
                arrowX + 12;

        String itemText =
                getNodeDisplayName(node)
                        + " x"
                        + node.getRequiredCount();

        /*
         * Check the alternate-recipe selector before checking
         * the tree expand/collapse arrow.
         */
        if (isRecipeSelectorClicked(
                node,
                nodePath,
                iconX,
                rowY,
                itemText,
                mouseX,
                mouseY
        )) {

            cycleRecipe(
                    nodePath,
                    node
            );

            return new ClickResult(
                    true,
                    rowY + TREE_ROW_HEIGHT
            );
        }

        boolean hasChildren =
                !node.getChildren().isEmpty();

        if (hasChildren
                && mouseY >= rowY
                && mouseY < rowY + TREE_ROW_HEIGHT
                && mouseX >= arrowX - 2
                && mouseX < arrowX + 28) {

            if (expandedNodes.contains(nodePath)) {

                expandedNodes.remove(nodePath);

            } else {

                expandedNodes.add(nodePath);
            }

            clampTreeScroll();

            return new ClickResult(
                    true,
                    rowY + TREE_ROW_HEIGHT
            );
        }

        int nextY =
                rowY + TREE_ROW_HEIGHT;

        if (expandedNodes.contains(nodePath)) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        buildChildPath(
                                nodePath,
                                i,
                                child
                        );

                ClickResult result =
                        handleNodeClick(
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY
                        );

                if (result.handled()) {
                    return result;
                }

                nextY =
                        result.nextY();
            }
        }

        return new ClickResult(
                false,
                nextY
        );
    }

    private boolean isRecipeSelectorClicked(
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText,
            double mouseX,
            double mouseY
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null
                || choices.size() <= 1) {

            return false;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(itemText)
                        + 8;

        int maxX =
                width
                        - TREE_SIDE_MARGIN
                        - font.width(selectorText)
                        - 8;

        int selectorX =
                Math.min(
                        desiredX,
                        maxX
                );

        return mouseX >= selectorX - 2
                && mouseX
                < selectorX
                + font.width(selectorText)
                + 2
                && mouseY >= rowY
                && mouseY
                < rowY + TREE_ROW_HEIGHT;
    }

    private void cycleRecipe(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null
                || choices.size() <= 1) {

            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        int nextIndex =
                (currentIndex + 1)
                        % choices.size();

        ResourceLocation nextRecipe =
                choices.get(nextIndex);

        /*
         * Recipe index 0 is CraftScope's preferred/default
         * recipe. It does not need to be stored as an
         * override.
         */
        if (nextIndex == 0) {

            recipeOverrides.remove(nodePath);

            project.removeRecipeOverride(
                    nodePath
            );

        } else {

            recipeOverrides.put(
                    nodePath,
                    nextRecipe
            );

            project.setRecipeOverride(
                    nodePath,
                    nextRecipe.toString()
            );
        }

        /*
         * Changing a recipe can completely replace this
         * node's children.
         *
         * Any recipe selections below this point may therefore
         * refer to branches that no longer exist.
         */
        clearDescendantRecipeState(
                nodePath
        );

        CraftScopeProjectManager.save();

        rebuildTree();
    }

    private void clearDescendantRecipeState(
            String nodePath
    ) {
        String prefix =
                nodePath + "/";

        recipeOverrides
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(prefix)
                );

        recipeChoices
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(prefix)
                );

        expandedNodes
                .removeIf(
                        key ->
                                key.startsWith(prefix)
                );

        List<String> savedDescendants =
                new ArrayList<>();

        for (String key :
                project.getRecipeOverrides()
                        .keySet()) {

            if (key.startsWith(prefix)) {
                savedDescendants.add(key);
            }
        }

        for (String key :
                savedDescendants) {

            project.removeRecipeOverride(key);
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int viewportTop =
                TREE_TOP + 18;

        int viewportBottom =
                Math.max(
                        viewportTop + TREE_ROW_HEIGHT,
                        height - TREE_BOTTOM_MARGIN
                );

        if (mouseY >= viewportTop
                && mouseY < viewportBottom) {

            treeScroll -=
                    scrollY
                            * SCROLL_AMOUNT;

            clampTreeScroll();

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    private ItemStack getTargetStack() {
        String itemId =
                project.getTargetItemId();

        if (itemId == null
                || itemId.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ResourceLocation location =
                ResourceLocation.tryParse(itemId);

        if (location == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(location);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private String buildChildPath(
            String parentPath,
            int childIndex,
            CraftScopeRecipeNode child
    ) {
        return parentPath
                + "/"
                + childIndex
                + ":"
                + getItemId(
                        child.getStack()
                );
    }

    private String getItemId(
            ItemStack stack
    ) {
        return BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString();
    }

    @Override
    public void craftscope$setTargetItem(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        project.setTargetItemId(
                itemId.toString()
        );

        /*
         * A different target produces a completely different
         * recipe tree, so recipe selections belonging to the
         * previous target must not carry over.
         */
        project.clearRecipeOverrides();

        expandedNodes.clear();
        recipeOverrides.clear();
        recipeChoices.clear();

        treeScroll = 0;

        CraftScopeProjectManager.save();

        rebuildTree();
    }

    @Override
    public int craftscope$getTargetSlotX() {
        return targetSlotX;
    }

    @Override
    public int craftscope$getTargetSlotY() {
        return targetSlotY;
    }

    @Override
    public int craftscope$getTargetSlotWidth() {
        return TARGET_SLOT_SIZE;
    }

    @Override
    public int craftscope$getTargetSlotHeight() {
        return TARGET_SLOT_SIZE;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record ClickResult(
            boolean handled,
            int nextY
    ) {
    }
}