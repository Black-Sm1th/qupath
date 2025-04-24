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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tools.QuPathTranslator;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowListener;
import qupath.lib.plugins.workflow.WorkflowStep;


/**
 * Show logged commands, and optionally generate a script.
 * 
 * @author Pete Bankhead
 *
 */
public class WorkflowCommandLogView implements ChangeListener<ImageData<BufferedImage>>, WorkflowListener {

	private static final Logger logger = LoggerFactory.getLogger(WorkflowCommandLogView.class);
	
	private QuPathGUI qupath;
	
	private VBox pane;
	
	private final boolean isStaticWorkflow;
	
	private ObjectProperty<Workflow> workflowProperty = new SimpleObjectProperty<>();
	private VBox stepsBox;
	private ScrollPane stepsPane;
	private VBox detailsBox;
	private ScrollPane detailsPane;
	private WorkflowStep selectedStep;
	
	private final KeyCodeCombination copyCombination = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
	/**
	 * Construct a view to display the workflow for the currently-active ImageData within a running QuPath instance.
	 * 
	 * @param qupath
	 */
	public WorkflowCommandLogView(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.imageDataProperty().addListener(this);
		ImageData<BufferedImage> imageData = qupath.getImageData();
		isStaticWorkflow = false;
		if (imageData != null) {
			var workflow = imageData.getHistoryWorkflow();
			workflow.addWorkflowListener(this);
			workflowProperty.set(workflow);
			workflowUpdated(workflow);
		}
	}

	/**
	 * Construct a view displaying a static workflow (i.e. not dependent on any particular ImageData).
	 * 
	 * @param qupath
	 * @param workflow
	 */
	public WorkflowCommandLogView(final QuPathGUI qupath, final Workflow workflow) {
		this.qupath = qupath;
		Objects.requireNonNull(workflow);
		workflowProperty.set(workflow);
		workflow.addWorkflowListener(this);
		this.isStaticWorkflow = true;
	}
	
	/**
	 * Get the pane to add to a scene.
	 * @return
	 */
	public VBox getPane() {
		if (pane == null)
			pane = createPane();
		return pane;
	}
	
