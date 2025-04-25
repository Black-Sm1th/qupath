package qupath.lib.gui.tools;

import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class FrostedGlassEffectUtil {

    /**
     * 为任意布局添加磨玻璃背景效果
     * @param layout 要添加效果的布局（Region或其子类）
     * @param blurRadius 高斯模糊半径（像素）
     */
    public static void applyFrostedGlassEffect(Region layout, double blurRadius) {
        // 保存原始背景和边框
        Background originalBackground = layout.getBackground();
        Border originalBorder = layout.getBorder();
        
        // 保存原始圆角属性（如果有）
        CornerRadii cornerRadii = null;
        if (originalBackground != null && !originalBackground.getFills().isEmpty()) {
            cornerRadii = originalBackground.getFills().get(0).getRadii();
        }
        
        // 创建一个矩形作为模糊背景
        Rectangle background = new Rectangle();
        background.widthProperty().bind(layout.widthProperty());
        background.heightProperty().bind(layout.heightProperty());
        
        // 从原始背景中提取颜色（默认为半透明白色）
        Color fillColor = Color.rgb(255, 255, 255, 0.7);
        if (originalBackground != null && !originalBackground.getFills().isEmpty()) {
            BackgroundFill firstFill = originalBackground.getFills().get(0);
            if (firstFill.getFill() instanceof Color) {
                fillColor = (Color) firstFill.getFill();
                // 保持颜色但增加透明度
                fillColor = Color.color(
                    fillColor.getRed(), 
                    fillColor.getGreen(), 
                    fillColor.getBlue(), 
                    0.7
                );
            }
        }
        background.setFill(fillColor);
        
        // 应用圆角（如果有）
        if (cornerRadii != null) {
            background.setArcWidth(cornerRadii.getTopLeftHorizontalRadius() * 2);
            background.setArcHeight(cornerRadii.getTopLeftVerticalRadius() * 2);
        }
        
        // 应用高斯模糊
        GaussianBlur blur = new GaussianBlur(blurRadius);
        background.setEffect(blur);
        
        // 清除布局的原始背景和边框
        layout.setBackground(null);
        layout.setBorder(null);
        
        // 如果布局不是StackPane或Pane，需要将其放入新的StackPane中
        if (!(layout instanceof Pane)) {
            StackPane container = new StackPane();
            container.getChildren().add(layout);
            
            // 将背景添加到容器中
            container.getChildren().add(0, background);
            
            // 替换原始布局
            if (layout.getParent() != null && layout.getParent() instanceof Pane) {
                Pane parent = (Pane) layout.getParent();
                int index = parent.getChildren().indexOf(layout);
                parent.getChildren().remove(layout);
                parent.getChildren().add(index, container);
            }
        } else {
            // 对于Pane/StackPane，可以直接添加背景
            ((Pane) layout).getChildren().add(0, background);
        }
        
        // 确保背景位于所有其他子节点之下
        background.toBack();
    }
}