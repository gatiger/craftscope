package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummary;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummarizer;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeResolver;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftScopeProjectScreen
        extends CraftScopeBaseScreen
        implements CraftScopeTargetItemReceiver {

    /*
     * ---------------------------------------------------------
     * Layout
     * ---------------------------------------------------------
     */

    private static final int WINDOW_MARGIN =
            8;

    /*
     * On Recipe Tree, this area is left available for JEI.
     *
     * Other tabs reclaim this space because JEI is hidden.
     */
    private static final int JEI_RESERVED_WIDTH =
            170;

    private static final int HEADER_HEIGHT =
            30;

    private static final int TARGET_ROW_HEIGHT =
            34;

    private static final int TAB_BAR_HEIGHT =
            26;

    private static final int TARGET_SLOT_SIZE =
            24;

    private static final int CONTENT_SIDE_MARGIN =
            10;

    private static final int CONTENT_ROW_HEIGHT =
            20;

    private static final int TREE_INDENT =
            18;

    private static final int VIEW_BUTTON_HEIGHT =
            20;

    private static final int VIEW_BUTTON_GAP =
            3;

    private static final int VIEW_BUTTON_Y =
            WINDOW_MARGIN
                    + HEADER_HEIGHT
                    + TARGET_ROW_HEIGHT
                    + 2;

    private static final int CONTENT_TITLE_Y =
            VIEW_BUTTON_Y
                    + TAB_BAR_HEIGHT
                    + 5;

    private static final int CONTENT_VIEWPORT_TOP =
            CONTENT_TITLE_Y
                    + 19;

    private static final int CONTENT_BOTTOM_MARGIN =
            10;

    private static final int SCROLL_AMOUNT =
            20;

    private static final long VARIANT_CYCLE_MS =
            1000L;

    /*
     * ---------------------------------------------------------
     * Project state
     * ---------------------------------------------------------
     */

    private final Screen parent;
    private final CraftScopeProject project;

    /*
     * Recipe-tree nodes start collapsed.
     */
    private final Set<String> expandedNodes =
            new HashSet<>();

    /*
     * Active project recipe overrides.
     */
    private final Map<String, ResourceLocation> recipeOverrides =
            new HashMap<>();

    /*
     * Stable ordering used by each [1/2] recipe selector.
     */
    private final Map<String, List<ResourceLocation>> recipeChoices =
            new HashMap<>();

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

    private CraftScopeFlatButton recipeTreeButton;
    private CraftScopeFlatButton totalMaterialsButton;
    private CraftScopeFlatButton processDiagramButton;
    private CraftScopeFlatButton setupButton;

    private ViewMode activeView =
            ViewMode.RECIPE_TREE;

    private double treeScroll;
    private double materialScroll;

    private CraftScopeRecipeTree currentTree;

    private CraftScopeMaterialSummary currentMaterialSummary =
            new CraftScopeMaterialSummary(
                    List.of()
            );

    public CraftScopeProjectScreen(
            Screen parent,
            CraftScopeProject project
    ) {
        super(
                Component.literal(
                        project.getName()
                )
        );

        this.parent =
                parent;

        this.project =
                project;

        loadRecipeOverrides();
    }

    /*
     * ---------------------------------------------------------
     * Initialization
     * ---------------------------------------------------------
     */

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

        int windowLeft =
                getWindowLeft();

        int controlRight =
                getRecipeSafeRight();

        /*
         * -----------------------------------------------------
         * Compact target row
         * -----------------------------------------------------
         */

        int targetRowTop =
                WINDOW_MARGIN
                        + HEADER_HEIGHT;

        targetSlotX =
                windowLeft
                        + 70;

        targetSlotY =
                targetRowTop
                        + 5;

        int plusX =
                controlRight
                        - 12
                        - 20;

        int quantityBoxX =
                plusX
                        - 3
                        - 42;

        int minusX =
                quantityBoxX
                        - 3
                        - 20;

        int controlY =
                targetRowTop
                        + 8;

        quantityField =
                new EditBox(
                        font,
                        quantityBoxX + 4,
                        controlY + 5,
                        34,
                        9,
                        Component.literal(
                                "Quantity"
                        )
                );

        quantityField.setBordered(
                false
        );

        quantityField.setValue(
                Integer.toString(
                        project.getTargetCount()
                )
        );

        quantityField.setFilter(
                value ->
                        value.isEmpty()
                                || value.matches(
                                "\\d+"
                        )
        );

        quantityField.setResponder(
                this::craftscope$quantityChanged
        );

        addRenderableWidget(
                quantityField
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        minusX,
                        controlY,
                        20,
                        18,
                        Component.literal("-"),
                        () ->
                                craftscope$changeQuantity(
                                        -1
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        plusX,
                        controlY,
                        20,
                        18,
                        Component.literal("+"),
                        () ->
                                craftscope$changeQuantity(
                                        1
                                )
                )
        );

        /*
         * -----------------------------------------------------
         * Main tabs
         * -----------------------------------------------------
         */

        int tabX =
                windowLeft
                        + 8;

        int recipeWidth =
                82;

        int materialsWidth =
                96;

        int diagramWidth =
                108;

        int setupWidth =
                58;

        recipeTreeButton =
                addRenderableWidget(
                        new CraftScopeFlatButton(
                                tabX,
                                VIEW_BUTTON_Y,
                                recipeWidth,
                                VIEW_BUTTON_HEIGHT,
                                Component.literal(
                                        "Recipe Tree"
                                ),
                                () ->
                                        setActiveView(
                                                ViewMode.RECIPE_TREE
                                        )
                        )
                );

        tabX +=
                recipeWidth
                        + VIEW_BUTTON_GAP;

        totalMaterialsButton =
                addRenderableWidget(
                        new CraftScopeFlatButton(
                                tabX,
                                VIEW_BUTTON_Y,
                                materialsWidth,
                                VIEW_BUTTON_HEIGHT,
                                Component.literal(
                                        "Total Materials"
                                ),
                                () ->
                                        setActiveView(
                                                ViewMode.TOTAL_MATERIALS
                                        )
                        )
                );

        tabX +=
                materialsWidth
                        + VIEW_BUTTON_GAP;

        processDiagramButton =
                addRenderableWidget(
                        new CraftScopeFlatButton(
                                tabX,
                                VIEW_BUTTON_Y,
                                diagramWidth,
                                VIEW_BUTTON_HEIGHT,
                                Component.literal(
                                        "Process Diagram"
                                ),
                                () ->
                                        setActiveView(
                                                ViewMode.PROCESS_DIAGRAM
                                        )
                        )
                );

        tabX +=
                diagramWidth
                        + VIEW_BUTTON_GAP;

        setupButton =
                addRenderableWidget(
                        new CraftScopeFlatButton(
                                tabX,
                                VIEW_BUTTON_Y,
                                setupWidth,
                                VIEW_BUTTON_HEIGHT,
                                Component.literal(
                                        "Setup"
                                ),
                                () ->
                                        setActiveView(
                                                ViewMode.SETUP
                                        )
                        )
                );

        /*
         * -----------------------------------------------------
         * Header controls
         * -----------------------------------------------------
         */

        int headerButtonY =
                WINDOW_MARGIN
                        + 6;

        int exitWidth =
                38;

        int optionsWidth =
                52;

        int helpWidth =
                42;

        int gap =
                3;

        int exitX =
                controlRight
                        - 8
                        - exitWidth;

        int optionsX =
                exitX
                        - gap
                        - optionsWidth;

        int helpX =
                optionsX
                        - gap
                        - helpWidth;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        helpX,
                        headerButtonY,
                        helpWidth,
                        18,
                        Component.literal(
                                "Help"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeGuideScreen(
                                                this
                                        )
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        optionsX,
                        headerButtonY,
                        optionsWidth,
                        18,
                        Component.literal(
                                "Options"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeProjectOptionsScreen(
                                                this,
                                                parent,
                                                project
                                        )
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        exitX,
                        headerButtonY,
                        exitWidth,
                        18,
                        Component.literal(
                                "Exit"
                        ),
                        () ->
                                minecraft.setScreen(
                                        parent
                                )
                )
        );

        treeScroll =
                0;

        materialScroll =
                0;

        updateViewButtons();

        rebuildTree();
    }

    /*
     * ---------------------------------------------------------
     * View switching
     * ---------------------------------------------------------
     */

    private void setActiveView(
            ViewMode viewMode
    ) {
        activeView =
                viewMode;

        switch (activeView) {

            case RECIPE_TREE ->
                    clampTreeScroll();

            case TOTAL_MATERIALS ->
                    clampMaterialScroll();

            case PROCESS_DIAGRAM,
                 SETUP -> {
            }
        }

        updateViewButtons();
    }

    private void updateViewButtons() {
        if (recipeTreeButton != null) {
            recipeTreeButton.setSelected(
                    activeView
                            == ViewMode.RECIPE_TREE
            );
        }

        if (totalMaterialsButton != null) {
            totalMaterialsButton.setSelected(
                    activeView
                            == ViewMode.TOTAL_MATERIALS
            );
        }

        if (processDiagramButton != null) {
            processDiagramButton.setSelected(
                    activeView
                            == ViewMode.PROCESS_DIAGRAM
            );
        }

        if (setupButton != null) {
            setupButton.setSelected(
                    activeView
                            == ViewMode.SETUP
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * Responsive CraftScope window
     * ---------------------------------------------------------
     */

    private int getWindowLeft() {
        return WINDOW_MARGIN;
    }

    private int getWindowTop() {
        return WINDOW_MARGIN;
    }

    private int getWindowBottom() {
        return height
                - WINDOW_MARGIN;
    }

    /*
     * This is the permanent JEI-safe boundary.
     *
     * Header controls and target quantity stay inside this
     * boundary even when another tab expands farther right.
     */
    private int getRecipeSafeRight() {
        int fullRight =
                width
                        - WINDOW_MARGIN;

        int desiredRight =
                fullRight
                        - JEI_RESERVED_WIDTH;

        int minimumRight =
                getWindowLeft()
                        + 420;

        return Math.min(
                fullRight,
                Math.max(
                        minimumRight,
                        desiredRight
                )
        );
    }

    private int getWindowRight() {
        if (activeView
                == ViewMode.RECIPE_TREE) {

            return getRecipeSafeRight();
        }

        return width
                - WINDOW_MARGIN;
    }

    private int getContentLeft() {
        return getWindowLeft()
                + CONTENT_SIDE_MARGIN;
    }

    private int getContentRight() {
        return getWindowRight()
                - CONTENT_SIDE_MARGIN;
    }

    private int getContentCenterX() {
        return (
                getContentLeft()
                        + getContentRight()
        ) / 2;
    }

    /*
     * These public bounds are used by the JEI integrations so
     * JEI and CraftScope share the exact same layout information.
     */
    public int craftscope$getWindowLeft() {
        return getWindowLeft();
    }

    public int craftscope$getWindowTop() {
        return getWindowTop();
    }

    public int craftscope$getWindowRight() {
        return getWindowRight();
    }

    public int craftscope$getWindowBottom() {
        return getWindowBottom();
    }

    /*
     * ---------------------------------------------------------
     * Recipe tree building
     * ---------------------------------------------------------
     */

    private void rebuildTree() {
        ItemStack target =
                getTargetStack();

        if (target.isEmpty()) {

            currentTree =
                    null;

            currentMaterialSummary =
                    new CraftScopeMaterialSummary(
                            List.of()
                    );

            treeScroll =
                    0;

            materialScroll =
                    0;

            return;
        }

        currentTree =
                CraftScopeRecipeResolver.resolveTree(
                        target,
                        project.getTargetCount(),
                        recipeOverrides
                );

        currentMaterialSummary =
                CraftScopeMaterialSummarizer.summarize(
                        currentTree
                );

        populateRecipeChoices();

        clampTreeScroll();
        clampMaterialScroll();
    }

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
                && !recipeChoices.containsKey(
                nodePath
        )) {

            List<ResourceLocation> choices =
                    new ArrayList<>();

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

                choices.add(
                        selected
                );

                choices.addAll(
                        node.getAlternativeRecipeIds()
                );

            } else {

                List<ResourceLocation> allRecipes =
                        new ArrayList<>(
                                node.getAlternativeRecipeIds()
                        );

                allRecipes.add(
                        savedOverride
                );

                ResourceLocation defaultRecipe =
                        null;

                for (ResourceLocation id :
                        node.getAlternativeRecipeIds()) {

                    if (!id.equals(
                            savedOverride
                    )) {

                        defaultRecipe =
                                id;

                        break;
                    }
                }

                if (defaultRecipe != null) {
                    choices.add(
                            defaultRecipe
                    );
                }

                for (ResourceLocation id :
                        allRecipes) {

                    if (!choices.contains(
                            id
                    )) {

                        choices.add(
                                id
                        );
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

    /*
     * ---------------------------------------------------------
     * Quantity
     * ---------------------------------------------------------
     */

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
                Integer.toString(
                        updated
                )
        );
    }

    private int craftscope$getQuantityFromField() {
        try {

            int value =
                    Integer.parseInt(
                            quantityField.getValue()
                    );

            return Math.max(
                    1,
                    value
            );

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
                    Integer.parseInt(
                            value
                    );

            if (quantity < 1) {
                return;
            }

            project.setTargetCount(
                    quantity
            );

            CraftScopeProjectManager.save();

            rebuildTree();

        } catch (NumberFormatException ignored) {
        }
    }

    /*
     * ---------------------------------------------------------
     * Main rendering
     * ---------------------------------------------------------
     */

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int windowLeft =
                getWindowLeft();

        int windowTop =
                getWindowTop();

        int windowRight =
                getWindowRight();

        int windowBottom =
                getWindowBottom();

        /*
         * Dim the world but keep it faintly recognizable.
         */
        CraftScopeUiTheme.drawBackdrop(
                graphics,
                width,
                height
        );

        /*
         * Main CraftScope application window.
         */
        CraftScopeUiTheme.drawWindow(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowBottom
        );

        /*
         * Header.
         */
        CraftScopeUiTheme.drawHeader(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowTop
                        + HEADER_HEIGHT
        );

        renderHeader(
                graphics
        );

        renderTargetRow(
                graphics,
                mouseX,
                mouseY
        );

        /*
         * Dark integrated tab strip.
         */
        graphics.fill(
                windowLeft + 1,
                VIEW_BUTTON_Y - 3,
                windowRight - 1,
                VIEW_BUTTON_Y
                        + TAB_BAR_HEIGHT,
                CraftScopeUiTheme.TAB_BAR_BACKGROUND
        );

        graphics.fill(
                windowLeft + 1,
                VIEW_BUTTON_Y
                        + TAB_BAR_HEIGHT
                        - 1,
                windowRight - 1,
                VIEW_BUTTON_Y
                        + TAB_BAR_HEIGHT,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        /*
         * Active content.
         */
        switch (activeView) {

            case RECIPE_TREE ->
                    renderRecipeTree(
                            graphics,
                            mouseX,
                            mouseY
                    );

            case TOTAL_MATERIALS ->
                    renderTotalMaterials(
                            graphics,
                            mouseX,
                            mouseY
                    );

            case PROCESS_DIAGRAM ->
                    renderProcessDiagramPlaceholder(
                            graphics
                    );

            case SETUP ->
                    renderSetupPlaceholder(
                            graphics
                    );
        }

        /*
         * Widgets are rendered last so buttons and edit boxes
         * remain above the application surfaces.
         */
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderHeader(
            GuiGraphics graphics
    ) {
        int windowLeft =
                getWindowLeft();

        int windowTop =
                getWindowTop();

        CraftScopeUiTheme.drawPlaceholderLogo(
                graphics,
                windowLeft + 7,
                windowTop + 6,
                18
        );

        graphics.drawString(
                font,
                "CraftScope",
                windowLeft + 31,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawString(
                font,
                "Project: "
                        + project.getName(),
                windowLeft + 105,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_SECONDARY
        );
    }

    private void renderTargetRow(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int windowLeft =
                getWindowLeft();

        int windowRight =
                getWindowRight();

        int targetRowTop =
                getWindowTop()
                        + HEADER_HEIGHT;

        graphics.fill(
                windowLeft + 1,
                targetRowTop,
                windowRight - 1,
                targetRowTop
                        + TARGET_ROW_HEIGHT,
                CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

        graphics.fill(
                windowLeft + 1,
                targetRowTop
                        + TARGET_ROW_HEIGHT
                        - 1,
                windowRight - 1,
                targetRowTop
                        + TARGET_ROW_HEIGHT,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        graphics.drawString(
                font,
                "Target",
                windowLeft + 12,
                targetRowTop + 13,
                CraftScopeUiTheme.TEXT_MUTED
        );

        renderTargetSlot(
                graphics,
                mouseX,
                mouseY
        );

        ItemStack targetStack =
                getTargetStack();

        if (targetStack.isEmpty()) {

            graphics.drawString(
                    font,
                    "Drop an item here from JEI/EMI",
                    targetSlotX
                            + TARGET_SLOT_SIZE
                            + 8,
                    targetRowTop + 13,
                    CraftScopeUiTheme.TEXT_MUTED
            );

        } else {

            graphics.drawString(
                    font,
                    targetStack
                            .getHoverName()
                            .getString(),
                    targetSlotX
                            + TARGET_SLOT_SIZE
                            + 8,
                    targetRowTop + 13,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        int controlRight =
                getRecipeSafeRight();

        int plusX =
                controlRight
                        - 12
                        - 20;

        int quantityBoxX =
                plusX
                        - 3
                        - 42;

        int quantityBoxY =
                targetRowTop
                        + 8;

        graphics.fill(
                quantityBoxX,
                quantityBoxY,
                quantityBoxX + 42,
                quantityBoxY + 18,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                quantityBoxX,
                quantityBoxY,
                quantityBoxX + 42,
                quantityBoxY + 18,
                CraftScopeUiTheme.BORDER
        );

        int minusX =
                quantityBoxX
                        - 3
                        - 20;

        int quantityLabelX =
                minusX
                        - 8
                        - font.width(
                        "Quantity"
                );

        graphics.drawString(
                font,
                "Quantity",
                quantityLabelX,
                targetRowTop + 13,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    /*
     * ---------------------------------------------------------
     * Target slot
     * ---------------------------------------------------------
     */

    private void renderTargetSlot(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left =
                targetSlotX;

        int top =
                targetSlotY;

        int right =
                left
                        + TARGET_SLOT_SIZE;

        int bottom =
                top
                        + TARGET_SLOT_SIZE;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.BORDER_HOVER
        );

        ItemStack targetStack =
                getTargetStack();

        if (targetStack.isEmpty()) {
            return;
        }

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
    }

    /*
     * ---------------------------------------------------------
     * Recipe Tree
     * ---------------------------------------------------------
     */

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int panelLeft =
                getContentLeft();

        int panelRight =
                getContentRight();

        int panelTop =
                CONTENT_TITLE_Y - 4;

        int panelBottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        drawPanel(
                graphics,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                panelLeft + 1,
                panelTop + 1,
                panelRight - 1,
                CONTENT_VIEWPORT_TOP - 2
        );

        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                getContentCenterX(),
                CONTENT_TITLE_Y + 3,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (currentTree == null
                || currentTree.getRoot() == null) {

            graphics.drawCenteredString(
                    font,
                    "Select a target item to build a recipe tree.",
                    getContentCenterX(),
                    CONTENT_VIEWPORT_TOP + 12,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        int viewportHeight =
                viewportBottom
                        - viewportTop;

        int treeLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 140
                );

        graphics.enableScissor(
                getContentLeft() + 1,
                viewportTop,
                getContentRight() - 1,
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

        int contentHeight =
                getVisibleNodeCount()
                        * CONTENT_ROW_HEIGHT;

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight,
                contentHeight,
                treeScroll
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
                expandedNodes.contains(
                        nodePath
                );

        int indent =
                depth
                        * TREE_INDENT;

        int arrowX =
                treeLeft
                        + indent;

        int iconX =
                arrowX
                        + 12;

        if (rowY + CONTENT_ROW_HEIGHT >= viewportTop
                && rowY <= viewportBottom) {

            if (hasChildren) {

                graphics.drawString(
                        font,
                        expanded
                                ? "▼"
                                : "▶",
                        arrowX,
                        rowY + 4,
                        CraftScopeUiTheme.TEXT_SECONDARY
                );
            }

            ItemStack displayStack =
                    getDisplayStack(
                            node
                    );

            graphics.renderItem(
                    displayStack,
                    iconX,
                    rowY
            );

            String text =
                    getNodeDisplayName(
                            node
                    )
                            + " x"
                            + node.getRequiredCount();

            graphics.drawString(
                    font,
                    text,
                    iconX + 20,
                    rowY + 4,
                    node.isCraftable()
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
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
                rowY
                        + CONTENT_ROW_HEIGHT;

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
                recipeChoices.get(
                        nodePath
                );

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
                        + font.width(
                        itemText
                )
                        + 8;

        int maxX =
                getContentRight()
                        - font.width(
                        selectorText
                )
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
                0xFF55FFFF
        );
    }

    private int getCurrentRecipeIndex(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

        if (choices == null
                || choices.isEmpty()) {

            return 0;
        }

        ResourceLocation selected =
                node.getPreferredRecipeId();

        for (int i = 0;
             i < choices.size();
             i++) {

            if (choices
                    .get(i)
                    .equals(
                            selected
                    )) {

                return i;
            }
        }

        return 0;
    }

    /*
     * ---------------------------------------------------------
     * Total Materials
     * ---------------------------------------------------------
     */

    private void renderTotalMaterials(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int panelLeft =
                getContentLeft();

        int panelRight =
                getContentRight();

        int panelTop =
                CONTENT_TITLE_Y - 4;

        int panelBottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        drawPanel(
                graphics,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                panelLeft + 1,
                panelTop + 1,
                panelRight - 1,
                CONTENT_VIEWPORT_TOP - 2
        );

        graphics.drawCenteredString(
                font,
                "Total Materials",
                getContentCenterX(),
                CONTENT_TITLE_Y + 3,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (currentMaterialSummary == null
                || currentMaterialSummary.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "No materials to display.",
                    getContentCenterX(),
                    CONTENT_VIEWPORT_TOP + 12,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            return;
        }

        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        int viewportHeight =
                viewportBottom
                        - viewportTop;

        int listLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 120
                );

        graphics.enableScissor(
                getContentLeft() + 1,
                viewportTop,
                getContentRight() - 1,
                viewportBottom
        );

        int rowY =
                viewportTop
                        - (int) materialScroll;

        for (CraftScopeMaterialSummary.Entry entry :
                currentMaterialSummary.getEntries()) {

            if (rowY + CONTENT_ROW_HEIGHT >= viewportTop
                    && rowY <= viewportBottom) {

                renderMaterialEntry(
                        graphics,
                        entry,
                        listLeft,
                        rowY,
                        mouseX,
                        mouseY,
                        viewportTop,
                        viewportBottom
                );
            }

            rowY +=
                    CONTENT_ROW_HEIGHT;
        }

        graphics.disableScissor();

        int contentHeight =
                currentMaterialSummary.size()
                        * CONTENT_ROW_HEIGHT;

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight,
                contentHeight,
                materialScroll
        );
    }

    private void renderMaterialEntry(
            GuiGraphics graphics,
            CraftScopeMaterialSummary.Entry entry,
            int listLeft,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom
    ) {
        ItemStack displayStack =
                getMaterialDisplayStack(
                        entry
                );

        graphics.renderItem(
                displayStack,
                listLeft,
                rowY
        );

        String materialName =
                getMaterialDisplayName(
                        entry
                );

        graphics.drawString(
                font,
                materialName,
                listLeft + 20,
                rowY + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        String quantityText =
                "x"
                        + entry.getRequiredCount();

        int quantityX =
                Math.max(
                        listLeft + 120,
                        getContentCenterX() + 75
                );

        quantityX =
                Math.min(
                        quantityX,
                        getContentRight()
                                - font.width(
                                quantityText
                        )
                                - 10
                );

        graphics.drawString(
                font,
                quantityText,
                quantityX,
                rowY + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (mouseX >= listLeft
                && mouseX < listLeft + 16
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

    /*
     * ---------------------------------------------------------
     * Process Diagram shell
     * ---------------------------------------------------------
     *
     * This intentionally resembles the selected CraftScope
     * visual reference even though the route data is not wired
     * into the diagram yet.
     */

    private void renderProcessDiagramPlaceholder(
            GuiGraphics graphics
    ) {
        int left =
                getContentLeft();

        int right =
                getContentRight();

        int top =
                CONTENT_TITLE_Y - 4;

        int summaryHeight =
                74;

        int summaryTop =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN
                        - summaryHeight;

        int mainBottom =
                summaryTop
                        - 6;

        int availableWidth =
                right
                        - left;

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
         * Left column:
         *
         * Routes
         * Production Resources
         * Legend
         */

        int leftHeight =
                mainBottom
                        - top;

        int routesBottom =
                top
                        + Math.max(
                        76,
                        leftHeight * 43 / 100
                );

        int resourcesTop =
                routesBottom
                        + gap;

        int resourcesBottom =
                resourcesTop
                        + Math.max(
                        62,
                        leftHeight * 32 / 100
                );

        resourcesBottom =
                Math.min(
                        resourcesBottom,
                        mainBottom - 52
                );

        int legendTop =
                resourcesBottom
                        + gap;

        drawPanel(
                graphics,
                left,
                top,
                left + leftColumnWidth,
                routesBottom
        );

        drawPanel(
                graphics,
                left,
                resourcesTop,
                left + leftColumnWidth,
                resourcesBottom
        );

        drawPanel(
                graphics,
                left,
                legendTop,
                left + leftColumnWidth,
                mainBottom
        );

        drawSectionTitle(
                graphics,
                left,
                top,
                left + leftColumnWidth,
                "Production Routes"
        );

        drawSectionTitle(
                graphics,
                left,
                resourcesTop,
                left + leftColumnWidth,
                "Production Resources"
        );

        drawSectionTitle(
                graphics,
                left,
                legendTop,
                left + leftColumnWidth,
                "Legend"
        );

        graphics.drawString(
                font,
                "Routes for",
                left + 10,
                top + 28,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawString(
                font,
                getTargetStack().isEmpty()
                        ? "selected target"
                        : getTargetStack()
                        .getHoverName()
                        .getString(),
                left + 10,
                top + 42,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "Route data will",
                left + 10,
                top + 62,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawString(
                font,
                "appear here.",
                left + 10,
                top + 76,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawString(
                font,
                "+ Fluids",
                left + 10,
                resourcesTop + 28,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "+ Chemicals",
                left + 10,
                resourcesTop + 43,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "+ Gases",
                left + 10,
                resourcesTop + 58,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "→ Item",
                left + 10,
                legendTop + 28,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "→ Fluid",
                left + 10,
                legendTop + 43,
                0xFF4B9CFF
        );

        /*
         * Center diagram.
         */

        drawPanel(
                graphics,
                centerLeft,
                top,
                centerRight,
                mainBottom
        );

        drawSectionTitle(
                graphics,
                centerLeft,
                top,
                centerRight,
                "Process Diagram"
        );

        graphics.drawCenteredString(
                font,
                "Production route diagram",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 42,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawCenteredString(
                font,
                "Machine and item icons will be connected here.",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 60,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawCenteredString(
                font,
                "The diagram will support route selection,",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 82,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawCenteredString(
                font,
                "pan / zoom and expandable support resources.",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 97,
                CraftScopeUiTheme.TEXT_MUTED
        );

        /*
         * Right details panel.
         */

        int detailsLeft =
                centerRight
                        + gap;

        drawPanel(
                graphics,
                detailsLeft,
                top,
                right,
                mainBottom
        );

        drawSectionTitle(
                graphics,
                detailsLeft,
                top,
                right,
                "Selected Item / Machine"
        );

        graphics.drawCenteredString(
                font,
                "Select a diagram node",
                detailsLeft
                        + (
                        right
                                - detailsLeft
                ) / 2,
                top + 44,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawCenteredString(
                font,
                "to view details.",
                detailsLeft
                        + (
                        right
                                - detailsLeft
                ) / 2,
                top + 59,
                CraftScopeUiTheme.TEXT_MUTED
        );

        renderProcessSummaryBar(
                graphics,
                summaryTop,
                summaryHeight
        );
    }

    /*
     * ---------------------------------------------------------
     * Setup shell
     * ---------------------------------------------------------
     */

    private void renderSetupPlaceholder(
            GuiGraphics graphics
    ) {
        int left =
                getContentLeft();

        int right =
                getContentRight();

        int top =
                CONTENT_TITLE_Y - 4;

        int summaryHeight =
                74;

        int summaryTop =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN
                        - summaryHeight;

        int bottom =
                summaryTop
                        - 6;

        drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );

        drawSectionTitle(
                graphics,
                left,
                top,
                right,
                "Setup"
        );

        graphics.drawCenteredString(
                font,
                "Required machines and supporting infrastructure",
                getContentCenterX(),
                top + 44,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawCenteredString(
                font,
                "will be calculated from the selected production route.",
                getContentCenterX(),
                top + 61,
                CraftScopeUiTheme.TEXT_MUTED
        );

        renderProcessSummaryBar(
                graphics,
                summaryTop,
                summaryHeight
        );
    }

    /*
     * Three lower summary cards matching the current visual
     * reference:
     *
     * Required Machines
     * Required Resources
     * Outputs
     */

    private void renderProcessSummaryBar(
            GuiGraphics graphics,
            int top,
            int height
    ) {
        int left =
                getContentLeft();

        int right =
                getContentRight();

        int bottom =
                top
                        + height;

        int gap =
                6;

        int totalWidth =
                right
                        - left
                        - gap * 2;

        int machinesWidth =
                totalWidth * 36 / 100;

        int resourcesWidth =
                totalWidth * 36 / 100;

        int outputsWidth =
                totalWidth
                        - machinesWidth
                        - resourcesWidth;

        int machinesLeft =
                left;

        int machinesRight =
                machinesLeft
                        + machinesWidth;

        int resourcesLeft =
                machinesRight
                        + gap;

        int resourcesRight =
                resourcesLeft
                        + resourcesWidth;

        int outputsLeft =
                resourcesRight
                        + gap;

        int outputsRight =
                outputsLeft
                        + outputsWidth;

        drawPanel(
                graphics,
                machinesLeft,
                top,
                machinesRight,
                bottom
        );

        drawPanel(
                graphics,
                resourcesLeft,
                top,
                resourcesRight,
                bottom
        );

        drawPanel(
                graphics,
                outputsLeft,
                top,
                outputsRight,
                bottom
        );

        drawSectionTitle(
                graphics,
                machinesLeft,
                top,
                machinesRight,
                "Required Machines"
        );

        drawSectionTitle(
                graphics,
                resourcesLeft,
                top,
                resourcesRight,
                "Required Resources (Total)"
        );

        drawSectionTitle(
                graphics,
                outputsLeft,
                top,
                outputsRight,
                "Outputs"
        );

        graphics.drawCenteredString(
                font,
                "Machine requirements",
                machinesLeft
                        + machinesWidth / 2,
                top + 39,
                CraftScopeUiTheme.TEXT_MUTED
        );

        graphics.drawCenteredString(
                font,
                "Materials / fluids / chemicals",
                resourcesLeft
                        + resourcesWidth / 2,
                top + 39,
                CraftScopeUiTheme.TEXT_MUTED
        );

        ItemStack target =
                getTargetStack();

        if (!target.isEmpty()) {

            int outputCenter =
                    outputsLeft
                            + outputsWidth / 2;

            graphics.renderItem(
                    target,
                    outputCenter - 30,
                    top + 32
            );

            graphics.drawString(
                    font,
                    "x"
                            + project.getTargetCount(),
                    outputCenter - 8,
                    top + 36,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );

        } else {

            graphics.drawCenteredString(
                    font,
                    "No target selected",
                    outputsLeft
                            + outputsWidth / 2,
                    top + 39,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    private void drawSectionTitle(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            String title
    ) {
        int headerBottom =
                top + 22;

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                left + 1,
                top + 1,
                right - 1,
                headerBottom
        );

        graphics.drawString(
                font,
                title,
                left + 8,
                top + 7,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    private void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        CraftScopeUiTheme.drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );
    }

    /*
     * ---------------------------------------------------------
     * Variant display helpers
     * ---------------------------------------------------------
     */

    private ItemStack getDisplayStack(
            CraftScopeRecipeNode node
    ) {
        return getCyclingStack(
                node.getAcceptedVariants(),
                node.getStack()
        );
    }

    private ItemStack getMaterialDisplayStack(
            CraftScopeMaterialSummary.Entry entry
    ) {
        return getCyclingStack(
                entry.getAcceptedVariants(),
                entry.getStack()
        );
    }

    private ItemStack getCyclingStack(
            List<ItemStack> variants,
            ItemStack fallback
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return fallback.copy();
        }

        if (variants.size() == 1) {

            return variants
                    .getFirst()
                    .copy();
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
        return getVariantDisplayName(
                node.getAcceptedVariants(),
                node.getStack()
        );
    }

    private String getMaterialDisplayName(
            CraftScopeMaterialSummary.Entry entry
    ) {
        return getVariantDisplayName(
                entry.getAcceptedVariants(),
                entry.getStack()
        );
    }

    private String getVariantDisplayName(
            List<ItemStack> variants,
            ItemStack fallback
    ) {
        if (variants == null
                || variants.size() <= 1) {

            return fallback
                    .getHoverName()
                    .getString();
        }

        String genericName =
                findGenericVariantName(
                        variants
                );

        if (genericName != null) {

            return "Any "
                    + genericName;
        }

        return "Any Valid Ingredient";
    }

    private String findGenericVariantName(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return null;
        }

        boolean allLogs =
                true;

        boolean allPlanks =
                true;

        boolean allWool =
                true;

        for (ItemStack variant :
                variants) {

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
            return "Log";
        }

        if (allPlanks) {
            return "Planks";
        }

        if (allWool) {
            return "Wool";
        }

        return findCommonWordSuffix(
                variants
        );
    }

    private String findCommonWordSuffix(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return null;
        }

        List<String[]> names =
                new ArrayList<>();

        for (ItemStack variant :
                variants) {

            String name =
                    variant
                            .getHoverName()
                            .getString()
                            .trim();

            if (name.isEmpty()) {
                return null;
            }

            names.add(
                    name.split(
                            "\\s+"
                    )
            );
        }

        String[] first =
                names.getFirst();

        int commonWords =
                0;

        for (int offset = 1;
             offset <= first.length;
             offset++) {

            String expected =
                    first[
                            first.length
                                    - offset
                            ];

            boolean matchesAll =
                    true;

            for (int i = 1;
                 i < names.size();
                 i++) {

                String[] words =
                        names.get(i);

                if (words.length < offset
                        || !words[
                        words.length
                                - offset
                        ].equalsIgnoreCase(
                        expected
                )) {

                    matchesAll =
                            false;

                    break;
                }
            }

            if (!matchesAll) {
                break;
            }

            commonWords++;
        }

        if (commonWords == 0) {
            return null;
        }

        StringBuilder result =
                new StringBuilder();

        int start =
                first.length
                        - commonWords;

        for (int i = start;
             i < first.length;
             i++) {

            if (!result.isEmpty()) {
                result.append(
                        " "
                );
            }

            result.append(
                    first[i]
            );
        }

        return result.toString();
    }

    /*
     * ---------------------------------------------------------
     * Scroll bars
     * ---------------------------------------------------------
     */

    private void renderScrollbar(
            GuiGraphics graphics,
            int viewportTop,
            int viewportBottom,
            int viewportHeight,
            int contentHeight,
            double scroll
    ) {
        if (contentHeight <= viewportHeight) {
            return;
        }

        int barX =
                getContentRight()
                        - 5;

        graphics.fill(
                barX,
                viewportTop,
                barX + 3,
                viewportBottom,
                CraftScopeUiTheme.BORDER_SUBTLE
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
                        contentHeight
                                - viewportHeight
                );

        int travel =
                viewportHeight
                        - thumbHeight;

        int thumbOffset =
                (int) (
                        (scroll / maxScroll)
                                * travel
                );

        graphics.fill(
                barX,
                viewportTop + thumbOffset,
                barX + 3,
                viewportTop
                        + thumbOffset
                        + thumbHeight,
                CraftScopeUiTheme.TEXT_MUTED
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
        int count =
                1;

        if (!expandedNodes.contains(
                nodePath
        )) {

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

    private int getViewportBottom() {
        int bottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        if (activeView
                == ViewMode.PROCESS_DIAGRAM
                || activeView
                == ViewMode.SETUP) {

            bottom -=
                    80;
        }

        return Math.max(
                CONTENT_VIEWPORT_TOP
                        + CONTENT_ROW_HEIGHT,
                bottom
        );
    }

    private int getViewportHeight() {
        return getViewportBottom()
                - CONTENT_VIEWPORT_TOP;
    }

    private void clampTreeScroll() {
        int contentHeight =
                getVisibleNodeCount()
                        * CONTENT_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight
                                - getViewportHeight()
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

    private void clampMaterialScroll() {
        int contentHeight =
                currentMaterialSummary == null
                        ? 0
                        : currentMaterialSummary.size()
                        * CONTENT_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight
                                - getViewportHeight()
                );

        materialScroll =
                Math.max(
                        0,
                        Math.min(
                                materialScroll,
                                maxScroll
                        )
                );
    }

    /*
     * ---------------------------------------------------------
     * Recipe-tree interaction
     * ---------------------------------------------------------
     */

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (activeView
                == ViewMode.RECIPE_TREE
                && button == 0
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
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        if (mouseY < viewportTop
                || mouseY >= viewportBottom) {

            return false;
        }

        int treeLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 140
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
                depth
                        * TREE_INDENT;

        int arrowX =
                treeLeft
                        + indent;

        int iconX =
                arrowX
                        + 12;

        String itemText =
                getNodeDisplayName(
                        node
                )
                        + " x"
                        + node.getRequiredCount();

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
                    rowY
                            + CONTENT_ROW_HEIGHT
            );
        }

        boolean hasChildren =
                !node.getChildren().isEmpty();

        if (hasChildren
                && mouseY >= rowY
                && mouseY < rowY
                + CONTENT_ROW_HEIGHT
                && mouseX >= arrowX - 2
                && mouseX < arrowX + 28) {

            if (expandedNodes.contains(
                    nodePath
            )) {

                expandedNodes.remove(
                        nodePath
                );

            } else {

                expandedNodes.add(
                        nodePath
                );
            }

            clampTreeScroll();

            return new ClickResult(
                    true,
                    rowY
                            + CONTENT_ROW_HEIGHT
            );
        }

        int nextY =
                rowY
                        + CONTENT_ROW_HEIGHT;

        if (expandedNodes.contains(
                nodePath
        )) {

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
                recipeChoices.get(
                        nodePath
                );

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
                        + font.width(
                        itemText
                )
                        + 8;

        int maxX =
                getContentRight()
                        - font.width(
                        selectorText
                )
                        - 8;

        int selectorX =
                Math.min(
                        desiredX,
                        maxX
                );

        return mouseX >= selectorX - 2
                && mouseX
                < selectorX
                + font.width(
                selectorText
        )
                + 2
                && mouseY >= rowY
                && mouseY
                < rowY
                + CONTENT_ROW_HEIGHT;
    }

    private void cycleRecipe(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

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
                choices.get(
                        nextIndex
                );

        if (nextIndex == 0) {

            recipeOverrides.remove(
                    nodePath
            );

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
                nodePath
                        + "/";

        recipeOverrides
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        recipeChoices
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        expandedNodes
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        List<String> savedDescendants =
                new ArrayList<>();

        for (String key :
                project
                        .getRecipeOverrides()
                        .keySet()) {

            if (key.startsWith(
                    prefix
            )) {

                savedDescendants.add(
                        key
                );
            }
        }

        for (String key :
                savedDescendants) {

            project.removeRecipeOverride(
                    key
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * Mouse wheel
     * ---------------------------------------------------------
     */

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        if (mouseY >= viewportTop
                && mouseY < viewportBottom) {

            if (activeView
                    == ViewMode.RECIPE_TREE) {

                treeScroll -=
                        scrollY
                                * SCROLL_AMOUNT;

                clampTreeScroll();

                return true;
            }

            if (activeView
                    == ViewMode.TOTAL_MATERIALS) {

                materialScroll -=
                        scrollY
                                * SCROLL_AMOUNT;

                clampMaterialScroll();

                return true;
            }
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    /*
     * ---------------------------------------------------------
     * Target item
     * ---------------------------------------------------------
     */

    private ItemStack getTargetStack() {
        String itemId =
                project.getTargetItemId();

        if (itemId == null
                || itemId.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ResourceLocation location =
                ResourceLocation.tryParse(
                        itemId
                );

        if (location == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        location
                );

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(
                item
        );
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
                .getKey(
                        stack.getItem()
                )
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
         * A different target creates a different recipe tree, so
         * old node-path recipe overrides cannot safely survive.
         */
        project.clearRecipeOverrides();

        expandedNodes.clear();
        recipeOverrides.clear();
        recipeChoices.clear();

        treeScroll =
                0;

        materialScroll =
                0;

        CraftScopeProjectManager.save();

        rebuildTree();
    }

    /*
     * ---------------------------------------------------------
     * JEI / EMI target-slot API
     * ---------------------------------------------------------
     */

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

    /*
     * JEI uses this to decide whether its ingredient list should
     * be displayed.
     */
    public boolean craftscope$isRecipeTreeView() {
        return activeView
                == ViewMode.RECIPE_TREE;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(
                parent
        );
    }

    private enum ViewMode {

        RECIPE_TREE,

        TOTAL_MATERIALS,

        PROCESS_DIAGRAM,

        SETUP
    }

    private record ClickResult(
            boolean handled,
            int nextY
    ) {
    }
}