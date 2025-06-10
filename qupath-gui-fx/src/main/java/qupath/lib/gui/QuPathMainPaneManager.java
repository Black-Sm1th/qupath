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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.lib.images.servers.TransformedServerBuilder;

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
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
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
import qupath.lib.gui.tools.QuPathTranslator;
import qupath.lib.gui.panes.CustomAnnotationPane;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.LLMClient;
import qupath.lib.gui.tools.QuPathTranslator;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
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
	
	// 添加自定义标注面板
	private CustomAnnotationPane customAnnotationPane;
	
	private StackPane extendContainer;
	// 记录当前选中的标注对象
	private PathObject currentSelectedAnnotation;
	
	// 用于列表项的滚动容器
	private VBox listContainer;
	private HBox analysisContainer;
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
		
		// 初始化 LLMClient
		client = new LLMClient();
		
		// Get viewer pane
		mainViewerPane = qupath.getViewerManager().getRegion();
		pane.getChildren().add(mainViewerPane);

		// Create overlay container for UI elements
		var overlayContainer = new BorderPane();
		overlayContainer.setPickOnBounds(false);
		pane.getChildren().add(overlayContainer);

		// Add topbar to overlayContainer
		overlayContainer.setTop(createTopBarContainer());
		analysisContainer = createLeftContainer();
		// Add left to overlayContainer
		overlayContainer.setLeft(analysisContainer);
		// Add bottom to overlayContainer
		overlayContainer.setBottom(createBottomBarContainer());

		
		// 添加选择监听器 
		setupSelectionListener();
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
		if(visible){
			analysisContainer.setVisible(true);
		}else{
			analysisContainer.setVisible(false);
		}
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
		
		// 为眼睛按钮添加弹出菜单
		ContextMenu displayMenu = new ContextMenu();
		String os = System.getProperty("os.name").toLowerCase();
		// 添加"概览图"选项
		CheckMenuItem overviewItem = new CheckMenuItem("概览图");
		overviewItem.selectedProperty().bindBidirectional(qupath.getViewerActions().SHOW_OVERVIEW.selectedProperty());
		// 添加"网格"选项
		CheckMenuItem gridItem = new CheckMenuItem();
		if(os.contains("windows")){
			gridItem.setText("网格\t\t\tShift+G");
		}else{
			gridItem.setText("网格 \t\tShift+G");
		}
		gridItem.selectedProperty().bindBidirectional(qupath.getOverlayActions().SHOW_GRID.selectedProperty());
		
		// 添加"比例尺"选项
		CheckMenuItem scalebarItem = new CheckMenuItem("比例尺");
		scalebarItem.selectedProperty().bindBidirectional(qupath.getViewerActions().SHOW_SCALEBAR.selectedProperty());
		
		// 添加"坐标"选项
		CheckMenuItem locationItem = new CheckMenuItem("坐标");
		locationItem.selectedProperty().bindBidirectional(qupath.getViewerActions().SHOW_LOCATION.selectedProperty());
		
		// 添加"标注名"选项
		CheckMenuItem namesItem = new CheckMenuItem();
		if(os.contains("windows")){
			namesItem.setText("标注名\t\t          N");
		}else{
			namesItem.setText("标注名\t\t            N");
		}
		namesItem.selectedProperty().bindBidirectional(qupath.getOverlayActions().SHOW_NAMES.selectedProperty());
		
		// 添加"导航栏"选项 - 使用CommonActions中的SHOW_ANALYSIS_PANE
		CheckMenuItem navBarItem = new CheckMenuItem("导航栏\t\tShift+A");
		navBarItem.selectedProperty().bindBidirectional(qupath.getCommonActions().SHOW_ANALYSIS_PANE.selectedProperty());
		
		// 添加"AI助手"选项 - 控制底部输入框的显示
		BooleanProperty showAIHelper = new SimpleBooleanProperty(true);
		CheckMenuItem aiHelperItem = new CheckMenuItem("AI助手");
		aiHelperItem.setSelected(true);
		aiHelperItem.selectedProperty().addListener((v, o, n) -> {
			// 获取底部容器中的输入容器
			var bottomContainer = (BorderPane)pane.getChildren().stream()
					.filter(node -> node instanceof BorderPane)
					.map(node -> ((BorderPane)node).getBottom())
					.filter(node -> node instanceof BorderPane)
					.findFirst().orElse(null);
					
			if (bottomContainer != null) {
				var inputContainer = bottomContainer.getCenter();
				if (inputContainer != null) {
					inputContainer.setVisible(n);
					inputContainer.setManaged(n);
				}
			}
		});
		
		// 将所有选项添加到菜单中
		displayMenu.getItems().addAll(
			navBarItem,
			aiHelperItem,
			overviewItem,
			gridItem,
			scalebarItem,
			locationItem,
			namesItem
		);
		
		// 为眼睛按钮添加点击事件
		eyeBtn.setOnMouseClicked(e -> {
			displayMenu.show(eyeBtn, javafx.geometry.Side.BOTTOM, 0, 0);
		});
		
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
			// 获取当前图片真实路径
			String currentImage = "";
			if (qupath.getImageData() != null && qupath.getImageData().getServer() != null) {
				try {
					ImageServer<BufferedImage> server = (ImageServer<BufferedImage>)qupath.getImageData().getServer();
					final String tempImagePath = "@" + convertImageToTempFile(server);
					client.getCompletionAsync(input.getText(), tempImagePath,
						response -> {
							// 更新UI
							Platform.runLater(() -> logger.info(response));
							// 删除临时文件
							deleteTempFile(tempImagePath);
						},
						error -> {
							Platform.runLater(() -> logger.error(error.getMessage()));
							// 发生错误时也要删除临时文件
							deleteTempFile(tempImagePath);
						}
					);
				} catch (Exception e) {
					logger.error("获取图片路径失败", e);
				}
			}
			input.setText("");
		});

		// 添加回车键事件监听
		input.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				// 获取当前图片真实路径
				String currentImage = "";
				if (qupath.getImageData() != null && qupath.getImageData().getServer() != null) {
					try {
						ImageServer<BufferedImage> server = (ImageServer<BufferedImage>)qupath.getImageData().getServer();
						final String tempImagePath = "@" + convertImageToTempFile(server);
						client.getCompletionAsync(input.getText(), tempImagePath,
							response -> {
								// 更新UI
								Platform.runLater(() -> logger.info(response));
								// 删除临时文件
								deleteTempFile(tempImagePath);
							},
							error -> {
								Platform.runLater(() -> logger.error(error.getMessage()));
								// 发生错误时也要删除临时文件
								deleteTempFile(tempImagePath);
							}
						);
					} catch (Exception ex) {
						logger.error("获取图片路径失败", ex);
					}
				}
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

	private MenuItem copyMenuItem(MenuItem item) {
		if (item instanceof Menu menu) {
			// 直接返回原始Menu，保证动态子菜单能正常显示
			return menu;
		} else {
			MenuItem newItem = new MenuItem(item.getText());
			newItem.setOnAction(item.getOnAction());
			return newItem;
		}
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

		// 新增：弹出菜单
		ContextMenu popupMenu = new ContextMenu();
		for (Menu menu : qupath.getMenuBar().getMenus()) {
			popupMenu.getItems().add(copyMenuItem(menu));
		}
		menuBtn.setOnMouseClicked(e -> {
			popupMenu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
		});

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
		HBox.setHgrow(workContainer, Priority.ALWAYS);

		// Create extend pane container
		extendContainer = new StackPane();
		extendContainer.getStyleClass().add("extend-pane");
		extendContainer.setVisible(false); // 初始状态为隐藏
		extendContainer.setManaged(false); // 添加这一行，让隐藏时不占据空间
		
		// Get work panes from analysis tab pane
		this.analysisTabPane = new AnalysisTabPane(qupath);
		var projectPane = analysisTabPane.getProjectBrowser().getPane();
		var imagePane = analysisTabPane.getTabPane().getTabs().get(1).getContent();
		
		// 初始化自定义标注面板
		this.customAnnotationPane = new CustomAnnotationPane(qupath);
		var annotationPane = customAnnotationPane.getPane();
		
		// 注册到CustomAnnotationPane的点击事件
		this.customAnnotationPane.addCountLabelClickListener(new CustomAnnotationPane.CountLabelClickListener() {
			@Override
			public void onCountLabelClicked(PathObject annotation) {
				// 直接显示或隐藏扩展面板
				if (annotation.equals(currentSelectedAnnotation)) {
					toggleExtendContainerVisibility();
				} else {
					// 如果是新对象，显示扩展面板并保存当前对象
					currentSelectedAnnotation = annotation;
					populateExtendContainerWithChildren(annotation);
					showExtendContainer(true);
				}
			}
		});
			
		// 监听ImageData变化，以便更新扩展面板
		qupath.imageDataProperty().addListener((v, o, n) -> {
			if (n != null) {
				// 添加层次结构监听器
				n.getHierarchy().addListener(hierarchyEvent -> {
					// 当层次结构变化时，如果扩展面板可见且当前有选中标注，刷新扩展面板
					if (extendContainer != null && extendContainer.isVisible() && currentSelectedAnnotation != null) {
						Platform.runLater(() -> {
							// 检查当前选中标注是否仍然存在
							if (n.getHierarchy() != null && PathObjectTools.hierarchyContainsObject(n.getHierarchy(), currentSelectedAnnotation)) {
								// 刷新扩展面板
								populateExtendContainerWithChildren(currentSelectedAnnotation);
							} else {
								// 如果标注已不存在，隐藏扩展面板
								showExtendContainer(false);
								currentSelectedAnnotation = null;
							}
						});
					}
				});
			}
		});
		
		// 初始添加层次结构监听器（如果已有图像）
		if (qupath.getImageData() != null) {
			qupath.getImageData().getHierarchy().addListener(hierarchyEvent -> {
				// 当层次结构变化时，如果扩展面板可见且当前有选中标注，刷新扩展面板
				if (extendContainer != null && extendContainer.isVisible() && currentSelectedAnnotation != null) {
					Platform.runLater(() -> {
						// 检查当前选中标注是否仍然存在
						if (qupath.getImageData() != null && 
							PathObjectTools.hierarchyContainsObject(qupath.getImageData().getHierarchy(), currentSelectedAnnotation)) {
							// 刷新扩展面板
							populateExtendContainerWithChildren(currentSelectedAnnotation);
						} else {
							// 如果标注已不存在，隐藏扩展面板
							showExtendContainer(false);
							currentSelectedAnnotation = null;
						}
					});
				}
			});
		}

		var workflowPane = analysisTabPane.getTabPane().getTabs().get(4).getContent();
		// var analysisPane = analysisTabPane.getTabPane().getTabs().get(2).getContent();
		// var classifyPane = analysisTabPane.getTabPane().getTabs().get(3).getContent();
		
		// Add all panes to container but make them invisible initially
		workContainer.getChildren().addAll(projectPane, imagePane, annotationPane, workflowPane/*,analysisPane, classifyPane*/);
		projectPane.setVisible(true);
		imagePane.setVisible(false);
		annotationPane.setVisible(false);
		workflowPane.setVisible(false);
		// analysisPane.setVisible(false);
		// classifyPane.setVisible(false);
		// Add navigation buttons
		var projectBtn = (ToggleButton)createNavButton("project", "项目");
		var imageBtn = (ToggleButton)createNavButton("image", "图像");
		var annotationBtn = (ToggleButton)createNavButton("annotation", "标注");
		var workflowBtn = (ToggleButton)createNavButton("workflow", "工作流");
		var analysisBtn = (ToggleButton)createNavButton("analysis", "分析");
		analysisBtn.setDisable(true);
		var classifyBtn = (ToggleButton)createNavButton("classify", "分类");
		classifyBtn.setDisable(true);

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
			// analysisPane.setVisible(false);
			// classifyPane.setVisible(false);
			// 隐藏扩展面板
			showExtendContainer(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});
		
		imageBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(1);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(true);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			// analysisPane.setVisible(false);
			// classifyPane.setVisible(false);
			// 隐藏扩展面板
			showExtendContainer(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});
		
		annotationBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(5);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(true);
			workflowPane.setVisible(false);
			// analysisPane.setVisible(false);
			// classifyPane.setVisible(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});
		
		workflowBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(4);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(true);
			// analysisPane.setVisible(false);
			// classifyPane.setVisible(false);
			// 隐藏扩展面板
			showExtendContainer(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});
		
		analysisBtn.setOnAction(e -> {
			analysisTabPane.getTabPane().getSelectionModel().select(2);
			// 处理分析按钮点击事件
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			// analysisPane.setVisible(true);
			// classifyPane.setVisible(false);
			// 隐藏扩展面板
			showExtendContainer(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});
		
		classifyBtn.setOnAction(e -> {
			// 处理分类按钮点击事件
			analysisTabPane.getTabPane().getSelectionModel().select(3);
			// 切换工作区显示
			projectPane.setVisible(false);
			imagePane.setVisible(false);
			annotationPane.setVisible(false);
			workflowPane.setVisible(false);
			// analysisPane.setVisible(false);
			// classifyPane.setVisible(true);
			// 隐藏扩展面板
			showExtendContainer(false);
			// 清除所有标签选中效果
			customAnnotationPane.clearAllCountLabelSelections();
		});

		for (var btn : Arrays.asList(projectBtn, imageBtn, annotationBtn, workflowBtn, analysisBtn, classifyBtn)) {
			navBar.getChildren().add(btn);
		}

		leftContainer.getChildren().addAll(navBar, workContainer, extendContainer);
		return leftContainer;
	}

	// 显示或隐藏扩展面板
	public void showExtendContainer(boolean show) {
		if (extendContainer != null) {
			extendContainer.setVisible(show);
			extendContainer.setManaged(show); // 同时设置managed属性，确保布局正确调整
		}
	}
	
	// 切换扩展面板的显示状态
	private void toggleExtendContainerVisibility() {
		if (extendContainer != null) {
			boolean currentVisible = extendContainer.isVisible();
			showExtendContainer(!currentVisible); // 使用showExtendContainer方法确保一致性
		}
	}
	
	// 用子对象的内容填充扩展面板
	private void populateExtendContainerWithChildren(PathObject annotation) {
		if (extendContainer == null || annotation == null)
			return;
		
		// 清空当前内容
		extendContainer.getChildren().clear();
		
		// 创建一个VBox来放置子对象信息
		VBox childrenBox = new VBox();
		
		// 添加标题
		HBox titleBox = new HBox();
		titleBox.getStyleClass().add("extend-container-title");
		// 获取标注名称作为标题
		String annotationName = annotation.getDisplayedName();
		if (annotationName == null || annotationName.isEmpty()) {
			annotationName = "标注子对象";
		}
		if (annotation.getPathClass() != null && annotationName.contains("(")) {
            annotationName = annotationName.substring(0, annotationName.lastIndexOf("(")).trim();
        }
		Label titleLabel = new Label(annotationName);
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

		titleBox.getChildren().addAll(titleLabel);
		childrenBox.getChildren().add(titleBox);
		
		// 创建滚动视图来包含子对象列表
		ScrollPane scrollPane = new ScrollPane();
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		
		// 创建子对象列表容器
		listContainer = new VBox();
		listContainer.setPadding(new Insets(0, 12, 0, 12));
		
		// 如果有子对象，添加到列表中
		if (annotation.nChildObjects() > 0) {
			// 遍历子对象并添加到VBox中
			// 对子对象进行排序，与PathObjectHierarchyView保持一致
			List<PathObject> children = new ArrayList<>(annotation.getChildObjects());
			
			// 按照显示名称排序
			children.sort((o1, o2) -> o1.getDisplayedName().compareTo(o2.getDisplayedName()));
			
			for (PathObject child : children) {
				HBox childItem = createChildItem(child);
				listContainer.getChildren().add(childItem);
			}
		} else {
			// 如果没有子对象，显示一个提示
			HBox noChildItem = new HBox();
			noChildItem.setAlignment(Pos.CENTER_LEFT);
			noChildItem.setPadding(new Insets(10, 15, 10, 15));
			
			Label noChildrenLabel = new Label("没有子对象");
			noChildrenLabel.setStyle("-fx-text-fill: #999;");
			
			noChildItem.getChildren().add(noChildrenLabel);
			listContainer.getChildren().add(noChildItem);
		}
		
		scrollPane.setContent(listContainer);
		childrenBox.getChildren().add(scrollPane);
		VBox.setVgrow(scrollPane, Priority.ALWAYS);
		
		// 将VBox添加到扩展容器中
		extendContainer.getChildren().add(childrenBox);
		
		// 确保与当前选中对象同步
		if (qupath.getImageData() != null) {
			Platform.runLater(() -> {
				PathObject selectedObject = qupath.getImageData().getHierarchy().getSelectionModel().getSelectedObject();
				updateChildSelectionState(selectedObject);
			});
		}
	}
	
	// 更新子对象的选中状态
	private void updateChildSelectionState(PathObject selectedObject) {
		if (listContainer == null || selectedObject == null)
			return;
			
		// 遍历所有子项，更新选中状态
		for (Node node : listContainer.getChildren()) {
			if (node instanceof HBox childItem) {
				// 清除所有项的选中状态
				childItem.setStyle("-fx-background-color: transparent;");
				
				// 从childItem中获取对应的PathObject（将其存储为用户数据）
				Object userData = childItem.getUserData();
				if (userData instanceof PathObject pathObject && pathObject.equals(selectedObject)) {
					// 如果是当前选中的对象，设置选中样式
					childItem.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06);");
				}
			}
		}
	}
	
	// 创建子对象列表项
	private HBox createChildItem(PathObject child) {
		HBox childItem = new HBox();
		childItem.setAlignment(Pos.CENTER_LEFT);
		childItem.getStyleClass().add("custom-annotation-item");
		
		// 存储PathObject引用
		childItem.setUserData(child);
		
		// 获取对象的类型和分类图标
		Node typeIcon = IconFactory.createPathObjectIcon(child, 16, 16);
		typeIcon.getStyleClass().add("custom-annotation-icon");
		
		// 创建子对象名称标签，包含分类信息（如果有）
		String displayName = child.getDisplayedName();
		if (displayName.contains("(")) {
            displayName = displayName.substring(0, displayName.lastIndexOf("(")).trim();
        }
		Label childNameLabel = new Label(QuPathTranslator.getTranslatedName(displayName));
		childNameLabel.getStyleClass().add("custom-annotation-name");
		
		childItem.getChildren().addAll(typeIcon, childNameLabel);
		
		// 设置选中状态的样式变化
		if (qupath.getImageData() != null && 
			qupath.getImageData().getHierarchy().getSelectionModel().isSelected(child)) {
			childItem.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06)");
			logger.info("创建时设置选中样式: " + child.getDisplayedName());
		}

		// 添加鼠标悬停效果
		childItem.setOnMouseEntered(e -> {
			if (!(qupath.getImageData() != null && 
				qupath.getImageData().getHierarchy().getSelectionModel().isSelected(child))) {
				childItem.setStyle(childItem.getStyle() + " -fx-background-color: rgba(22, 146, 255, 0.06);");
			}
		});
		
		childItem.setOnMouseExited(e -> {
			if (!(qupath.getImageData() != null && 
				qupath.getImageData().getHierarchy().getSelectionModel().isSelected(child))) {
				childItem.setStyle(childItem.getStyle().replace("-fx-background-color: rgba(22, 146, 255, 0.06);", ""));
			}
		});	
		
		// 添加点击事件
		childItem.setOnMouseClicked(e -> {
			// 选中该子对象
			if (qupath.getImageData() != null) {
				qupath.getImageData().getHierarchy().getSelectionModel().setSelectedObject(child);
				
				// 应用选中状态样式
				for (Node node : listContainer.getChildren()) {
					if (node instanceof HBox) {
						node.setStyle("-fx-background-color: transparent;");
					}
				}
				childItem.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06);");
				logger.info("点击设置选中样式: " + child.getDisplayedName());
				
				// 如果是双击，居中显示
				if (e.getClickCount() > 1 && child.hasROI()) {
					qupath.getViewer().centerROI(child.getROI());
				}
			}
		});
		
		return childItem;
	}

	// 设置选择变化监听器
	private void setupSelectionListener() {
		// 监听图像数据变化
		qupath.imageDataProperty().addListener((v, oldImageData, newImageData) -> {
			if (newImageData != null) {
				// 为新图像添加选择监听器
				newImageData.getHierarchy().getSelectionModel().addPathObjectSelectionListener(
					new qupath.lib.objects.hierarchy.events.PathObjectSelectionListener() {
						@Override
						public void selectedPathObjectChanged(PathObject pathObjectSelected, 
															 PathObject previousObject,
															 Collection<PathObject> allSelected) {
							// 如果扩展面板已显示，则更新子项选择状态
							if (extendContainer != null && extendContainer.isVisible() && 
								currentSelectedAnnotation != null && listContainer != null) {
								
								// 使用Platform.runLater确保在JavaFX线程中更新UI
								Platform.runLater(() -> {
									logger.info("选择变化: " + (pathObjectSelected == null ? "null" : pathObjectSelected.getDisplayedName()));
									
									// 遍历所有子项
									for (Node node : listContainer.getChildren()) {
										if (node instanceof HBox childItem) {
											// 清除所有项的选中样式
											childItem.setStyle("-fx-background-color: transparent;");
											
											// 获取关联的PathObject
											Object userData = childItem.getUserData();
											if (userData instanceof PathObject pathObject) {
												// 比较ID更可靠
												boolean isSelected = pathObjectSelected != null && 
													pathObject.getID().equals(pathObjectSelected.getID());
												
												if (isSelected) {
													// 应用选中样式
													childItem.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06);");
													logger.info("找到并更新选中项: " + pathObject.getDisplayedName());
												}
											}
										}
									}
								});
							}
						}
					}
				);
			}
		});
		
		// 初始为当前图像添加监听器
		if (qupath.getImageData() != null) {
			qupath.getImageData().getHierarchy().getSelectionModel().addPathObjectSelectionListener(
				(pathObjectSelected, previousObject, allSelected) -> {
					// 如果扩展面板已显示，则更新子项选择状态
					if (extendContainer != null && extendContainer.isVisible() && 
						currentSelectedAnnotation != null && listContainer != null) {
						
						// 使用Platform.runLater确保在JavaFX线程中更新UI
						Platform.runLater(() -> {
							logger.info("选择变化: " + (pathObjectSelected == null ? "null" : pathObjectSelected.getDisplayedName()));
							
							// 遍历所有子项
							for (Node node : listContainer.getChildren()) {
								if (node instanceof HBox childItem) {
									// 清除所有项的选中样式
									childItem.setStyle("-fx-background-color: transparent;");
									
									// 获取关联的PathObject
									Object userData = childItem.getUserData();
									if (userData instanceof PathObject pathObject) {
										// 比较ID更可靠
										boolean isSelected = pathObjectSelected != null && 
											pathObject.getID().equals(pathObjectSelected.getID());
										
										if (isSelected) {
											// 应用选中样式
											childItem.setStyle("-fx-background-color: rgba(22, 146, 255, 0.06);");
											logger.info("找到并更新选中项: " + pathObject.getDisplayedName());
										}
									}
								}
							}
						});
					}
				}
			);
		}
	}

	private String convertImageToTempFile(ImageServer<BufferedImage> server) {
		try {
			// 创建临时文件
			Path tempFile = Files.createTempFile("qupath_image_", ".jpg");
			
			// 获取图片的缩略图（降低分辨率以提高性能）
			double downsample = Math.max(server.getWidth(), server.getHeight()) / 1024.0;
			RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight());
			BufferedImage img = server.readRegion(request);
			
			// 保存为jpg格式
			ImageIO.write(img, "jpg", tempFile.toFile());
			
			return tempFile.toString();
		} catch (Exception e) {
			logger.error("转换图片失败", e);
			return null;
		}
	}

	private void deleteTempFile(String filePath) {
		if (filePath != null) {
			try {
				Files.deleteIfExists(Path.of(filePath));
			} catch (Exception e) {
				logger.error("删除临时文件失败: " + filePath, e);
			}
		}
	}
}