package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.QuPathTranslator;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;


public class CustomAnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener{
    // 定义一个回调接口，用于监听子对象计数标签的点击事件
    public interface CountLabelClickListener {
        void onCountLabelClicked(PathObject annotation);
    }
    
    private QuPathGUI qupath;
    private static final Logger logger = LoggerFactory.getLogger(CustomAnnotationPane.class);
    private ScrollPane pane;
    private VBox mdPane; // 保存对mdPane的引用
    
    private ImageData<BufferedImage> imageData;
    private PathObjectHierarchy hierarchy;
    
    private final BooleanProperty disableUpdates = new SimpleBooleanProperty(false);
    private final BooleanProperty hasImageData = new SimpleBooleanProperty(false);
    
    private final ObservableList<PathObject> allAnnotations = FXCollections.observableArrayList();
    private final FilteredList<PathObject> filteredAnnotations = new FilteredList<>(allAnnotations);
    private final ContextMenu menuAnnotations = new ContextMenu();
    
    private VBox annotationList;
    private PredicateTextField<PathObject> filter;
    
    private boolean suppressSelectionChanges = false;
    
    // 添加一个监听器列表
    private List<CountLabelClickListener> countLabelClickListeners = new ArrayList<>();
    
    // 添加一个Map来存储已选中的countLabel对应的annotation
    private Map<String, Boolean> selectedCountLabels = new HashMap<>();
    
    // 添加一个记录当前选中的countLabel的变量
    private Label currentSelectedCountLabel = null;
    
    // 添加分类列表的右键菜单变量和初始化方法
    private final ContextMenu menuClasses = new ContextMenu();
    
    // 在CustomAnnotationPane类中添加属性面板相关变量
    private VBox attributesBox;
    private VBox propertiesBox;
    private ObservableList<KeyValuePair> propertiesItems = FXCollections.observableArrayList();
    private TabPane attributesTabs;
    
    private ObservableMeasurementTableData tableModel = new ObservableMeasurementTableData();
    
    // 添加自动设置分类的属性
    private final BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
    
    public CustomAnnotationPane(QuPathGUI qupath){
        this.qupath = qupath;
        this.disableUpdates.addListener((v, o, n) -> {
            if (!n)
                enableUpdates();
        });
        
        initializeFilter();
        GuiTools.populateAnnotationsMenu(qupath, menuAnnotations);
        initializeClassesContextMenu();
        
        setImageData(qupath.getImageData());
        qupath.imageDataProperty().addListener(this);
        
        // 初始化自动设置类别的状态
        doAutoSetPathClass.addListener((e, f, g) -> updateAutoSetPathClassProperty());
        
        // 添加对分类列表的监听，当分类列表发生变化时更新UI
        qupath.getAvailablePathClasses().addListener((ListChangeListener<PathClass>) c -> {
            Platform.runLater(this::updateClassList);
        });
    }
    
    // 添加监听器方法
    public void addCountLabelClickListener(CountLabelClickListener listener) {
        if (listener != null && !countLabelClickListeners.contains(listener)) {
            countLabelClickListeners.add(listener);
        }
    }
    
    // 移除监听器方法
    public void removeCountLabelClickListener(CountLabelClickListener listener) {
        countLabelClickListeners.remove(listener);
    }
    
    // 触发监听器的方法
    private void fireCountLabelClicked(PathObject annotation) {
        for (CountLabelClickListener listener : countLabelClickListeners) {
            listener.onCountLabelClicked(annotation);
        }
    }
    
    private void initializeFilter() {
        filter = new PredicateTextField<>(PathObject::getDisplayedName);
        filter.setPromptText("搜索...");
        filter.setIgnoreCase(true);
        filteredAnnotations.predicateProperty().bind(filter.predicateProperty());
    }
    
    public ScrollPane getPane(){
        if (pane == null)
            pane = createPane();
        return pane;
    }
    
    public BooleanProperty disableUpdatesProperty() {
        return disableUpdates;
    }
    
    private void enableUpdates() {
        if (hierarchy == null)
            return;
        hierarchyChanged(PathObjectHierarchyEvent.createStructureChangeEvent(this, hierarchy, hierarchy.getRootObject()));
        selectedPathObjectChanged(hierarchy.getSelectionModel().getSelectedObject(), null, hierarchy.getSelectionModel().getSelectedObjects());
    }
    
