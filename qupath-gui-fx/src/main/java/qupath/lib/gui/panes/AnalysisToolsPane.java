package qupath.lib.gui.panes;

import java.util.function.Consumer;

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
import javafx.scene.shape.SVGPath;
import qupath.imagej.detect.cells.PositiveCellDetection;
import qupath.imagej.detect.cells.WatershedCellDetection;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.plugins.PathPlugin;
/**
 * 分析工具面板，提供各种分析功能的访问
 */
public class AnalysisToolsPane {

    private QuPathGUI qupath;
    private BorderPane pane;
    private boolean isPositiveCellDetection = false; // 标记当前是否为阳性细胞检测模式
    
    /**
     * 创建一个新的分析工具面板
     * @param qupath QuPath GUI实例
     */
    public AnalysisToolsPane(QuPathGUI qupath) {
        this.qupath = qupath;
        this.pane = new BorderPane();
        pane.getStyleClass().add("analysis-pane");
        // 创建卡片式布局
        GridPane gridPane = new GridPane();
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
                
        // // 添加密度图功能
        // Node densityMapCard = createActionCard("密度图", 
        //     "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;", 
        //     "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
        //     "/images/icon1.png", 
        //     e -> handlePredictStainVector(),
        //     false);
                
        // // 添加图像块和超像素功能
        // Node tileAndSuperpixelCard = createActionCard("图像块和超像素", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
        //         "/images/icon1.png", 
        //         e -> handlePredictStainVector(),
        //         false);
                
        // // 添加计算特征功能
        // Node computeFeaturesCard = createActionCard("计算特征", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
        //         "/images/icon1.png", 
        //         e -> handlePredictStainVector(),
        //         false);
                
        // // 添加空间分析功能
        // Node spatialAnalysisCard = createActionCard("空间分析", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;", 
        //         "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EFE3FF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
        //         "/images/icon1.png", 
        //         e -> handlePredictStainVector(),
        //         false);
                
        // 在网格中排列卡片
        gridPane.add(predictStainVectorCard, 0, 0, 2, 1); // 跨两列
        gridPane.add(cellDetectionCard, 0, 1, 2, 1); // 跨两列
        
        // 小卡片组
        // gridPane.add(densityMapCard, 0, 2);
        // gridPane.add(tileAndSuperpixelCard, 1, 2);
        // gridPane.add(computeFeaturesCard, 0, 3);
        // gridPane.add(spatialAnalysisCard, 1, 3);
        
        pane.setCenter(gridPane);
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
    
    // 处理密度图
    private void handleDensityMap() {
        // new DensityMapCommand(qupath).run();
    }
    
    // 处理图像块和超像素
    private void handleTileAndSuperpixel() {
        qupath.createImageDataAction(imageData -> {
            // 这里应该打开图像块创建对话框
        }).handle(null);
    }
    
    // 处理计算特征
    private void handleComputeFeatures() {
        qupath.createImageDataAction(imageData -> {
            // 这里应该打开特征计算对话框
        }).handle(null);
    }
    
    // 处理空间分析
    private void handleSpatialAnalysis() {
        qupath.createImageDataAction(imageData -> {
            // 这里应该打开空间分析对话框
        }).handle(null);
    }
}