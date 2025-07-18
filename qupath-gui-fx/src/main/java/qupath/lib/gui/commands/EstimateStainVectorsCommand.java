/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;

import javafx.scene.control.*;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import qupath.lib.analysis.algorithms.EstimateStainVectors;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command to help set stain vectors for brightfield chromogenic stains,
 * e.g. H-DAB or H&amp;E.
 * 
 * @author Pete Bankhead
 *
 */
class EstimateStainVectorsCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(EstimateStainVectorsCommand.class);
	
	static int MAX_PIXELS = 4000*4000;
	
	private static final String TITLE = "估计染色向量";
	
	
	private enum AxisColor {RED, GREEN, BLUE;
		@Override
		public String toString() {
            return switch (this) {
                case RED -> "红";
                case GREEN -> "绿";
                case BLUE -> "蓝";
                default -> throw new IllegalArgumentException();
            };
		}
	};


	public static void promptToEstimateStainVectors(ImageData<BufferedImage> imageData) {
		if (imageData == null) {
			GuiTools.showNoImageError(TITLE);
			return;
		}
		if (!imageData.isBrightfield() || imageData.getServer() == null || imageData.getServer().nChannels() != 3) {
			Dialogs.showErrorMessage(TITLE, "没有选择明场、3通道图像！");
			return;
		}
		ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
		if (stains == null || !stains.getStain(3).isResidual()) {
			Dialogs.showErrorMessage(TITLE, "抱歉，染色估计只适用于具有2种染色的明场、3通道图像");
			return;
		}
		
		PathObject pathObject = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		ROI roi = pathObject == null ? null : pathObject.getROI();
		if (roi == null)
			roi = ROIs.createRectangleROI(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight(), ImagePlane.getDefaultPlane());
		
		double downsample = Math.max(1, Math.sqrt((roi.getBoundsWidth() * roi.getBoundsHeight()) / MAX_PIXELS));
		RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, roi);
		BufferedImage img = null;
		
		try {
			img = imageData.getServer().readRegion(request);
		} catch (IOException e) {
			Dialogs.showErrorMessage("Estimate stain vectors", e);
			logger.error("Unable to obtain pixels for {}", request, e);
			return;
		}
		
		// Apply small amount of smoothing to reduce compression artefacts
		try {
			img = EstimateStainVectors.smoothImage(img);
		} catch (ImagingOpException e) {
				logger.warn("Unable to convolve image - will attempt stain estimation without smoothing");
				logger.debug(e.getMessage(), e);
		}
		
		// Check modes for background
		double rMax, gMax, bMax;
		if (BufferedImageTools.is8bitColorType(img.getType())) {
			int[] rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			int[] rgbMode = EstimateStainVectors.getModeRGB(rgb);
			rMax = rgbMode[0];
			gMax = rgbMode[1];
			bMax = rgbMode[2];
		} else {
			rMax = EstimateStainVectors.getMode(ColorDeconvolutionHelper.getPixels(img.getRaster(), 0), 256);
			gMax = EstimateStainVectors.getMode(ColorDeconvolutionHelper.getPixels(img.getRaster(), 1), 256);
			bMax = EstimateStainVectors.getMode(ColorDeconvolutionHelper.getPixels(img.getRaster(), 2), 256);
		}

		// Check if the background values may need to be changed
		if (rMax != stains.getMaxRed() || gMax != stains.getMaxGreen() || bMax != stains.getMaxBlue()) {
			ButtonType response =
					Dialogs.showYesNoCancelDialog(TITLE,
							String.format("模态RGB值 %s, %s, %s 与当前背景值不匹配 - 是否要使用模态值？",
									GeneralTools.formatNumber(rMax, 2),
									GeneralTools.formatNumber(gMax, 2),
									GeneralTools.formatNumber(bMax, 2)));
			if (response == ButtonType.CANCEL)
				return;
			else if (response == ButtonType.YES) {
				stains = stains.changeMaxValues(rMax, gMax, bMax);
				imageData.setColorDeconvolutionStains(stains);
			}
		}
		
		
		ColorDeconvolutionStains stainsUpdated = null;
		logger.info("Requesting region for stain vector estimation: {}", request);
		try {
			stainsUpdated = showStainEditor(img, stains);
		} catch (Exception e) {
			Dialogs.showErrorMessage(TITLE, "染色估计错误: " + e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
			return;
		}
		if (!stains.equals(stainsUpdated)) {
			String suggestedName;
			String collectiveNameBefore = stainsUpdated.getName();
			if (collectiveNameBefore.endsWith("default"))
				suggestedName = collectiveNameBefore.substring(0, collectiveNameBefore.lastIndexOf("default")) + "estimated";
			else
				suggestedName = collectiveNameBefore;
			
			String newName = Dialogs.showInputDialog(TITLE, "为染色向量设置名称", suggestedName);
			if (newName == null)
				return;
			if (!newName.isBlank())
				stainsUpdated = stainsUpdated.changeName(newName);
			imageData.setColorDeconvolutionStains(stainsUpdated);
		}
		
	}
	
	
	
	
	
	@SuppressWarnings("unchecked")
	public static ColorDeconvolutionStains showStainEditor(final BufferedImage img, final ColorDeconvolutionStains stains) {

		float[] red, green, blue;
		int[] rgb;

		if (BufferedImageTools.is8bitColorType(img.getType())) {
			int[] buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			rgb = EstimateStainVectors.subsample(buf, 10000);

			red = ColorDeconvolutionHelper.getRedOpticalDensities(rgb, stains.getMaxRed(), null);
			green = ColorDeconvolutionHelper.getGreenOpticalDensities(rgb, stains.getMaxGreen(), null);
			blue = ColorDeconvolutionHelper.getBlueOpticalDensities(rgb, stains.getMaxBlue(), null);
		} else {
			red = ColorDeconvolutionHelper.getPixels(img.getRaster(), 0, null);
			green = ColorDeconvolutionHelper.getPixels(img.getRaster(), 1, null);
			blue = ColorDeconvolutionHelper.getPixels(img.getRaster(), 2, null);

			// TODO: Try to generate a sensible RGB color for each pixel
			int n = red.length;
			rgb = new int[n];
			for (int i = 0; i < n; i++) {
				int r = (int)GeneralTools.clipValue(red[i]/stains.getMaxRed()*255, 0, 255);
				int g = (int)GeneralTools.clipValue(green[i]/stains.getMaxGreen()*255, 0, 255);
				int b = (int)GeneralTools.clipValue(blue[i]/stains.getMaxBlue()*255, 0, 255);
				rgb[i] = ColorTools.packRGB(r, g, b);
			}

			ColorDeconvolutionHelper.convertToOpticalDensity(red, stains.getMaxRed());
			ColorDeconvolutionHelper.convertToOpticalDensity(green, stains.getMaxGreen());
			ColorDeconvolutionHelper.convertToOpticalDensity(blue, stains.getMaxBlue());
		}

		
//		panelPlots.setBorder(BorderFactory.createTitledBorder(null, "Stain vector scatterplots", TitledBorder.CENTER, TitledBorder.TOP));
		
		StainsWrapper stainsWrapper = new StainsWrapper(stains);
		Node panelRedGreen = createScatterPanel(new ScatterPlot(red, green, null, rgb), stainsWrapper, AxisColor.RED, AxisColor.GREEN);
		Node panelRedBlue = createScatterPanel(new ScatterPlot(red, blue, null, rgb), stainsWrapper, AxisColor.RED, AxisColor.BLUE);
		Node panelGreenBlue = createScatterPanel(new ScatterPlot(green, blue, null, rgb), stainsWrapper, AxisColor.GREEN, AxisColor.BLUE);
		
//		GridPane panelPlots = PanelToolsFX.createColumnGrid(panelRedGreen, panelRedBlue, panelGreenBlue);
		GridPane panelPlots = new GridPane();
		panelPlots.setHgap(10);
		panelPlots.add(panelRedGreen, 0, 0);
		panelPlots.add(panelRedBlue, 1, 0);
		panelPlots.add(panelGreenBlue, 2, 0);
//		panelPlots.getChildren().addAll(panelRedGreen, panelRedBlue, panelGreenBlue);
		panelPlots.setPadding(new Insets(0, 0, 10, 0));
		
		BorderPane panelSouth = new BorderPane();
		
		TableView<Integer> table = new TableView<>();
		table.getItems().setAll(1, 2, 3);
		
		stainsWrapper.addStainListener(new StainChangeListener() {

			@Override
			public void stainChanged(StainsWrapper stainsWrapper) {
				table.refresh();
			}
			
		});
		TableColumn<Integer, String> colName = new TableColumn<>("名称");
		colName.setCellValueFactory(v -> new SimpleStringProperty(stainsWrapper.getStains().getStain(v.getValue()).getName()));
		TableColumn<Integer, String> colOrig = new TableColumn<>("原始");
		colOrig.setCellValueFactory(v -> new SimpleStringProperty(
				stainArrayAsString(Locale.getDefault(Category.FORMAT), stainsWrapper.getOriginalStains().getStain(v.getValue()), " | ", 3)));
		TableColumn<Integer, String> colCurrent = new TableColumn<>("当前");
		colCurrent.setCellValueFactory(v -> new SimpleStringProperty(
				stainArrayAsString(Locale.getDefault(Category.FORMAT), stainsWrapper.getStains().getStain(v.getValue()), " | ", 3)));
		TableColumn<Integer, String> colAngle = new TableColumn<>("角度");
		colAngle.setCellValueFactory(v -> {
			return new SimpleStringProperty(
					GeneralTools.formatNumber(
						StainVector.computeAngle(
						stainsWrapper.getOriginalStains().getStain(v.getValue()),
						stainsWrapper.getStains().getStain(v.getValue()))
						, 2)
					);
		});//new SimpleStringProperty(stainsWrapper.getStains().getStain(v.getValue()).arrayAsString(", ", 3)));
		table.getColumns().addAll(colName, colOrig, colCurrent, colAngle);
		table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		table.setPrefHeight(120);
		
		// Create auto detection parameters
		ParameterList params = new ParameterList()
				.addDoubleParameter("minStainOD", "最小通道OD", 0.05, "", "最小染色OD - 任何通道(RGB)中OD值更低的像素将被忽略（默认= 0.05）")
				.addDoubleParameter("maxStainOD", "最大总OD", 1., "", "最大染色OD - 染色更深的像素将被忽略（默认= 1）")
				.addDoubleParameter("ignorePercentage", "忽略极值", 1., "%", "要忽略的极端像素的百分比，以提高在噪声/其他伪影存在时的稳健性（默认= 1）")
				.addBooleanParameter("checkColors", "排除不可识别的颜色（仅限H&E）", false, "排除可能由伪影引起而非真实染色的意外颜色（如绿色）");
//				.addDoubleParameter("ignorePercentage", "Ignore extrema", 1., "%", 0, 20, "Percentage of extreme pixels to ignore, to improve robustness in the presence of noise/other artefacts");
		
		Button btnAuto = new Button("自动");
		btnAuto.setOnAction(e -> {
				double minOD = params.getDoubleParameterValue("minStainOD");
				double maxOD = params.getDoubleParameterValue("maxStainOD");
				double ignore = params.getDoubleParameterValue("ignorePercentage");
				boolean checkColors = params.getBooleanParameterValue("checkColors") && stainsWrapper.getOriginalStains().isH_E(); // Only accept if H&E
				ignore = Math.max(0, Math.min(ignore, 100));
//				ColorDeconvolutionStains stains = estimateStains(imgFinal, stainsWrapper.getStains(), minOD, maxOD, ignore);
				try {
					ColorDeconvolutionStains stainsNew = EstimateStainVectors.estimateStains(img, stainsWrapper.getStains(), minOD, maxOD, ignore, checkColors);
					stainsWrapper.setStains(stainsNew);
				} catch (Exception e2) {
					Dialogs.showErrorMessage("Estimate stain vectors", e2);
					logger.error(e2.getMessage(), e2);
				}
		});
		
		ParameterPanelFX panelParams = new ParameterPanelFX(params);
//		panelParams.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		BorderPane panelAuto = new BorderPane();
//		panelAuto.setBorder(BorderFactory.createTitledBorder("Auto detect"));
		panelAuto.setCenter(panelParams.getPane());
		panelAuto.setBottom(btnAuto);

		var titledStainVectors = new TitledPane("染色向量", table);
		titledStainVectors.setCollapsible(false);
		panelSouth.setCenter(titledStainVectors);

		var titledAutoDetect = new TitledPane("自动检测", panelAuto);
		titledAutoDetect.setCollapsible(false);
		panelSouth.setBottom(titledAutoDetect);
		
		BorderPane panelMain = new BorderPane();
		panelMain.setCenter(panelPlots);
		panelMain.setBottom(panelSouth);
		
		if (Dialogs.builder()
				.title("可视化染色编辑器")
				.content(panelMain)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.showAndWait()
				.orElse(ButtonType.CANCEL)
				.equals(ButtonType.OK)) {
			return stainsWrapper.getStains();
		} else {
			stainsWrapper.resetStains();
			return stainsWrapper.getStains();
		}
	}
	
	
	
	private static String stainArrayAsString(final Locale locale, final StainVector stain, final String delimiter, final int nDecimalPlaces) {
		return GeneralTools.arrayToString(locale, stain.getArray(), delimiter, nDecimalPlaces);
	}

	
	
	static Node createScatterPanel(final ScatterPlot scatterPlot, final StainsWrapper stainsWrapper, AxisColor axisX, AxisColor axisY) {
		StainScatterPanel panelScatter = new StainScatterPanel(scatterPlot, stainsWrapper, axisX, axisY);
//		panelScatter.setPreferredSize(new Dimension(200, 200));
//		panelScatter.setMinimumSize(new Dimension(100, 100));
		panelScatter.setWidth(200);
		panelScatter.setHeight(200);
		
		GridPane pane = new GridPane();
		Label y = new Label(axisY.toString());
		y.setAlignment(Pos.CENTER_LEFT);
		y.setRotate(-90);
//		y.prefHeightProperty().bind(panelScatter.heightProperty());
//		Text y = new Text(axisY.toString());
		Group group = new Group();
		group.getChildren().add(y);
//		group.prefHeightProperty().bind(panelScatter.heightProperty());
		pane.add(group, 0, 0);
		
		pane.add(panelScatter, 1, 0);

		Label x = new Label(axisX.toString());
		x.setAlignment(Pos.CENTER);
		x.prefWidthProperty().bind(panelScatter.widthProperty());
		pane.add(x, 1, 1);
		
		return pane;
	}
	
	
	
	static interface StainChangeListener {
		
		public void stainChanged(final StainsWrapper stainsWrapper);
		
	}
	
	static class StainsWrapper {
		
		private List<StainChangeListener> listeners = new ArrayList<>();
		
		private ColorDeconvolutionStains stainsOrig;
		private ColorDeconvolutionStains stains;
		
		StainsWrapper(final ColorDeconvolutionStains stains) {
			this.stainsOrig = stains;
			this.stains = stains;
		}
		
		public ColorDeconvolutionStains getOriginalStains() {
			return stainsOrig;
		}
		
		public void addStainListener(final StainChangeListener listener) {
			this.listeners.add(listener);
		}

		public void removeStainListener(final StainChangeListener listener) {
			this.listeners.remove(listener);
		}

//		public void updateStains(final StainVector stain1, final StainVector stain2) {
//			stains = stains.changeStain(stain1, 1).changeStain(stain2, 2);
//		}
		
		public void changeStain(final StainVector stainOld, final StainVector stainNew) {
			int n = stains.getStainNumber(stainOld);
			if (n >= 1)
				setStains(stains.changeStain(stainNew, n));
		}
		
		public void setStains(final ColorDeconvolutionStains stainsNew) {
			this.stains = stainsNew;
			for (StainChangeListener l : listeners)
				l.stainChanged(this);
		}
		
		
		public void resetStains() {
			setStains(getOriginalStains());
		}
		
		public ColorDeconvolutionStains getStains() {
			return stains;
		}
		
	}
	
	
	
	static class StainScatterPanel extends Canvas implements StainChangeListener {
		
		private AxisColor xAxis, yAxis;
		
		private ScatterPlot plot;
		private StainsWrapper stainsWrapper;
		
		private StainVector stainEditing = null;
		private double handleSize = 5;
		
		private int padding = 2;
		
		private boolean constrainX = false;
		
		public StainScatterPanel(final ScatterPlot plot, final StainsWrapper stainsWrapper, final AxisColor xAxis, final AxisColor yAxis) {
			super();
			this.plot = plot;
			this.stainsWrapper = stainsWrapper;
			this.stainsWrapper.addStainListener(this);
			this.xAxis = xAxis;
			this.yAxis = yAxis;
			
			this.plot.setLimitsX(0, 1);
			this.plot.setLimitsY(0, 1);
			
			this.addEventHandler(MouseEvent.ANY, new MouseListener());
			
			updatePlot();
			
			 // Redraw canvas when size changes.
            widthProperty().addListener(e -> updatePlot());
            heightProperty().addListener(e -> updatePlot());

		}
		
//		public void setStains(final StainsWrapper stains) {
//			this.stains = stains;
//			repaint();
//		}
//
//		public ColorDeconvolutionStains getStains() {
//			return stains;
//		}
		
		private double componentXtoDataX(final double x) {
			double scaleX = (getWidth() - padding * 2) / (plot.getMaxX() - plot.getMinY());
			return (x - padding) / scaleX + plot.getMinX();
		}
		
		private double componentYtoDataY(final double y) {
			double scaleY = (getHeight() - padding * 2) / (plot.getMaxY() - plot.getMinY());
			// TODO: Check this!  I'm not sure why it (appears) to work... so may be incorrect
			return (padding - y)/scaleY + plot.getMaxX() + plot.getMinY();
		}
		
		private double dataXtoComponentX(final double x) {
			double scaleX = (getWidth() - padding * 2) / (plot.getMaxX() - plot.getMinY());
			return (x - plot.getMinX()) * scaleX + padding;
		}
		
		private double dataYtoComponentY(final double y) {
			double scaleY = (getHeight() - padding * 2) / (plot.getMaxY() - plot.getMinY());
			return (plot.getMaxY() - (y-plot.getMinY()))*scaleY + padding;
//			return (plot.getMaxY() - (y-plot.getMinY()))*scaleY + padding;
		}
		
		private Rectangle2D getRegion() {
			return new Rectangle2D(padding, padding, getWidth()-padding*2, getHeight()-padding*2);
		}
		
		private StainVector grabHandle(double x, double y) {
			ColorDeconvolutionStains stains = stainsWrapper == null ? null : stainsWrapper.getStains();
			if (stains == null)
				return null;
			
			StainVector s1 = stains.getStain(1);
			StainVector s2 = stains.getStain(2);
			double d1 = Point2D.distanceSq(x, y, dataXtoComponentX(getStainX(s1)), dataYtoComponentY(getStainY(s1)));
			double d2 = Point2D.distanceSq(x, y, dataXtoComponentX(getStainX(s2)), dataYtoComponentY(getStainY(s2)));
			if (d1 <= d2 && d1 <= handleSize*handleSize)
				return s1;
			if (d2 < d1 && d2 <= handleSize*handleSize)
				return s2;
			return null;
		}
		
		
		private double getStainX(final StainVector stain) {
			switch (xAxis) {
			case BLUE:
				return stain.getBlue();
			case GREEN:
				return stain.getGreen();
			case RED:
				return stain.getRed();
			}
			return Double.NaN;
		}
		
		private double getStainY(final StainVector stain) {
			switch (yAxis) {
			case BLUE:
				return stain.getBlue();
			case GREEN:
				return stain.getGreen();
			case RED:
				return stain.getRed();
			}
			return Double.NaN;
		}
		
		
		public boolean getConstrainX() {
			return constrainX;
		}
		
		public void setConstrainX(final boolean constrainX) {
			this.constrainX = constrainX;
		}

		
		public void updatePlot() {
			GraphicsContext g2d = getGraphicsContext2D();

			g2d.setFill(javafx.scene.paint.Color.WHITE);
			g2d.clearRect(0, 0, getWidth(), getHeight());
			
			if (getWidth() < padding*2 || getHeight() < padding*2)
				return;

			Rectangle2D region = getRegion();
			g2d.fillRect(padding, padding, getWidth()-padding*2, getHeight()-padding*2);
			plot.drawPlot(g2d, region);
			
			g2d.setLineWidth(1);
			g2d.setStroke(javafx.scene.paint.Color.GRAY);
			g2d.strokeRect(region.getMinX(), region.getMinY(), region.getWidth(), region.getHeight());

			
//			// Draw border
//			g2d.setStroke(javafx.scene.paint.Color.GRAY);
//			g2d.strokeRect(padding, padding, getWidth()-padding*2, getHeight()-padding*2);

			if (stainsWrapper == null)
				return;
			
//			// Draw labels
//			String s = xAxis.toString();
//			g2d.drawString(s, (getWidth()-fm.stringWidth(s))*.5f, getHeight()-fm.getHeight()*.5f);
//			s = yAxis.toString();
//			Graphics2D g2d2 = (Graphics2D)g2d.create();
//			
//			g2d2.translate(0, getHeight());
//			g2d2.rotate(-Math.PI/2);
//			g2d2.drawString(s, (getHeight() - fm.stringWidth(s))*.5f, fm.getHeight());
//			g2d2.dispose();
//			
//			BasicStroke stroke = new BasicStroke(3f);
//			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			g2d.setLineWidth(3);
			ColorDeconvolutionStains stains = stainsWrapper.getStains();
			for (int i = 1; i <= 3; i++) {
				StainVector stain = stains.getStain(i);
				
				if (stain.isResidual())
					continue;
				
				double x2 = getStainX(stain);
				double y2 = getStainY(stain);
				if (Double.isNaN(x2) || Double.isNaN(y2))
					continue;

				
				if (stain == stainEditing)
					g2d.setStroke(javafx.scene.paint.Color.YELLOW);
				else
					g2d.setStroke(ColorToolsFX.getCachedColor(stain.getColor()));
				g2d.strokeLine(
						dataXtoComponentX(0),
						dataYtoComponentY(0),
						dataXtoComponentX(x2),
						dataYtoComponentY(y2)
						);
				
				g2d.strokeOval(
						dataXtoComponentX(x2)-handleSize/2,
						dataYtoComponentY(y2)-handleSize/2,
						handleSize,
						handleSize
						);
			}
			
		}
		
		
		class MouseListener implements EventHandler<MouseEvent> {

			@Override
			public void handle(MouseEvent event) {
				if (event.getEventType() == MouseEvent.MOUSE_DRAGGED)
					handleMouseDragged(event);
				else if (event.getEventType() == MouseEvent.MOUSE_PRESSED)
					handleMousePressed(event);
				else if (event.getEventType() == MouseEvent.MOUSE_RELEASED)
					handleMouseReleased(event);
			}
			
		}
		

		private void handleMouseDragged(MouseEvent e) {
			if (stainEditing == null)
				return;
			
			double x = componentXtoDataX(e.getX());
			double y = componentYtoDataY(e.getY());
			
			double r = stainEditing.getRed();
			double g = stainEditing.getGreen();
			double b = stainEditing.getBlue();
			switch (yAxis) {
			case BLUE:
				b = y;
				break;
			case GREEN:
				g = y;
				break;
			case RED:
				r = y;
				break;
			}
			// Only allow the x-value to update if it is not constrained
			if (!constrainX) {
				switch (xAxis) {
				case BLUE:
					b = x;
					break;
				case GREEN:
					g = x;
					break;
				case RED:
					r = x;
					break;
				}
			}
			StainVector stainNew = StainVector.createStainVector(
					stainEditing.getName(), r, g, b);
			stainsWrapper.changeStain(stainEditing, stainNew);
			stainEditing = stainNew;
			
//			logger.debug(String.format("x=%.2f, y=%.2f", x, y));
			
			updatePlot();
		}

		void handleMousePressed(MouseEvent e) {
			stainEditing = grabHandle(e.getX(), e.getY());
			if (stainEditing != null)
				logger.debug("Editing stain vector: " + stainEditing);
			updatePlot();
		}

		void handleMouseReleased(MouseEvent e) {
			if (stainEditing == null)
				return;
			logger.info("Updated stain vector: {}", stainEditing);
			stainEditing = null;
			updatePlot();
		}

		@Override
		public void stainChanged(StainsWrapper stainsWrapper) {
			updatePlot();
		}
		
	}
	
	
	
	
	static class ScatterPlot {
		
		private static Color DEFAULT_COLOR_DRAW = ColorToolsFX.getColorWithOpacity(Color.RED, 0.1);
//		private static Color DEFAULT_COLOR_FILL = null; //DisplayHelpers.getColorWithOpacity(Color.RED, 0.5);
		
		private double[] x;
		private double[] y;
		private double minX, maxX, minY, maxY;
		
		private boolean correlationsCalculated = false;
		private double pearsonsCorrelation = Double.NaN;
		private double spearmansCorrelation = Double.NaN;
		
		private Color[] colorDraw = null;
		private Color[] colorFill = null;
		
		private boolean fillMarkers = false;
		private double markerSize = 3;
		
		public ScatterPlot(double[] x, double[] y) {
			this(x, y, null, null);
		}

		public ScatterPlot(float[] x, float[] y) {
			this(x, y, null, null);
		}

		public ScatterPlot(float[] x, float[] y, int[] colorDraw, int[] colorFill) {
			this(toDouble(x), toDouble(y), colorDraw, colorFill);
		}
		
		
		private void compute(double[] x, double[] y) {
			this.x = x;
			this.y = y;
			
			minX = Double.POSITIVE_INFINITY;
			maxX = Double.NEGATIVE_INFINITY;
			for (double v : x) {
				if (v > maxX)
					maxX = v;
				else if (v < minX)
					minX = v;
			}
			
			minY = Double.POSITIVE_INFINITY;
			maxY = Double.NEGATIVE_INFINITY;
			for (double v : y) {
				if (v > maxY)
					maxY = v;
				else if (v < minY)
					minY = v;
			}
		}
		
		
		private void setColors(int[] colorDraw, int[] colorFill) {
			// Set the colors
			this.colorDraw = new Color[x.length];		
			if (colorDraw == null || colorDraw.length == 0) {
				if (colorFill == null)
					Arrays.fill(this.colorDraw, DEFAULT_COLOR_DRAW);
				else
					Arrays.fill(this.colorDraw, null);
			}
			else if (colorDraw.length == 1) {
				Arrays.fill(this.colorDraw, ColorToolsAwt.getCachedColor(colorDraw[0], ColorTools.alpha(colorDraw[0]) != 0));
			} else {
				for (int i = 0; i < Math.min(this.colorDraw.length, colorDraw.length); i++) {
					this.colorDraw[i] = ColorToolsFX.getCachedColor(colorDraw[i], ColorTools.alpha(colorFill[i]) != 0);
				}
			}

			this.colorFill = new Color[x.length];		
			fillMarkers = true;
			if (colorFill == null || colorFill.length == 0) {
				fillMarkers = false;
			}
			else if (colorFill.length == 1)
				Arrays.fill(this.colorFill, ColorToolsFX.getCachedColor(colorFill[0], false));
//				Arrays.fill(this.colorFill, ColorToolsFX.getCachedColor(colorFill[0], ColorTools.alpha(colorFill[0]) != 0));
			else {
				this.colorFill = new Color[x.length];
				for (int i = 0; i < Math.min(this.colorFill.length, colorFill.length); i++) {
					// TODO: Check what to do with alpha...?
					this.colorFill[i] = ColorToolsFX.getCachedColor(colorFill[i], false);
//					this.colorFill[i] = ColorToolsFX.getCachedColor(colorFill[i], ColorTools.alpha(colorFill[i]) != 0);
				}
			}
		}
		
		
		static double[][] removeNaNs(final double[] x, final double[] y) {
			double[] x2 = new double[x.length];
			double[] y2 = new double[y.length];
			int k = 0;
			for (int i = 0; i < x.length; i++) {
				double xx = x[i];
				double yy = y[i];
				if (Double.isNaN(xx) || Double.isNaN(yy))
					continue;
				x2[k] = xx;
				y2[k] = yy;
				k++;
			}
			if (k < x.length) {
				x2 = Arrays.copyOf(x2, k);
				y2 = Arrays.copyOf(y2, k);
			}
			return new double[][]{x2, y2};
		}
		
		
		private void calculateCorrelations() {
			double[][] denaned = removeNaNs(x, y);
			if (denaned[0].length > 0) {
				pearsonsCorrelation = new PearsonsCorrelation().correlation(denaned[0], denaned[1]);
				spearmansCorrelation = new SpearmansCorrelation().correlation(denaned[0], denaned[1]);
			}
			correlationsCalculated = true;
		}
		
		public double getPearsonsCorrelation() {
			if (!correlationsCalculated)
				calculateCorrelations();
			return pearsonsCorrelation;
		}

		
		public double getSpearmansCorrelation() {
			if (!correlationsCalculated)
				calculateCorrelations();
			return spearmansCorrelation;
		}

		
		public ScatterPlot(double[] x, double[] y, int[] colorDraw, int[] colorFill) {
			compute(x, y);
			setColors(colorDraw, colorFill);
		}
		
		
//		public static float[] toFloat(final double[] arr) {
//			float[] arr2 = new float[arr.length];
//			for (int i = 0; i < arr.length; i++)
//				arr2[i] = (float)arr[i];
//			return arr2;
//		}
		
		public static double[] toDouble(final float[] arr) {
			double[] arr2 = new double[arr.length];
			for (int i = 0; i < arr.length; i++)
				arr2[i] = arr[i];
			return arr2;
		}
		
		public void setLimitsX(final double minX, final double maxX) {
			this.minX = (float)minX;
			this.maxX = (float)maxX;
		}
		
		public void setLimitsY(final double minY, final double maxY) {
			this.minY = (float)minY;
			this.maxY = (float)maxY;
		}
		
		
		public double getMinX() {
			return minX;
		}

		public double getMinY() {
			return minY;
		}
		
		public double getMaxX() {
			return maxX;
		}

		public double getMaxY() {
			return maxY;
		}
		
		public double getMarkerSize() {
			return markerSize;
		}
		
		public void setMarkerSize(final double markerSize) {
			this.markerSize = markerSize;
		}
		
		public boolean getFillMarkers() {
			return fillMarkers;
		}
		
		public void setFillMarkers(final boolean doFill) {
			this.fillMarkers = doFill;
		}

		public void drawPlot(GraphicsContext g, Rectangle2D region) {
			drawPlot(g, region, 10000);
		}

		
		public void drawPlot(GraphicsContext g, Rectangle2D region, int maxPoints) {
			
			g.save();
			
			g.beginPath();
			g.moveTo(region.getMinX(), region.getMinY());
			g.lineTo(region.getMaxX(), region.getMinY());
			g.lineTo(region.getMaxX(), region.getMaxY());
			g.lineTo(region.getMinX(), region.getMaxY());
			g.closePath();
			g.clip();
			
//			int pad = 10;
			double scaleX = region.getWidth() / (maxX - minX);
			double scaleY = region.getHeight() / (maxY - minY);

			g.setLineWidth(1.5f);
			
//			g.setStroke(javafx.scene.paint.Color.GRAY);
//			g.strokeRect(region.getX(), region.getY(), region.getWidth(), region.getHeight());
			
			g.translate(region.getMinX(), region.getMinY());

//			g2d.drawLine(0, 0, 0, region.height);
//			g2d.drawLine(0, region.height, region.width, region.height);
			
			double increment;
			if (maxPoints < 0 || x.length <= maxPoints)
				increment = 1;
			else
				increment = (double)x.length / maxPoints;
			
			for (double i = 0; i < x.length; i += increment) {
				int ind = (int)i;
				double xx = x[ind];
				double yy = y[ind];
//				// Skip if out of range
//				if (xx < minX || xx > maxX || yy < minY || yy > maxY)
//					continue;
				
				double xo = (xx-minX)*scaleX-markerSize/2;
				double yo = region.getHeight() - (yy-minY)*scaleY-markerSize/2;
				
				Color cDraw = colorDraw == null ? null : colorDraw[ind];
				if (fillMarkers) {
					Color cFill = colorFill[ind] == null ? cDraw : colorFill[ind];
					if (cFill != null) {
						g.setFill(cFill);
						g.fillOval(xo, yo, markerSize, markerSize);
						// Don't need to draw if it would be the same color anyway
						if (cFill == cDraw)
							continue;
					}				
				}
				if (cDraw != null) {
					g.setStroke(cDraw);
					g.strokeOval(xo, yo, markerSize, markerSize);
				}
			}
			
			g.restore();
		}
		
		
	}

}