    protected ScrollPane createPane(){
        mdPane = new VBox();
        mdPane.getStyleClass().add("custom-annotation-box");
        
        HBox topBarAnnotation = new HBox();
        topBarAnnotation.getStyleClass().add("custom-annotation-top-bar");
        mdPane.getChildren().add(topBarAnnotation);
        Label labelAnnotation = new Label("标注");
        labelAnnotation.getStyleClass().add("custom-annotation-label");
        Button selectAllBtn = new Button();
        selectAllBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.SELECT_ALL_BTN));
        selectAllBtn.getStyleClass().add("custom-annotation-button");
        selectAllBtn.setOnAction(e -> selectAllAnnotations());
        selectAllBtn.setTooltip(new Tooltip("选择所有标注"));
        Button deleteBtn = new Button();
        deleteBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.DELETE_BTN));
        deleteBtn.getStyleClass().add("custom-annotation-button");
        deleteBtn.setOnAction(e -> {
            if (imageData == null) {
                Dialogs.showErrorMessage("删除标注", "没有打开的图像");
                return;
            }
            GuiTools.promptToClearAllSelectedObjects(imageData);
        });
        deleteBtn.setTooltip(new Tooltip("删除选中的标注"));
        Button moreBtn = new Button();
        moreBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.MORE_BTN));
        moreBtn.getStyleClass().add("custom-annotation-button");
        moreBtn.setOnAction(e -> {
            menuAnnotations.show(moreBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        moreBtn.setTooltip(new Tooltip("更多选项"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBarAnnotation.getChildren().addAll(labelAnnotation, spacer, selectAllBtn, deleteBtn, moreBtn);

        annotationList = new VBox();
        VBox.setMargin(annotationList, new Insets(0, 0, 8, 0));
        mdPane.getChildren().add(annotationList);
        
        HBox classificationTopBox = new HBox();
        classificationTopBox.getStyleClass().add("custom-annotation-top-bar");
        VBox.setMargin(classificationTopBox, new Insets(0, 0, 4, 0));
        Label classificationLabel = new Label("分类");
        classificationLabel.getStyleClass().add("custom-annotation-label");
        Button searchBtn = new Button();
        searchBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.SEARCH_BTN));
        searchBtn.getStyleClass().add("custom-annotation-button");
        searchBtn.setTooltip(new Tooltip("搜索"));
        Button setSelectBtn = new Button();
        setSelectBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.SET_SELECT_BTN));
        setSelectBtn.getStyleClass().add("custom-annotation-button");
        setSelectBtn.setTooltip(new Tooltip("设置选中对象的分类"));
        // 为设置选中对象的分类按钮添加点击事件
        setSelectBtn.setOnAction(e -> promptToSetClass());
        
        Button autoSetBtn = new Button();
        autoSetBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.AUTO_SET_BTN));
        autoSetBtn.getStyleClass().add("custom-annotation-button");
        autoSetBtn.setTooltip(new Tooltip("自动设置新标注的分类"));
        // 为自动设置分类按钮添加点击事件，切换自动设置状态
        autoSetBtn.setOnAction(e -> {
            doAutoSetPathClass.set(!doAutoSetPathClass.get());
            updateAutoSetButtonStyle(autoSetBtn);
        });
        updateAutoSetButtonStyle(autoSetBtn); // 初始化按钮样式
        
        Button moreBtnClassification = new Button();
        moreBtnClassification.setGraphic(IconFactory.createNode(16, 16, PathIcons.MORE_BTN));
        moreBtnClassification.getStyleClass().add("custom-annotation-button");
        moreBtnClassification.setTooltip(new Tooltip("更多分类操作"));
        // 为更多分类操作按钮添加点击事件
        moreBtnClassification.setOnAction(e -> {
            // 创建分类操作菜单
            ContextMenu menu = createClassesActionMenu();
            menu.show(moreBtnClassification, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        
        Region spacerClassification = new Region();
        HBox.setHgrow(spacerClassification, Priority.ALWAYS);
        classificationTopBox.getChildren().addAll(classificationLabel, spacerClassification, searchBtn, setSelectBtn, autoSetBtn, moreBtnClassification);
        mdPane.getChildren().add(classificationTopBox);

        // 添加分类列表（使用GridPane，但初始为空）
        GridPane classList = new GridPane();
        classList.setId("classification-grid"); // 添加ID以便更容易查找
        VBox.setMargin(classList, new Insets(0, 0, 8, 0));
        classList.getStyleClass().add("custom-class-list");
        classList.setHgap(4);  // 水平间距
        classList.setVgap(4);  // 垂直间距
        classList.setMaxWidth(Double.MAX_VALUE); // 让GridPane填充可用宽度

        // 设置列宽约束，使三列均匀分布
        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(33.33);
        column1.setHgrow(Priority.ALWAYS);
        column1.setFillWidth(true);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(33.33);
        column2.setHgrow(Priority.ALWAYS);
        column2.setFillWidth(true);
        ColumnConstraints column3 = new ColumnConstraints();
        column3.setPercentWidth(33.33);
        column3.setHgrow(Priority.ALWAYS);
        column3.setFillWidth(true);
        classList.getColumnConstraints().addAll(column1, column2, column3);

        mdPane.getChildren().add(classList);
        
        // 添加属性面板
        VBox attributesPanel = createAttributesPanel();
        mdPane.getChildren().add(attributesPanel);
        
        pane = new ScrollPane(mdPane);
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        // 设置mdPane初始右侧边距
        mdPane.setPadding(new Insets(0, 12, 0, 0));
        
        // 监听滚动条显示状态，动态调整右侧padding
        pane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            Platform.runLater(() -> updatePadding());
        });
        pane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            Platform.runLater(() -> updatePadding());
        });
        
        updateAnnotationList();
        
        // 创建完UI后，异步更新分类列表
        Platform.runLater(this::updateClassList);
        
        return pane;
    }
    
    // 更新padding方法
    private void updatePadding() {
        if (pane == null || mdPane == null)
            return;
            
        // 判断是否显示了垂直滚动条
        boolean vbarVisible = pane.getWidth() < mdPane.getWidth() || 
                             pane.getViewportBounds().getHeight() < mdPane.getHeight();
                             
        // 根据滚动条显示状态设置右侧padding
        Insets currentPadding = mdPane.getPadding();
        if (vbarVisible) {
            // 有滚动条，右侧padding为0
            mdPane.setPadding(new Insets(
                currentPadding.getTop(),
                4,
                currentPadding.getBottom(),
                currentPadding.getLeft()
            ));
        } else {
            // 没有滚动条，右侧padding为12
            mdPane.setPadding(new Insets(
                currentPadding.getTop(),
                12,
                currentPadding.getBottom(),
                currentPadding.getLeft()
            ));
        }
    }
    
    // 更新自动设置按钮的样式，以反映当前状态
    private void updateAutoSetButtonStyle(Button autoSetBtn) {
        if (doAutoSetPathClass.get()) {
            // 选中状态 - 添加样式类
            if (!autoSetBtn.getStyleClass().contains("button-selected")) {
                autoSetBtn.getStyleClass().add("button-selected");
            }
        } else {
            // 未选中状态 - 移除样式类
            autoSetBtn.getStyleClass().remove("button-selected");
        }
    }
    
    // 创建分类相关操作的菜单
    private ContextMenu createClassesActionMenu() {
        ContextMenu menu = new ContextMenu();
        
        // 添加/移除分类子菜单
        Menu addRemoveMenu = new Menu("添加/移除");
        MenuItem miAddClass = new MenuItem("添加分类");
        miAddClass.setOnAction(e -> {
            String input = Dialogs.showInputDialog("添加分类", "分类名称", "");
            if (input != null && !input.trim().isEmpty() && !input.equalsIgnoreCase("null")) {
                PathClass pathClass = PathClass.fromString(input);
                if (!qupath.getAvailablePathClasses().contains(pathClass)) {
                    qupath.getAvailablePathClasses().add(pathClass);
                    // 更新分类列表UI
                    updateClassList();
                }
            }
        });
        
        MenuItem miRemoveClass = new MenuItem("移除选中分类");
        miRemoveClass.setOnAction(e -> {
            PathClass selectedClass = getSelectedPathClass();
            if (selectedClass != null && selectedClass != PathClass.NULL_CLASS) {
                if (Dialogs.showConfirmDialog("移除分类", "移除 '" + selectedClass.toString() + "' 从分类列表?")) {
                    qupath.getAvailablePathClasses().remove(selectedClass);
                    // 更新分类列表UI
                    updateClassList();
                }
            }
        });
        addRemoveMenu.getItems().addAll(miAddClass, miRemoveClass);
        
        // 从现有对象填充菜单项
        Menu populateFromObjects = new Menu("从现有对象填充");
        MenuItem miPopulateAll = new MenuItem("所有分类(包括子分类)");
        miPopulateAll.setOnAction(e -> {
            if (hierarchy != null) {
                // 获取图像中所有对象的分类
                List<PathClass> newClasses = hierarchy.getFlattenedObjectList(null)
                    .stream()
                    .filter(p -> !p.isRootObject())
                    .map(PathObject::getPathClass)
                    .filter(p -> p != null && p != PathClass.NULL_CLASS)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
                    
                if (!newClasses.isEmpty()) {
                    // 添加Ignore分类
                    newClasses.add(PathClass.StandardPathClasses.IGNORE);
                    // 添加到现有分类
                    boolean changed = false;
                    for (PathClass pc : newClasses) {
                        if (!qupath.getAvailablePathClasses().contains(pc)) {
                            qupath.getAvailablePathClasses().add(pc);
                            changed = true;
                        }
                    }
                    // 有变化时更新UI
                    if (changed) {
                        updateClassList();
                    }
                }
            }
        });
        
        MenuItem miPopulateBase = new MenuItem("仅基础分类");
        miPopulateBase.setOnAction(e -> {
            if (hierarchy != null) {
                // 获取图像中所有对象的基础分类
                List<PathClass> newClasses = hierarchy.getFlattenedObjectList(null)
                    .stream()
                    .filter(p -> !p.isRootObject())
                    .map(PathObject::getPathClass)
                    .filter(p -> p != null && p != PathClass.NULL_CLASS)
                    .map(PathClass::getBaseClass)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
                    
                if (!newClasses.isEmpty()) {
                    // 添加Ignore分类
                    newClasses.add(PathClass.StandardPathClasses.IGNORE);
                    // 添加到现有分类
                    boolean changed = false;
                    for (PathClass pc : newClasses) {
                        if (!qupath.getAvailablePathClasses().contains(pc)) {
                            qupath.getAvailablePathClasses().add(pc);
                            changed = true;
                        }
                    }
                    // 有变化时更新UI
                    if (changed) {
                        updateClassList();
                    }
                }
            }
        });
        populateFromObjects.getItems().addAll(miPopulateAll, miPopulateBase);
        
        // 从图像通道填充
        MenuItem miPopulateFromChannels = new MenuItem("从图像通道填充");
        miPopulateFromChannels.setOnAction(e -> {
            if (imageData == null)
                return;
                
            var server = imageData.getServer();
            List<PathClass> newClasses = new ArrayList<>();
            
            // 从通道创建分类
            for (var channel : server.getMetadata().getChannels()) {
                newClasses.add(PathClass.fromString(channel.getName(), channel.getColor()));
            }
            
            if (!newClasses.isEmpty()) {
                // 添加Ignore分类
                newClasses.add(PathClass.StandardPathClasses.IGNORE);
                // 添加到现有分类
                boolean changed = false;
                for (PathClass pc : newClasses) {
                    if (!qupath.getAvailablePathClasses().contains(pc)) {
                        qupath.getAvailablePathClasses().add(pc);
                        changed = true;
                    }
                }
                // 有变化时更新UI
                if (changed) {
                    updateClassList();
                }
            }
        });
        
        // 重置为默认分类
        MenuItem miResetToDefault = new MenuItem("重置为默认分类");
        miResetToDefault.setOnAction(e -> {
            if (Dialogs.showConfirmDialog("重置分类", "重置所有可用分类?")) {
                qupath.resetAvailablePathClasses();
                // 更新分类列表UI
                updateClassList();
            }
        });
        
        // 从项目导入分类
        MenuItem miImportFromProject = new MenuItem("从项目导入分类");
        miImportFromProject.setOnAction(e -> {
            File file = FileChoosers.promptForFile("导入分类", 
                FileChoosers.createExtensionFilter("QuPath项目", ProjectIO.getProjectExtension()));
                
            if (file != null && file.getAbsolutePath().toLowerCase().endsWith(ProjectIO.getProjectExtension())) {
                try {
                    Project<?> project = ProjectIO.loadProject(file, BufferedImage.class);
                    List<PathClass> pathClasses = project.getPathClasses();
                    if (!pathClasses.isEmpty()) {
                        // 添加到现有分类
                        boolean changed = false;
                        for (PathClass pc : pathClasses) {
                            if (!qupath.getAvailablePathClasses().contains(pc)) {
                                qupath.getAvailablePathClasses().add(pc);
                                changed = true;
                            }
                        }
                        // 有变化时更新UI
                        if (changed) {
                            updateClassList();
                        }
                    }
                } catch (Exception ex) {
                    Dialogs.showErrorMessage("读取项目错误", ex);
                    logger.error(ex.getMessage(), ex);
                }
            }
        });
        
        // 显示/隐藏菜单
        Menu showHideMenu = new Menu("显示/隐藏分类");
        MenuItem miShowSelected = new MenuItem("显示选中的分类");
        miShowSelected.setOnAction(e -> {
            PathClass selectedClass = getSelectedPathClass();
            if (selectedClass != null && qupath.getOverlayOptions() != null) {
                // 从隐藏列表中移除选中的分类，使其显示
                qupath.getOverlayOptions().selectedClassesProperty().remove(selectedClass);
            }
        });
        
        MenuItem miHideSelected = new MenuItem("隐藏选中的分类");
        miHideSelected.setOnAction(e -> {
            PathClass selectedClass = getSelectedPathClass();
            if (selectedClass != null && qupath.getOverlayOptions() != null) {
                // 添加到隐藏列表中
                if (!qupath.getOverlayOptions().selectedClassesProperty().contains(selectedClass)) {
                    qupath.getOverlayOptions().selectedClassesProperty().add(selectedClass);
                }
            }
        });
        
        showHideMenu.getItems().addAll(miShowSelected, miHideSelected);
        
        // 为显示/隐藏菜单项设置启用/禁用条件
        showHideMenu.setOnShowing(e -> {
            PathClass selectedClass = getSelectedPathClass();
            boolean hasSelection = selectedClass != null && selectedClass != PathClass.NULL_CLASS;
            miShowSelected.setDisable(!hasSelection);
            miHideSelected.setDisable(!hasSelection);
        });
        
        // 按分类选择对象
        MenuItem miSelectByClass = new MenuItem("按分类选择对象");
        miSelectByClass.setOnAction(e -> {
            PathClass selectedClass = getSelectedPathClass();
            if (selectedClass != null && imageData != null) {
                Commands.selectObjectsByClassification(imageData, selectedClass);
            }
        });
        // 添加所有菜单项
        menu.getItems().addAll(
            addRemoveMenu,
            populateFromObjects,
            miPopulateFromChannels,
            new SeparatorMenuItem(),
            miResetToDefault,
            miImportFromProject,
            showHideMenu,
            miSelectByClass
        );

        // 设置菜单项启用/禁用状态
        menu.setOnShowing(e -> {
            boolean hasImageData = imageData != null;
            boolean hasHierarchy = hierarchy != null;
            
            populateFromObjects.setDisable(!hasHierarchy);
            miPopulateFromChannels.setDisable(!hasImageData);
            miSelectByClass.setDisable(getSelectedPathClass() == null || !hasImageData);
        });
        
        return menu;
    }
    
    /**
     * 更新分类列表UI显示
     */
    private void updateClassList() {
        // 确保在JavaFX主线程执行UI更新
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateClassList);
            return;
        }
        
        // 查找分类网格
        if (pane == null || pane.getContent() == null) {
            logger.debug("无法更新分类列表：pane为空或未完成初始化");
            return;
        }
        
        // 使用ID查找GridPane
        Node content = pane.getContent();
        GridPane classList = (GridPane)content.lookup("#classification-grid");
        
        // 如果找不到，再尝试使用CSS类查找
        if (classList == null) {
            classList = (GridPane)content.lookup(".custom-class-list");
        }
        
        if (classList == null) {
            logger.warn("无法找到分类列表网格");
            return;
        }
        
        // 清空现有分类列表
        classList.getChildren().clear();
        
        // 从QuPath获取所有可用的分类
        List<PathClass> pathClasses = new ArrayList<>(qupath.getAvailablePathClasses());
        
        // 过滤掉空分类（如果有的话）
        pathClasses.removeIf(pc -> pc == null || pc.getName() == null || pc.getName().isEmpty());
        
        // 记录已处理的分类数量
        logger.debug("更新分类列表，找到 {} 个分类", pathClasses.size());
        
        // 创建类名称项目并添加到GridPane
        int row = 0;
        int col = 0;
        int maxCols = 3; // 每行显示3个项目
        
        for (PathClass pathClass : pathClasses) {
            if (pathClass == null) continue;
            
            // 创建分类项
            HBox classItem = createClassificationItem(pathClass);
            classItem.setMaxWidth(Double.MAX_VALUE);
            
            // 添加到网格的对应位置
            classList.add(classItem, col, row);
            
            // 更新行列索引
            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }
        
        // 通知界面刷新
        classList.requestLayout();
    }
    
    // 设置选中对象的分类
    private void promptToSetClass() {
        if (hierarchy == null)
            return;
        
        PathClass pathClass = getSelectedPathClass();
        if (pathClass == PathClass.NULL_CLASS)
            pathClass = null;
        
        var pathObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
        List<PathObject> changed = new ArrayList<>();
        
        for (PathObject pathObject : pathObjects) {
            if (pathObject.getPathClass() == pathClass)
                continue;
            
            pathObject.setPathClass(pathClass);
            changed.add(pathObject);
        }
        
        if (!changed.isEmpty()) {
            hierarchy.fireObjectClassificationsChangedEvent(this, changed);
            updateAnnotationList();
        }
    }
    
    // 重置选中对象的分类
    private void resetClassificationsForSelectedObjects() {
        if (hierarchy == null)
            return;
        
        var pathObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
        List<PathObject> changed = new ArrayList<>();
        
        for (PathObject pathObject : pathObjects) {
            if (pathObject.getPathClass() == null)
                continue;
            
            pathObject.setPathClass(null);
            changed.add(pathObject);
        }
        
        if (!changed.isEmpty()) {
            hierarchy.fireObjectClassificationsChangedEvent(this, changed);
            updateAnnotationList();
        }
    }
    
    // 获取当前选中的PathClass
    private PathClass getSelectedPathClass() {
        // 正确处理层次结构：pane.getContent()返回VBox，需要在其中查找GridPane
        if (pane == null || pane.getContent() == null)
            return null;
            
        // 使用lookup方法查找带有样式类custom-class-list的GridPane
        Node content = pane.getContent();
        GridPane classList = (GridPane)content.lookup(".custom-class-list");
        if (classList == null)
            return null;
            
        // 查找带有background-color样式的子项
        for (Node node : classList.getChildren()) {
            if (node instanceof HBox && node.getStyle().contains("background-color")) {
                // 找到选中的项
                if (node.getUserData() instanceof PathClass)
                    return (PathClass)node.getUserData();
            }
        }
        return null;
    }
    
    // 更新自动设置分类的属性
    private void updateAutoSetPathClassProperty() {
        PathClass pathClass = null;
        if (doAutoSetPathClass.get()) {
            pathClass = getSelectedPathClass();
        }
        if (pathClass == null || pathClass == PathClass.NULL_CLASS)
            PathPrefs.autoSetAnnotationClassProperty().set(null);
        else
            PathPrefs.autoSetAnnotationClassProperty().set(pathClass);
    }
    
    // 初始化分类右键菜单的方法
    private void initializeClassesContextMenu() {
        MenuItem miSelectByClass = new MenuItem("选择此分类的对象");
        miSelectByClass.setOnAction(e -> {
            if (imageData == null || hierarchy == null)
                return;
            
            PathClass selectedClass = getLastSelectedPathClass();
            if (selectedClass != null) {
                Commands.selectObjectsByClassification(imageData, selectedClass);
            }
        });
        
        MenuItem miEditClass = new MenuItem("编辑分类");
        miEditClass.setOnAction(e -> {
            PathClass selectedClass = getLastSelectedPathClass();
            if (selectedClass != null && Commands.promptToEditClass(selectedClass)) {
                // 更新UI显示
                qupath.getViewerManager().repaintAllViewers();
            }
        });
        
        menuClasses.getItems().addAll(miSelectByClass, miEditClass);
    }

    // 获取最后选中的PathClass
    private PathClass getLastSelectedPathClass() {
        Node focusOwner = qupath.getStage().getScene().getFocusOwner();
        if (focusOwner == null)
            return null;
        
        // 从鼠标点击位置或者焦点位置查找PathClass
        if (focusOwner instanceof HBox) {
            HBox box = (HBox)focusOwner;
            if (box.getUserData() instanceof PathClass)
                return (PathClass)box.getUserData();
        }
        
        // 从鼠标事件的目标查找
        if (menuClasses.getUserData() instanceof PathClass)
            return (PathClass)menuClasses.getUserData();
        
        return null;
    }
    
    private HBox createClassificationItem(PathClass pathClass) {
        HBox item = new HBox();
        item.getStyleClass().add("custom-class-item");
        item.setMaxWidth(Double.MAX_VALUE); // 宽度自适应
        item.setPrefWidth(Double.MAX_VALUE); // 预设宽度尽可能占满父容器
        item.setUserData(pathClass); // 存储PathClass引用
        
        String name = pathClass.getName();
        String translatedName = QuPathTranslator.getTranslatedName(name);
        Color color = ColorToolsFX.getPathClassColor(pathClass);
        
        // 使用Rectangle替代Circle
        Rectangle colorRect = new Rectangle(8, 8);
        colorRect.setFill(color);
        colorRect.setArcWidth(6); // 添加轻微的圆角
        colorRect.setArcHeight(6);
        
        Label classLabel = new Label(translatedName);
        classLabel.getStyleClass().add("custom-class-label");
        HBox.setHgrow(classLabel, Priority.ALWAYS); // 让标签自适应宽度增长
        classLabel.setMaxWidth(Double.MAX_VALUE);
        
        item.getChildren().addAll(colorRect, classLabel);
        
        // 创建工具提示 - 包含分类详细信息
        StringBuilder tipText = new StringBuilder();
        tipText.append("分类名称: ").append(translatedName);
        tipText.append("\n分类原名: ").append(name);
        Color c = ColorToolsFX.getPathClassColor(pathClass);
        tipText.append("\n颜色: RGB(").append((int)(c.getRed()*255)).append(",")
               .append((int)(c.getGreen()*255)).append(",")
               .append((int)(c.getBlue()*255)).append(")");
        Tooltip tooltip = new Tooltip(tipText.toString());
        Tooltip.install(item, tooltip);
        
        // 添加点击事件
        item.setOnMouseClicked(event -> {
            if (imageData == null)
                return;
            
            // 右键菜单
            if (event.getButton() == MouseButton.SECONDARY) {
                menuClasses.setUserData(pathClass);
                menuClasses.show(item, event.getScreenX(), event.getScreenY());
                return;
            }
            
            // 双击编辑分类
            if (event.getClickCount() > 1) {
                if (Commands.promptToEditClass(pathClass)) {
                    // 更新UI显示
                    colorRect.setFill(ColorToolsFX.getPathClassColor(pathClass));
                    classLabel.setText(QuPathTranslator.getTranslatedName(pathClass.getName()));
                    
                    // 更新所有视图
                    qupath.getViewerManager().repaintAllViewers();
                    
                    // 如果有hierarchy，通知变更
                    if (hierarchy != null)
                        hierarchy.fireHierarchyChangedEvent(this);
                }
                return;
            }
            
            // 单击只显示选中效果
            // 清除其他项目的样式
            Node parent = item.getParent();
            if (parent instanceof GridPane) {
                GridPane grid = (GridPane) parent;
                for (Node node : grid.getChildren()) {
                    if (node instanceof HBox && node != item) {
                        node.setStyle("");
                    }
                }
            }
            
            // 设置当前项目的选中样式
            String backgroundColor = String.format(
                "-fx-background-color: rgba(%d,%d,%d,%.1f);", 
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255),
                0.1 // 透明度0.1
            );
            item.setStyle(backgroundColor);
            
            // 选中类别后，如果已开启自动设置，更新自动设置的类别
            if (doAutoSetPathClass.get()) {
                updateAutoSetPathClassProperty();
            }
        });
        
        // 添加鼠标悬停效果
        item.setOnMouseEntered(e -> {
            if (!item.getStyle().contains("-fx-background-color"))
                item.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05);");
        });
        
        item.setOnMouseExited(e -> {
            if (item.getStyle().contains("rgba(0, 0, 0, 0.05)"))
                item.setStyle("");
        });
        
        return item;
    }
    
    private void selectAllAnnotations() {
        if (hierarchy == null)
            return;
        
        Collection<PathObject> annotations = hierarchy.getAnnotationObjects();
        if (annotations.isEmpty())
            return;
        
        hierarchy.getSelectionModel().setSelectedObjects(annotations, null);
    }
    
    private void updateAnnotationList() {
        annotationList.getChildren().clear();
        
        if (filteredAnnotations.isEmpty()) {
            return;
        }
        
        // 找出顶层标注（没有父标注的标注或父标注不是标注对象的标注）
        List<PathObject> topLevelAnnotations = filteredAnnotations.stream()
            .filter(p -> p.getParent() == null || !p.getParent().isAnnotation())
            .collect(Collectors.toList());
        
        // 递归添加标注及其子标注
        for (PathObject annotation : topLevelAnnotations) {
            addAnnotationRecursively(annotation);
        }
    }
    
    // 递归添加标注及其子标注
    private void addAnnotationRecursively(PathObject annotation) {
        // 添加当前标注
        annotationList.getChildren().add(createAnnotationItem(annotation));
        
        // 查找并添加所有子标注
        if (annotation.nChildObjects() > 0) {
            List<PathObject> childAnnotations = annotation.getChildObjects().stream()
                .filter(p -> p.isAnnotation() && filteredAnnotations.contains(p))
                .collect(Collectors.toList());
            
            for (PathObject childAnnotation : childAnnotations) {
                addAnnotationRecursively(childAnnotation);
            }
        }
    }
    
    private HBox createAnnotationItem(PathObject annotation) {
        HBox item = new HBox();
        item.getStyleClass().add("custom-annotation-item");
        item.setAlignment(Pos.CENTER_LEFT);
        
        // 获取标注分类颜色
        Color color = ColorToolsFX.getDisplayedColor(annotation);
        
        // 计算层级深度并添加缩进空间
        int indentLevel = 0;
        PathObject parent = annotation.getParent();
        while (parent != null && parent.isAnnotation()) {
            indentLevel++;
            parent = parent.getParent();
        }
        
        if (indentLevel > 0) {
            // 根据层级深度添加相应倍数的缩进空间
            Region spacer = new Region();
            spacer.setPrefWidth(28 * indentLevel);  // 每层级28px的缩进
            spacer.setMinWidth(28 * indentLevel);
            item.getChildren().add(0, spacer);  // 在最前面添加空间
        }
        
        // 标注图标 - 使用与标注类型相关的图标，颜色与分类颜色一致
        Node annotationIcon = IconFactory.createPathObjectIcon(annotation, 16, 16);
        annotationIcon.getStyleClass().add("custom-annotation-icon");
        
        // 获取纯名称（不含分类信息）
        String displayName = annotation.getDisplayedName();
        String pureName = displayName;
        // 如果有分类，名称中可能包含 "(...)"，移除这部分
        if (annotation.getPathClass() != null && displayName.contains("(")) {
            pureName = displayName.substring(0, displayName.lastIndexOf("(")).trim();
        }
        
        // 标注名称
        Label nameLabel = new Label(pureName);
        nameLabel.getStyleClass().add("custom-annotation-name");
        
        // 创建工具提示 - 包含更详细的标注信息
        StringBuilder tipText = new StringBuilder();
        tipText.append("名称: ").append(pureName);
        if (annotation.getPathClass() != null) {
            tipText.append("\n分类: ").append(annotation.getPathClass().toString());
        }
        if (annotation.hasROI()) {
            tipText.append("\n类型: ").append(annotation.getROI().getRoiName());
            tipText.append("\nID: ").append(annotation.getID());
        }
        Tooltip tooltip = new Tooltip(tipText.toString());
        Tooltip.install(item, tooltip);
        
        // 计数/锁定图标
        HBox rightIcons = new HBox();
        rightIcons.setAlignment(Pos.CENTER_RIGHT);
        rightIcons.setSpacing(5);
        // 锁定图标
        if (annotation.isLocked()) {
            Node lockIcon = IconFactory.createNode(16, 16, PathIcons.LOCK_BTN);
            lockIcon.setOpacity(0.45);
            rightIcons.getChildren().add(lockIcon);
        }

        // 子对象计数
        if (annotation.nChildObjects() > 0) {
            int directChildCount = annotation.nChildObjects();
            int allDescendantsCount = countAllDescendants(annotation);
            
            String countText;
            if (directChildCount == allDescendantsCount) {
                countText = String.valueOf(directChildCount);
            } else {
                countText = directChildCount + "/" + allDescendantsCount;
            }
            
            Label countLabel = new Label(countText);
            countLabel.getStyleClass().add("custom-annotation-count");
            countLabel.setCursor(javafx.scene.Cursor.HAND);
            
            // 检查是否应该应用选中样式
            if (isCountLabelSelected(annotation)) {
                countLabel.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06); -fx-text-fill: rgba(22, 146, 255, 1);");
                currentSelectedCountLabel = countLabel; // 记录当前选中的标签
            }
            
            // 添加鼠标点击事件到countLabel
            countLabel.setOnMouseClicked(event -> {
                // 设置选中效果
                boolean isSelected = countLabel.getStyle().contains("rgba(22, 146, 255, 0.06)");
                
                // 先清除其他标签的选中状态
                if (currentSelectedCountLabel != null && currentSelectedCountLabel != countLabel) {
                    currentSelectedCountLabel.setStyle("");
                }
                
                if (isSelected) {
                    // 如果已经是选中状态，则取消选中
                    countLabel.setStyle("");
                    setCountLabelSelected(annotation, false);
                    currentSelectedCountLabel = null;
                } else {
                    // 设置选中效果：背景色和字体颜色
                    countLabel.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06); -fx-text-fill: rgba(22, 146, 255, 1);");
                    setCountLabelSelected(annotation, true);
                    currentSelectedCountLabel = countLabel;
                }
                // 调用回调通知监听器
                fireCountLabelClicked(annotation);
                // 阻止事件冒泡，防止触发item的点击事件
                event.consume();
            });
            rightIcons.getChildren().add(countLabel);
        }
        
        // 组装所有元素
        item.getChildren().addAll(annotationIcon, nameLabel);
        
        // 只有当有分类标签时才添加
        if (annotation.getPathClass() != null) {
              // 使用QuPathTranslator翻译分类名称
            String translatedClassName = QuPathTranslator.getTranslatedName(annotation.getPathClass().getName());
            Label classLabel = new Label(translatedClassName);
            
            // 设置背景颜色与分类颜色匹配
            String backgroundColor = String.format("-fx-background-color: rgba(%d,%d,%d,%.1f);", 
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255),
                0.1); // 透明度降低以便更好看

            String textColor = String.format("-fx-text-fill: rgba(%d,%d,%d,%d);", 
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255),
            1); // 透明度降低以便更好看
            // 设置文本颜色为对比色或黑/白文本
            classLabel.setStyle(backgroundColor + textColor + "-fx-font-size: 10px;-fx-padding: 0 4;");
                
            item.getChildren().add(classLabel);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        item.getChildren().add(spacer);
        item.getChildren().add(rightIcons);
        
        // 添加点击事件
        item.setOnMouseClicked(event -> {
            if (hierarchy == null)
                return;
            
            if (event.getButton() == MouseButton.PRIMARY) {
                // 单击选择
                if (event.isShiftDown() || event.isControlDown()) {
                    // 多选
                    if (hierarchy.getSelectionModel().isSelected(annotation)) {
                        hierarchy.getSelectionModel().deselectObject(annotation);
                    } else {
                        hierarchy.getSelectionModel().setSelectedObject(annotation, true);
                    }
                } else {
                    // 单选
                    hierarchy.getSelectionModel().setSelectedObject(annotation);
                }
                
                // 双击居中显示
                if (event.getClickCount() > 1 && annotation.hasROI()) {
                    qupath.getViewer().centerROI(annotation.getROI());
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                // 右键菜单
                if (!hierarchy.getSelectionModel().isSelected(annotation)) {
                    hierarchy.getSelectionModel().setSelectedObject(annotation);
                }
                menuAnnotations.show(item, event.getScreenX(), event.getScreenY());
            }
        });
        
        // 设置特殊样式
        if (hierarchy != null && hierarchy.getSelectionModel().isSelected(annotation)) {
            // 使用淡蓝色背景表示选中状态
            item.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06)");
        }
        
        return item;
    }
    
    private Node createAnnotationIcon(PathObject annotation, Color color) {
        // 创建图标
        Node icon = null;
        
        // 根据标注类型创建不同的图标
        if (annotation instanceof PathAnnotationObject) {
            if (annotation.getROI() != null) {
                String roiType = annotation.getROI().getRoiName();
                if ("Rectangle".equals(roiType)) {
                    icon = IconFactory.createNode(16, 16, PathIcons.RECTANGLE_TOOL);
                } else if ("Ellipse".equals(roiType)) {
                    icon = IconFactory.createNode(16, 16, PathIcons.ELLIPSE_TOOL);
                } else if ("Polygon".equals(roiType)) {
                    icon = IconFactory.createNode(16, 16, PathIcons.POLYGON_TOOL);
                } else if ("Line".equals(roiType)) {
                    icon = IconFactory.createNode(16, 16, PathIcons.LINE_TOOL);
                } else if ("Points".equals(roiType)) {
                    icon = IconFactory.createNode(16, 16, PathIcons.POINTS_TOOL);
                }
            }
            
            if (icon == null) {
                // 默认标注图标
                icon = IconFactory.createNode(16, 16, PathIcons.ANNOTATIONS);
            }
        } else {
            // 默认图标
            icon = IconFactory.createNode(16, 16, PathIcons.ANNOTATIONS);
        }
        
        // 设置图标颜色
        if (icon != null && color != null) {
            // 尝试设置图标颜色
            try {
                String colorStyle = String.format("-fx-icon-color: rgba(%d,%d,%d,%.1f);", 
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255),
                    color.getOpacity());
                
                if (icon instanceof Region) {
                    ((Region)icon).setStyle(colorStyle);
                }
            } catch (Exception e) {
                logger.error("无法设置图标颜色", e);
            }
        }
        
        return icon;
    }

    @Override
    public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject,
            Collection<PathObject> allSelected) {
        if (disableUpdates.get() || suppressSelectionChanges)
            return;
        
        // 更新属性表格
        updatePropertiesGrid(pathObjectSelected);
        
        Platform.runLater(() -> updateAnnotationList());
    }

    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> source,
            ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        setImageData(imageDataNew);
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (disableUpdates.get())
            return;
        
        refreshList();
    }
    
    private void refreshList() {
        // 获取所有标注对象
        List<PathObject> annotations = hierarchy == null ? List.of() : 
            hierarchy.getAnnotationObjects().stream()
                .filter(p -> p.isAnnotation())
                .collect(Collectors.toList());
        
        // 更新列表
        allAnnotations.setAll(annotations);
        
        // 更新UI
        Platform.runLater(() -> updateAnnotationList());
    }
    
    void setImageData(ImageData<BufferedImage> imageData) {
        // 移除旧监听器
        if (this.hierarchy != null) {
            this.hierarchy.removeListener(this);
            this.hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
        }
        
        // 设置新的图像数据
        this.imageData = imageData;
        this.hierarchy = imageData == null ? null : imageData.getHierarchy();
        this.hasImageData.set(imageData != null);
        
        // 重置选中状态
        this.selectedCountLabels.clear();
        
        // 添加新监听器
        if (this.hierarchy != null) {
            this.hierarchy.addListener(this);
            this.hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
        }
        
        // 刷新列表
        refreshList();
    }

    /**
     * 递归计算所有后代元素的数量(包括直接子元素)
     * @param parentObject 父对象
     * @return 所有后代元素的数量
     */
    private int countAllDescendants(PathObject parentObject) {
        if (parentObject.nChildObjects() == 0) {
            return 0;
        }
        
        int count = parentObject.nChildObjects();
        for (PathObject child : parentObject.getChildObjects()) {
            count += countAllDescendants(child);
        }
        return count;
    }

    // 检查countLabel是否被选中
    private boolean isCountLabelSelected(PathObject annotation) {
        return selectedCountLabels.getOrDefault(annotation.getID().toString(), false);
    }
    
    // 设置countLabel选中状态
    private void setCountLabelSelected(PathObject annotation, boolean selected) {
        // 如果是设置为选中状态，则先清除所有的选中状态
        if (selected) {
            // 清除所有选中状态
            selectedCountLabels.clear();
        }
        // 设置当前的选中状态
        selectedCountLabels.put(annotation.getID().toString(), selected);
    }
    
    // 清除所有标签的选中效果
    public void clearAllCountLabelSelections() {
        // 清除当前选中的标签样式
        if (currentSelectedCountLabel != null) {
            currentSelectedCountLabel.setStyle("");
            currentSelectedCountLabel = null;
        }
        // 清空选中状态记录
        selectedCountLabels.clear();
    }

    // 添加属性面板的创建方法
    private VBox createAttributesPanel() {
        attributesBox = new VBox();
        attributesBox.getStyleClass().add("custom-annotation-attributes-box");
        attributesBox.setSpacing(8);
        // 创建标题栏
        HBox attributesTopBar = new HBox();
        attributesTopBar.getStyleClass().add("custom-annotation-top-bar");
        // 创建标题
        Label attributesLabel = new Label("测量");
        attributesLabel.getStyleClass().add("custom-annotation-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        attributesTopBar.getChildren().addAll(attributesLabel, spacer);
        attributesBox.getChildren().add(attributesTopBar);
        // 创建属性容器
        propertiesBox = new VBox();
        propertiesBox.setSpacing(8);
        // 添加各个组件到属性面板
        attributesBox.getChildren().add(propertiesBox);
        return attributesBox;
    }

    // 添加一个从原版获取属性的方法
    private Map<String, String> getObjectProperties(PathObject pathObject) {
        if (pathObject == null || imageData == null)
            return Collections.emptyMap();
        
        // 使用LinkedHashMap保持顺序
        Map<String, String> properties = new LinkedHashMap<>();
        
        // 设置表格模型
        tableModel.setImageData(imageData, Collections.singletonList(pathObject));
        
        // 获取所有属性名称
        List<String> allNames = tableModel.getAllNames();
        
        // 获取每个属性的值
        int nDecimalPlaces = 4; // 与SelectedMeasurementTableView一致
        for (String name : allNames) {
            String value = tableModel.getStringValue(pathObject, name, nDecimalPlaces);
            if (value != null && !value.isEmpty()) {
                properties.put(name, value);
            }
        }
        
        return properties;
    }

    // 更新属性表格的方法
    private void updatePropertiesGrid(PathObject pathObject) {
        propertiesBox.getChildren().clear();
        propertiesItems.clear();
        
        if (pathObject == null)
            return;
        
        // 直接从原版属性列表获取属性
        Map<String, String> properties = getObjectProperties(pathObject);
        
        // 转换为KeyValuePair列表
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            propertiesItems.add(new KeyValuePair(entry.getKey(), entry.getValue()));
        }
        
        // 获取PathClass用于显示分类颜色
        PathClass pathClass = pathObject.getPathClass();
        
        // 将所有属性添加到VBox中
        for (KeyValuePair pair : propertiesItems) {
            // 为每个属性创建一个HBox容器
            HBox itemBox = new HBox();
            itemBox.setSpacing(10);
            itemBox.setAlignment(Pos.CENTER_LEFT);
            itemBox.getStyleClass().add("custom-annotation-properties-item");
            
            // 创建键标签
            Label keyLabel = new Label(QuPathTranslator.getTranslatedName(pair.getKey()));
            keyLabel.setPrefWidth(120); // 固定宽度以对齐
            keyLabel.setMinWidth(120);
            keyLabel.getStyleClass().add("custom-annotation-properties-item-key");
            // 根据行类型自定义样式
            if ("Classification".equals(pair.getKey()) && pathClass != null) {
                // 为分类添加颜色方块
                Color color = ColorToolsFX.getPathClassColor(pathClass);
                
                Rectangle colorRect = new Rectangle(8, 8);
                colorRect.setFill(color);
                colorRect.setArcWidth(2);
                colorRect.setArcHeight(2);
                
                HBox valueBox = new HBox(5, colorRect, new Label(pair.getValue()));
                valueBox.setAlignment(Pos.CENTER_LEFT);
                valueBox.getStyleClass().add("custom-annotation-properties-item-value");
                // 添加到容器
                itemBox.getChildren().addAll(keyLabel, valueBox);
                
                // 添加工具提示
                Tooltip.install(itemBox, new Tooltip("对象的分类: " + pair.getValue()));
            } else {
                // 创建值标签
                Label valueLabel = new Label(pair.getValue());
                valueLabel.setWrapText(true); // 允许文本换行
                valueLabel.getStyleClass().add("custom-annotation-properties-item-value");
                HBox.setHgrow(valueLabel, Priority.ALWAYS); // 允许值标签自适应增长
                
                // 添加到容器
                itemBox.getChildren().addAll(keyLabel, valueLabel);
                
                // 为每个属性项添加工具提示
                String tipText = QuPathTranslator.getTranslatedName(pair.getKey()) + ": " + pair.getValue();
                Tooltip.install(itemBox, new Tooltip(tipText));
            }
            
            // 添加到属性列表
            propertiesBox.getChildren().add(itemBox);
        }
    }

    // 添加KeyValuePair类用于表格数据
    public static class KeyValuePair {
        private final String key;
        private final String value;
        
        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() {
            return key;
        }
        
        public String getValue() {
            return value;
        }
    }
} 