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

package qupath.lib.gui.viewer;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;

/**
 * A small preview panel to be associated with a viewer, which shows the currently-visible
 * region &amp; can be clicked on to navigate to other regions.
 * 
 * @author Pete Bankhead
 *
 */
class ImageOverview implements QuPathViewerListener {

	private static final Logger logger = LoggerFactory.getLogger(ImageOverview.class);

	private QuPathViewer viewer;
	
	private Canvas canvas = new Canvas();
		
	private boolean repaintRequested = false;

	/*
	 * There are two reasons for the following variables...
	 * 	1 - The viewer's thumbnail is not guaranteed to remain constant, even if the image server does -
	 * 		this is because it can be modified by color transforms, brightness/contrast settings
	 * 	2 - Simply drawing the image directly & rescaling on-the-fly produces a very low-quality image -
	 * 		getScaledInstance() provides something smoother
	 * Therefore to assist repainting, we need to have:
	 * 	- a nicely-rescaled thumbnail to draw
	 * 	- the original thumbnail image used to produce the scaled version
	 * The latter means we can compare it with the viewer's thumbnail & we know if it needs to be updated
	 */
	private BufferedImage imgLastThumbnail; // The last thumbnail the viewer gave us
	private WritableImage imgPreview;       // The (probably downsampled) preview version

	private int preferredWidth = 150; // Preferred component/image width - used for thumbnail scaling

	private Shape shapeVisible = null; // The visible shape (transformed already)
	private AffineTransform transform;
	
	private static Color color = Color.rgb(0, 0, 0, 0.08);
	private static Color colorBorder = Color.rgb(255, 255, 255, 0.2);

