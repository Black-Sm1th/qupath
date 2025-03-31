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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
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
	
	private BorderPane pane;
	private SplitPane splitPane;
	private Region mainViewerPane;
	private StackPane mainContent;
	private StackPane viewerContainer;
	
	private ToolBarComponent toolbar;
	private AnalysisTabPane analysisTabPane;
	
	private double lastDividerLocation;
	
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
		pane = new BorderPane();
		pane.getStyleClass().add("main-pane");
		
		// Initialize split pane and analysis tab pane
		splitPane = new SplitPane();
		this.analysisTabPane = new AnalysisTabPane(qupath);
		var tabPane = analysisTabPane.getTabPane();
		tabPane.setMinWidth(300);
		tabPane.setPrefWidth(400);
		splitPane.setMinWidth(tabPane.getMinWidth() + 200);
		splitPane.setPrefWidth(tabPane.getPrefWidth() + 200);
		SplitPane.setResizableWithParent(tabPane, Boolean.FALSE);
		
		// Create toolbar container
		var toolbarContainer = new VBox();
		toolbarContainer.getStyleClass().add("toolbar-container");
		toolbarContainer.setAlignment(Pos.TOP_CENTER);
		toolbarContainer.setPadding(new Insets(10));
		toolbarContainer.getChildren().add(toolbar.getToolBar());
		
		// Create left navigation bar
		var navBar = new VBox();
		navBar.getStyleClass().add("nav-bar");
		navBar.setPrefWidth(60);
		navBar.setMinWidth(60);
		navBar.setMaxWidth(60);
		
		// Add navigation buttons
		var projectBtn = createNavButton("folder", "项目");
		var annotationBtn = createNavButton("annotation", "标注");
		var measureBtn = createNavButton("measure", "测量");
		var analysisBtn = createNavButton("analysis", "分析");
		var settingsBtn = createNavButton("settings", "设置");
		
		navBar.getChildren().addAll(
			projectBtn,
			annotationBtn,
			measureBtn,
			analysisBtn,
			settingsBtn
		);
		
		// Create main content area
		mainContent = new StackPane();
		mainContent.getStyleClass().add("main-content");
		
		// Get project browser from analysis tab pane
		var projectBrowserPane = analysisTabPane.getProjectBrowser().getPane();
		projectBrowserPane.getStyleClass().add("project-pane");
		projectBrowserPane.setPrefWidth(300);
		projectBrowserPane.setMinWidth(200);
		projectBrowserPane.setMaxWidth(400);
		projectBrowserPane.setStyle("-fx-background-color: rgba(255,255,255,0.95);");
		VBox.setVgrow(projectBrowserPane, Priority.ALWAYS);
		
		// Get viewer pane
		var viewerManager = qupath.getViewerManager();
		mainViewerPane = viewerManager.getRegion();
		mainViewerPane.getStyleClass().add("viewer-pane");
		VBox.setVgrow(mainViewerPane, Priority.ALWAYS);
		HBox.setHgrow(mainViewerPane, Priority.ALWAYS);
		
		// Create viewer container that fills the entire space
		viewerContainer = new StackPane();
		viewerContainer.getStyleClass().add("viewer-container");
		viewerContainer.getChildren().add(mainViewerPane);
		viewerContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		
		// Add viewer container as the base layer
		mainContent.getChildren().add(viewerContainer);
		
		// Create overlay container for UI elements
		var overlayContainer = new BorderPane();
		overlayContainer.setPickOnBounds(false); // Allow clicks to pass through to viewer
		
		// Create left side container with nav bar and project browser
		var leftContainer = new HBox();
		leftContainer.setSpacing(10);
		leftContainer.getChildren().addAll(navBar, projectBrowserPane);
		leftContainer.setStyle("-fx-background-color: transparent;");
		
		// Set up overlay layout
		overlayContainer.setTop(toolbarContainer);
		overlayContainer.setLeft(leftContainer);
		
		// Add overlay container on top of viewer
		mainContent.getChildren().add(overlayContainer);
		
		// Set the main content as the center of the border pane
		pane.setCenter(mainContent);
		
		// Add CSS styles
		pane.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
		
		// Set analysis pane visibility (now safe to call since splitPane is initialized)
		setAnalysisPaneVisible(false);  // Start with analysis pane hidden
	}
	
	private Node createNavButton(String icon, String tooltip) {
		var button = new Button();
		button.getStyleClass().add("nav-button");
		
		// Set icon based on type
		Node iconNode = switch(icon) {
			case "folder" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.EXTRACT_REGION);
			case "annotation" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.PIXEL_CLASSIFICATION);
			case "measure" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.PIXEL_CLASSIFICATION);
			case "analysis" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.PIXEL_CLASSIFICATION);
			case "settings" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.PIXEL_CLASSIFICATION);
			default -> null;
		};
		
		if (iconNode != null) {
			iconNode.getStyleClass().add("nav-icon");
			button.setGraphic(iconNode);
		}
		
		// Set tooltip
		button.setTooltip(new Tooltip(tooltip));
		
		return button;
	}

	AnalysisTabPane getAnalysisTabPane() {
		return analysisTabPane;
	}
	
	ProjectBrowser getProjectBrowser() {
		return analysisTabPane == null ? null : analysisTabPane.getProjectBrowser();
	}
	
	ToolBar getToolBar() {
		return toolbar.getToolBar();
	}

	
	Pane getMainPane() {
		return pane;
	}
	
	void setDividerPosition(double pos) {
		splitPane.setDividerPosition(0, pos);
	}
	
	void setAnalysisPaneVisible(boolean visible) {
		// Do nothing - analysis pane is always hidden
	}
	
	private boolean analysisPanelVisible() {
		return false;
	}
	
}