package qupath.lib.gui.panes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;


public class CustomAnnotationPane{
    private QuPathGUI qupath;
    private static final Logger logger = LoggerFactory.getLogger(CustomAnnotationPane.class);
    private ScrollPane pane;
    public CustomAnnotationPane(QuPathGUI qupath){
        this.qupath = qupath;
    }
    public ScrollPane getPane(){
        if (pane == null)
            pane = createPane();
        return pane;
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
        Button deleteBtn = new Button();
		deleteBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.DELETE_BTN));
        deleteBtn.getStyleClass().add("custom-annotation-button");
        Button moreBtn = new Button();
		moreBtn.setGraphic(IconFactory.createNode(16, 16, PathIcons.MORE_BTN));
        moreBtn.getStyleClass().add("custom-annotation-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBarAnnotation.getChildren().addAll(labelAnnotation, spacer, selectAllBtn, deleteBtn, moreBtn);

        VBox annotationList = new VBox();
        annotationList.getStyleClass().add("custom-annotation-list");
        mdPane.getChildren().add(annotationList);

        pane = new ScrollPane(mdPane);
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        
        return pane;
    }
} 