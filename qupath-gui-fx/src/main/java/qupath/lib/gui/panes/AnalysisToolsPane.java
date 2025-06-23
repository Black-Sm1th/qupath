package qupath.lib.gui.panes;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import qupath.imagej.detect.cells.WatershedCellDetection;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;

/**
 * 分析工具面板，提供各种分析功能的访问
 */
public class AnalysisToolsPane {

    private QuPathGUI qupath;
    private BorderPane pane;
    
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
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#D6BBFB 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 276px;-fx-max-width: 276px;-fx-cursor: hand;", 
                "/images/icon1.png", 
                e -> handlePredictStainVector());
                
        // // 添加细胞检测功能
        // Node cellDetectionCard = createActionCard("细胞检测", 
        //         "检测和分析图像中的细胞", 
        //         Color.rgb(15, 156, 230), 
        //         e -> handleCellDetection());
                
        // // 添加密度图功能
        // Node densityMapCard = createActionCard("密度图", 
        //         "创建对象密度的热力图", 
        //         null, 
        //         e -> handleDensityMap());
                
        // // 添加图像块和超像素功能
        // Node tileAndSuperpixelCard = createActionCard("图像块和超像素", 
        //         "创建网格块或图像分割", 
        //         null, 
        //         e -> handleTileAndSuperpixel());
                
        // // 添加计算特征功能
        // Node computeFeaturesCard = createActionCard("计算特征", 
        //         "计算选中对象的特征", 
        //         null, 
        //         e -> handleComputeFeatures());
                
        // // 添加空间分析功能
        // Node spatialAnalysisCard = createActionCard("空间分析", 
        //         "分析对象的空间分布", 
        //         null, 
        //         e -> handleSpatialAnalysis());
                
        // 在网格中排列卡片
        gridPane.add(predictStainVectorCard, 0, 0, 2, 1); // 跨两列
        // gridPane.add(cellDetectionCard, 0, 1, 2, 1); // 跨两列
        
        // // 小卡片组
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
     */
    private Node createActionCard(String title, String gradientStyle, String iconString, Consumer<Void> action) {
        BorderPane card = new BorderPane();
        card.getStyleClass().add("analysis-card");
        
        card.setStyle(gradientStyle);
        
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("analysis-card-title");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(titleLabel);
        
        card.setCenter(content);
        card.setOnMouseClicked(e -> {
            if (action != null) {
                action.accept(null);
            }
        });
        
        return card;
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
        // 直接创建并运行细胞检测插件
        qupath.createPluginAction(null, WatershedCellDetection.class, null)
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