	protected VBox createPane() {
		VBox pane = new VBox();
		BorderPane mdPane = new BorderPane();
		VBox.setVgrow(mdPane, Priority.ALWAYS);
		pane.getStyleClass().add("workflow-command-pane");
		// Title
		HBox topTabBar = new HBox();
		topTabBar.getStyleClass().add("topbar-tab-container");
		topTabBar.setAlignment(Pos.CENTER_LEFT);
		VBox.setMargin(topTabBar, new Insets(0, 12, 0, 12));
		ToggleGroup group = new ToggleGroup();
		
		// Create toggle buttons
        ToggleButton button1 = new ToggleButton("历史命令");
        ToggleButton button2 = new ToggleButton("脚本");
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
		// Steps list
		stepsBox = new VBox();
		stepsBox.getStyleClass().add("workflow-steps-box");
		// Details box
		detailsBox = new VBox();

		
		// Single ScrollPane containing all content
		ScrollPane stepScrollPane = new ScrollPane(stepsBox);
		stepScrollPane.setFitToWidth(true);
		stepScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		stepScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		stepScrollPane.setFitToHeight(true);
		

		VBox detailBox = new VBox();
		// Create workflow button
		Button btnCreateWorkflow = new Button("创建脚本");
		btnCreateWorkflow.setStyle("-fx-background-color: rgba(22, 146, 255, 1); -fx-text-fill: rgba(255, 255, 255, 1); -fx-font-size: 14px; -fx-background-radius: 12;");
		btnCreateWorkflow.setPrefHeight(48);
		btnCreateWorkflow.setMinHeight(48);
		btnCreateWorkflow.setMaxHeight(48);
		btnCreateWorkflow.setPrefWidth(Double.MAX_VALUE);
		btnCreateWorkflow.setOnAction(e -> showScript());
		btnCreateWorkflow.disableProperty().bind(workflowProperty.isNull());
		VBox.setMargin(btnCreateWorkflow, new Insets(0, 12, 0, 12));

		ScrollPane deatilScrollPane = new ScrollPane(detailsBox);
		deatilScrollPane.setFitToWidth(true);
		deatilScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		deatilScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		deatilScrollPane.setFitToHeight(true);

		detailBox.getChildren().addAll(deatilScrollPane, btnCreateWorkflow);
		mdPane.setCenter(stepScrollPane);
		button1.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				mdPane.setCenter(stepScrollPane);
			}
		});
		button2.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				mdPane.setCenter(detailBox);
			}
		});
		topTabBar.getChildren().addAll(button1, button2);
		
		pane.setFillWidth(true);
		detailsBox.setFillWidth(true);
		// Set layout
		pane.getChildren().addAll(topTabBar, mdPane);
		
		return pane;
	}
	
	private void updateStepsList(List<WorkflowStep> steps) {
		stepsBox.getChildren().clear();
		if (steps == null) return;
		
		for (WorkflowStep step : steps) {
			Node stepNode = createStepNode(step);
			stepsBox.getChildren().add(stepNode);
		}
	}
	
	private Node createStepNode(WorkflowStep step) {
		VBox stepBox = new VBox();
		stepBox.getStyleClass().add("workflow-command-step");
		Label nameLabel = new Label(QuPathTranslator.getTranslatedName(step.getName()));
		nameLabel.getStyleClass().add("workflow-command-step-name");
		stepBox.getChildren().add(nameLabel);
		
		// Context menu
		ContextMenu contextMenu = new ContextMenu();
		MenuItem miCopyCommand = new MenuItem("Copy command");
		miCopyCommand.setOnAction(e -> {
			if (step instanceof ScriptableWorkflowStep) {
				String script = ((ScriptableWorkflowStep)step).getScript();
				ClipboardContent content = new ClipboardContent();
				content.putString(script);
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		contextMenu.getItems().add(miCopyCommand);
		
		if (isStaticWorkflow) {
			MenuItem miRemoveSelected = new MenuItem("Remove step");
			miRemoveSelected.setOnAction(e -> {
				if (Dialogs.showYesNoDialog("Remove workflow step", "Remove workflow step?")) {
					getWorkflow().removeStep(step);
				}
			});
			contextMenu.getItems().add(miRemoveSelected);
		}
		
		stepBox.setOnContextMenuRequested(e -> contextMenu.show(stepBox, e.getScreenX(), e.getScreenY()));
		
		// Selection handling
		stepBox.setOnMouseClicked(e -> {
			// Clear previous selection
			stepsBox.getChildren().forEach(node -> {
				node.getStyleClass().remove("selected");
				node.lookup(".workflow-command-step-name").getStyleClass().remove("selected");
			});
			
			// Set new selection
			stepBox.getStyleClass().add("selected");
			nameLabel.getStyleClass().add("selected");
			selectedStep = step;
			updateDetailsPane(step);
		});
		
		return stepBox;
	}
	
	private void updateDetailsPane(WorkflowStep step) {
		detailsBox.getChildren().clear();
		
		if (step == null) {
			return;
		}
		// Add parameters
		Map<String, ?> params = step.getParameterMap();
		if (!params.isEmpty()) {
			for (Map.Entry<String, ?> entry : params.entrySet()) {
				VBox paramBox = createParameterBox(entry.getKey(), entry.getValue());
				detailsBox.getChildren().add(paramBox);
			}
		}
		
		// Add script if available
		if (step instanceof ScriptableWorkflowStep) {
			String script = ((ScriptableWorkflowStep) step).getScript();
			if (script != null && !script.isEmpty()) {
				VBox scriptBox = createParameterBox("Script", script);
				detailsBox.getChildren().add(scriptBox);
			}
		}
	}
	
	private VBox createParameterBox(String key, Object value) {
		VBox box = new VBox(5);
		box.setPadding(new Insets(5, 0, 5, 0));
		
		// Parameter name
		Label keyLabel = new Label(key);
		keyLabel.setFont(Font.font(null, FontWeight.MEDIUM, 12));
		keyLabel.setTextFill(Color.web("#666666"));
		
		// Parameter value
		Label valueLabel = new Label(value == null ? "" : value.toString());
		valueLabel.setFont(Font.font(null, FontWeight.NORMAL, 12));
		valueLabel.setTextFill(Color.web("#333333"));
		valueLabel.setWrapText(true);
		
		box.getChildren().addAll(keyLabel, valueLabel);
		return box;
	}
	
	private Workflow getWorkflow() {
		var workflow = workflowProperty.get();
		if (workflow == null) {
			logger.error("Workflow is null!");
		}
		return workflow;
	}
	
	void showScript() {
		showScript(qupath.getScriptEditor(), workflowProperty.get());
	}
	
	/**
	 * Show a script in the script editor based on the specified workflow.
	 * @param scriptEditor
	 * @param workflow
	 */
	public static void showScript(final ScriptEditor scriptEditor, final Workflow workflow) {
		if (workflow == null)
			return;
		String script = workflow.createScript();
		logger.info("\n//---------------------------------\n" + script + "\n//---------------------------------");
		if (scriptEditor != null)
			scriptEditor.showScript("New script", script);
	}
	
	@Override
	public void workflowUpdated(Workflow workflow) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> workflowUpdated(workflow));
			return;
		}
		if (workflow == null) {
			stepsBox.getChildren().clear();
			detailsBox.getChildren().clear();
		} else {
			updateStepsList(workflow.getSteps());
			if (selectedStep != null) {
				updateDetailsPane(selectedStep);
			}
		}
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		if (imageDataOld == imageDataNew)
			return;
		if (imageDataOld != null)
			imageDataOld.getHistoryWorkflow().removeWorkflowListener(this);
		
		if (imageDataNew != null) {
			imageDataNew.getHistoryWorkflow().addWorkflowListener(this);
			workflowProperty.set(imageDataNew.getHistoryWorkflow());
			selectedStep = null;
			Workflow workflow = imageDataNew.getHistoryWorkflow();
			updateStepsList(workflow.getSteps());
			workflowUpdated(workflow);
		} else {
			workflowProperty.set(null);
		}
	}
}