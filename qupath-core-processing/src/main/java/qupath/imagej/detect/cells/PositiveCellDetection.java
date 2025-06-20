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

package qupath.imagej.detect.cells;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;

/**
 * Alternative implementation of {@link WatershedCellDetection} that automatically applies 1 or 3 intensity thresholds to classify cells.
 * 
 * @author Pete Bankhead
 *
 */
public class PositiveCellDetection extends WatershedCellDetection {
	
	/**
	 * Default constructor.
	 */
	public PositiveCellDetection() {
		super();
	}
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		if (parametersInitialized)
			return super.getDefaultParameterList(imageData);
		else {
			super.getDefaultParameterList(imageData);
			params.addTitleParameter("强度阈值参数");
			var stains = imageData.getColorDeconvolutionStains();
			Set<String> channels = new LinkedHashSet<>();
			if (stains != null) {
				for (int i = 1; i <= 3; i++) {
					var stain = stains.getStain(i);
					if (!ColorDeconvolutionStains.isHematoxylin(stain) && !stain.isResidual())
						channels.add(stain.getName() + " OD");
				}
			} else {
				var server = imageData.getServer();
				for (var channel : server.getMetadata().getChannels())
					channels.add(channel.getName());
			}
			List<String> choices = new ArrayList<>();
			for (var channel : channels) {
				choices.add("Nucleus: " + channel + " mean");
				choices.add("Nucleus: " + channel + " max");
				choices.add("Cytoplasm: " + channel + " mean");
				choices.add("Cytoplasm: " + channel + " max");
				choices.add("Cell: " + channel + " mean");
				choices.add("Cell: " + channel + " max");
			}
			var server = imageData.getServer();
			var type = server.getMetadata().getPixelType();
			// Determine appropriate starting thresholds & maxima
			double t1 = 0.2;
			double tMax = 1.5;
			if (stains == null) {
				if (!type.isFloatingPoint()) {
					if (type.getBytesPerPixel() <= 1)
						t1 = 10;
					else
						t1 = 100;
					tMax = Math.min(10000, Math.pow(2, type.getBitsPerPixel()) - 1);
				}
			}
			params.addChoiceParameter("thresholdCompartment", "评分区间", choices.get(0), choices, "选择要阈值化的强度测量");
//			params.addChoiceParameter("thresholdCompartment", "评分区间", "Nucleus: DAB OD mean",
//					Arrays.asList("Nucleus: DAB OD mean", "Nucleus: DAB OD max",
//							"Cytoplasm: DAB OD mean", "Cytoplasm: DAB OD max",
//							"Cell: DAB OD mean", "Cell: DAB OD max"));
			params.addDoubleParameter("thresholdPositive1", "阈值 1+", t1, null, 0, tMax, "低阳性强度阈值");
			params.addDoubleParameter("thresholdPositive2", "阈值 2+", t1*2, null, 0, tMax, "中度阳性强度阈值");
			params.addDoubleParameter("thresholdPositive3", "阈值 3+", t1*3, null, 0, tMax, "高阳性强度阈值");
			params.addBooleanParameter("singleThreshold", "单一阈值", true);
		}
		return params;
	}
	
	@Override
	public String getName() {
		return "阳性细胞检测";
	}
	
	
	/**
	 * Wrap the detector to apply any required classification.
	 */
	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		ObjectDetector<BufferedImage> detector = super.createDetector(imageData, params);
		return new DetectorWrapper<>(detector);
	}
	
	
	
	static class DetectorWrapper<T> implements ObjectDetector<T> {
		
		private ObjectDetector<T> detector;
			
		public DetectorWrapper(ObjectDetector<T> detector) {
			this.detector = detector;
		}

		@Override
		public Collection<PathObject> runDetection(ImageData<T> imageData, ParameterList params, ROI pathROI) throws IOException {
			Collection<PathObject> detections = detector.runDetection(imageData, params, pathROI);
			// Apply intensity classifications
			String measurement = (String)params.getChoiceParameterValue("thresholdCompartment");
			double threshold1 = params.getDoubleParameterValue("thresholdPositive1");
			double threshold2 = params.getDoubleParameterValue("thresholdPositive2");
			double threshold3 = params.getDoubleParameterValue("thresholdPositive3");
			boolean singleThreshold = params.getBooleanParameterValue("singleThreshold");
			for (PathObject pathObject : detections) {
				double val = pathObject.getMeasurementList().get(measurement);
				if (singleThreshold) {
					if (val >= threshold1) {
						pathObject.setPathClass(PathClass.getPositive(pathObject.getPathClass()));
					} else {
						pathObject.setPathClass(PathClass.getNegative(pathObject.getPathClass()));
					}
				} else {
					if (val >= threshold3) {
						pathObject.setPathClass(PathClass.getThreePlus(pathObject.getPathClass()));
					} else if (val >= threshold2){
						pathObject.setPathClass(PathClass.getTwoPlus(pathObject.getPathClass()));
					} else if (val >= threshold1){
						pathObject.setPathClass(PathClass.getOnePlus(pathObject.getPathClass()));
					} else
						pathObject.setPathClass(PathClass.getNegative(pathObject.getPathClass()));
				}
			}
			return detections;
		}

		@Override
		public String getLastResultsDescription() {
			return detector.getLastResultsDescription();
		}
		
	}

}