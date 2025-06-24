package qupath.lib.gui.panes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import qupath.imagej.detect.cells.PositiveCellDetection;
import qupath.imagej.detect.cells.WatershedCellDetection;
import qupath.imagej.superpixels.DoGSuperpixelsPlugin;
import qupath.imagej.superpixels.SLICSuperpixelsPlugin;
import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;
import qupath.lib.plugins.objects.TileClassificationsToAnnotationsPlugin;
import qupath.opencv.features.DelaunayClusteringPlugin;

/**
 * 分析工具面板，提供各种分析功能的访问
 */
public class AnalysisToolsPane {

    private QuPathGUI qupath;
    private BorderPane pane;
    private boolean isPositiveCellDetection = false; // 标记当前是否为阳性细胞检测模式
    private VBox submenuContainer; // 子菜单容器
    private boolean isSubmenuVisible = false; // 子菜单是否可见
    private Node selectedSmallCard = null; // 当前选中的小卡片
    private GridPane gridPane; // 网格面板
    private Map<String, VBox> submenuContainers = new HashMap<>(); // 存储各个子菜单容器
    private String currentSubmenuType = null; // 当前显示的子菜单类型
    
    /**
     * 创建一个新的分析工具面板
     * @param qupath QuPath GUI实例
     */
    public AnalysisToolsPane(QuPathGUI qupath) {
        this.qupath = qupath;
        this.pane = new BorderPane();
        pane.getStyleClass().add("analysis-pane");
        
        // 创建卡片式布局
        gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);
        
        // 添加预测染色向量功能
        Node predictStainVectorCard = createActionCard("预测染色向量", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
                "/images/icon1.png", 
                e -> handlePredictStainVector(),
                false);
                
        // 添加细胞检测功能，带有切换按钮
        Node cellDetectionCard = createActionCard(isPositiveCellDetection ? "阳性细胞检测" : "细胞检测", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#E4EBFF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#E4EBFF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
                "/images/icon2.png", 
                e -> handleCellDetection(),
                true);
                
        // 小卡片组
        Node densityMapCard = createSmallCard("密度图", e -> toggleSubmenu("densityMap"));
        Node tileAndSuperpixelCard = createSmallCard("图像块&超像素", e -> toggleSubmenu("tileAndSuperpixel"));
        Node computeFeaturesCard = createSmallCard("计算特征", e -> toggleSubmenu("computeFeatures"));
        Node spatialAnalysisCard = createSmallCard("空间分析", e -> toggleSubmenu("spatialAnalysis"));
                
        // 在网格中排列卡片
        gridPane.add(predictStainVectorCard, 0, 0, 2, 1); // 跨两列
        gridPane.add(cellDetectionCard, 0, 1, 2, 1); // 跨两列
        
        // 小卡片组
        gridPane.add(densityMapCard, 0, 2);
        gridPane.add(tileAndSuperpixelCard, 1, 2);
        gridPane.add(computeFeaturesCard, 0, 3);
        gridPane.add(spatialAnalysisCard, 1, 3);
        
        // 创建各个子菜单容器
        createAllSubmenus();
        
        // 将子菜单添加到网格中，但初始时不可见
        gridPane.add(submenuContainers.get("densityMap"), 0, 4, 2, 1);
        gridPane.add(submenuContainers.get("tileAndSuperpixel"), 0, 4, 2, 1);
        gridPane.add(submenuContainers.get("computeFeatures"), 0, 4, 2, 1);
        gridPane.add(submenuContainers.get("spatialAnalysis"), 0, 4, 2, 1);
        
