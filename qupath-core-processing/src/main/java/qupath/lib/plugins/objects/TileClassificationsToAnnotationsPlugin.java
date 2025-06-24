/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.plugins.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to merge classified tiles into annotation objects.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class TileClassificationsToAnnotationsPlugin<T> extends AbstractDetectionPlugin<T> {
	
	private static Logger logger = LoggerFactory.getLogger(TileClassificationsToAnnotationsPlugin.class);
	
	private ParameterList params;
	private boolean parametersInitialized = false;
		
	

	@Override
	public String getName() {
		return "图块分类转为标注";
	}

	@Override
	public String getDescription() {
		return "从已分类的图块创建标注";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		Collection<Class<? extends PathObject>> parentClasses = new ArrayList<>();
		parentClasses.add(TMACoreObject.class);
		parentClasses.add(PathAnnotationObject.class);
		parentClasses.add(PathRootObject.class);
		return parentClasses;
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {
		ParameterList params = getParameterList(imageData);
		if (params.getBooleanParameterValue("clearAnnotations")) {
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			Collection<PathObject> annotations = PathObjectTools.getDescendantObjects(parentObject, null, PathAnnotationObject.class);
			hierarchy.removeObjects(annotations, true);
		}
		tasks.add(new ClassificationToAnnotationRunnable(params, imageData, parentObject));
	}
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		if (!parametersInitialized) {
			Set<PathClass> pathClasses = PathObjectTools.getRepresentedPathClasses(imageData.getHierarchy(), PathTileObject.class);
			List<PathClass> choices = new ArrayList<>(pathClasses);
			Collections.sort(choices, new Comparator<>() {

                @Override
                public int compare(PathClass pc1, PathClass pc2) {
                    return pc1.getName().compareTo(pc2.getName());
                }

            });
			PathClass allClasses = PathClass.getInstance("All classes");
			PathClass defaultChoice = allClasses;
			choices.add(0, allClasses);
//			PathClass classTumor = PathClassFactory.getDefaultPathClass(PathClasses.TUMOR); // Tumor is the most likely choice, so default to it if available
//			PathClass defaultChoice = choices.contains(classTumor) ? classTumor : choices.get(0);
			params = new ParameterList();
			
			params.addChoiceParameter("pathClass", "选择类别", defaultChoice, choices, "选择要从中创建标注的路径类别")
					.addBooleanParameter("deleteTiles", "删除现有子对象", false, "删除用于创建标注的图块 - 删除后将无法进行进一步训练")
					.addBooleanParameter("clearAnnotations", "移除现有标注", true, "移除所有现有标注（如果它们用于训练分类器但不再需要，这通常是个好主意）")
					.addBooleanParameter("splitAnnotations", "拆分新标注", false, "将新创建的标注拆分为不同的区域（而不是一个可能不连续的大对象）");
	//				.addDoubleParameter("simplify", "Simplify shapes", 0);
		}
		return params;
	}

	@Override
	protected Collection<PathObject> getParentObjects(final ImageData<T> imageData) {
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		List<PathObject> parents = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());

		// Deal with nested objects - the code is clumsy, but the idea is to take the highest
		// object in the hierarchy in instances where tiles are nested within other objects
		List<PathObject> tempList = new ArrayList<>(parents);
		for (PathObject temp : tempList) {
			Iterator<PathObject> iter = parents.iterator();
			while (iter.hasNext()) {
				if (PathObjectTools.isAncestor(iter.next(), temp))
					iter.remove();
			}
		}
		
		
		return parents;
	}
	
	
	
	
	static class ClassificationToAnnotationRunnable implements PathTask {
		
		private ParameterList params;
		private PathObject parentObject;
		private ImageData<?> imageData;
		private List<PathObject> pathAnnotations = new ArrayList<>();
		private String resultsString;
		
		public ClassificationToAnnotationRunnable(final ParameterList params, final ImageData<?> imageData, final PathObject parentObject) {
			this.params = params;
			this.parentObject = parentObject;
			this.imageData = imageData;
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			
			PathClass choice = (PathClass)params.getChoiceParameterValue("pathClass");
			Collection<PathClass> pathClasses = "All classes".equals(choice.getName()) ? 
					parentObject.getChildObjects().stream().map(p -> p.getPathClass()).collect(Collectors.toSet()) : 
						Collections.singletonList(choice);
			
			boolean doSplit = params.getBooleanParameterValue("splitAnnotations");
			boolean deleteTiles = params.getBooleanParameterValue("deleteTiles");

			for (PathClass pathClass : pathClasses) {
				PathObject pathSingleAnnotation = null;
				List<PathObject> tiles = new ArrayList<>();
				if (pathClass != null && !PathClassTools.isIgnoredClass(pathClass)) {
					List<ROI> roisToMerge = new ArrayList<>();
					for (PathObject pathObject : parentObject.getChildObjectsAsArray()) {
						if ((pathObject instanceof PathTileObject) && (RoiTools.isShapeROI(pathObject.getROI())) && pathClass.equals(pathObject.getPathClass())) {
							roisToMerge.add(pathObject.getROI());
							tiles.add(pathObject);
						}
					}
					if (!tiles.isEmpty()) {
						ROI pathROINew = RoiTools.union(roisToMerge);
						pathSingleAnnotation = PathObjects.createAnnotationObject(pathROINew, pathClass);
						if (!deleteTiles)
							pathSingleAnnotation.addChildObjects(tiles);
					}
				}
				
				if (pathSingleAnnotation == null) {
					continue;
				}
				
				// Split if necessary
				if (doSplit) {
					RoiTools.splitROI(pathSingleAnnotation.getROI())
							.stream()
							.map(p -> PathObjects.createAnnotationObject(p, pathClass))
							.forEach(pathAnnotations::add);
				}
				else {
					pathAnnotations.add(pathSingleAnnotation);
				}
			}
			
			if (resultsString == null) {
				if (pathAnnotations.isEmpty())
					resultsString = "未创建标注！";
				else if (pathAnnotations.size() == 1)
					resultsString = "已创建1个标注";
				else
					resultsString = "已创建" + pathAnnotations.size() + "个标注";
			}
			
			long endTime = System.currentTimeMillis();
			logger.info(parentObject + String.format(" processed in %.2f seconds", (endTime-startTime)/1000.));
		}
		
		@Override
		public void taskComplete(boolean wasCancelled) {
			if (!wasCancelled && !Thread.currentThread().isInterrupted()) {
				if (params.getBooleanParameterValue("deleteTiles"))
					parentObject.clearChildObjects();
				if (pathAnnotations != null && !pathAnnotations.isEmpty())
					parentObject.addChildObjects(pathAnnotations);
				imageData.getHierarchy().fireHierarchyChangedEvent(parentObject);
			}
			pathAnnotations = null;
		}
		
		@Override
		public String getLastResultsDescription() {
			return resultsString;
		}
		
	}

}
