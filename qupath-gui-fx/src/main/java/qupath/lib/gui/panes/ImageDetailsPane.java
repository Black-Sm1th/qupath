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
		groups.put("属性", Arrays.asList(ImageDetailRow.NAME, ImageDetailRow.URI, ImageDetailRow.UNCOMPRESSED_SIZE,ImageDetailRow.METADATA_CHANGED,ImageDetailRow.SERVER_TYPE,ImageDetailRow.PYRAMID));
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
			groupContainer.getStyleClass().add("detail-group");
			groupContainer.setSpacing(8);
			// 创建分组标题栏
			HBox titleBar = new HBox();
			titleBar.setAlignment(Pos.CENTER_LEFT);
			titleBar.getStyleClass().add("detail-group-header");
			
			Label titleLabel = new Label(groupName);
			titleLabel.getStyleClass().add("detail-group-title");
			
			// 创建箭头指示器
			Region arrow = new Region();
			arrow.getStyleClass().addAll("arrow", "arrow-down");
			arrow.setMinWidth(12);
			arrow.setMinHeight(12);
			arrow.setMaxWidth(12);
			arrow.setMaxHeight(12);
			arrow.setVisible(groupName.equals("属性"));
			arrow.setManaged(groupName.equals("属性"));
			
			HBox.setHgrow(titleLabel, Priority.ALWAYS);
			titleBar.setAlignment(Pos.CENTER_LEFT);
			titleBar.getChildren().addAll(titleLabel);
			
			if (groupName.equals("属性")) {
				titleBar.getChildren().add(arrow);
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
				for (ImageDetailRow row : groupRows) {
					if (row != ImageDetailRow.NAME) {
						String name = getName(row);
						Object value = getValue(row);
						if (name != null && value != null) {
							HBox itemContainer = createDetailItem(name, value, row);
							otherProperties.getChildren().add(itemContainer);
						}
					}
				}
				
				contentContainer.getChildren().add(otherProperties);
				
				// 添加点击事件处理
				titleBar.setOnMouseClicked(e -> {
					boolean isExpanded = otherProperties.isVisible();
					otherProperties.setVisible(!isExpanded);
					otherProperties.setManaged(!isExpanded);
					arrow.getStyleClass().remove(isExpanded ? "arrow-up" : "arrow-down");
					arrow.getStyleClass().add(isExpanded ? "arrow-down" : "arrow-up");
					titleBar.pseudoClassStateChanged(PseudoClass.getPseudoClass("expanded"), !isExpanded);
				});
				
				// 设置初始状态
				titleBar.pseudoClassStateChanged(PseudoClass.getPseudoClass("expanded"), false);
			} else {
				// 其他分组正常显示所有内容
				for (ImageDetailRow row : groupRows) {
					String name = getName(row);
					Object value = getValue(row);
					if (name != null && value != null) {
						HBox itemContainer = createDetailItem(name, value, row);
						contentContainer.getChildren().add(itemContainer);
					}
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
		Double mag2 = Dialogs.showInputDialog("Set magnification", "Set magnification for full resolution image", mag);
		if (mag2 == null || Double.isInfinite(mag) || Objects.equals(mag, mag2))
			return false;
		var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
				.magnification(mag2)
				.build();
		imageData.updateServerMetadata(metadata2);
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
				message = "Enter selected line length";
				defaultValue = roi.getScaledLength(pixelWidth, pixelHeight);
			} else {
				message = "Enter selected ROI area";
				units = units + "^2";
				defaultValue = roi.getScaledArea(pixelWidth, pixelHeight);
			}

			if (Double.isNaN(defaultValue))
				defaultValue = 1.0;
			var params = new ParameterList()
					.addDoubleParameter("inputValue", message, defaultValue, units, "Enter calibrated value in " + units + " for the selected ROI to calculate the pixel size")
					.addBooleanParameter("squarePixels", "Assume square pixels", true, "Set the pixel width to match the pixel height");
			params.setHiddenParameters(setPixelHeight && setPixelWidth, "squarePixels");
			if (!GuiTools.showParameterDialog("Set pixel size", params))
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
					.addDoubleParameter("pixelWidth", "Pixel width", pixelWidthMicrons, GeneralTools.micrometerSymbol(), "Enter the pixel width")
					.addDoubleParameter("pixelHeight", "Pixel height", pixelHeightMicrons, GeneralTools.micrometerSymbol(), "Entry the pixel height")
					.addDoubleParameter("zSpacing", "Z-spacing", zSpacingMicrons, GeneralTools.micrometerSymbol(), "Enter the spacing between slices of a z-stack");
			params.setHiddenParameters(server.nZSlices() == 1, "zSpacing");
			if (!GuiTools.showParameterDialog("Set pixel size", params))
				return false;
			if (server.nZSlices() != 1) {
				zSpacingMicrons = params.getDoubleParameterValue("zSpacing");
			}
			pixelWidthMicrons = params.getDoubleParameterValue("pixelWidth");
			pixelHeightMicrons = params.getDoubleParameterValue("pixelHeight");
		}
		if ((pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) || (server.nZSlices() > 1 && zSpacingMicrons <= 0)) {
			if (!Dialogs.showConfirmDialog("Set pixel size", "You entered values <= 0, do you really want to remove this pixel calibration information?")) {
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
				step = new DefaultScriptableWorkflowStep("Set pixel size " + GeneralTools.micrometerSymbol(), map, script);
			} else {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons,
						"zSpacingMicrons", zSpacingMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f, %f)", pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons);
				step = new DefaultScriptableWorkflowStep("Set pixel size " + GeneralTools.micrometerSymbol(), map, script);
			}
			imageData.getHistoryWorkflow().addStep(step);
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
					createImageTypeButton(ImageType.BRIGHTFIELD_H_E, "Brightfield\nH&E",
							createImageTypeCell(Color.WHITE, Color.PINK, Color.DARKBLUE, size),
							"Brightfield image with hematoylin & eosin stains\n(8-bit RGB only)", isRGB)
					);

			buttonMap.put(
					ImageType.BRIGHTFIELD_H_DAB,
					createImageTypeButton(ImageType.BRIGHTFIELD_H_DAB, "Brightfield\nH-DAB",
							createImageTypeCell(Color.WHITE, Color.rgb(200, 200, 220), Color.rgb(120, 50, 20), size),
							"Brightfield image with hematoylin & DAB stains\n(8-bit RGB only)", isRGB)
					);

			buttonMap.put(
					ImageType.BRIGHTFIELD_OTHER,
					createImageTypeButton(ImageType.BRIGHTFIELD_OTHER, "Brightfield\nOther",
							createImageTypeCell(Color.WHITE, Color.ORANGE, Color.FIREBRICK, size),
							"Brightfield image with other chromogenic stains\n(8-bit RGB only)", isRGB)
					);
		}

		buttonMap.put(
				ImageType.FLUORESCENCE,
				createImageTypeButton(ImageType.FLUORESCENCE, "Fluorescence",
						createImageTypeCell(Color.BLACK, Color.LIGHTGREEN, Color.BLUE, size),
						"Fluorescence or fluorescence-like image with a dark background\n" +
								"Also suitable for imaging mass cytometry", true)
				);

		buttonMap.put(
				ImageType.OTHER,
				createImageTypeButton(ImageType.OTHER, "Other",
						createImageTypeCell(Color.BLACK, Color.WHITE, Color.GRAY, size),
						"Any other image type", true)
				);

		buttonMap.put(
				ImageType.UNSET,
				createImageTypeButton(ImageType.UNSET, "Unspecified",
						iconUnspecified,
						"Do not set the image type (not recommended for analysis)", true)
				);

		var buttons = buttonMap.values().toArray(ToggleButton[]::new);
		for (var btn: buttons) {
			if (btn.isDisabled()) {
				btn.getTooltip().setText("Image type is not supported because image is not RGB");
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
		//		grid.setHgap(5);
		grid.setMaxWidth(Double.MAX_VALUE);
		for (int i = 0; i < buttons.length; i++) {
			grid.add(buttons[i], i % nHorizontal, i / nHorizontal);
		}
		//		grid.getChildren().setAll(buttons);

		var content = new BorderPane(grid);
		var comboOptions = new ComboBox<ImageTypeSetting>();
		comboOptions.getItems().setAll(ImageTypeSetting.values());

		var prompts = Map.of(
				ImageTypeSetting.AUTO_ESTIMATE, "Always auto-estimate type (don't prompt)",
				ImageTypeSetting.PROMPT, "Always prompt me to set type",
				ImageTypeSetting.NONE, "Don't set the image type"
				);
		comboOptions.setButtonCell(FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setCellFactory(c -> FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setTooltip(
				new Tooltip("Choose whether you want to see these prompts " +
						"when opening an image for the first time"));
		comboOptions.setMaxWidth(Double.MAX_VALUE);
		//		comboOptions.prefWidthProperty().bind(grid.widthProperty().subtract(100));
		comboOptions.getSelectionModel().select(PathPrefs.imageTypeSettingProperty().get());

		if (nVertical > 1)
			BorderPane.setMargin(comboOptions, new Insets(5, 0, 0, 0));
		else
			BorderPane.setMargin(comboOptions, new Insets(10, 0, 0, 0));
		content.setBottom(comboOptions);

		var labelDetails = new Label("The image type is used for stain separation "
				+ "by some commands, e.g. 'Cell detection'.\n"
				+ "Brightfield types are only available for 8-bit RGB images.");
		//				+ "For 'Brightfield' images you can set the color stain vectors.");
		labelDetails.setWrapText(true);
		labelDetails.prefWidthProperty().bind(grid.widthProperty().subtract(10));
		labelDetails.setMaxHeight(Double.MAX_VALUE);
		labelDetails.setPrefHeight(Label.USE_COMPUTED_SIZE);
		labelDetails.setPrefHeight(100);
		labelDetails.setAlignment(Pos.CENTER);
		labelDetails.setTextAlignment(TextAlignment.CENTER);

		var dialog = Dialogs.builder()
				.title("Set image type")
				.headerText("What type of image is this?")
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
				Platform.runLater(() -> {
					if (instance != null) {
						instance.updateDetailsView();
					}
				});
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
			message = "Set stain vector from ROI?";
		} else
			message = "Set color deconvolution background values from ROI?";

		ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedROI();
		boolean wasChanged = false;
		String warningMessage = null;
		boolean editableName = imageData.getImageType() == ImageType.BRIGHTFIELD_OTHER;
		if (roi != null) {
			if ((roi instanceof RectangleROI) && 
					!roi.isEmpty() &&
					roi.getArea() < 500*500) {
				if (Dialogs.showYesNoDialog("Color deconvolution stains", message)) {
					ImageServer<BufferedImage> server = imageData.getServer();
					BufferedImage img = null;
					try {
						img = server.readRegion(RegionRequest.createInstance(server.getPath(), 1, roi));
					} catch (IOException e) {
						Dialogs.showErrorMessage("Set stain vector", "Unable to read image region");
						logger.error("Unable to read region", e);
						return;
					}
					if (num >= 0) {
						StainVector vectorValue = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels(name, img, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
						if (!Double.isFinite(vectorValue.getRed() + vectorValue.getGreen() + vectorValue.getBlue())) {
							Dialogs.showErrorMessage("Set stain vector",
									"Cannot set stains for the current ROI!\n"
											+ "It might be too close to the background color.");
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
				warningMessage = "Note: To set stain values from an image region, draw a small, rectangular ROI first";
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
		params.addStringParameter("collectiveName", "Collective name", suggestedName, "Enter collective name for all 3 stains (e.g. H-DAB Scanner A, H&E Scanner B)");
		if (value instanceof StainVector) {
			nameBefore = ((StainVector)value).getName();
			valuesBefore = ((StainVector)value).arrayAsString(Locale.getDefault(Category.FORMAT));
			params.addStringParameter("name", "Name", nameBefore, "Enter stain name")
			.addStringParameter("values", "Values", valuesBefore, "Enter 3 values (red, green, blue) defining color deconvolution stain vector, separated by spaces");
			title = "Set stain vector";
		} else {
			nameBefore = "Background";
			valuesBefore = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2);
			params.addStringParameter("name", "Stain name", nameBefore);
			params.addStringParameter("values", "Stain values", valuesBefore, "Enter 3 values (red, green, blue) defining background, separated by spaces");
			params.setHiddenParameters(true, "name");
			title = "Set background";
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
			Dialogs.showErrorMessage("Set stain vector", "Cannot set stain name to 'Red', 'Green', or 'Blue' - please choose a different name");
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
				Dialogs.showErrorMessage("Set stain vectors", "Requested stain vectors are not valid!\nAre two stains equal?");
			}
		} else {
			// Update the background
			stains = stains.changeMaxValues(valuesParsed[0], valuesParsed[1], valuesParsed[2]);
		}

		// Set the collective name
		stains = stains.changeName(collectiveName);
		imageData.setColorDeconvolutionStains(stains);
	}

	private HBox createDetailItem(String name, Object value, ImageDetailRow row) {
		HBox itemContainer = new HBox();
		itemContainer.setSpacing(16);
		itemContainer.getStyleClass().add("detail-item");
		itemContainer.setFillHeight(true);
		
		Label nameLabel = new Label(name);
		nameLabel.getStyleClass().add("detail-name");
		
		Label valueLabel = new Label(value.toString());
		valueLabel.setWrapText(true);
		valueLabel.getStyleClass().add("detail-value");
		HBox.setHgrow(valueLabel, Priority.ALWAYS);
		
		if (value instanceof StainVector) {
			StainVector stain = (StainVector)value;
			Integer color = stain.getColor();
			valueLabel.setStyle(String.format("-fx-text-fill: rgb(%d, %d, %d);", 
				ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color)));
			valueLabel.setTooltip(new Tooltip("双击设置染色颜色（可以输入数值或在图像中使用小矩形ROI）"));
			
			itemContainer.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2)
					editStainVector(imageData, value);
			});
		} else if (value instanceof double[]) {
			valueLabel.setText(GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2));
			valueLabel.setTooltip(new Tooltip("双击设置颜色反卷积的背景值（可以输入数值或在图像中使用小矩形ROI）"));
			
			itemContainer.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2)
					editStainVector(imageData, value);
			});
		} else {
			if (row == ImageDetailRow.PIXEL_WIDTH || row == ImageDetailRow.PIXEL_HEIGHT || row == ImageDetailRow.Z_SPACING) {
				if ("Unknown".equals(value))
					valueLabel.setStyle("-fx-text-fill: red;");
				valueLabel.setTooltip(new Tooltip("双击设置像素校准（可以使用选定的线条或区域ROI）"));
				itemContainer.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2)
						promptToSetPixelSize(imageData, row == ImageDetailRow.Z_SPACING);
				});
			} else if (row == ImageDetailRow.METADATA_CHANGED) {
				valueLabel.setTooltip(new Tooltip("双击重置原始元数据"));
				itemContainer.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2) {
						if (!hasOriginalMetadata(imageData.getServer())) {
							if (promptToResetServerMetadata(imageData)) {
								updateDetailsView();
								imageData.getHierarchy().fireHierarchyChangedEvent(this);
							}
						}
					}
				});
			} else if (row == ImageDetailRow.MAGNIFICATION) {
				valueLabel.setTooltip(new Tooltip("双击设置放大倍数"));
				itemContainer.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2) {
						if (promptToSetMagnification(imageData)) {
							updateDetailsView();
							imageData.getHierarchy().fireHierarchyChangedEvent(this);
						}
					}
				});
			} else if (row == ImageDetailRow.IMAGE_TYPE) {
				valueLabel.setTooltip(new Tooltip("双击设置图像类型"));
				itemContainer.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2) {
						if (promptToSetImageType(imageData, imageData.getImageType())) {
							updateDetailsView();
						}
					}
				});
			} else if (row == ImageDetailRow.UNCOMPRESSED_SIZE) {
				valueLabel.setTooltip(new Tooltip("存储所有未压缩像素所需的大致内存"));
			}else{
				valueLabel.setTooltip(new Tooltip(value.toString()));
			}
		}
		
		itemContainer.getChildren().addAll(nameLabel, valueLabel);
		return itemContainer;
	}
}