	protected void mouseViewerToLocation(double x, double y) {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null)
			return;
		double cx = x / getWidth() * server.getWidth();
		double cy = y / getHeight() * server.getHeight();
		viewer.setCenterPixelLocation(cx, cy);
	}

	public ImageOverview(final QuPathViewer viewer) {
		this.viewer = viewer;
		setImage(viewer.getRGBThumbnail());
		
		canvas.setOnMouseClicked(e -> {
			// TODO: Check focus situation
//			// Pass focus to viewer if required - use first click for focus, not moving yet
//			if (viewer != null && viewer.isAncestorOf(ImageOverview.this) && !viewer.hasFocus())
//				viewer.requestFocus();
//			else
			mouseViewerToLocation(e.getX(), e.getY());
		});
		
		canvas.setOnMouseDragged(e -> {
			mouseViewerToLocation(e.getX(), e.getY());
			e.consume();
		});
		
		viewer.zPositionProperty().addListener(v -> repaint());
		viewer.tPositionProperty().addListener(v -> repaint());
			
		viewer.addViewerListener(this);
	}

	private void updateTransform() {
		if (imgPreview != null && viewer != null && viewer.getServer() != null) {
			double scale = imgPreview.getWidth() / viewer.getServer().getWidth();
			if (scale > 0) {
				// Reuse an existing transform if we have one
				if (transform == null)
					transform = AffineTransform.getScaleInstance(scale, scale);
				else
					transform.setToScale(scale, scale);
			}
			else
				transform = null;
		}
	}

	void paintCanvas() {
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = getWidth();
		double h = getHeight();
		
		// 清除整个画布
		g.clearRect(0, 0, w, h);
		
		if (viewer == null || !viewer.hasServer()) {
			return;
		}
		
		// 设置圆角裁剪区域
		g.save();
		g.beginPath();
		g.moveTo(12, 0);
		g.lineTo(w-12, 0);
		g.quadraticCurveTo(w, 0, w, 12);
		g.lineTo(w, h-12);
		g.quadraticCurveTo(w, h, w-12, h);
		g.lineTo(12, h);
		g.quadraticCurveTo(0, h, 0, h-12);
		g.lineTo(0, 12);
		g.quadraticCurveTo(0, 0, 12, 0);
		g.closePath();
		g.clip();
		
		// 确保图像已设置
		setImage(viewer.getRGBThumbnail());
		g.drawImage(imgPreview, 0, 0);
		
		// 创建遮罩路径
		g.beginPath();
		g.moveTo(12, 0);
		g.lineTo(w-12, 0);
		g.quadraticCurveTo(w, 0, w, 12);
		g.lineTo(w, h-12);
		g.quadraticCurveTo(w, h, w-12, h);
		g.lineTo(12, h);
		g.quadraticCurveTo(0, h, 0, h-12);
		g.lineTo(0, 12);
		g.quadraticCurveTo(0, 0, 12, 0);
		g.closePath();
		
		if (shapeVisible != null) {
			// 获取可见区域的边界
			java.awt.Rectangle bounds = shapeVisible.getBounds();
			g.moveTo(bounds.getX() + 12, bounds.getY());
			g.lineTo(bounds.getX() + bounds.getWidth() - 12, bounds.getY());
			g.quadraticCurveTo(bounds.getX() + bounds.getWidth(), bounds.getY(), 
				bounds.getX() + bounds.getWidth(), bounds.getY() + 12);
			g.lineTo(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight() - 12);
			g.quadraticCurveTo(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight(),
				bounds.getX() + bounds.getWidth() - 12, bounds.getY() + bounds.getHeight());
			g.lineTo(bounds.getX() + 12, bounds.getY() + bounds.getHeight());
			g.quadraticCurveTo(bounds.getX(), bounds.getY() + bounds.getHeight(),
				bounds.getX(), bounds.getY() + bounds.getHeight() - 12);
			g.lineTo(bounds.getX(), bounds.getY() + 12);
			g.quadraticCurveTo(bounds.getX(), bounds.getY(),
				bounds.getX() + 12, bounds.getY());
			g.closePath();
		}
		
		// 使用EVEN_ODD规则填充遮罩
		g.setFillRule(FillRule.EVEN_ODD);
		g.setFill(Color.rgb(50, 50, 50, 0.2));
		g.fill();
		
		// 绘制可见区域边框
		if (shapeVisible != null) {
			g.setStroke(color);
			g.setLineWidth(1);
			
			// 获取可见区域的边界
			java.awt.Rectangle bounds = shapeVisible.getBounds();
			g.beginPath();
			g.moveTo(bounds.getX() + 12, bounds.getY());
			g.lineTo(bounds.getX() + bounds.getWidth() - 12, bounds.getY());
			g.quadraticCurveTo(bounds.getX() + bounds.getWidth(), bounds.getY(), 
				bounds.getX() + bounds.getWidth(), bounds.getY() + 12);
			g.lineTo(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight() - 12);
			g.quadraticCurveTo(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight(),
				bounds.getX() + bounds.getWidth() - 12, bounds.getY() + bounds.getHeight());
			g.lineTo(bounds.getX() + 12, bounds.getY() + bounds.getHeight());
			g.quadraticCurveTo(bounds.getX(), bounds.getY() + bounds.getHeight(),
				bounds.getX(), bounds.getY() + bounds.getHeight() - 12);
			g.lineTo(bounds.getX(), bounds.getY() + 12);
			g.quadraticCurveTo(bounds.getX(), bounds.getY(),
				bounds.getX() + 12, bounds.getY());
			g.stroke();
		}
		
		// 绘制外边框
		g.setLineWidth(2);
		g.setStroke(colorBorder);
		g.beginPath();
		g.moveTo(12, 0);
		g.lineTo(w-12, 0);
		g.quadraticCurveTo(w, 0, w, 12);
		g.lineTo(w, h-12);
		g.quadraticCurveTo(w, h, w-12, h);
		g.lineTo(12, h);
		g.quadraticCurveTo(0, h, 0, h-12);
		g.lineTo(0, 12);
		g.quadraticCurveTo(0, 0, 12, 0);
		g.stroke();
		
		// 重置裁剪区域
		g.restore();
		
		repaintRequested = false;
	}

	
	public boolean isVisible() {
		return canvas.isVisible();
	}
	
	public void setVisible(final boolean visible) {
		canvas.setVisible(visible);
	}
	
	private double getWidth() {
		return canvas.getWidth();
	}

	private double getHeight() {
		return canvas.getHeight();
	}


	private void setImage(BufferedImage img) {
		if (img == imgLastThumbnail)
			return;
		if (img == null) {
			imgLastThumbnail = null;
		} else {
			int preferredHeight = (int)(img.getHeight() * (double)(preferredWidth / (double)img.getWidth()));
			
			imgPreview = GuiTools.getScaledRGBInstance(img, preferredWidth, preferredHeight);

			canvas.setWidth(imgPreview.getWidth());
			canvas.setHeight(imgPreview.getHeight());

			imgLastThumbnail = img;
		}
		updateTransform();
	}


	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImage(viewer.getRGBThumbnail());
		repaint();
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
		// Get the shape & apply a transform, if we have one
		if (shape != null) {
			if (transform == null)
				updateTransform();
			if (transform != null){
				shapeVisible = transform.createTransformedShape(shape);
			}
			else
				shapeVisible = shape;
		} else
			shapeVisible = null;
		// Repaint
		repaint();
	}


	
	void repaint() {
		if (Platform.isFxApplicationThread()) {
			repaintRequested = true;
			paintCanvas();
			return;
		}
		if (repaintRequested)
			return;
		logger.trace("Overview repaint requested!");
		repaintRequested = true;
		Platform.runLater(() -> repaint());
	}
	

	public Node getNode() {
		return canvas;
	}
	


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewer = null;
	}

}