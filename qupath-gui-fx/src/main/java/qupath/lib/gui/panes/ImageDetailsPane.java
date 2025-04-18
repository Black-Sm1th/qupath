/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.scene.control.TitledPane;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.Cursor;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import qupath.fx.utils.FXUtils;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.css.PseudoClass;
import javafx.scene.image.ImageView;

/**
 * A panel used for displaying basic info about an image, e.g. its path, width, height, pixel size etc.
 * <p>
 * It also includes displaying color deconvolution vectors for RGB brightfield images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageDetailsPane implements ChangeListener<ImageData<BufferedImage>>, PropertyChangeListener {

	private static final Logger logger = LoggerFactory.getLogger(ImageDetailsPane.class);

	private ImageData<BufferedImage> imageData;

	private VBox pane = new VBox();
	private VBox detailsContainer = new VBox();
	private ListView<String> listAssociatedImages = new ListView<>();
	private BorderPane mdPane = new BorderPane();
	private TitledPane titlePaneAssociated;

	private Map<String, SimpleImageViewer> associatedImageViewers = new HashMap<>();

	private enum ImageDetailRow {
		NAME, URI, PIXEL_TYPE, MAGNIFICATION, WIDTH, HEIGHT, DIMENSIONS,
		PIXEL_WIDTH, PIXEL_HEIGHT, Z_SPACING, UNCOMPRESSED_SIZE, SERVER_TYPE, PYRAMID,
		METADATA_CHANGED, IMAGE_TYPE,
		STAIN_1, STAIN_2, STAIN_3, BACKGROUND;
	};

	private static List<ImageDetailRow> brightfieldRows;
	private static List<ImageDetailRow> otherRows;
	
	static {
		brightfieldRows = Arrays.asList(ImageDetailRow.values());
		otherRows = new ArrayList<>(brightfieldRows);
		otherRows.remove(ImageDetailRow.STAIN_1);
		otherRows.remove(ImageDetailRow.STAIN_2);
		otherRows.remove(ImageDetailRow.STAIN_3);
		otherRows.remove(ImageDetailRow.BACKGROUND);
	}

	private static ImageDetailsPane instance;

	/**
	 * Constructor.
	 * @param imageDataProperty 
	 */
	public ImageDetailsPane(final ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		instance = this;
		imageDataProperty.addListener(this);
		pane.getStyleClass().add("image-details-pane");
		
		// Create details container with scroll pane
		detailsContainer.getStyleClass().add("image-details-container");
		
		ScrollPane scrollPane = new ScrollPane(detailsContainer);
		scrollPane.setFitToWidth(true);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setFitToHeight(true);
		
		// Create placeholder for no image
		Label placeholder = new Label("未选择图像");
		placeholder.setWrapText(true);
		placeholder.setTextAlignment(TextAlignment.CENTER);
		detailsContainer.getChildren().add(placeholder);
		
		// 设置VBox的填充属性
		VBox.setVgrow(mdPane, Priority.ALWAYS);
		pane.setFillWidth(true);
		detailsContainer.setFillWidth(true);

		setImageData(imageDataProperty.getValue());
		
		// Create top tab bar
		HBox topTabBar = new HBox();
		topTabBar.getStyleClass().add("topbar-tab-container");
		VBox.setMargin(topTabBar, new Insets(0, 12, 0, 12));
		topTabBar.setAlignment(Pos.CENTER_LEFT);
		ToggleGroup group = new ToggleGroup();
		
		// Create toggle buttons
        ToggleButton button1 = new ToggleButton("图像");
        ToggleButton button2 = new ToggleButton("相关图像");
		button1.getStyleClass().add("tab-button");
		button2.getStyleClass().add("tab-button");
		button1.setToggleGroup(group);
        button2.setToggleGroup(group);
		button1.setSelected(true); 
		
		button1.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
			if (button1.isSelected()) {
				event.consume();
			}
		});
		button2.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
			if (button2.isSelected()) {
				event.consume();
			}
		});
		
		// Create master detail pane and titled pane
		titlePaneAssociated = new TitledPane("相关图像", listAssociatedImages);
		titlePaneAssociated.setCollapsible(false);
		listAssociatedImages.setTooltip(new Tooltip("当前图像相关的额外图像，例如标签或缩略图"));
		
		mdPane.setCenter(scrollPane);
		
		// Handle tab changes
		button1.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				mdPane.setCenter(scrollPane);
			}
		});
		button2.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				mdPane.setCenter(titlePaneAssociated);
			}
		});
		
		topTabBar.getChildren().addAll(button1, button2);
		pane.getChildren().add(topTabBar);
		
		listAssociatedImages.setOnMouseClicked(this::handleAssociatedImagesMouseClick);
		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> updateDetailsView());
		
		pane.getChildren().add(mdPane);
	}
	
	private void updateDetailsView() {
		if (imageData == null || imageData.getServer() == null) {
			detailsContainer.getChildren().clear();
			Label placeholder = new Label("未选择图像");
			placeholder.setWrapText(true);
			placeholder.setTextAlignment(TextAlignment.CENTER);
			detailsContainer.getChildren().add(placeholder);
			return;
		}

		detailsContainer.getChildren().clear();
		List<ImageDetailRow> rows = getRows();
		
		// 创建分组
		Map<String, List<ImageDetailRow>> groups = new LinkedHashMap<>();
		groups.put("属性", Arrays.asList(ImageDetailRow.URI, ImageDetailRow.UNCOMPRESSED_SIZE, ImageDetailRow.METADATA_CHANGED, ImageDetailRow.SERVER_TYPE, ImageDetailRow.PYRAMID));
		groups.put("尺寸", Arrays.asList(ImageDetailRow.MAGNIFICATION, ImageDetailRow.DIMENSIONS, ImageDetailRow.WIDTH, ImageDetailRow.HEIGHT));
		groups.put("像素", Arrays.asList(ImageDetailRow.PIXEL_TYPE, ImageDetailRow.PIXEL_WIDTH, ImageDetailRow.PIXEL_HEIGHT));
		groups.put("染色", Arrays.asList(ImageDetailRow.IMAGE_TYPE, ImageDetailRow.STAIN_1, ImageDetailRow.STAIN_2, ImageDetailRow.STAIN_3, ImageDetailRow.BACKGROUND));
		
		for (Map.Entry<String, List<ImageDetailRow>> group : groups.entrySet()) {
			String groupName = group.getKey();
			List<ImageDetailRow> groupRows = group.getValue();
			
			// 过滤掉不在当前行列表中的行
			groupRows = groupRows.stream()
				.filter(rows::contains)
				.collect(Collectors.toList());
				
			if (groupRows.isEmpty())
				continue;
				
			// 创建分组容器
			VBox groupContainer = new VBox();
			groupContainer.setSpacing(8);
			groupContainer.getStyleClass().add("detail-group");
			
			// 创建分组标题栏
			HBox titleBar = new HBox();
			titleBar.setAlignment(Pos.CENTER_LEFT);
			titleBar.getStyleClass().add("detail-group-header");
			if(groupName.equals("属性")){
				titleBar.setCursor(Cursor.HAND);
			}
			
			Label titleLabel = new Label(groupName);
			titleLabel.getStyleClass().add("detail-group-title");
			
			// 创建箭头指示器
			Region arrow = new Region();
			arrow.getStyleClass().addAll("arrow");
			arrow.setMinWidth(14);
			arrow.setMinHeight(8);
			arrow.setMaxWidth(14);
			arrow.setMaxHeight(8);
			arrow.setVisible(groupName.equals("属性"));
			arrow.setManaged(groupName.equals("属性"));
			
			// 创建一个占位区域来推动箭头到右边
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			
			titleBar.setAlignment(Pos.CENTER_LEFT);
			if (groupName.equals("属性")) {
				titleBar.getChildren().addAll(titleLabel, spacer, arrow);
				// 设置箭头的初始状态为向下
				arrow.getStyleClass().add("arrow-down");
				// 设置箭头的右边距
				HBox.setMargin(arrow, new Insets(0, 8, 0, 0));
			} else {
				titleBar.getChildren().add(titleLabel);
			}
			
			// 创建内容容器
			VBox contentContainer = new VBox();
			contentContainer.setSpacing(8);
			contentContainer.getStyleClass().add("detail-group-content");
			
			// 为属性组添加折叠功能
			if (groupName.equals("属性")) {
				// 默认只显示图像名
				String imageName = getName(ImageDetailRow.NAME);
				Object imageNameValue = getValue(ImageDetailRow.NAME);
				if (imageName != null && imageNameValue != null) {
					HBox nameContainer = createDetailItem(imageName, imageNameValue, ImageDetailRow.NAME);
					contentContainer.getChildren().add(nameContainer);
				}
				
				// 创建其他属性的容器
				VBox otherProperties = new VBox();
				otherProperties.setSpacing(8);
				otherProperties.setVisible(false);
				otherProperties.setManaged(false);
				
				// 添加其他属性
				int i = 0;
				while (i < groupRows.size()) {
					if (i + 1 < groupRows.size()) {
						ImageDetailRow row1 = groupRows.get(i);
						ImageDetailRow row2 = groupRows.get(i + 1);
						
						// 检查是否应该并排显示
						if ((row1 == ImageDetailRow.UNCOMPRESSED_SIZE && row2 == ImageDetailRow.METADATA_CHANGED)) {
							otherProperties.getChildren().add(createPairedDetailItems(row1, row2));
							i += 2;
							continue;
						}
					}
					
					// 单独显示
					String name = getName(groupRows.get(i));
					Object value = getValue(groupRows.get(i));
					if (name != null && value != null) {
						HBox itemContainer = createDetailItem(name, value, groupRows.get(i));
						otherProperties.getChildren().add(itemContainer);
					}
					i++;
				}
				
				contentContainer.getChildren().add(otherProperties);
				
				// 添加点击事件处理
				titleBar.setOnMouseClicked(e -> {
					boolean isExpanded = otherProperties.isVisible();
					otherProperties.setVisible(!isExpanded);
					otherProperties.setManaged(!isExpanded);
					arrow.getStyleClass().removeAll("arrow-up", "arrow-down");
					arrow.getStyleClass().add(isExpanded ? "arrow-down" : "arrow-up");
					titleBar.pseudoClassStateChanged(PseudoClass.getPseudoClass("expanded"), !isExpanded);
				});
				
				// 设置初始状态
				titleBar.pseudoClassStateChanged(PseudoClass.getPseudoClass("expanded"), true);
				otherProperties.setVisible(true);
				otherProperties.setManaged(true);
				arrow.getStyleClass().removeAll("arrow-down");
				arrow.getStyleClass().add("arrow-up");
			} else {
				// 其他分组正常显示所有内容
				int i = 0;
				while (i < groupRows.size()) {
					if (i + 1 < groupRows.size()) {
						ImageDetailRow row1 = groupRows.get(i);
						ImageDetailRow row2 = groupRows.get(i + 1);
						
						// 检查是否应该并排显示
						if ((row1 == ImageDetailRow.MAGNIFICATION && row2 == ImageDetailRow.DIMENSIONS) ||
							(row1 == ImageDetailRow.WIDTH && row2 == ImageDetailRow.HEIGHT) ||
							(row1 == ImageDetailRow.PIXEL_WIDTH && row2 == ImageDetailRow.PIXEL_HEIGHT)) {
							contentContainer.getChildren().add(createPairedDetailItems(row1, row2));
							i += 2;
							continue;
						}
					}
					
					// 单独显示
					String name = getName(groupRows.get(i));
					Object value = getValue(groupRows.get(i));
					if (name != null && value != null) {
						HBox itemContainer = createDetailItem(name, value, groupRows.get(i));
						contentContainer.getChildren().add(itemContainer);
					}
					i++;
				}
			}
			
			groupContainer.getChildren().addAll(titleBar, contentContainer);
			detailsContainer.getChildren().add(groupContainer);
		}
	}
	
	private void handleAssociatedImagesMouseClick(MouseEvent event) {
		if (event.getClickCount() < 2 || listAssociatedImages.getSelectionModel().getSelectedItem() == null)
			return;
		String name = listAssociatedImages.getSelectionModel().getSelectedItem();
		var simpleViewer = associatedImageViewers.get(name);
		if (simpleViewer == null) {
			simpleViewer = new SimpleImageViewer();
			var img = imageData.getServer().getAssociatedImage(name);
			simpleViewer.updateImage(name, img);
			var stage = simpleViewer.getStage();
			var owner = FXUtils.getWindow(getPane());
			stage.initOwner(owner);
			stage.setOnCloseRequest(e -> {
				associatedImageViewers.remove(name);
				stage.close();
				e.consume();
			});
			// Show with constrained size (in case we have a large image)
			GuiTools.showWithScreenSizeConstraints(stage, 0.8);
			associatedImageViewers.put(name, simpleViewer);
		} else {
			simpleViewer.getStage().show();
			simpleViewer.getStage().toFront();
		}
	}

	private static boolean hasOriginalMetadata(ImageServer<BufferedImage> server) {
		var metadata = server.getMetadata();
		var originalMetadata = server.getOriginalMetadata();
		return Objects.equals(metadata, originalMetadata);
	}

	private static boolean promptToResetServerMetadata(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		if (hasOriginalMetadata(server)) {
			logger.info("ImageServer metadata is unchanged!");
			return false;
		}
		var originalMetadata = server.getOriginalMetadata();

		if (Dialogs.showConfirmDialog("Reset metadata", "Reset to original metadata?")) {
			imageData.updateServerMetadata(originalMetadata);
			return true;
		}
		return false;
	}

	private static boolean promptToSetMagnification(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		Double mag = server.getMetadata().getMagnification();
		Double mag2 = Dialogs.showInputDialog("设置放大倍数", "设置全分辨率图像的放大倍数", mag);
		if (mag2 == null || Double.isInfinite(mag) || Objects.equals(mag, mag2))
			return false;
		var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
				.magnification(mag2)
				.build();
		imageData.updateServerMetadata(metadata2);
		
		// 触发更新
		if (instance != null) {
			instance.updateDetailsView();
		}
		return true;
	}

	private static boolean promptToSetPixelSize(ImageData<BufferedImage> imageData, boolean requestZSpacing) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		var roi = selected == null ? null : selected.getROI();

		PixelCalibration cal = server.getPixelCalibration();
		double pixelWidthMicrons = cal.getPixelWidthMicrons();
		double pixelHeightMicrons = cal.getPixelHeightMicrons();
		double zSpacingMicrons = cal.getZSpacingMicrons();

		// Use line or area ROI if possible
		if (!requestZSpacing && roi != null && !roi.isEmpty() && (roi.isArea() || roi.isLine())) {
			boolean setPixelHeight = true;
			boolean setPixelWidth = true;	
			String message;
			String units = GeneralTools.micrometerSymbol();

			double pixelWidth = cal.getPixelWidthMicrons();
			double pixelHeight = cal.getPixelHeightMicrons();
			if (!Double.isFinite(pixelWidth))
				pixelWidth = 1;
			if (!Double.isFinite(pixelHeight))
				pixelHeight = 1;

			Double defaultValue = null;
			if (roi.isLine()) {
				setPixelHeight = roi.getBoundsHeight() != 0;
				setPixelWidth = roi.getBoundsWidth() != 0;
				message = "输入选定线条的长度";
				defaultValue = roi.getScaledLength(pixelWidth, pixelHeight);
			} else {
				message = "输入选定区域的面积";
				units = units + "^2";
				defaultValue = roi.getScaledArea(pixelWidth, pixelHeight);
			}

			if (Double.isNaN(defaultValue))
				defaultValue = 1.0;
			var params = new ParameterList()
					.addDoubleParameter("inputValue", message, defaultValue, units, "输入选定区域的校准值（" + units + "）以计算像素大小")
					.addBooleanParameter("squarePixels", "假设方形像素", true, "设置像素宽度与高度相同");
			params.setHiddenParameters(setPixelHeight && setPixelWidth, "squarePixels");
			if (!GuiTools.showParameterDialog("设置像素大小", params))
				return false;
			Double result = params.getDoubleParameterValue("inputValue");
			setPixelHeight = setPixelHeight || params.getBooleanParameterValue("squarePixels");
			setPixelWidth = setPixelWidth || params.getBooleanParameterValue("squarePixels");

			double sizeMicrons;
			if (roi.isLine())
				sizeMicrons = result.doubleValue() / roi.getLength();
			else
				sizeMicrons = Math.sqrt(result.doubleValue() / roi.getArea());

			if (setPixelHeight)
				pixelHeightMicrons = sizeMicrons;
			if (setPixelWidth)
				pixelWidthMicrons = sizeMicrons;
		} else {
			// Prompt for all required values
			ParameterList params = new ParameterList()
					.addDoubleParameter("pixelWidth", "像素宽度", pixelWidthMicrons, GeneralTools.micrometerSymbol(), "输入像素宽度")
					.addDoubleParameter("pixelHeight", "像素高度", pixelHeightMicrons, GeneralTools.micrometerSymbol(), "输入像素高度")
					.addDoubleParameter("zSpacing", "Z轴间距", zSpacingMicrons, GeneralTools.micrometerSymbol(), "输入Z轴切片之间的间距");
			params.setHiddenParameters(server.nZSlices() == 1, "zSpacing");
			if (!GuiTools.showParameterDialog("设置像素大小", params))
				return false;
			if (server.nZSlices() != 1) {
				zSpacingMicrons = params.getDoubleParameterValue("zSpacing");
			}
			pixelWidthMicrons = params.getDoubleParameterValue("pixelWidth");
			pixelHeightMicrons = params.getDoubleParameterValue("pixelHeight");
		}
		if ((pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) || (server.nZSlices() > 1 && zSpacingMicrons <= 0)) {
			if (!Dialogs.showConfirmDialog("设置像素大小", "您输入的值小于等于0，是否确定要删除此像素校准信息？")) {
				return false;
			}
			zSpacingMicrons = server.nZSlices() > 1 && zSpacingMicrons > 0 ? zSpacingMicrons : Double.NaN;
			if (pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) {
				pixelWidthMicrons = Double.NaN;
				pixelHeightMicrons = Double.NaN;
			}
		}
		if (QP.setPixelSizeMicrons(imageData, pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons)) {
			// Log for scripts
			WorkflowStep step;
			if (server.nZSlices() == 1) {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f)", pixelWidthMicrons, pixelHeightMicrons);
				step = new DefaultScriptableWorkflowStep("设置像素大小 " + GeneralTools.micrometerSymbol(), map, script);
			} else {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons,
						"zSpacingMicrons", zSpacingMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f, %f)", pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons);
				step = new DefaultScriptableWorkflowStep("设置像素大小 " + GeneralTools.micrometerSymbol(), map, script);
			}
			imageData.getHistoryWorkflow().addStep(step);
			
			// 触发更新
			if (instance != null) {
				instance.updateDetailsView();
			}
			return true;
		} else
			return false;
	}

	/**
	 * Prompt the user to set the {@link ImageType} for the image.
	 * @param imageData the image data for which the type should be set
	 * @param defaultType the default type (selected when the dialog is shown)
	 * @return true if the type was changed, false otherwise
	 */
	public static boolean promptToSetImageType(ImageData<BufferedImage> imageData, ImageType defaultType) {
		double size = 32;
		var group = new ToggleGroup();
		boolean isRGB = imageData.getServerMetadata().getChannels().size() == 3; // v0.6.0 supports non-8-bit color deconvolution
		if (defaultType == null)
			defaultType = ImageType.UNSET;
		var buttonMap = new LinkedHashMap<ImageType, ToggleButton>();

		// TODO: Create a nicer icon for unspecified type
		var iconUnspecified = (Group)createImageTypeCell(Color.GRAY, null, null, size);

		if (isRGB) {
			buttonMap.put(
					ImageType.BRIGHTFIELD_H_E,
					createImageTypeButton(ImageType.BRIGHTFIELD_H_E, "明场\nH&E",
							createImageTypeCell(Color.WHITE, Color.PINK, Color.DARKBLUE, size),
							"明场图像，苏木精和伊红染色\n(仅限8位RGB)", isRGB)
					);

			buttonMap.put(
					ImageType.BRIGHTFIELD_H_DAB,
					createImageTypeButton(ImageType.BRIGHTFIELD_H_DAB, "明场\nH-DAB",
							createImageTypeCell(Color.WHITE, Color.rgb(200, 200, 220), Color.rgb(120, 50, 20), size),
							"明场图像，苏木精和DAB染色\n(仅限8位RGB)", isRGB)
					);

			buttonMap.put(
					ImageType.BRIGHTFIELD_OTHER,
					createImageTypeButton(ImageType.BRIGHTFIELD_OTHER, "明场\n其他",
							createImageTypeCell(Color.WHITE, Color.ORANGE, Color.FIREBRICK, size),
							"明场图像，其他显色染色\n(仅限8位RGB)", isRGB)
					);
		}

		buttonMap.put(
				ImageType.FLUORESCENCE,
				createImageTypeButton(ImageType.FLUORESCENCE, "荧光",
						createImageTypeCell(Color.BLACK, Color.LIGHTGREEN, Color.BLUE, size),
						"荧光或类荧光图像，具有深色背景\n也适用于成像质谱", true)
				);

		buttonMap.put(
				ImageType.OTHER,
				createImageTypeButton(ImageType.OTHER, "其他",
						createImageTypeCell(Color.BLACK, Color.WHITE, Color.GRAY, size),
						"任何其他图像类型", true)
				);

		buttonMap.put(
				ImageType.UNSET,
				createImageTypeButton(ImageType.UNSET, "未指定",
						iconUnspecified,
						"不设置图像类型（不建议用于分析）", true)
				);

		var buttons = buttonMap.values().toArray(ToggleButton[]::new);
		for (var btn: buttons) {
			if (btn.isDisabled()) {
				btn.getTooltip().setText("图像类型不受支持，因为图像不是RGB格式");
			}
		}
		var buttonList = Arrays.asList(buttons);

		group.getToggles().setAll(buttons);
		group.selectedToggleProperty().addListener((v, o, n) -> {
			// Ensure that we can't deselect all buttons
			if (n == null)
				o.setSelected(true);
		});

		GridPaneUtils.setMaxWidth(Double.MAX_VALUE, buttons);
		GridPaneUtils.setMaxHeight(Double.MAX_VALUE, buttons);
		var selectedButton = buttonMap.get(defaultType);
		group.selectToggle(selectedButton);

		var grid = new GridPane();
		int nHorizontal = 3;
		int nVertical = (int)Math.ceil(buttons.length / (double)nHorizontal);
		grid.getColumnConstraints().setAll(IntStream.range(0, nHorizontal).mapToObj(i -> {
			var c = new ColumnConstraints();
			c.setPercentWidth(100.0/nHorizontal);
			return c;
		}).toList());

		grid.getRowConstraints().setAll(IntStream.range(0, nVertical).mapToObj(i -> {
			var c = new RowConstraints();
			c.setPercentHeight(100.0/nVertical);
			return c;
		}).toList());

		grid.setVgap(5);
		grid.setMaxWidth(Double.MAX_VALUE);
		for (int i = 0; i < buttons.length; i++) {
			grid.add(buttons[i], i % nHorizontal, i / nHorizontal);
		}

		var content = new BorderPane(grid);
		var comboOptions = new ComboBox<ImageTypeSetting>();
		comboOptions.getItems().setAll(ImageTypeSetting.values());

		var prompts = Map.of(
				ImageTypeSetting.AUTO_ESTIMATE, "始终自动估计类型（不提示）",
				ImageTypeSetting.PROMPT, "始终提示我设置类型",
				ImageTypeSetting.NONE, "不设置图像类型"
				);
		comboOptions.setButtonCell(FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setCellFactory(c -> FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setTooltip(
				new Tooltip("选择是否要在首次打开图像时看到这些提示"));
		comboOptions.setMaxWidth(Double.MAX_VALUE);
		comboOptions.getSelectionModel().select(PathPrefs.imageTypeSettingProperty().get());

		if (nVertical > 1)
			BorderPane.setMargin(comboOptions, new Insets(5, 0, 0, 0));
		else
			BorderPane.setMargin(comboOptions, new Insets(10, 0, 0, 0));
		content.setBottom(comboOptions);

		var labelDetails = new Label("图像类型用于某些命令的染色分离，例如'细胞检测'。\n"
				+ "明场类型仅适用于8位RGB图像。");
		labelDetails.setWrapText(true);
		labelDetails.prefWidthProperty().bind(grid.widthProperty().subtract(10));
		labelDetails.setMaxHeight(Double.MAX_VALUE);
		labelDetails.setPrefHeight(Label.USE_COMPUTED_SIZE);
		labelDetails.setPrefHeight(100);
		labelDetails.setAlignment(Pos.CENTER);
		labelDetails.setTextAlignment(TextAlignment.CENTER);

		var dialog = Dialogs.builder()
				.title("设置图像类型")
				.headerText("这是什么类型的图像？")
				.content(content)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.expandableContent(labelDetails)
				.build();

		// Try to make it easier to dismiss the dialog in a variety of ways
		var btnApply = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		Platform.runLater(() -> selectedButton.requestFocus());
		for (var btn : buttons) {
			btn.setOnMouseClicked(e -> {
				if (!btn.isDisabled() && e.getClickCount() == 2) {
					btnApply.fireEvent(new ActionEvent());
					e.consume();					
				}
			});
		}
		var enterPressed = new KeyCodeCombination(KeyCode.ENTER);
		var spacePressed = new KeyCodeCombination(KeyCode.SPACE);
		dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (enterPressed.match(e) || spacePressed.match(e)) {
				btnApply.fireEvent(new ActionEvent());
				e.consume();
			} else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
				var selected = (ToggleButton)group.getSelectedToggle();
				var ind = buttonList.indexOf(selected);
				var newSelected = selected;
				if (e.getCode() == KeyCode.UP && ind >= nHorizontal) {
					newSelected = buttonList.get(ind - nHorizontal);
				}
				if (e.getCode() == KeyCode.LEFT && ind > 0) {
					newSelected = buttonList.get(ind - 1);
				}
				if (e.getCode() == KeyCode.RIGHT && ind < buttonList.size()-1) {
					newSelected = buttonList.get(ind + 1);
				}
				if (e.getCode() == KeyCode.DOWN && ind < buttonList.size() - nHorizontal) {
					newSelected = buttonList.get(ind + nHorizontal);
				}
				newSelected.requestFocus();
				group.selectToggle(newSelected);
				e.consume();
			}
		});

		var response = dialog.showAndWait();
		if (response.orElse(ButtonType.CANCEL) == ButtonType.APPLY) {
			PathPrefs.imageTypeSettingProperty().set(comboOptions.getSelectionModel().getSelectedItem());
			var selectedType = (ImageType)group.getSelectedToggle().getUserData();
			if (selectedType != imageData.getImageType()) {
				imageData.setImageType(selectedType);
				// 触发更新
				if (instance != null) {
					instance.updateDetailsView();
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Create a standardized toggle button for setting the image type
	 * @param name
	 * @param node
	 * @param tooltip
	 * @return
	 */
	private static ToggleButton createImageTypeButton(ImageType type, String name, Node node, String tooltip, boolean isEnabled) {
		var btn = new ToggleButton(name, node);
		if (tooltip != null) {
			btn.setTooltip(new Tooltip(tooltip));
		}
		btn.setTextAlignment(TextAlignment.CENTER);
		btn.setAlignment(Pos.TOP_CENTER);
		btn.setContentDisplay(ContentDisplay.BOTTOM);
		btn.setOpacity(0.6);
		btn.selectedProperty().addListener((v, o, n) -> {
			if (n)
				btn.setOpacity(1.0);
			else
				btn.setOpacity(0.6);
		});
		btn.setUserData(type);
		if (!isEnabled)
			btn.setDisable(true);
		return btn;
	}

	/**
	 * Create a small icon of a cell, for use with image type buttons.
	 * @param bgColor
	 * @param cytoColor
	 * @param nucleusColor
	 * @param size
	 * @return
	 */
	private static Node createImageTypeCell(Color bgColor, Color cytoColor, Color nucleusColor, double size) {
		var group = new Group();
		if (bgColor != null) {
			var rect = new Rectangle(0, 0, size, size);
			rect.setFill(bgColor);
			rect.setEffect(new DropShadow(5.0, Color.BLACK));
			group.getChildren().add(rect);
		}
		if (cytoColor != null) {
			var cyto = new Ellipse(size/2.0, size/2.0, size/3.0, size/3.0);
			cyto.setFill(cytoColor);
			cyto.setEffect(new DropShadow(2.5, Color.BLACK));
			group.getChildren().add(cyto);
		}
		if (nucleusColor != null) {
			var nucleus = new Ellipse(size/2.4, size/2.4, size/5.0, size/5.0);
			nucleus.setFill(nucleusColor);
			nucleus.setEffect(new DropShadow(2.5, Color.BLACK));
			group.getChildren().add(nucleus);
		}
		group.setOpacity(0.7);
		return group;
	}

	/**
	 * Get the {@link Pane} component for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}

	private void setImageData(ImageData<BufferedImage> imageData) {
		this.imageData = imageData;
		updateDetailsView();
		
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (listAssociatedImages != null) {
			if (server == null)
				listAssociatedImages.getItems().clear();
			else
				listAssociatedImages.getItems().setAll(server.getAssociatedImageList());
		}

		// Check if we're showing associated images
		for (var entry : associatedImageViewers.entrySet()) {
			var name = entry.getKey();
			var simpleViewer = entry.getValue();
			logger.trace("Updating associated image viewer for {}", name);
			if (server == null || !server.getAssociatedImageList().contains(name))
				simpleViewer.updateImage(name, (BufferedImage)null);
			else
				simpleViewer.updateImage(name, server.getAssociatedImage(name));
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		setImageData(imageData);
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}

	private List<ImageDetailRow> getRows() {
		if (imageData == null || imageData.getServer() == null)
			return Collections.emptyList();
		var list = new ArrayList<ImageDetailRow>();
		if (imageData.isBrightfield())
			list.addAll(brightfieldRows);
		else
			list.addAll(otherRows);
		if (imageData.getServer().nZSlices() == 1)
			list.remove(ImageDetailRow.Z_SPACING);
		return list;
	}

	private String getName(ImageDetailRow row) {
		switch (row) {
		case NAME:
			return "图像名";
		case URI:
			if (imageData != null && imageData.getServer().getURIs().size() == 1)
				return "URI";
			return "URIs";
		case IMAGE_TYPE:
			return "类型";
		case METADATA_CHANGED:
			return "元数据";
		case PIXEL_TYPE:
			return "类型";
		case MAGNIFICATION:
			return "倍率";
		case WIDTH:
			return "宽";
		case HEIGHT:
			return "高";
		case DIMENSIONS:
			return "CZT";
		case PIXEL_WIDTH:
			return "宽";
		case PIXEL_HEIGHT:
			return "高";
		case Z_SPACING:
			return "Z轴间距";
		case UNCOMPRESSED_SIZE:
			return "未压缩";
		case SERVER_TYPE:
			return "服务器";
		case PYRAMID:
			return "金字塔";
		case STAIN_1:
			return "染色1";
		case STAIN_2:
			return "染色2";
		case STAIN_3:
			return "染色3";
		case BACKGROUND:
			return "背景";
		default:
			return null;
		}
	}

	private static String decodeURI(URI uri) {
		try {
			return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return uri.toString();
		}
	}

	private Object getValue(ImageDetailRow rowType) {
		if (imageData == null)
			return null;
		ImageServer<BufferedImage> server = imageData.getServer();
		PixelCalibration cal = server.getPixelCalibration();
		switch (rowType) {
		case NAME:
			var project = QuPathGUI.getInstance().getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry == null)
				return ServerTools.getDisplayableImageName(server);
			else
				return entry.getImageName();
		case URI:
			Collection<URI> uris = server.getURIs();
			if (uris.isEmpty())
				return "Not available";
			if (uris.size() == 1)
				return decodeURI(uris.iterator().next());
			return "[" + String.join(", ", uris.stream().map(ImageDetailsPane::decodeURI).toList()) + "]";
		case IMAGE_TYPE:
			return imageData.getImageType();
		case METADATA_CHANGED:
			return hasOriginalMetadata(imageData.getServer()) ? "No" : "Yes";
		case PIXEL_TYPE:
			String type = server.getPixelType().toString().toLowerCase();
			if (server.isRGB())
				type += " (rgb)";
			return type;
		case MAGNIFICATION:
			double mag = server.getMetadata().getMagnification();
			if (Double.isNaN(mag))
				return "Unknown";
			return mag;
		case WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getWidth(), server.getWidth() * cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getWidth());
		case HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getHeight(), server.getHeight() * cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getHeight());
		case DIMENSIONS:
			return String.format("%d x %d x %d", server.nChannels(), server.nZSlices(), server.nTimepoints());
		case PIXEL_WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case PIXEL_HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case Z_SPACING:
			if (cal.hasZSpacingMicrons())
				return String.format("%.4f %s", cal.getZSpacingMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case UNCOMPRESSED_SIZE:
			double size =
			server.getWidth()/1024.0 * server.getHeight()/1024.0 * 
			server.getPixelType().getBytesPerPixel() * server.nChannels() *
			server.nZSlices() * server.nTimepoints();
			String units = " MB";
			if (size > 1000) {
				size /= 1024.0;
				units = " GB";
			}
			return GeneralTools.formatNumber(size, 1) + units;
		case SERVER_TYPE:
			return server.getServerType();
		case PYRAMID:
			if (server.nResolutions() == 1)
				return "No";
			return GeneralTools.arrayToString(Locale.getDefault(Locale.Category.FORMAT), server.getPreferredDownsamples(), 1);
		case STAIN_1:
			return imageData.getColorDeconvolutionStains().getStain(1);
		case STAIN_2:
			return imageData.getColorDeconvolutionStains().getStain(2);
		case STAIN_3:
			return imageData.getColorDeconvolutionStains().getStain(3);
		case BACKGROUND:
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			double[] whitespace = new double[]{stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()};
			return whitespace;
		default:
			return null;
		}
	}
		
		private static void editStainVector(ImageData<BufferedImage> imageData, Object value) {
			if (imageData == null || !(value instanceof StainVector || value instanceof double[]))
				return;
			
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			int num = -1; // Default to background values
			String name = null;
			String message = null;
			if (value instanceof StainVector) {
				StainVector stainVector = (StainVector)value;
				if (stainVector.isResidual() && imageData.getImageType() != ImageType.BRIGHTFIELD_OTHER) {
					logger.warn("Cannot set residual stain vector - this is computed from the known vectors");
					return;
				}
				num = stains.getStainNumber(stainVector);
				if (num <= 0) {
                    logger.error("Could not identify stain vector {} inside {}", stainVector, stains);
					return;
				}
				name = stainVector.getName();
				message = "从ROI设置染色向量？";
			} else
				message = "从ROI设置颜色反卷积背景值？";

			ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedROI();
			boolean wasChanged = false;
			String warningMessage = null;
			boolean editableName = imageData.getImageType() == ImageType.BRIGHTFIELD_OTHER;
			if (roi != null) {
				if ((roi instanceof RectangleROI) && 
						!roi.isEmpty() &&
						roi.getArea() < 500*500) {
					if (Dialogs.showYesNoDialog("颜色反卷积染色", message)) {
						ImageServer<BufferedImage> server = imageData.getServer();
						BufferedImage img = null;
						try {
							img = server.readRegion(RegionRequest.createInstance(server.getPath(), 1, roi));
						} catch (IOException e) {
							Dialogs.showErrorMessage("设置染色向量", "无法读取图像区域");
							logger.error("Unable to read region", e);
							return;
						}
						if (num >= 0) {
							StainVector vectorValue = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels(name, img, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
							if (!Double.isFinite(vectorValue.getRed() + vectorValue.getGreen() + vectorValue.getBlue())) {
								Dialogs.showErrorMessage("设置染色向量",
										"无法为当前ROI设置染色！\n"
												+ "它可能太接近背景颜色。");
								return;
							}
							value = vectorValue;
						} else {
							// Update the background
							if (BufferedImageTools.is8bitColorType(img.getType())) {
								int rgb = ColorDeconvolutionHelper.getMedianRGB(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
								value = new double[]{ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb)};
							} else {
								double r = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 0));
								double g = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 1));
								double b = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 2));
								value = new double[]{r, g, b};
							}
						}
						wasChanged = true;
					}
				} else {
					warningMessage = "注意：要从图像区域设置染色值，请先绘制一个小的矩形ROI";
				}
			}

			// Prompt to set the name / verify stains
			ParameterList params = new ParameterList();
			String title;
			String nameBefore = null;
			String valuesBefore = null;
			String collectiveNameBefore = stains.getName();
			String suggestedName;
			if (collectiveNameBefore.endsWith("default"))
				suggestedName = collectiveNameBefore.substring(0, collectiveNameBefore.lastIndexOf("default")) + "modified";
			else
				suggestedName = collectiveNameBefore;
			params.addStringParameter("collectiveName", "集合名称", suggestedName, "输入所有3种染色的集合名称（例如：H-DAB扫描仪A，H&E扫描仪B）");
			if (value instanceof StainVector) {
				nameBefore = ((StainVector)value).getName();
				valuesBefore = ((StainVector)value).arrayAsString(Locale.getDefault(Category.FORMAT));
				params.addStringParameter("name", "名称", nameBefore, "输入染色名称")
				.addStringParameter("values", "值", valuesBefore, "输入3个值（红、绿、蓝）定义颜色反卷积染色向量，用空格分隔");
				title = "设置染色向量";
			} else {
				nameBefore = "背景";
				valuesBefore = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2);
				params.addStringParameter("name", "染色名称", nameBefore);
				params.addStringParameter("values", "染色值", valuesBefore, "输入3个值（红、绿、蓝）定义背景，用空格分隔");
				params.setHiddenParameters(true, "name");
				title = "设置背景";
			}

			if (warningMessage != null)
				params.addEmptyParameter(warningMessage);

			// Disable editing the name if it should be fixed
			ParameterPanelFX parameterPanel = new ParameterPanelFX(params);
			parameterPanel.setParameterEnabled("name", editableName);;
			if (!Dialogs.showConfirmDialog(title, parameterPanel.getPane()))
				return;

			// Check if anything changed
			String collectiveName = params.getStringParameterValue("collectiveName");
			String nameAfter = params.getStringParameterValue("name");
			String valuesAfter = params.getStringParameterValue("values");
			if (collectiveName.equals(collectiveNameBefore) && nameAfter.equals(nameBefore) && valuesAfter.equals(valuesBefore) && !wasChanged)
				return;

			if (Set.of("Red", "Green", "Blue").contains(nameAfter)) {
				Dialogs.showErrorMessage("设置染色向量", "不能将染色名称设置为'Red'、'Green'或'Blue' - 请选择其他名称");
				return;
			}

			double[] valuesParsed = ColorDeconvolutionStains.parseStainValues(Locale.getDefault(Category.FORMAT), valuesAfter);
			if (valuesParsed == null) {
				logger.error("Input for setting color deconvolution information invalid! Cannot parse 3 numbers from {}", valuesAfter);
				return;
			}

			if (num >= 0) {
				try {
					stains = stains.changeStain(StainVector.createStainVector(nameAfter, valuesParsed[0], valuesParsed[1], valuesParsed[2]), num);					
				} catch (Exception e) {
					logger.error("Error setting stain vectors", e);
					Dialogs.showErrorMessage("设置染色向量", "请求的染色向量无效！\n是否有两个染色相同？");
				}
			} else {
				// Update the background
				stains = stains.changeMaxValues(valuesParsed[0], valuesParsed[1], valuesParsed[2]);
			}

			// Set the collective name
			stains = stains.changeName(collectiveName);
			imageData.setColorDeconvolutionStains(stains);
		
		// 触发更新
		if (instance != null) {
			instance.updateDetailsView();
		}
	}

	private HBox createDetailItem(String name, Object value, ImageDetailRow row) {
		HBox itemContainer = new HBox();
		itemContainer.setSpacing(4);
		itemContainer.getStyleClass().add("detail-item");
		
		Label nameLabel = new Label(name);
		if(row == ImageDetailRow.PIXEL_TYPE) {
			nameLabel.getStyleClass().add("special-name");
			itemContainer.setMaxWidth(256);
			itemContainer.setMinWidth(256);
			itemContainer.setPrefWidth(256);
		} else {
			nameLabel.getStyleClass().add("detail-name");
		}
		
		Label valueLabel = new Label(value.toString());
		valueLabel.setWrapText(true);
		valueLabel.getStyleClass().add("detail-value");
		
		// 创建编辑图标
		Label editIcon = new Label();
		editIcon.setGraphic(IconFactory.createNode(16, 16, PathIcons.EDIT_BTN));
		editIcon.setVisible(false);
		
		if (value instanceof StainVector) {
			StainVector stain = (StainVector)value;
			Integer color = stain.getColor();
			valueLabel.setStyle(String.format("-fx-text-fill: rgb(%d, %d, %d);", 
				ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color)));
			valueLabel.setTooltip(new Tooltip("点击编辑按钮设置染色颜色（可以输入数值或在图像中使用小矩形ROI）"));
			
			editIcon.setVisible(true);
			editIcon.setCursor(Cursor.HAND);
			editIcon.setOnMouseClicked(e -> editStainVector(imageData, value));
		} else if (value instanceof double[]) {
			valueLabel.setText(GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2));
			valueLabel.setTooltip(new Tooltip("点击编辑按钮设置颜色反卷积的背景值（可以输入数值或在图像中使用小矩形ROI）"));
			
			editIcon.setVisible(true);
			editIcon.setCursor(Cursor.HAND);
			editIcon.setOnMouseClicked(e -> editStainVector(imageData, value));
		} else {
			if (row == ImageDetailRow.PIXEL_WIDTH || row == ImageDetailRow.PIXEL_HEIGHT || row == ImageDetailRow.Z_SPACING) {
				if ("Unknown".equals(value))
					valueLabel.setStyle("-fx-text-fill: red;");
				valueLabel.setTooltip(new Tooltip("点击编辑按钮设置像素校准（可以使用选定的线条或区域ROI）"));
				editIcon.setVisible(true);
				editIcon.setCursor(Cursor.HAND);
				editIcon.setOnMouseClicked(e -> promptToSetPixelSize(imageData, row == ImageDetailRow.Z_SPACING));
			} else if (row == ImageDetailRow.METADATA_CHANGED) {
				valueLabel.setTooltip(new Tooltip("点击编辑按钮重置原始元数据"));
				editIcon.setVisible(true);
				editIcon.setCursor(Cursor.HAND);
				editIcon.setOnMouseClicked(e -> {
					if (!hasOriginalMetadata(imageData.getServer())) {
						if (promptToResetServerMetadata(imageData)) {
							updateDetailsView();
							imageData.getHierarchy().fireHierarchyChangedEvent(this);
						}
					}
				});
			} else if (row == ImageDetailRow.MAGNIFICATION) {
				valueLabel.setTooltip(new Tooltip("点击编辑按钮设置放大倍数"));
				editIcon.setVisible(true);
				editIcon.setCursor(Cursor.HAND);
				editIcon.setOnMouseClicked(e -> {
					if (promptToSetMagnification(imageData)) {
						updateDetailsView();
						imageData.getHierarchy().fireHierarchyChangedEvent(this);
					}
				});
			} else if (row == ImageDetailRow.IMAGE_TYPE) {
				valueLabel.setTooltip(new Tooltip("点击编辑按钮设置图像类型"));
				editIcon.setVisible(true);
				editIcon.setCursor(Cursor.HAND);
				editIcon.setOnMouseClicked(e -> {
					if (promptToSetImageType(imageData, imageData.getImageType())) {
						updateDetailsView();
					}
				});
			} else if (row == ImageDetailRow.UNCOMPRESSED_SIZE) {
				valueLabel.setTooltip(new Tooltip("存储所有未压缩像素所需的大致内存"));
			} else {
				valueLabel.setTooltip(new Tooltip(value.toString()));
			}
		}
		
		itemContainer.getChildren().addAll(nameLabel, valueLabel);
		if (editIcon.isVisible()) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			itemContainer.getChildren().addAll(spacer, editIcon);
			editIcon.setVisible(false);
			
			itemContainer.setOnMouseEntered(e -> editIcon.setVisible(true));
			itemContainer.setOnMouseExited(e -> editIcon.setVisible(false));
		}
		return itemContainer;
	}

	private Node createPairedDetailItems(ImageDetailRow row1, ImageDetailRow row2) {
		HBox container = new HBox();
		container.setSpacing(4);
		
		String name1 = getName(row1);
		Object value1 = getValue(row1);
		String name2 = getName(row2);
		Object value2 = getValue(row2);
		var widthValue = 126;
		if(row1 == ImageDetailRow.UNCOMPRESSED_SIZE && row2 == ImageDetailRow.METADATA_CHANGED){
			widthValue = 140;
		}
		if (name1 != null && value1 != null && name2 != null && value2 != null) {
			// 创建第一个单元格
			HBox item1 = new HBox();
			item1.getStyleClass().add("detail-item");
			item1.setSpacing(4);
			item1.setPrefWidth(widthValue);
			item1.setMinWidth(widthValue);
			item1.setMaxWidth(widthValue);
			
			Label nameLabel1 = new Label(name1);
			if(row1 == ImageDetailRow.UNCOMPRESSED_SIZE && row2 == ImageDetailRow.METADATA_CHANGED){
				nameLabel1.getStyleClass().add("detail-name");
			} else {
				nameLabel1.getStyleClass().add("special-name");
			}
			
			// 分离数值和单位
			String valueStr1 = value1.toString();
			String unit1 = "";
			if (valueStr1.contains(" px")) {
				valueStr1 = valueStr1.substring(0, valueStr1.indexOf(" px"));
				unit1 = " px";
			} else if (valueStr1.contains(" " + GeneralTools.micrometerSymbol())) {
				valueStr1 = valueStr1.substring(0, valueStr1.indexOf(" " + GeneralTools.micrometerSymbol()));
				unit1 = " " + GeneralTools.micrometerSymbol();
			}
			
			Label valueLabel1 = new Label(valueStr1);
			valueLabel1.setWrapText(true);
			valueLabel1.getStyleClass().add("detail-value");
			
			Label unitLabel1 = new Label(unit1);
			unitLabel1.getStyleClass().add("detail-unit");
			unitLabel1.setAlignment(Pos.CENTER);
			
			// 创建编辑图标1
			Label editIcon1 = new Label();
			editIcon1.setGraphic(IconFactory.createNode(16, 16, PathIcons.EDIT_BTN));
			editIcon1.setVisible(false);
			
			item1.getChildren().addAll(nameLabel1, valueLabel1);
			
			// 创建第二个单元格
			HBox item2 = new HBox();
			item2.getStyleClass().add("detail-item");
			item2.setPrefWidth(widthValue);
			item2.setMinWidth(widthValue);
			item2.setMaxWidth(widthValue);
			item2.setSpacing(4);
			Label nameLabel2 = new Label(name2);
			if(row1 == ImageDetailRow.UNCOMPRESSED_SIZE && row2 == ImageDetailRow.METADATA_CHANGED){
				nameLabel2.getStyleClass().add("detail-name");
			} else {
				nameLabel2.getStyleClass().add("special-name");
			}
			
			// 分离数值和单位
			String valueStr2 = value2.toString();
			String unit2 = "";
			if (valueStr2.contains(" px")) {
				valueStr2 = valueStr2.substring(0, valueStr2.indexOf(" px"));
				unit2 = " px";
			} else if (valueStr2.contains(" " + GeneralTools.micrometerSymbol())) {
				valueStr2 = valueStr2.substring(0, valueStr2.indexOf(" " + GeneralTools.micrometerSymbol()));
				unit2 = " " + GeneralTools.micrometerSymbol();
			}
			
			Label valueLabel2 = new Label(valueStr2);
			valueLabel2.setWrapText(true);
			valueLabel2.getStyleClass().add("detail-value");
			
			Label unitLabel2 = new Label(unit2);
			unitLabel2.getStyleClass().add("detail-unit");
			
			// 创建编辑图标2
			Label editIcon2 = new Label();
			editIcon2.setGraphic(IconFactory.createNode(16, 16, PathIcons.EDIT_BTN));
			editIcon2.setVisible(false);
			
			item2.getChildren().addAll(nameLabel2, valueLabel2);
			
			// 添加点击事件和图标可见性
			if (row1 == ImageDetailRow.PIXEL_WIDTH || row1 == ImageDetailRow.PIXEL_HEIGHT || row1 == ImageDetailRow.Z_SPACING) {
				if ("Unknown".equals(valueStr1))
					valueLabel1.setStyle("-fx-text-fill: red;");
				valueLabel1.setTooltip(new Tooltip("点击编辑按钮设置像素校准（可以使用选定的线条或区域ROI）"));
				editIcon1.setVisible(true);
				editIcon1.setCursor(Cursor.HAND);
				editIcon1.setOnMouseClicked(e -> promptToSetPixelSize(imageData, row1 == ImageDetailRow.Z_SPACING));
			} else if (row1 == ImageDetailRow.MAGNIFICATION) {
				valueLabel1.setTooltip(new Tooltip("点击编辑按钮设置放大倍数"));
				editIcon1.setVisible(true);
				editIcon1.setCursor(Cursor.HAND);
				editIcon1.setOnMouseClicked(e -> {
					if (promptToSetMagnification(imageData)) {
						updateDetailsView();
						imageData.getHierarchy().fireHierarchyChangedEvent(this);
					}
				});
			}
			
			if (row2 == ImageDetailRow.PIXEL_WIDTH || row2 == ImageDetailRow.PIXEL_HEIGHT || row2 == ImageDetailRow.Z_SPACING) {
				if ("Unknown".equals(valueStr2))
					valueLabel2.setStyle("-fx-text-fill: red;");
				valueLabel2.setTooltip(new Tooltip("点击编辑按钮设置像素校准（可以使用选定的线条或区域ROI）"));
				editIcon2.setVisible(true);
				editIcon2.setCursor(Cursor.HAND);
				editIcon2.setOnMouseClicked(e -> promptToSetPixelSize(imageData, row2 == ImageDetailRow.Z_SPACING));
			} else if (row2 == ImageDetailRow.METADATA_CHANGED) {
				valueLabel2.setTooltip(new Tooltip("点击编辑按钮重置原始元数据"));
				editIcon2.setVisible(true);
				editIcon2.setCursor(Cursor.HAND);
				editIcon2.setOnMouseClicked(e -> {
					if (!hasOriginalMetadata(imageData.getServer())) {
						if (promptToResetServerMetadata(imageData)) {
							updateDetailsView();
							imageData.getHierarchy().fireHierarchyChangedEvent(this);
						}
					}
				});
			}
			
			// 添加编辑图标（如果可见）
			if (editIcon1.isVisible()) {
				Region spacer1 = new Region();
				HBox.setHgrow(spacer1, Priority.ALWAYS);
				item1.getChildren().addAll(spacer1, editIcon1);
				editIcon1.setVisible(false);
				
				item1.setOnMouseEntered(e -> editIcon1.setVisible(true));
				item1.setOnMouseExited(e -> editIcon1.setVisible(false));
			}
			
			if (editIcon2.isVisible()) {
				Region spacer2 = new Region();
				HBox.setHgrow(spacer2, Priority.ALWAYS);
				item2.getChildren().addAll(spacer2, editIcon2);
				editIcon2.setVisible(false);
				
				item2.setOnMouseEntered(e -> editIcon2.setVisible(true));
				item2.setOnMouseExited(e -> editIcon2.setVisible(false));
			}
			
			if(row1 == ImageDetailRow.UNCOMPRESSED_SIZE && row2 == ImageDetailRow.METADATA_CHANGED){
				container.getChildren().addAll(item1, item2);
			} else if(row1 == ImageDetailRow.WIDTH && row2 == ImageDetailRow.HEIGHT){
				Region spacer = new Region();
				HBox.setHgrow(spacer, Priority.ALWAYS);
				container.getChildren().addAll(item1, item2, spacer, unitLabel1);
			} else {
				container.getChildren().addAll(item1, item2, unitLabel1);
			}
		}
		
		return container;
	}
}