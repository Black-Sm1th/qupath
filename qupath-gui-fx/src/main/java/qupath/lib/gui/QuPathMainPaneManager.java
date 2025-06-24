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
import javafx.embed.swing.SwingFXUtils;
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
import qupath.lib.gui.panes.AnalysisToolsPane;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import qupath.lib.gui.panes.ClassifyPane;
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
	private BorderPane aiContainer;
	private AnalysisToolsPane analysisToolsPane;
	private VBox aiContent;
	private ClassifyPane classifyToolsPane;
	// 存储右下角容器和概览图状态
	private HBox rightBottomContainer;
	private boolean wasOverviewVisible;
	// 添加自定义标注面板
	private CustomAnnotationPane customAnnotationPane;
	
	private StackPane extendContainer;
	// 记录当前选中的标注对象
	private PathObject currentSelectedAnnotation;
	
	// 用于列表项的滚动容器
	private VBox listContainer;
	private HBox analysisContainer;
	
	// 加载动画相关属性和方法
	private Timeline loadingAnimation;
	private HBox loadingContainer;
	private Text loadingText;
	private int dotCount = 1;
	
	// 添加ScrollPane引用
	private ScrollPane aiScrollPane;
	
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

		aiContainer = new BorderPane();
		aiContainer.setPickOnBounds(false);
		aiContainer.setPadding(new Insets(88, 16, 16, 0));
		aiContainer.setRight(createAIContainer());
		pane.getChildren().add(aiContainer);
		aiContainer.setVisible(false);

		// 添加选择监听器 
		setupSelectionListener();
	}

	private BorderPane createAIContainer() {
		var aiPane = new BorderPane();
		aiPane.getStyleClass().add("ai-container");

		// 创建AI顶部栏
		HBox aiTopBar = new HBox();
		aiTopBar.getStyleClass().add("ai-top-bar");
		Pane magicIcon = new Pane();
		magicIcon.getStyleClass().add("magic-icon");
		Label aiLabel = new Label("AI助手");
		aiLabel.getStyleClass().add("ai-label");
		Region aiRegion = new Region();
		HBox.setHgrow(aiRegion, Priority.ALWAYS);
		Button closeBtn = new Button();
		closeBtn.getStyleClass().add("close-btn");
		// 添加关闭按钮点击事件
		closeBtn.setOnAction(event -> {
			// 隐藏AI容器
			aiContainer.setVisible(false);
			
			// 如果原先概览图是显示的，恢复显示
			if (wasOverviewVisible) {
				qupath.getViewerActions().SHOW_OVERVIEW.setSelected(true);
			}
			
			// 显示右下角容器
			if (rightBottomContainer != null) {
				rightBottomContainer.setVisible(true);
			}
		});
		aiTopBar.getChildren().addAll(magicIcon, aiLabel, aiRegion, closeBtn);
		aiPane.setTop(aiTopBar);

		// 创建AI内容容器
		aiContent = new VBox();
		aiContent.setPadding(new Insets(0, 20, 0, 20));
		ScrollPane aiScrollPane = new ScrollPane(aiContent);
		aiScrollPane.setFitToWidth(true);
		aiScrollPane.setFitToHeight(true);
		aiScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		aiScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		
		// 保存ScrollPane的引用，以便稍后滚动
		this.aiScrollPane = aiScrollPane;
		
		aiPane.setCenter(aiScrollPane);


		var inputPane = new HBox();
		inputPane.setPadding(new Insets(4,4,4,4));
		HBox.setHgrow(inputPane, Priority.ALWAYS);
		// 创建AI底部栏
		var inputContainer = new HBox();
		inputContainer.getStyleClass().add("toolbar-input-container");
		HBox.setHgrow(inputContainer, Priority.ALWAYS);
		inputPane.getChildren().add(inputContainer);
		// Create input field
		var input = new TextField();
		input.getStyleClass().add("toolbar-input");
		input.setPromptText("请输入您的问题");
		// Create left icon
		Button leftIcon = new Button();
		leftIcon.getStyleClass().add("toolbar-input-icon");
		
		// 设置初始tooltip
		Tooltip defaultTooltip = new Tooltip("点击启动对当前图片提问");
		Tooltip selectedTooltip = new Tooltip("已启用对当前图片问答，点击禁用");
		leftIcon.setTooltip(defaultTooltip);
		
		// 添加点击事件，使其可以被选中
		BooleanProperty selected = new SimpleBooleanProperty(false);
		leftIcon.setOnAction(e -> {
			boolean isSelected = !selected.get();
			selected.set(isSelected);
			if (isSelected) {
				leftIcon.getStyleClass().add("selected");
				leftIcon.setTooltip(selectedTooltip);
			} else {
				leftIcon.getStyleClass().remove("selected");
				leftIcon.setTooltip(defaultTooltip);
			}
		});
		
		// Create right icon
		var sendBtn = createNormalButton("send", "发送");
		sendBtn.getStyleClass().add("toolbar-message-button");
		sendBtn.setOnMouseClicked(value -> {
			String userMessage = input.getText().trim();
			if (userMessage.isEmpty()) return;
			
			// 发送用户消息
			BufferedImage image = null;
			if (qupath.getImageData() != null && qupath.getImageData().getServer() != null && selected.get()) {
				try {
					ImageServer<BufferedImage> server = (ImageServer<BufferedImage>)qupath.getImageData().getServer();
					// 获取图片缩略图用于显示
					double downsample = Math.max(server.getWidth(), server.getHeight()) / 240.0;
					RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight());
					image = server.readRegion(request);
					
					// 添加用户消息到聊天界面
					addUserMessage(userMessage, image);
					
					// 显示加载状态
					showLoadingMessage();
					
					// 为AI调用创建临时文件
					final String tempImagePath = convertImageToTempFile(server);
					client.getCompletionAsync(userMessage, tempImagePath,
						response -> {
							// 添加AI响应到聊天界面
							addAIResponse(response);
							// 删除临时文件
							deleteTempFile(tempImagePath);
						},
						error -> {
							// 显示错误消息
							Platform.runLater(() -> {
								addAIResponse("抱歉，处理您的请求时出现错误: " + error.getMessage());
								logger.error(error.getMessage());
							});
							// 发生错误时也要删除临时文件
							deleteTempFile(tempImagePath);
						}
					);
				} catch (Exception e) {
					logger.error("获取图片路径失败", e);
					addUserMessage(userMessage, null);
					addAIResponse("抱歉，处理图片时出现错误，无法完成您的请求。");
				}
			} else {
				// 如果没有选择图片或没有图片，只处理文本
				addUserMessage(userMessage, null);
				
				// 显示加载状态
				showLoadingMessage();
				
				client.getCompletionAsync(userMessage, "",
					response -> addAIResponse(response),
					error -> Platform.runLater(() -> {
						addAIResponse("抱歉，处理您的请求时出现错误: " + error.getMessage());
						logger.error(error.getMessage());
					})
				);
			}
			
			input.setText("");
		});
		
		// 添加回车键事件监听
		input.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER && !input.getText().trim().isEmpty()) {
				// 触发发送按钮的点击事件
				sendBtn.fireEvent(
					new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 
							0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1, 
							false, false, false, false, true, false, false, true, true, true, null)
				);
				e.consume();
			}
		});
		HBox.setHgrow(input, Priority.ALWAYS);
		inputContainer.getChildren().addAll(leftIcon, input, sendBtn);
		aiPane.setBottom(inputPane);
		return aiPane;
	}

	private Node createNormalButton(String icon, String tooltip) {
		var button = new Button();
		// Set icon based on type
		Node iconNode = switch(icon) {
			case "menu" -> IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.MEASURE);
			case "eye" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.EYE_BTN);
			case "gps" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.GPS_BTN);
			case "send" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.SEND_BTN);
			case "ai" -> IconFactory.createNode(QuPathGUI.NAVBAR_ICON_SIZE, QuPathGUI.NAVBAR_ICON_SIZE, PathIcons.AI_BTN);
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
		bottomBarContainer.setPrefHeight(56);
		bottomBarContainer.setMinHeight(56);
		bottomBarContainer.setMaxHeight(56);
		BorderPane.setMargin(bottomBarContainer,new Insets(0,16,16,16));
		
		HBox leftBottomContainer = new HBox();
		leftBottomContainer.getStyleClass().add("toolbar-main-container");
		leftBottomContainer.setSpacing(4);
		bottomBarContainer.setLeft(leftBottomContainer);
		// Create button	
		var eyeBtn = createNormalButton("eye", "显示/隐藏");
		var gpsBtn = createNormalButton("gps", "适应窗口");
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
		// 将所有选项添加到菜单中
		displayMenu.getItems().addAll(
			navBarItem,
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
		// 为gps按钮添加点击事件，实现ZOOM_TO_FIT功能
		gpsBtn.setOnMouseClicked(e -> {
			var viewer = qupath.getViewerManager().getActiveViewer();
			if (viewer != null) {
				viewer.zoomToFit();
			}
		});
		// Create left&right button container
		var eyeButtonContainer = new VBox();
		eyeButtonContainer.getStyleClass().add("toolbar-left-container");
		eyeButtonContainer.getChildren().add(eyeBtn);
		var gpsButtonContainer = new VBox();
		gpsButtonContainer.getStyleClass().add("toolbar-left-container");
		gpsButtonContainer.getChildren().add(gpsBtn);
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
		var scaleContainer = new BorderPane();
		scaleContainer.getStyleClass().add("toolbar-scale-container");
		scaleContainer.setCenter(magContainer);
		leftBottomContainer.getChildren().addAll(eyeButtonContainer, scaleContainer, gpsButtonContainer);
		
		HBox rightBottomContainer = new HBox();
		rightBottomContainer.getStyleClass().add("toolbar-rightBottom-container");
		rightBottomContainer.setAlignment(Pos.CENTER_LEFT);
		rightBottomContainer.setPadding(new Insets(4,8,4,8));
		var leftContainer = new HBox();
		leftContainer.setPadding(new Insets(0,12,0,12));
		leftContainer.setSpacing(20);
		// 获取viewer中的panelLocation和scalebarNode
		var viewer = qupath.getViewerManager().getActiveViewer();
		if (viewer instanceof QuPathViewerPlus) {
			var viewerPlus = (QuPathViewerPlus)viewer;
			var scalebarNode = viewerPlus.getScalebarNode();
			scalebarNode.getStyleClass().add("scale-container");
			var panelLocation = viewerPlus.getPanelLocation();
			leftContainer.getChildren().addAll(scalebarNode,panelLocation);
			// 添加对坐标和比例尺显示状态的监听器
			qupath.getViewerActions().SHOW_LOCATION.selectedProperty().addListener((obs, oldVal, newVal) -> {
				// 当坐标显示状态改变时调整布局
				Platform.runLater(() -> adjustLeftContainerSize(leftContainer, rightBottomContainer));
			});
			
			qupath.getViewerActions().SHOW_SCALEBAR.selectedProperty().addListener((obs, oldVal, newVal) -> {
				// 当比例尺显示状态改变时调整布局
				Platform.runLater(() -> adjustLeftContainerSize(leftContainer, rightBottomContainer));
			});
			// 初始调整一次布局
			adjustLeftContainerSize(leftContainer, rightBottomContainer);
		}
		Button aiBtn = new Button();
		aiBtn.getStyleClass().add("ai-button");
		aiBtn.setTooltip(new Tooltip("打开AI助手"));
		
		// 添加AI按钮点击事件
		aiBtn.setOnAction(event -> {
			// 显示AI容器
			aiContainer.setVisible(true);
			
			// 记录概览图当前状态并隐藏
			wasOverviewVisible = qupath.getViewerActions().SHOW_OVERVIEW.isSelected();
			if (wasOverviewVisible) {
				qupath.getViewerActions().SHOW_OVERVIEW.setSelected(false);
			}
			
			// 隐藏右下角容器
			rightBottomContainer.setVisible(false);
		});
		
		rightBottomContainer.getChildren().addAll(leftContainer,aiBtn);
		this.rightBottomContainer = rightBottomContainer;  // 保存引用
		bottomBarContainer.setRight(rightBottomContainer);
		return bottomBarContainer;
	}
	
	/**
	 * 根据坐标和比例尺的显示状态调整leftContainer的大小
	 * @param leftContainer 包含坐标和比例尺的容器
	 */
	private void adjustLeftContainerSize(HBox leftContainer, HBox rightBottomContainer) {
		if (leftContainer == null || leftContainer.getChildren().size() < 2)
			return;
			
		Node scalebarNode = leftContainer.getChildren().get(0);
		Node panelLocation = leftContainer.getChildren().get(1);
		
		// 根据显示状态设置visibility和managed
		boolean showLocation = qupath.getViewerActions().SHOW_LOCATION.isSelected();
		boolean showScalebar = qupath.getViewerActions().SHOW_SCALEBAR.isSelected();
		
		panelLocation.setVisible(showLocation);
		panelLocation.setManaged(showLocation);
		scalebarNode.setVisible(showScalebar);
		scalebarNode.setManaged(showScalebar);
		
		// 设置leftContainer的首选宽度
		// 如果两个都不显示，设置最小宽度
		if (!showLocation && !showScalebar) {
			leftContainer.setPrefWidth(0);
		} else {
			// 否则让布局自动计算宽度
			leftContainer.setPrefWidth(Region.USE_COMPUTED_SIZE);
		}
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
		
		this.analysisToolsPane = new AnalysisToolsPane(qupath);
		var analysisPane = analysisToolsPane.getPane();

		this.classifyToolsPane = new ClassifyPane(qupath);
		var classifyPane = classifyToolsPane.getPane();

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
		
		// Add all panes to container but make them invisible initially
		workContainer.getChildren().addAll(projectPane, imagePane, annotationPane, workflowPane, analysisPane, classifyPane);
		projectPane.setVisible(true);
		imagePane.setVisible(false);
		annotationPane.setVisible(false);
		workflowPane.setVisible(false);
		analysisPane.setVisible(false);
		classifyPane.setVisible(false);
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
			classifyPane.setVisible(false);
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
			analysisPane.setVisible(false);
			classifyPane.setVisible(false);
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
			analysisPane.setVisible(false);
			classifyPane.setVisible(false);
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
			analysisPane.setVisible(false);
			classifyPane.setVisible(false);
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
			analysisPane.setVisible(true);
			classifyPane.setVisible(false);
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
			analysisPane.setVisible(false);
			classifyPane.setVisible(true);
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

	// 滚动到底部的方法
	private void scrollToBottom() {
		if (aiScrollPane != null) {
			Platform.runLater(() -> {
				aiScrollPane.setVvalue(1.0);
			});
		}
	}
	
	// 添加处理用户输入和AI响应的方法
	private void addUserMessage(String message, BufferedImage image) {
		Platform.runLater(() -> {
			// 添加日期
			HBox dateBox = new HBox();
			dateBox.setAlignment(Pos.CENTER);
			dateBox.setPrefWidth(Double.MAX_VALUE);
			
			Label dateLabel = new Label(getCurrentDateTime());
			dateLabel.getStyleClass().add("chat-date");
			dateBox.getChildren().add(dateLabel);
			
			// 添加用户消息容器
			HBox userContainer = new HBox();
			userContainer.getStyleClass().add("chat-user-container");
			VBox messageColumn = new VBox();
			messageColumn.setAlignment(Pos.CENTER_RIGHT);
			
			// 如果有图片并且选中了图片按钮，添加图片
			if (image != null) {
				ImageView imageView = createImageView(image);
				HBox imageContainer = new HBox();
				imageContainer.getStyleClass().add("chat-image-container");
				imageContainer.setAlignment(Pos.CENTER_RIGHT);
				imageContainer.getChildren().add(imageView);
				messageColumn.getChildren().add(imageContainer);
			}
			
			// 添加文本气泡
			HBox bubbleBox = new HBox();
			bubbleBox.setAlignment(Pos.CENTER_RIGHT);
			VBox bubble = new VBox();
			bubble.getStyleClass().add("chat-bubble-user");
			
			// 使用TextFlow实现文本自适应
			TextFlow textFlow = new TextFlow();
			textFlow.setMaxWidth(320);
			
			Text text = new Text(message);
			text.getStyleClass().add("chat-bubble-user-text");
			
			textFlow.getChildren().add(text);
			bubble.getChildren().add(textFlow);
			bubbleBox.getChildren().add(bubble);
			
			messageColumn.getChildren().add(bubbleBox);
			userContainer.getChildren().add(messageColumn);
			
			// 添加到聊天内容
			aiContent.getChildren().addAll(dateBox, userContainer);
			
			// 滚动到底部
			scrollToBottom();
		});
	}
	
	private void addAIResponse(String response) {
		Platform.runLater(() -> {
			// 从聊天内容移除加载提示（如果有）
			removeLoadingMessage();
			
			// 尝试解析JSON响应
			String responseText = response;
			if (response.startsWith("{") && response.endsWith("}")) {
				try {
					// 简单的JSON解析，获取response字段
					int startIndex = response.indexOf("\"response\":\"");
					if (startIndex > 0) {
						startIndex += "\"response\":\"".length();
						int endIndex = response.indexOf("\"}", startIndex);
						if (endIndex > startIndex) {
							responseText = response.substring(startIndex, endIndex)
									.replace("\\\"", "\"")
									.replace("\\n", "\n")
									.replace("\\r", "\r")
									.replace("\\t", "\t")
									.replace("\\\\", "\\");
						}
					}
				} catch (Exception e) {
					logger.error("解析响应时出错", e);
					// 如果解析失败，使用原始响应
				}
			}
			
			// 移除结尾的</s>标记
			if (responseText.endsWith("</s>")) {
				responseText = responseText.substring(0, responseText.length() - 4);
			}
			
			// 添加AI响应容器
			HBox aiResponseContainer = new HBox();
			aiResponseContainer.getStyleClass().add("chat-ai-container");
			
			// 添加AI文本气泡
			VBox bubble = new VBox();
			bubble.getStyleClass().add("chat-bubble-ai");
			
			// 使用TextFlow实现文本自适应
			TextFlow textFlow = new TextFlow();
			textFlow.setMaxWidth(320);
			
			Text text = new Text(responseText);
			text.getStyleClass().add("chat-bubble-ai-text");
			
			textFlow.getChildren().add(text);
			bubble.getChildren().add(textFlow);
			aiResponseContainer.getChildren().add(bubble);
			
			// 添加到聊天内容
			aiContent.getChildren().add(aiResponseContainer);
			
			// 添加免责声明
			HBox disclaimerBox = new HBox();
			disclaimerBox.setAlignment(Pos.CENTER);
			disclaimerBox.setPrefWidth(Double.MAX_VALUE);
			
			Label disclaimerLabel = new Label("内容由AI生成，仅供参考");
			disclaimerLabel.getStyleClass().add("chat-disclaimer");
			disclaimerBox.getChildren().add(disclaimerLabel);
			
			aiContent.getChildren().add(disclaimerBox);
			
			// 滚动到底部
			scrollToBottom();
		});
	}
	
	// 加载动画相关属性和方法
	private void showLoadingMessage() {
		Platform.runLater(() -> {
			// 创建加载提示容器
			loadingContainer = new HBox();
			loadingContainer.getStyleClass().add("chat-ai-container");
			
			// 创建加载提示气泡
			VBox bubble = new VBox();
			bubble.getStyleClass().add("chat-bubble-ai");
			
			// 使用TextFlow实现文本自适应
			TextFlow textFlow = new TextFlow();
			textFlow.setMaxWidth(320);
			
			loadingText = new Text("生成中.");
			loadingText.getStyleClass().add("chat-bubble-ai-text");
			loadingText.getStyleClass().add("loading-text");
			
			textFlow.getChildren().add(loadingText);
			bubble.getChildren().add(textFlow);
			loadingContainer.getChildren().add(bubble);
			
			// 添加到聊天内容
			aiContent.getChildren().add(loadingContainer);
			
			// 滚动到底部
			scrollToBottom();
			
			// 创建动画效果
			if (loadingAnimation != null) {
				loadingAnimation.stop();
			}
			
			loadingAnimation = new Timeline(new KeyFrame(Duration.millis(500), event -> {
				dotCount = (dotCount % 3) + 1; // 在1、2、3之间循环
				String dots = ".".repeat(dotCount);
				loadingText.setText("生成中" + dots);
			}));
			loadingAnimation.setCycleCount(Timeline.INDEFINITE);
			loadingAnimation.play();
		});
	}
	
	private void removeLoadingMessage() {
		// 停止加载动画
		if (loadingAnimation != null) {
			loadingAnimation.stop();
			loadingAnimation = null;
		}
		
		// 从聊天内容中移除加载提示
		if (loadingContainer != null && aiContent.getChildren().contains(loadingContainer)) {
			aiContent.getChildren().remove(loadingContainer);
			loadingContainer = null;
		}
	}
	
	private ImageView createImageView(BufferedImage image) {
		// 创建ImageView并设置样式
		ImageView imageView = new ImageView(SwingFXUtils.toFXImage(image, null));
		imageView.setPreserveRatio(true);
		imageView.setFitWidth(120);
		imageView.setFitHeight(120);
		imageView.getStyleClass().add("chat-image");
		return imageView;
	}
	
	private String getCurrentDateTime() {
		return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}
}