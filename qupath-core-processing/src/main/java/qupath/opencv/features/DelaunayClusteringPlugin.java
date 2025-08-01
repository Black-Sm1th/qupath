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

package qupath.opencv.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.TaskRunner;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Plugin for calculating Delaunay clustering, and associated features.
 * <p>
 * Warning! Because the implementation will have to change in the future, it is best not to rely on this class!
 * 
 * @author Pete Bankhead
 * @param <T> 
 * @deprecated v0.6.0 to be replaced by {@link qupath.lib.analysis.DelaunayTools}.
 *             See https://github.com/qupath/qupath/issues/1590 for discussion of the problems with this command.
 */
@Deprecated
public class DelaunayClusteringPlugin<T> extends AbstractInteractivePlugin<T> {

	private static final Logger logger = LoggerFactory.getLogger(DelaunayClusteringPlugin.class);
	
	/**
	 * Constructor.
	 */
	public DelaunayClusteringPlugin() {
		super();
	}	
	
	@Override
	protected void preprocess(TaskRunner taskRunner, ImageData<T> imageData) {
		super.preprocess(taskRunner, imageData);
		// Reset any previous connections
		imageData.removeProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
	}
	
	@Override
	protected void postprocess(TaskRunner taskRunner, ImageData<T> imageData) {
		super.postprocess(taskRunner, imageData);
		imageData.getHierarchy().fireHierarchyChangedEvent(this);
	}
	

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(TMACoreObject.class, PathAnnotationObject.class, PathRootObject.class);
	}

	@Override
	public String getName() {
		return "Delaunay聚类";
	}

	@Override
	public String getDescription() {
		return "对邻近对象进行聚类，可选择按分类和/或距离限制";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		ParameterList params = new ParameterList()
				.addDoubleParameter("distanceThreshold", "距离阈值", 0, "像素", "距离阈值 - 长度超过此值的边将被省略")
				.addDoubleParameter("distanceThresholdMicrons", "距离阈值", 0, GeneralTools.micrometerSymbol(), "距离阈值 - 长度超过此值的边将被省略")
				.addBooleanParameter("limitByClass", "限制为相同类别", false, "防止边连接具有不同基本分类的对象")
				.addBooleanParameter("addClusterMeasurements", "添加聚类测量", false, "添加从连接对象的聚类中派生的测量")
				;
		
		ImageServer<?> server = imageData.getServer();
		boolean hasMicrons = server != null && server.getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(hasMicrons, "distanceThreshold");
		params.setHiddenParameters(!hasMicrons, "distanceThresholdMicrons");
		return params;
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(ImageData<T> imageData) {
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		
		List<PathObject> selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		if (selected.isEmpty()) {
			logger.trace("Creating task for the root object");
			return Collections.singletonList(hierarchy.getRootObject());
		} else {
			logger.trace("Creating tasks for {} parent objects", selected.size());
			return selected;
		}
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
		
		// Get pixel sizes, if possible
		ImageServer<?> server = imageData.getServer();
		double pixelWidth = 1, pixelHeight = 1;
		PixelCalibration cal = server.getPixelCalibration();
		boolean hasMicrons = server != null && cal.hasPixelSizeMicrons();
		if (hasMicrons) {
			pixelWidth = cal.getPixelWidthMicrons();
			pixelHeight = cal.getPixelHeightMicrons();
		}
		double distanceThresholdPixels;
		if (cal.hasPixelSizeMicrons())
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThresholdMicrons") / cal.getAveragedPixelSizeMicrons();
		else
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThreshold");


		tasks.add(new DelaunayRunnable(
				imageData,
				parentObject,
				params.getBooleanParameterValue("addClusterMeasurements"),
				pixelWidth,
				pixelHeight,
				distanceThresholdPixels,
				params.getBooleanParameterValue("limitByClass")
				));
	}
	
	
	
	
	private static class DelaunayRunnable implements PathTask {
		
		private ImageData<?> imageData;
		private PathObject parentObject;
		private double pixelWidth;
		private double pixelHeight;
		private double distanceThresholdPixels;
		
		private boolean addClusterMeasurements;
		private boolean limitByClass;
		
		private PathObjectConnectionGroup result;
		
		private String lastResult = null;
		
		DelaunayRunnable(final ImageData<?> imageData, final PathObject parentObject, final boolean addClusterMeasurements, final double pixelWidth, final double pixelHeight, final double distanceThresholdPixels, final boolean limitByClass) {
			this.imageData = imageData;
			this.parentObject = parentObject;
			this.pixelWidth = pixelWidth;
			this.pixelHeight = pixelHeight;
			this.addClusterMeasurements = addClusterMeasurements;
			this.distanceThresholdPixels = distanceThresholdPixels;
			this.limitByClass = limitByClass;
		}

		
		@Override
		public void run() {
			
			List<PathObject> pathObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false);
			pathObjects = pathObjects.stream().filter(p -> p.isDetection()).toList();
			if (pathObjects.isEmpty()) {
				lastResult = "No detection descendant objects for " + parentObject;
				return;
			}
			
			DelaunayTriangulation dt = new DelaunayTriangulation(pathObjects, pixelWidth, pixelHeight, distanceThresholdPixels, limitByClass);
			
			DefaultPathObjectConnectionGroup result = new DefaultPathObjectConnectionGroup(dt);
			pathObjects = new ArrayList<>(result.getPathObjects());
			
			dt.addNodeMeasurements();
			if (addClusterMeasurements)
				dt.addClusterMeasurements();
			
			
			this.result = result;
			
			lastResult = "Delaunay triangulation calculated for " + parentObject;
		}
		

		@Override
		public void taskComplete(boolean wasCancelled) {
			if (wasCancelled)
				return;
			
			if (result != null && imageData != null) {
				synchronized(imageData) {
					Object o = imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
					PathObjectConnections connections = null;
					if (o instanceof PathObjectConnections)
						connections = (PathObjectConnections)o;
					else {
						connections = new PathObjectConnections();
						imageData.setProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS, connections);
					}
					connections.addGroup(result);
				}
			}
		}

		@Override
		public String getLastResultsDescription() {
			return lastResult;
		}
		
		
	}

}
