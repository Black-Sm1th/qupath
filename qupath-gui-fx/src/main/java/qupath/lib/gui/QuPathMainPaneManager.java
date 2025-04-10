/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */


package qupath.lib.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;

/**
 * Inelegantly named class to manage the main components of the main QuPath window.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class QuPathMainPaneManager {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathMainPaneManager.class);
	
	private StackPane pane;
	private Region mainViewerPane;
	private ToolBarComponent toolbar;
	private AnalysisTabPane analysisTabPane;
	private QuPathGUI qupath;
	
	QuPathMainPaneManager(QuPathGUI qupath) {
		this.qupath = qupath;
		// Create main components
		toolbar = new ToolBarComponent(qupath.getToolManager(),
				qupath.getViewerActions(),
				qupath.getCommonActions(),
				qupath.getAutomateActions(),
				qupath.getOverlayActions());
		// Create the main pane
		pane = new StackPane();	
		
		// Get viewer pane
		mainViewerPane = qupath.getViewerManager().getRegion();
		pane.getChildren().add(mainViewerPane);

		// Create overlay container for UI elements
		var overlayContainer = new BorderPane();
		overlayContainer.setPickOnBounds(false);
		pane.getChildren().add(overlayContainer);

		// Add topbar to overlayContainer
		overlayContainer.setTop(createTopBarContainer());
		// Add left to overlayContainer
		overlayContainer.setLeft(createLeftContainer());
		// Add bottom to overlayContainer
		overlayContainer.setBottom(createBottomBarContainer());

		// Add CSS styles
		pane.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
	}

	private Node createNormalButton(String icon, String tooltip) {
		var button = new Button();
		// Set icon based on type
		Node iconNode = switch(icon) {
			case "menu" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.MEASURE);
			case "eye" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.EYE_BTN);
			case "gps" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.GPS_BTN);
			default -> null;
		};
		if (iconNode != null) {
			button.setGraphic(iconNode);
		}
		// Set tooltip
		var tip = new Tooltip(tooltip);
		button.setTooltip(tip);
		return button;
	}

	private Node createNavButton(String icon, String tooltip) {
		var button = new Button();
		button.getStyleClass().add("nav-button");
		// Set icon based on type
		Node iconNode = switch(icon) {
			case "project" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.PROJECT_BTN);
			case "image" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.IMAGE_BTN);
			case "annotation" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANNOTATION_BTN);
			case "workflow" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.WORKFLOW_BTN);
			case "analysis" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANALYSIS_BTN);
			case "classify" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.CLASSIFY_BTN);
			default -> null;
		};
		if (iconNode != null) {
			button.setGraphic(iconNode);
		}
		
		// Set tooltip
		var tip = new Tooltip(tooltip);
		button.setTooltip(tip);
		return button;
	}

	AnalysisTabPane getAnalysisTabPane() {
		return analysisTabPane;
	}
	
	ProjectBrowser getProjectBrowser() {
		return analysisTabPane == null ? null : analysisTabPane.getProjectBrowser();
	}
	
	HBox getToolBar() {
		return toolbar.getToolBar();
	}

	
	Pane getMainPane() {
		return pane;
	}
	
	void setDividerPosition(double pos) {
		// splitPane.setDividerPosition(0, pos);
	}
	
	void setAnalysisPaneVisible(boolean visible) {
		// Do nothing - analysis pane is always hidden
	}
	
	private boolean analysisPanelVisible() {
		return false;
	}
	
	private HBox createBottomBarContainer() {
		// Create bottombar container
		var bottomBarContainer = new HBox();
		BorderPane.setMargin(bottomBarContainer,new Insets(0,16,16,16));
		bottomBarContainer.getStyleClass().add("toolbar-main-container");
		// Create button	
		var eyeBtn = createNormalButton("eye", "项目");
		var gpsBtn = createNormalButton("gps", "项目");
		eyeBtn.getStyleClass().add("qupath-tool-button");
		gpsBtn.getStyleClass().add("qupath-tool-button");
		// Create left&right button container
		var leftButtonContainer = new VBox();
		leftButtonContainer.getStyleClass().add("toolbar-left-container");
		leftButtonContainer.getChildren().add(eyeBtn);
		var rightButtonContainer = new VBox();
		rightButtonContainer.getStyleClass().add("toolbar-left-container");
		rightButtonContainer.getChildren().add(gpsBtn);
		// Create Spacer
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		bottomBarContainer.getChildren().addAll(leftButtonContainer, spacer, rightButtonContainer);

		return bottomBarContainer;
	}

	private HBox createTopBarContainer() {
		// Create topbar container
		var topBarContainer = new HBox();
		BorderPane.setMargin(topBarContainer,new Insets(16,16,0,16));
		topBarContainer.getStyleClass().add("toolbar-main-container");
	
		// Create left button container
		var leftButtonContainer = new VBox();
		leftButtonContainer.getStyleClass().add("toolbar-left-container");
		var menuBtn = createNormalButton("menu", "菜单");
		menuBtn.getStyleClass().add("qupath-tool-button");
		leftButtonContainer.getChildren().add(menuBtn);

		// Create right button container
		var rightButtonContainer = new VBox();
		rightButtonContainer.getStyleClass().add("toolbar-right-container");
		
		// Create Spacer
		Region leftSpacer = new Region();
        Region rightSpacer = new Region();
		HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
		var toolBar = toolbar.getToolBar();

		// Add components to main container
		topBarContainer.getChildren().addAll(leftButtonContainer, leftSpacer, toolBar, rightSpacer, rightButtonContainer);

		return topBarContainer;
	}

	private HBox createLeftContainer() {
		var leftContainer = new HBox();
		BorderPane.setMargin(leftContainer,new Insets(16,0,16,16));
		leftContainer.getStyleClass().add("left-container");

		// Create left navigation bar
		var navBar = new VBox();
		navBar.getStyleClass().add("nav-bar");
		// Add navigation buttons
		var projectBtn = createNavButton("project", "项目");
		var imageBtn = createNavButton("image", "图像");
		var annotationBtn = createNavButton("annotation", "标注");
		var workflowBtn = createNavButton("workflow", "工作流");
		var analysisBtn = createNavButton("analysis", "分析");
		var classifyBtn = createNavButton("classify", "分类");

		for (var btn : Arrays.asList(projectBtn, imageBtn, annotationBtn, workflowBtn, analysisBtn, classifyBtn)) {
			navBar.getChildren().add(btn);
		}

		// Get project browser from analysis tab pane
		this.analysisTabPane = new AnalysisTabPane(qupath);
		var projectBrowserPane = analysisTabPane.getProjectBrowser().getPane();
		projectBrowserPane.getStyleClass().add("project-pane");
		leftContainer.getChildren().addAll(navBar, projectBrowserPane);
		return leftContainer;
	}
}