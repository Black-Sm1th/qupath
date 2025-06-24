package qupath.lib.gui.panes;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.objects.PathDetectionObject;

/**
 * 分析工具面板，提供各种分析功能的访问
 */
public class ClassifyPane {

    private QuPathGUI qupath;
    private BorderPane pane;
    private boolean isSubmenuVisible = false; // 子菜单是否可见
    private Node selectedSmallCard = null; // 当前选中的小卡片
    private GridPane gridPane; // 网格面板
    
    /**
     * 创建一个新的分析工具面板
     * @param qupath QuPath GUI实例
     */
    public ClassifyPane(QuPathGUI qupath) {
        this.qupath = qupath;
        this.pane = new BorderPane();
        pane.getStyleClass().add("analysis-pane");
        
        // 创建卡片式布局
        gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);
        
        Node card1 = createActionCard("训练对象分类器", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#FFE8E2 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#FFE8E2 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
                "/images/icon3.png", 
                e -> runObjectClassifierCommand());
                
        Node card2 = createActionCard("训练像素分类器", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#FFEFF5 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#FFEFF5 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
                "/images/icon4.png", 
                e -> runPixelClassifierCommand());

        Node card3 = createActionCard("创建训练图像", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EAF9EF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;", 
                "-fx-border-radius: 20px;-fx-background-radius: 20px; -fx-background-color: linear-gradient(to right,white 0%,#EAF9EF 50%,white 100%);-fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px;-fx-min-height: 80px;-fx-max-height: 80px;-fx-min-width: 296px;-fx-max-width: 296px;-fx-cursor: hand;-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);", 
                "/images/icon5.png", 
                e -> runCreateTrainingImageCommand());
                
        // 小卡片组
        Node card4 = createSmallCard("重置检测分类", e -> resetObjectClassifications());
        Node card5 = createSmallCard("加载对象分类器", e -> loadObjectClassifier());
        Node card6 = createSmallCard("创建阈值器", e -> createThresholder());
        Node card7 = createSmallCard("加载像素分类器", e -> loadPixelClassifier());
        Node card8 = createSmallCard("创建区域标注", e -> createRegionAnnotations());
        Node card9 = createSmallCard("拆分项目训练/验证/测试", e -> splitProject());
                
        // 在网格中排列卡片
        gridPane.add(card1, 0, 0, 2, 1); // 跨两列
        gridPane.add(card4, 0, 1);
        gridPane.add(card5, 1, 1);
        gridPane.add(card2, 0, 2, 2, 1); // 跨两列
        gridPane.add(card6, 0, 3);
        gridPane.add(card7, 1, 3);
        gridPane.add(card3, 0, 4, 2, 1);// 跨两列
        gridPane.add(card8, 0, 5);
        gridPane.add(card9, 1, 5);

        pane.setCenter(gridPane);
    }
    
    // 运行对象分类器命令
    private void runObjectClassifierCommand() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.ObjectClassifierCommand");
            Object command = commandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 运行像素分类器命令
    private void runPixelClassifierCommand() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.PixelClassifierCommand");
            Object command = commandClass.getConstructor().newInstance();
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 重置对象分类
    private void resetObjectClassifications() {
        try {
            var imageData = qupath.getImageData();
            if (imageData == null) {
                Dialogs.showErrorMessage("重置分类", "当前没有打开图像！");
                return;
            }
            Commands.resetClassifications(imageData, PathDetectionObject.class);
        } catch (Exception e) {
            Dialogs.showErrorMessage("重置分类错误", "无法重置检测分类: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 加载对象分类器
    private void loadObjectClassifier() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.ObjectClassifierLoadCommand");
            Object command = commandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 创建阈值器
    private void createThresholder() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.SimpleThresholdCommand");
            Object command = commandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 加载像素分类器
    private void loadPixelClassifier() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.ui.LoadResourceCommand");
            Object command = commandClass.getMethod("createLoadPixelClassifierCommand", QuPathGUI.class).invoke(null, qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 创建区域标注
    private void createRegionAnnotations() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.CreateRegionAnnotationsCommand");
            Object command = commandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 拆分项目
    private void splitProject() {
        try {
            // 使用反射动态调用
            Class<?> commandClass = Class.forName("qupath.process.gui.commands.SplitProjectTrainingCommand");
            Object command = commandClass.getConstructor(QuPathGUI.class).newInstance(qupath);
            commandClass.getMethod("run").invoke(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 运行创建训练图像命令
    private void runCreateTrainingImageCommand() {
        try {
            // 检查是否有项目
            var project = qupath.getProject();
            if (project == null) {
                Dialogs.showErrorMessage("创建训练图像", "您需要先打开一个项目！");
                return;
            }
            
            // 使用与菜单栏相同的方法实现
            // 这与ProcessingExtension中的promptToCreateSparseServer方法类似
            try {
                // 创建训练图像
                Class<?> createTrainingImageCommandClass = Class.forName("qupath.process.gui.commands.CreateTrainingImageCommand");
                Method method = createTrainingImageCommandClass.getMethod("promptToCreateTrainingImage", 
                                                                    Class.forName("qupath.lib.projects.Project"),
                                                                    java.util.List.class);
                
                var entry = method.invoke(null, project, qupath.getAvailablePathClasses());
                
                // 刷新项目并打开图像
                qupath.refreshProject();
                if (entry != null) {
                    // 打开新创建的图像
                    Method openImageEntryMethod = qupath.getClass().getMethod("openImageEntry", Class.forName("qupath.lib.projects.ProjectImageEntry"));
                    openImageEntryMethod.invoke(qupath, entry);
                }
            } catch (ClassNotFoundException e) {
                Dialogs.showErrorMessage("创建训练图像", "找不到CreateTrainingImageCommand类。请确保已安装processing扩展。");
                throw e;
            }
        } catch (Exception e) {
            Dialogs.showErrorMessage("创建训练图像错误", "无法创建训练图像: " + e.getMessage());
            e.printStackTrace();
        }
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
    private Node createActionCard(String title, String gradientStyle, String hoverStyle, String iconString, Consumer<Void> action) {
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
        
        // 如果不需要箭头按钮，只添加普通悬停效果
        card.setOnMouseEntered(e -> {
            card.setStyle(hoverStyle);
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle(gradientStyle);
        });
        
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
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;-fx-padding: 10 20 10 20;");
        card.setPrefSize(144, 68); // 设置小卡片尺寸
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;-fx-text-fill: rgba(0, 0, 0, 0.85)");
        card.setCenter(titleLabel);
        
        // 悬停效果
        card.setOnMouseEntered(e -> {
            if (card != selectedSmallCard) {
                card.setStyle("-fx-background-color: rgba(253, 253, 253, 0.8); -fx-border-color: rgba(0, 0, 0, 0.12); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);-fx-cursor: hand;-fx-padding: 10 20 10 20;");
            }
        });
        
        card.setOnMouseExited(e -> {
            if (card != selectedSmallCard) {
                card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-border-color: rgba(0, 0, 0, 0.08); -fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;-fx-cursor: hand;-fx-padding: 10 20 10 20;");
            }
        });
        
        card.setOnMouseClicked(e -> {
            if (action != null) {
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
}