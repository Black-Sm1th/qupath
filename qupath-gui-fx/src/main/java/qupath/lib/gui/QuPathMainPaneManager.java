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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;	

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import qupath.lib.gui.ToolBarComponent.ViewerMagnificationLabel;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.LLMClient;
import qupath.lib.gui.viewer.QuPathViewerPlus;
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
	private ToggleGroup navToggleGroup;
	private Map<String, ToggleButton> navButtons = new HashMap<>();
	private LLMClient client;

	QuPathMainPaneManager(QuPathGUI qupath) {
		this.qupath = qupath;
		this.navToggleGroup = new ToggleGroup();
		// Create main components
		toolbar = new ToolBarComponent(qupath.getToolManager(),
				qupath.getViewerActions(),
				qupath.getCommonActions(),
				qupath.getAutomateActions(),
				qupath.getOverlayActions());
		// Create the main pane
		pane = new StackPane();	
		// client = new LLMClient("sk-0cd1d137904448bf8d41b2b2c5d4988e", LLMClient.LLMType.DEEP_SEEK);
		client = new LLMClient("af59e9c0a84d46c6bd3c1a3d1e9f4391e2f6b6f3f9cb48db98f1e5a7b70c2d63", LLMClient.LLMType.PATHOLOGY);
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
			case "send" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.SEND_BTN);
			case "plus" -> IconFactory.createNode(24, 24, PathIcons.PLUS_BTN);
			case "minus" -> IconFactory.createNode(24, 24, PathIcons.MINUS_BTN);
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
		var button = new ToggleButton();
		button.getStyleClass().add("nav-button");
		// 创建普通状态和选中状态的图标
		Node normalIcon = switch(icon) {
			case "project" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.PROJECT_BTN);
			case "image" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.IMAGE_BTN);
			case "annotation" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANNOTATION_BTN);
			case "workflow" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.WORKFLOW_BTN);
			case "analysis" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANALYSIS_BTN);
			case "classify" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.CLASSIFY_BTN);
			default -> null;
		};
		
		Node selectedIcon = switch(icon) {
			case "project" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.PROJECT_BTN_FILL);
			case "image" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.IMAGE_BTN_FILL);
			case "annotation" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANNOTATION_BTN_FILL);
			case "workflow" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.WORKFLOW_BTN_FILL);
			case "analysis" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.ANALYSIS_BTN_FILL);
			case "classify" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.CLASSIFY_BTN_FILL);
			default -> null;
		};
		
		if (normalIcon != null && selectedIcon != null) {
			normalIcon.getStyleClass().add("nav-icon");
			selectedIcon.getStyleClass().add("nav-icon");
			// 设置初始图标
			button.setGraphic(normalIcon);
			// 监听选中状态变化
			button.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
				button.setGraphic(isSelected ? selectedIcon : normalIcon);
			});
		}
		// Set tooltip
		var tip = new Tooltip(tooltip);
		button.setTooltip(tip);
		// Add to toggle group
		button.setToggleGroup(navToggleGroup);
		// 阻止已选中按钮的点击事件和状态变化
		button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
			if (button.isSelected()) {
				event.consume();
			}
		});
		// Store button reference
		navButtons.put(icon, button);
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
	
	private BorderPane createBottomBarContainer() {
		// Create bottombar container
		var bottomBarContainer = new BorderPane();
		BorderPane.setMargin(bottomBarContainer,new Insets(0,16,16,16));
		bottomBarContainer.getStyleClass().add("toolbar-main-container");
		
		// Create button	
		var eyeBtn = createNormalButton("eye", "显示/隐藏");
		var gpsBtn = createNormalButton("gps", "定位");
		eyeBtn.getStyleClass().add("qupath-tool-button");
		gpsBtn.getStyleClass().add("qupath-tool-button");
		
		// Create left&right button container
		var leftButtonContainer = new VBox();
		leftButtonContainer.getStyleClass().add("toolbar-left-container");
		leftButtonContainer.getChildren().add(eyeBtn);
		
		var rightButtonContainer = new VBox();
		rightButtonContainer.getStyleClass().add("toolbar-left-container");
		rightButtonContainer.getChildren().add(gpsBtn);

		var rightScaleContainer = new BorderPane();
		rightScaleContainer.getStyleClass().add("toolbar-rightScale-container");

		// 创建放大倍数控制容器
		var magContainer = new HBox();
		magContainer.setSpacing(8);
		magContainer.setAlignment(Pos.CENTER);
		
		// 创建减号按钮
		var minusBtn = createNormalButton("minus", "减少");
		minusBtn.getStyleClass().addAll("mag-button");
		minusBtn.setOnMouseClicked(e -> {
			var viewer = qupath.getViewerManager().getActiveViewer();
			if (viewer != null) {
				double currentDownsample = viewer.getDownsampleFactor();
				viewer.setDownsampleFactor(currentDownsample * 1.25);
			}
		});

		var magLabel = new ViewerMagnificationLabel();
		var viewerProperty = qupath.getViewerActions().getViewerManager().activeViewerProperty();
		viewerProperty.addListener((v, o, n) -> magLabel.setViewer(n));
		magLabel.setViewer(viewerProperty.getValue());
		// 创建加号按钮
		var plusBtn = createNormalButton("plus", "增加");
		plusBtn.getStyleClass().addAll("mag-button");
		plusBtn.setOnMouseClicked(e -> {
			var viewer = qupath.getViewerManager().getActiveViewer();
			if (viewer != null) {
				double currentDownsample = viewer.getDownsampleFactor();
				viewer.setDownsampleFactor(currentDownsample / 1.25);
			}
		});

		magContainer.getChildren().addAll(minusBtn, magLabel, plusBtn);
		rightScaleContainer.setCenter(magContainer);
		
		// Create input container
		var inputContainer = new HBox();
		inputContainer.getStyleClass().add("toolbar-input-container");
		
		// Create input field
		var input = new TextField();
		input.getStyleClass().add("toolbar-input");
		input.setPromptText("请输入您的问题");
		
		// Create left icon
		var leftIcon = new Region();
		leftIcon.getStyleClass().addAll("toolbar-input-icon", "left");
		
		// Create right icon
		var sendBtn = createNormalButton("send", "发送");
		sendBtn.getStyleClass().add("toolbar-message-button");

		sendBtn.setOnMouseClicked(value -> {
			client.getCompletionAsync(input.getText(), 
				response -> {
					// 更新UI
					Platform.runLater(() -> logger.info(response));
				},
				error -> {
					Platform.runLater(() -> logger.error(error.getMessage()));
				}
			);
			input.setText("");
		});

		// 添加回车键事件监听
		input.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				client.getStreamingCompletionAsync(input.getText(), 
					response -> {
						// 更新UI
						Platform.runLater(() -> logger.info(response));
					},
					error -> {
						Platform.runLater(() -> logger.error(error.getMessage()));
					}
				);
				input.setText("");
				e.consume();
			}
		});

		HBox.setHgrow(input, Priority.ALWAYS);
		// Add components to input container in specific order
		inputContainer.getChildren().addAll(leftIcon, input, sendBtn);

		var leftContainer = new HBox();
		leftContainer.setPrefWidth(400);
		leftContainer.setMinWidth(400);
		leftContainer.setPrefWidth(400);
		leftContainer.getChildren().add(leftButtonContainer);

		var rightContainer = new HBox();
		rightContainer.setPrefWidth(400);
		rightContainer.setMinWidth(400);
		rightContainer.setPrefWidth(400);
		rightContainer.setSpacing(4);
		var spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		rightContainer.getChildren().addAll(spacer,rightScaleContainer, rightButtonContainer);
		// 获取viewer中的panelLocation和scalebarNode
		var viewer = qupath.getViewerManager().getActiveViewer();
		if (viewer instanceof QuPathViewerPlus) {
			var viewerPlus = (QuPathViewerPlus)viewer;
			var scalebarNode = viewerPlus.getScalebarNode();
			scalebarNode.getStyleClass().add("scale-container");
			HBox.setMargin(scalebarNode, new Insets(0,0,0,33));
			var panelLocation = viewerPlus.getPanelLocation();
			HBox.setMargin(panelLocation, new Insets(0,0,0,20));
			leftContainer.getChildren().addAll(scalebarNode,panelLocation);
		}
		bottomBarContainer.setLeft(leftContainer);
		bottomBarContainer.setCenter(inputContainer);
		bottomBarContainer.setRight(rightContainer);
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
		BorderPane.setMargin(leftContainer, new Insets(16,0,16,16));
		leftContainer.getStyleClass().add("left-container");

		// Create left navigation bar
		var navBar = new VBox();
		navBar.getStyleClass().add("nav-bar");
		
		// Create work pane container
		var workContainer = new StackPane();
		workContainer.getStyleClass().add("work-pane");
		
		// Get work panes from analysis tab pane
		this.analysisTabPane = new AnalysisTabPane(qupath);
		var projectPane = analysisTabPane.getProjectBrowser().getPane();
		var imagePane = analysisTabPane.getTabPane().getTabs().get(1).getContent();
		var annotationPane = analysisTabPane.getTabPane().getTabs().get(2).getContent();
		var workflowPane = analysisTabPane.getTabPane().getTabs().get(4).getContent();
		var analysisPane = analysisTabPane.getTabPane().getTabs().get(3).getContent();
		
		// Add all panes to container but make them invisible initially
		workContainer.getChildren().addAll(projectPane, imagePane, annotationPane, workflowPane, analysisPane);
		projectPane.setVisible(true);
		imagePane.setVisible(false);
		annotationPane.setVisible(false);
		workflowPane.setVisible(false);
		analysisPane.setVisible(false);
		
		// Add navigation buttons
		var projectBtn = (ToggleButton)createNavButton("project", "项目");
		var imageBtn = (ToggleButton)createNavButton("image", "图像");
		var annotationBtn = (ToggleButton)createNavButton("annotation", "标注");
		var workflowBtn = (ToggleButton)createNavButton("workflow", "工作流");
		var analysisBtn = (ToggleButton)createNavButton("analysis", "分析");
		var classifyBtn = (ToggleButton)createNavButton("classify", "分类");

		// Set default selected button
		projectBtn.setSelected(true);

		// 添加按钮点击事件处理
		projectBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(0);
			// 切换工作区显示
			projectPane.setVisible(true);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			analysisPane.setVisible(false);
		});
		
		imageBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(1);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(true);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			analysisPane.setVisible(false);
		});
		
		annotationBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(2);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(true);
			workflowPane.setVisible(false);
			analysisPane.setVisible(false);
		});
		
		workflowBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(4);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(true);
			analysisPane.setVisible(false);
		});
		
		analysisBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(3);
			// 处理分析按钮点击事件
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			analysisPane.setVisible(true);
		});
		
		classifyBtn.setOnAction(e -> {
			// 处理分类按钮点击事件
		});

		for (var btn : Arrays.asList(projectBtn, imageBtn, annotationBtn, workflowBtn, analysisBtn, classifyBtn)) {
			navBar.getChildren().add(btn);
		}

		leftContainer.getChildren().addAll(navBar, workContainer);
		return leftContainer;
	}
}