        pane.setCenter(gridPane);
    }
    
    /**
     * 创建所有子菜单容器
     */
    private void createAllSubmenus() {
        // 密度图子菜单
        VBox densityMapSubmenu = createSubmenuContainer();
        addSubmenuItem(densityMapSubmenu, "创建密度图", e -> handleCreateDensityMap());
        addSubmenuItem(densityMapSubmenu, "加载密度图", e -> handleLoadDensityMap());
        submenuContainers.put("densityMap", densityMapSubmenu);
        
        // 图像块和超像素子菜单
        VBox tileSubmenu = createSubmenuContainer();
        addSubmenuItem(tileSubmenu, "创建图块", e -> handleCreateTiles());
        addSubmenuItem(tileSubmenu, "SLIC超像素分割", e -> handleSLICSuperpixels());
        addSubmenuItem(tileSubmenu, "DoG超像素分割", e -> handleDoGSuperpixels());
        addSubmenuItem(tileSubmenu, "图块分类到注释", e -> handleTileClassification());
        submenuContainers.put("tileAndSuperpixel", tileSubmenu);
        
        // 计算特征子菜单
        VBox featuresSubmenu = createSubmenuContainer();
        addSubmenuItem(featuresSubmenu, "平滑特征", e -> handleSmoothFeatures());
        addSubmenuItem(featuresSubmenu, "强度特征", e -> handleIntensityFeatures());
        addSubmenuItem(featuresSubmenu, "形状特征", e -> handleShapeFeatures());
        submenuContainers.put("computeFeatures", featuresSubmenu);
        
        // 空间分析子菜单
        VBox spatialSubmenu = createSubmenuContainer();
        addSubmenuItem(spatialSubmenu, "到标注的距离 2D", e -> handlePointToPointDistance());
        addSubmenuItem(spatialSubmenu, "到标注区域的带符号距离 2D", e -> handleCellConvergenceAnalysis());
        addSubmenuItem(spatialSubmenu, "检测质心距离 2D", e -> handleCellAnnotationEvaluation());
        addSubmenuItem(spatialSubmenu, "Delaunay聚类特征 2D", e -> handleDelaunayTriangulation());
        submenuContainers.put("spatialAnalysis", spatialSubmenu);
        
        // 初始化所有子菜单为不可见
        submenuContainers.forEach((key, menu) -> {
            menu.setVisible(false);
            menu.setManaged(false);
        });
    }
    
    /**
     * 创建子菜单容器
     * @return 子菜单容器
     */
    private VBox createSubmenuContainer() {
        VBox container = new VBox();
        container.setPadding(new Insets(12, 20, 12, 20));
        container.setStyle("-fx-background-color: rgba(244, 248, 255, 1); -fx-border-color: rgba(233, 241, 255, 1); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;");
        container.setVisible(false);
        container.setManaged(false);
        return container;
    }
    
    /**
     * 获取面板组件
     * @return 分析工具面板的JavaFX组件
     */
    public Node getPane() {
        return pane;
    }
    
    /**
     * 创建一个动作卡片，带有图标和标题
     * @param title 卡片标题
     * @param gradientStyle 卡片样式
     * @param hoverStyle 悬停样式
     * @param iconString 图标路径
     * @param action 点击动作
     * @param showArrow 是否显示切换箭头
     * @return 创建的卡片节点
     */
    private Node createActionCard(String title, String gradientStyle, String hoverStyle, String iconString, Consumer<Void> action, boolean showArrow) {
        BorderPane card = new BorderPane();
        card.getStyleClass().add("analysis-card");
        
        card.setStyle(gradientStyle);
        
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(8);
        ImageView iconView = new ImageView(new Image(iconString));
        iconView.setFitWidth(40);
        iconView.setFitHeight(40);
        content.getChildren().add(iconView);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("analysis-card-title");
        content.getChildren().add(titleLabel);
        
        // 设置内边距以便更好地对齐内容
        content.setPadding(new Insets(0, 0, 0, 70));
        
        // 如果需要支持箭头切换功能
        if (showArrow) {
            // 创建箭头按钮
            Button arrowButton = createArrowButton();
            
            // 设置箭头按钮的样式和大小
            arrowButton.setPrefSize(24, 24);
            
            // 使用HBox包装箭头按钮以便更好地控制位置
            HBox arrowContainer = new HBox(arrowButton);
            arrowContainer.setAlignment(Pos.CENTER_RIGHT);
            arrowContainer.setPadding(new Insets(0, 15, 0, 0));
            
            // 初始状态下箭头不可见
            arrowContainer.setVisible(false);
            arrowContainer.setManaged(true);
            
            // 添加箭头按钮点击事件 - 切换检测模式
            arrowButton.setOnAction(e -> {
                // 切换检测模式
                isPositiveCellDetection = !isPositiveCellDetection;
                
                // 更新卡片标题
                titleLabel.setText(isPositiveCellDetection ? "阳性细胞检测" : "细胞检测");
                
                // 阻止事件传播，避免触发卡片点击事件
                e.consume();
            });
            
            // 将箭头容器添加到右侧
            card.setRight(arrowContainer);
            
            // 卡片悬停时显示箭头按钮
            card.setOnMouseEntered(e -> {
                card.setStyle(hoverStyle);
                arrowContainer.setVisible(true);
            });
            
            card.setOnMouseExited(e -> {
                card.setStyle(gradientStyle);
                arrowContainer.setVisible(false);
            });
        } else {
            // 如果不需要箭头按钮，只添加普通悬停效果
            card.setOnMouseEntered(e -> {
                card.setStyle(hoverStyle);
            });
            
            card.setOnMouseExited(e -> {
                card.setStyle(gradientStyle);
            });
        }
        
        card.setCenter(content);
        card.setOnMouseClicked(e -> {
            if (action != null) {
                action.accept(null);
            }
        });
        
        // 按下效果 - 添加轻微的下移效果
        card.setOnMousePressed(e -> {
            card.setTranslateY(1.0); // 轻微下移
            card.setStyle(hoverStyle + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 2, 0, 0, 1);"); // 减小阴影
        });
        
        card.setOnMouseReleased(e -> {
            card.setTranslateY(0.0); // 恢复位置
            card.setStyle(hoverStyle);
        });
        
        return card;
    }
    
    /**
     * 创建小型卡片
     * @param title 卡片标题
     * @param action 点击动作
     * @return 创建的小卡片节点
     */
    private Node createSmallCard(String title, Consumer<Void> action) {
        BorderPane card = new BorderPane();
        card.getStyleClass().add("small-card");
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;");
        card.setPrefSize(144, 68); // 设置小卡片尺寸
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;-fx-text-fill: rgba(0, 0, 0, 0.85)");
        card.setCenter(titleLabel);
        
        // 悬停效果
        card.setOnMouseEntered(e -> {
            if (card != selectedSmallCard) {
                card.setStyle("-fx-background-color: rgba(253, 253, 253, 0.8); -fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);-fx-cursor: hand;");
            }
        });
        
        card.setOnMouseExited(e -> {
            if (card != selectedSmallCard) {
                card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;");
            }
        });
        
        card.setOnMouseClicked(e -> {
            if (action != null) {
                // 如果之前有选中的卡片，重置其样式
                if (selectedSmallCard != null && selectedSmallCard != card) {
                    ((BorderPane)selectedSmallCard).setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;");
                }
                
                // 设置选中样式或取消选中
                if (selectedSmallCard == card) { // 再次点击已选中卡片
                    card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;");
                    selectedSmallCard = null;
                } else { // 首次点击卡片
                    card.setStyle("-fx-background-color: rgba(244, 248, 255, 1); -fx-border-color: rgba(233, 241, 255, 1); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;");
                    selectedSmallCard = card;
                }
                
                // 卡片标题作为用户数据，用于识别
                card.setUserData(title);
                
                action.accept(null);
            }
        });
        
        // 按下效果 - 添加轻微的下移效果
        card.setOnMousePressed(e -> {
            card.setTranslateY(1.0); // 轻微下移
        });
        
        card.setOnMouseReleased(e -> {
            card.setTranslateY(0.0); // 恢复位置
        });
        
        return card;
    }
    
    /**
     * 添加子菜单项
     * @param container 子菜单容器
     * @param title 菜单项标题
     * @param action 点击动作
     */
    private void addSubmenuItem(VBox container, String title, Consumer<Void> action) {
        HBox item = new HBox();
        item.setAlignment(Pos.CENTER);
        item.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;-fx-cursor: hand;-fx-border-radius: 20px; -fx-background-radius: 20px;-fx-padding: 10;");
        
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 14px;-fx-text-fill: rgba(0, 0, 0, 0.85);");
        item.getChildren().add(label);
        
        // 悬停效果
        item.setOnMouseEntered(e -> {
            item.setStyle("-fx-background-color: rgba(255, 255, 255, 1);-fx-border-color: rgba(233, 241, 255, 1); -fx-cursor: hand; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-padding: 10;");
        });
        
        item.setOnMouseExited(e -> {
            item.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;-fx-cursor: hand;-fx-border-radius: 20px; -fx-background-radius: 20px;-fx-padding: 10;");
        });
        
        item.setOnMouseClicked(e -> {
            if (action != null) {
                action.accept(null);
            }
        });
        
        container.getChildren().add(item);
    }
    
    /**
     * 创建一个箭头按钮
     * @return 箭头按钮
     */
    private Button createArrowButton() {
        Button button = new Button();
        
        // 创建一个三角形箭头（播放按钮样式）
        SVGPath arrow = new SVGPath();
        arrow.setContent("M 8 4 L 14 10 L 8 16 Z");  // 更高更窄的箭头
        arrow.setStyle("-fx-fill: #888888;");
        
        StackPane arrowContainer = new StackPane(arrow);
        arrowContainer.setAlignment(Pos.CENTER);
        
        button.setGraphic(arrowContainer);
        button.setStyle("-fx-background-color: transparent;-fx-border-color: transparent;");
        
        // 添加悬停效果
        button.setOnMouseEntered(e -> {
            arrow.setStyle("-fx-fill: #555555;");
        });
        
        button.setOnMouseExited(e -> {
            arrow.setStyle("-fx-fill: #888888;");
        });
        
        return button;
    }
    
    /**
     * 切换指定类型的子菜单
     * @param submenuType 子菜单类型
     */
    private void toggleSubmenu(String submenuType) {
        // 如果当前已经显示了这个子菜单，就隐藏它
        if (submenuType.equals(currentSubmenuType) && submenuContainers.get(submenuType).isVisible()) {
            hideAllSubmenus();
            return;
        }
        
        // 先隐藏所有子菜单
        hideAllSubmenus();
        
        // 显示指定的子菜单
        VBox submenu = submenuContainers.get(submenuType);
        if (submenu != null) {
            submenu.setVisible(true);
            submenu.setManaged(true);
            
            // 显示动画
            submenu.setOpacity(0);
            submenu.setTranslateY(-5);
            
            TranslateTransition tt = new TranslateTransition(Duration.millis(150), submenu);
            tt.setToY(0);
            tt.play();
            
            submenu.setOpacity(1);
            
            // 记录当前显示的子菜单类型
            currentSubmenuType = submenuType;
        }
    }
    
    /**
     * 隐藏所有子菜单
     */
    private void hideAllSubmenus() {
        submenuContainers.forEach((key, menu) -> {
            menu.setVisible(false);
            menu.setManaged(false);
        });
        currentSubmenuType = null;
    }
    
    // 处理预测染色向量
    private void handlePredictStainVector() {
        qupath.createImageDataAction(imageData -> {
            // 调用估计染色向量命令
            try {
                Commands.promptToEstimateStainVectors(imageData);
            } catch (Exception e) {
                // 忽略异常
            }
        }).handle(null);
    }
    
    // 处理细胞检测
    private void handleCellDetection() {
        // 根据当前模式选择不同的检测类
        Class<? extends PathPlugin> detectionClass = isPositiveCellDetection ? 
            PositiveCellDetection.class : WatershedCellDetection.class;
        
        // 创建并运行相应的细胞检测插件
        qupath.createPluginAction(null, detectionClass, null)
              .handle(null);
    }
    
    // 处理创建密度图
    private void handleCreateDensityMap() {
        qupath.createImageDataAction(imageData -> {
            // 使用反射动态调用密度图命令
            try {
                // 尝试通过反射调用DensityMapCommand
                Class<?> densityMapCommandClass = Class.forName("qupath.process.gui.commands.DensityMapCommand");
                Object densityMapCommand = densityMapCommandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
                densityMapCommandClass.getMethod("run").invoke(densityMapCommand);
            } catch (Exception e) {
                // 发生异常时显示错误消息
                System.err.println("创建密度图失败: " + e.getMessage());
            }
        }).handle(null);
    }
    
    // 处理加载密度图
    private void handleLoadDensityMap() {
        qupath.createImageDataAction(imageData -> {
            // 使用反射动态调用加载密度图命令
            try {
                // 尝试通过反射调用LoadResourceCommand
                Class<?> loadResourceCommandClass = Class.forName("qupath.process.gui.commands.ui.LoadResourceCommand");
                Object loadCommand = loadResourceCommandClass.getMethod("createLoadDensityMapCommand", QuPathGUI.class).invoke(null, qupath);
                loadResourceCommandClass.getMethod("run").invoke(loadCommand);
            } catch (Exception e) {
                // 发生异常时显示错误消息
                System.err.println("加载密度图失败: " + e.getMessage());
            }
        }).handle(null);
    }
    
    // 处理创建图块
    private void handleCreateTiles() {
        qupath.createPluginAction("Create tiles", TilerPlugin.class, null).handle(null);
    }
    
    // 处理SLIC超像素分割
    private void handleSLICSuperpixels() {
        qupath.createPluginAction("SLIC superpixel segmentation", SLICSuperpixelsPlugin.class, null).handle(null);
    }
    
    // 处理DoG超像素分割
    private void handleDoGSuperpixels() {
        qupath.createPluginAction("DoG superpixel segmentation", DoGSuperpixelsPlugin.class, null).handle(null);
    }
    
    // 处理图块分类到注释
    private void handleTileClassification() {
        qupath.createPluginAction("Tile classifications to annotations", TileClassificationsToAnnotationsPlugin.class, null).handle(null);
    }
    
    // 处理平滑特征
    private void handleSmoothFeatures() {
        qupath.createPluginAction("Add smoothed features", SmoothFeaturesPlugin.class, null).handle(null);
    }
    
    // 处理强度特征
    private void handleIntensityFeatures() {
        qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, null).handle(null);
    }
    
    // 处理形状特征
    private void handleShapeFeatures() {
        qupath.createImageDataAction(imageData -> Commands.promptToAddShapeFeatures(qupath)).handle(null);
    }
    
    // 处理点对点距离
    private void handlePointToPointDistance() {
        qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, false)).handle(null);
    }
    
    // 处理细胞汇聚分析
    private void handleCellConvergenceAnalysis() {
        qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, true)).handle(null);
    }
    
    // 处理细胞注释关联评价
    private void handleCellAnnotationEvaluation() {
        qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData)).handle(null);
    }
    
    // 处理德劳内三角划分
    private void handleDelaunayTriangulation() {
        qupath.createPluginAction("Delaunay cluster features 2D", DelaunayClusteringPlugin.class, null).handle(null);
    }
}