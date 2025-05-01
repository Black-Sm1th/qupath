package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import qupath.fx.controls.PredicateTextField;
import qupath.lib.gui.QuPathGUI;
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


public class CustomAnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener{
    // 定义一个回调接口，用于监听子对象计数标签的点击事件
    public interface CountLabelClickListener {
        void onCountLabelClicked(PathObject annotation);
    }
    
    private QuPathGUI qupath;
    private static final Logger logger = LoggerFactory.getLogger(CustomAnnotationPane.class);
    private ScrollPane pane;
    
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
    
    public CustomAnnotationPane(QuPathGUI qupath){
        this.qupath = qupath;
        this.disableUpdates.addListener((v, o, n) -> {
            if (!n)
                enableUpdates();
        });
        
        initializeFilter();
        GuiTools.populateAnnotationsMenu(qupath, menuAnnotations);
        
        setImageData(qupath.getImageData());
        qupath.imageDataProperty().addListener(this);
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
        VBox mdPane = new VBox();
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
        deleteBtn.setOnAction(e -> GuiTools.promptToClearAllSelectedObjects(imageData));
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
        annotationList.getStyleClass().add("custom-annotation-list");
        mdPane.getChildren().add(annotationList);
        
        // 分类部分
        VBox classificationBox = new VBox();
        classificationBox.getStyleClass().add("custom-annotation-classification-box");
        Label classificationLabel = new Label("分类");
        classificationLabel.getStyleClass().add("custom-annotation-label");
        classificationBox.getChildren().add(classificationLabel);
        
        // 添加分类列表（可以根据需要扩展）
        VBox classList = new VBox();
        
        // 创建各种分类选项
        HBox tumorClass = createClassificationItem("肿瘤", Color.RED);
        HBox stromaClass = createClassificationItem("间质", Color.GREEN);
        HBox immuneClass = createClassificationItem("免疫细胞", Color.PURPLE);
        HBox necrosisClass = createClassificationItem("坏死", Color.BLACK);
        HBox otherClass = createClassificationItem("其他", Color.ORANGE);
        HBox regionClass = createClassificationItem("区域*", Color.BLUE);
        HBox ignoreClass = createClassificationItem("忽略*", Color.LIGHTGRAY);
        HBox positiveClass = createClassificationItem("良性", Color.RED);
        HBox negativeClass = createClassificationItem("异常", Color.BLUE);
        
        classList.getChildren().addAll(tumorClass, stromaClass, immuneClass, necrosisClass, 
                otherClass, regionClass, ignoreClass, positiveClass, negativeClass);
        
        classificationBox.getChildren().add(classList);
        mdPane.getChildren().add(classificationBox);
        
        VBox attributesBox = new VBox();
        attributesBox.getStyleClass().add("custom-annotation-attributes-box");
        Label attributesLabel = new Label("属性");
        attributesLabel.getStyleClass().add("custom-annotation-label");
        attributesBox.getChildren().add(attributesLabel);
        
        HBox descriptionBox = new HBox();
        descriptionBox.setPadding(new Insets(10));
        Label descriptionLabel = new Label("描述  这里是描述，编辑后不隐藏");
        descriptionBox.getChildren().add(descriptionLabel);
        attributesBox.getChildren().add(descriptionBox);
        
        mdPane.getChildren().add(attributesBox);
        
        pane = new ScrollPane(mdPane);
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        updateAnnotationList();
        
        return pane;
    }
    
    private HBox createClassificationItem(String name, Color color) {
        HBox item = new HBox();
        item.setSpacing(5);
        item.setAlignment(Pos.CENTER_LEFT);
        
        Circle colorCircle = new Circle(6);
        colorCircle.setFill(color);
        colorCircle.setStroke(Color.BLACK);
        colorCircle.setStrokeWidth(0.5);
        
        Label classLabel = new Label(name);
        
        item.getChildren().addAll(colorCircle, classLabel);
        
        // 添加点击事件
        item.setOnMouseClicked(event -> {
            if (imageData == null)
                return;
            
            List<PathObject> selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
            if (selected.isEmpty())
                return;
            
            // 查找对应的PathClass
            PathClass pathClass = null;
            for (PathClass cls : qupath.getAvailablePathClasses()) {
                if (cls.getName() != null && cls.getName().equals(name)) {
                    pathClass = cls;
                    break;
                }
            }
            
            // 应用分类到选中对象
            if (pathClass != null) {
                for (PathObject pathObject : selected) {
                    if (pathObject.isTMACore() || pathObject.isAnnotation() || pathObject.isDetection())
                        pathObject.setPathClass(pathClass);
                }
                hierarchy.fireObjectClassificationsChangedEvent(this, selected);
            }
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
} 