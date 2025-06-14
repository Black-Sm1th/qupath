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

package qupath.lib.gui.prefs;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

import javafx.application.ColorScheme;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.UserDirectoryManager;
import qupath.lib.gui.commands.Commands;
import qupath.fx.dialogs.Dialogs;


/**
 * Class to facilitate the use of different styles within QuPath.
 * <p>
 * These register themselves with {@link PathPrefs} so that they can be persistent across restarts.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathStyleManager {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathStyleManager.class);
	
	/**
	 * Main stylesheet, used to define new colors for QuPath.
	 * This should always be applied, since it defines colors that are required for scripting.
	 */
	private static final String STYLESHEET_MAIN = "css/main.css";

	/**
	 * Default dark stylesheet.
	 */
	private static final String STYLESHEET_DARK = "css/dark.css";

	/**
	 * Default JavaFX stylesheet
	 */
	private static final StyleOption DEFAULT_LIGHT_STYLE = new JavaFXStylesheet("Modena Light", Application.STYLESHEET_MODENA);

	/**
	 * Default QuPath stylesheet used for 'dark mode'
	 */
	private static final StyleOption DEFAULT_DARK_STYLE = new CustomStylesheet(
			"Modena Dark",
			"Darker version of JavaFX Modena stylesheet",
			ColorScheme.DARK,
			STYLESHEET_DARK);

	// Maintain a record of what stylesheets we've added, so we can try to clean up later if needed
	private static final List<String> previouslyAddedStyleSheets = new ArrayList<>();

	private static final StyleOption DEFAULT_SYSTEM_STYLE = new SystemStylesheet(DEFAULT_LIGHT_STYLE, DEFAULT_DARK_STYLE);

	private static final ReadOnlyObjectProperty<ColorScheme> systemColorScheme = Platform.getPreferences().colorSchemeProperty();

	private static final ObservableList<StyleOption> styles = FXCollections.observableArrayList(
			// DEFAULT_SYSTEM_STYLE,
			DEFAULT_LIGHT_STYLE
			// DEFAULT_DARK_STYLE
			);
	
	private static final ObservableList<StyleOption> stylesUnmodifiable = FXCollections.unmodifiableObservableList(styles);
	
	private static final ObjectProperty<StyleOption> selectedStyle;

	/**
	 * Find the first available {@link StyleOption} with the specified name.
	 * @param name
	 * @return
	 */
	private static StyleOption findByName(String name) {
		return DEFAULT_LIGHT_STYLE;
		// return styles.stream().filter(s -> Objects.equals(s.getName(), name)).findFirst().orElse(DEFAULT_LIGHT_STYLE);
	}
	
	/**
	 * Watch for custom styles, which the user may add, remove or modify.
	 */
	private static CssStylesWatcher watcher;
	
	/**
	 * Available font families.
	 */
	public enum Fonts {
		/**
		 * JavaFX default. May not look great on macOS, which lacks support for bold font weight by default.
		 */
		DEFAULT,
		/**
		 * Preferred sans-serif font.
		 */
		SANS_SERIF,
		/**
		 * Preferred serif font.
		 */
		SERIF;
		
		private String getURL() {
            return switch (this) {
                case SANS_SERIF -> "css/sans-serif.css";
                case SERIF -> "css/serif.css";
                default -> null;
            };
		}
		
		@Override
		public String toString() {
            return switch (this) {
                case SANS_SERIF -> "Sans-serif";
                case SERIF -> "Serif";
                default -> "Default";
            };
		}
	}

	private static final ObservableList<Fonts> availableFonts =
			FXCollections.unmodifiableObservableList(
					FXCollections.observableArrayList(Fonts.values()));

	private static final ObjectProperty<Fonts> selectedFont = PathPrefs.createPersistentPreference("selectedFont",
			GeneralTools.isMac() ? Fonts.SANS_SERIF : Fonts.DEFAULT, Fonts.class);

	static {
		
		/**
		 * Add custom user styles, if available.
		 * We need to do this before setting the default (since the last used style might be one of these).
		 */
		updateAvailableStyles();
		selectedStyle = PathPrefs.createPersistentPreference("qupathStylesheet", DEFAULT_SYSTEM_STYLE, StyleOption::getName, QuPathStyleManager::findByName);

		systemColorScheme.addListener((v, o, n) -> {
			if (selectedStyle.get() == DEFAULT_SYSTEM_STYLE) {
				updateStyle();
			}
		});


		// Add listener to adjust style as required
		selectedStyle.addListener((v, o, n) -> updateStyle());
		selectedFont.addListener((v, o, n) -> updateStyle());
	}
	
	private static void updateStyle() {
		// Support calling updateStyle from different threads
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(QuPathStyleManager::updateStyle);
			return;
		}
		StyleOption n = selectedStyle.get();
		if (n != null) {
			n.setStyle();
		} else {
			Application.setUserAgentStylesheet(null);
		}
		// Set the font if required
		Fonts font = selectedFont.get();
		if (font != null) {
			String url = font.getURL();
			if (url != null)
				addStyleSheets(url);
		}
	}
	
	
	private static Path getCssDirectory() {
		return UserDirectoryManager.getInstance().getCssStylesPath();
	}
	
	
	/**
	 * Request that the list of available styles is updated.
	 * It makes sense to call this when a new user directory has been set, so that a check for CSS files 
	 * can be performed.
	 */
	public static void updateAvailableStyles() {
		
		// Make sure we're still watching the correct directory for custom styles
		var cssPath = getCssDirectory();
		if (cssPath != null) {
			// Create a new watcher if needed, or else check the CSS path is still correct
			if (watcher == null) {
				try {
					watcher = new CssStylesWatcher(cssPath);
					watcher.styles.addListener((Change<? extends StyleOption> c) -> {
						updateAvailableStyles();
					});
				} catch (Exception e) {
                    logger.warn("Exception searching for css files: {}", e.getMessage(), e);
				}
			} else if (!Objects.equals(watcher.cssPath, cssPath)) {
				watcher.setCssPath(cssPath);
			}
		}
		
		
		// Cache the current selection, since it could become lost during the update
		var previouslySelected = selectedStyle == null ? null : selectedStyle.get();
		
		// Update all available styles
		if (watcher == null || watcher.styles.isEmpty())
			styles.setAll(DEFAULT_SYSTEM_STYLE, DEFAULT_LIGHT_STYLE, DEFAULT_DARK_STYLE);
		else {
			var temp = new ArrayList<StyleOption>();
			temp.add(DEFAULT_SYSTEM_STYLE);
			temp.add(DEFAULT_LIGHT_STYLE);
			temp.add(DEFAULT_DARK_STYLE);
			temp.addAll(watcher.styles);
			styles.setAll(temp);
		}
		
		// Reinstate the selection, or use the default if necessary
		if (selectedStyle != null) {
			if (previouslySelected == null || !styles.contains(previouslySelected))
				selectedStyle.set(DEFAULT_LIGHT_STYLE);
			else
				selectedStyle.set(previouslySelected);
		}
	}
	
	
	/**
     * Handle installing CSS files (which can be used to style QuPath).
     * @param list list of css files
     * @return
     */
	public static boolean installStyles(final Collection<File> list) {
		var dir = Commands.requestUserDirectory(true);
		if (dir == null)
			return false;
		
		var pathCss = getCssDirectory();
		
		int nInstalled = 0;
		try {
			// If we have a user directory, add a CSS subdirectory if needed
			if (pathCss != null && !Files.exists(pathCss)) {
				if (Files.isDirectory(pathCss.getParent()))
					Files.createDirectory(pathCss);
			}
			// If we still don't have a css directory, return
			if (!Files.isDirectory(pathCss))
				return false;
			
			// Copy over the files
			Boolean overwriteExisting = null;
			for (var file : list) {
				if (!file.getName().toLowerCase().endsWith(".css")) {
					logger.warn("Cannot install style for {} - not a .css file!", file);
					continue;
				}
				var source = file.toPath();
				var target = pathCss.resolve(file.getName());
				if (Objects.equals(source, target)) {
					logger.warn("Can't copy CSS - source and target files are the same!");
					continue;
				}
				if (Files.exists(target)) {
					// Check if we want to overwrite - if so, retain the response so we don't 
					// have to prompt multiple times if there are multiple files
					if (overwriteExisting == null) {
						var response = Dialogs.showYesNoCancelDialog("Install CSS", "Do you want to overwrite existing CSS files?");
						if (response == ButtonType.YES)
							overwriteExisting = Boolean.TRUE;
						else if (response == ButtonType.NO)
							overwriteExisting = Boolean.FALSE;
						else // cancelled
							return false;
					}
					// Skip
					if (!overwriteExisting)
						continue;
				}
				logger.info("Copying {} -> {}", source, target);
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);	
				nInstalled++;
			}
		} catch (IOException e) {
            logger.error("Exception installing CSS files: {}", e.getLocalizedMessage(), e);
			return false;
		}
		if (nInstalled > 0)
			QuPathStyleManager.updateAvailableStyles();
		return true;
	}
	
	
	
	/**
	 * Refresh the current style.
	 * This should not normally be required, but may be useful during startup to ensure 
	 * that the style is properly set at the appropriate time.
	 */
	public static void refresh() {
		updateStyle();
	}
	
	/**
	 * Get the color scheme of the current style, or the system color scheme if no other is available.
	 * @return
	 */
	public static ColorScheme getStyleColorScheme() {
		var selected = selectedStyle.get();
		return selected == null ? Platform.getPreferences().getColorScheme() : selected.getColorScheme();
	}
	
	/**
	 * Get the current available styles as an observable list.
	 * The list is unmodifiable, since any changes should be made via adding/removing files in {@link UserDirectoryManager#getCssStylesPath()}.
	 * @return
	 */
	public static ObservableList<StyleOption> availableStylesProperty() {
		return stylesUnmodifiable;
	}
	
	/**
	 * Get the current selected style.
	 * @return
	 */
	public static ObjectProperty<StyleOption> selectedStyleProperty() {
		return selectedStyle;
	}
	
	/**
	 * Get a list of available fonts.
	 * The list is unmodifiable, since this is primarily used to overcome issues with the default font on macOS 
	 * by changing the font family. More fine-grained changes can be made via css.
	 * @return list of available fonts
	 */
	public static ObservableList<Fonts> availableFontsProperty() {
		return availableFonts;
	}
	
	/**
	 * Get the current selected font.
	 * @return
	 */
	public static ObjectProperty<Fonts> fontProperty() {
		return selectedFont;
	}

	/**
	 * Interface defining a style that may be applied to QuPath.
	 */
	public interface StyleOption {
		
		/**
		 * Set the style for the QuPath application.
		 */
		void setStyle();
		
		/**
		 * Get a user-friendly description of the style.
		 * @return
		 */
		String getDescription();
		
		/**
		 * Get a user-friendly name for the style.
		 * @return
		 */
		String getName();

		/**
		 * Get the color scheme. By default this will return the color scheme from the Platform preferences,
		 * but implementations may return a different one for the specific theme.
		 * @return
		 */
		default ColorScheme getColorScheme() {
			return Platform.getPreferences().getColorScheme();
		}
		
	}

	/**
	 * Default JavaFX stylesheet.
	 */
	static class SystemStylesheet implements StyleOption {

		private final StyleOption defaultLight;
		private final StyleOption defaultDark;

		private SystemStylesheet(StyleOption defaultLight, StyleOption defaultDark) {
			this.defaultLight = defaultLight;
			this.defaultDark = defaultDark;
		}

		@Override
		public void setStyle() {
			if (Platform.getPreferences().getColorScheme() == ColorScheme.DARK) {
				defaultDark.setStyle();
			} else {
				defaultLight.setStyle();
			}
		}

		@Override
		public String getDescription() {
			return "Use a style based on the system-wide light/dark setting";
		}

		@Override
		public String getName() {
			return "System theme";
		}

		@Override
		public String toString() {
			return getName();
		}

	}
	
	
	/**
	 * Default JavaFX stylesheet.
	 */
	static class JavaFXStylesheet implements StyleOption {
		
		private String name;
		private String cssName;
		
		JavaFXStylesheet(final String name, final String cssName) {
			this.name = name;
			this.cssName = cssName;
		}

		@Override
		public void setStyle() {
			Application.setUserAgentStylesheet(cssName);
			removePreviousStyleSheets(cssName);
			addStyleSheets(STYLESHEET_MAIN);
		}

		@Override
		public String getDescription() {
			return "Built-in JavaFX stylesheet " + cssName;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ColorScheme getColorScheme() {
			return ColorScheme.LIGHT;
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			return Objects.hash(cssName, name);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			JavaFXStylesheet other = (JavaFXStylesheet) obj;
			return Objects.equals(cssName, other.cssName) && Objects.equals(name, other.name);
		}
		
	}
	
	
	/**
	 * Custom stylesheets, requiring one or more URLs (added on top of the default).
	 */
	static class CustomStylesheet implements StyleOption {
		
		private final String name;
		private final String description;
		private final String[] urls;
		private final ColorScheme colorScheme;
		
		CustomStylesheet(final String name, final String description, final ColorScheme colorScheme, final String... urls) {
			this.name = name;
			this.description = description;
			this.urls = urls.clone();
			this.colorScheme = colorScheme;
		}
		
		CustomStylesheet(final Path path) {
			this(GeneralTools.getNameWithoutExtension(path.toFile()), path.toString(), null, path.toUri().toString());
		}

		@Override
		public void setStyle() {
			setStyleSheets(urls);
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		@Override
		public ColorScheme getColorScheme() {
			if (colorScheme != null)
				return colorScheme;
			String name = getName().toLowerCase();
			if (name.contains("dark"))
				return ColorScheme.DARK;
			else if (name.contains("light"))
				return ColorScheme.LIGHT;
			else
				return Platform.getPreferences().getColorScheme();
		}

		/**
		 * Check if a specified url is used as part of this stylesheet.
		 * @param url
		 * @return
		 */
		private boolean containsUrl(String url) {
			for (var css: urls) {
				if (Objects.equals(url, css))
					return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(urls);
			result = prime * result + Objects.hash(description, name);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CustomStylesheet other = (CustomStylesheet) obj;
			return Objects.equals(description, other.description) && Objects.equals(name, other.name)
					&& Arrays.equals(urls, other.urls);
		}
		
	}
	
	private static void setStyleSheets(String... urls) {
		Application.setUserAgentStylesheet(null);

		// Replace previous stylesheets with the new ones
		removePreviousStyleSheets();
		
		addStyleSheets(STYLESHEET_MAIN);
		
		addStyleSheets(urls);
	}
		
	private static void removePreviousStyleSheets(String... urls) {
		if (previouslyAddedStyleSheets.isEmpty())
			return;
		try {
			Class<?> cStyleManager = Class.forName("com.sun.javafx.css.StyleManager");
			Object styleManager = cStyleManager.getMethod("getInstance").invoke(null);
			Method m = styleManager.getClass().getMethod("removeUserAgentStylesheet", String.class);
			var iterator = previouslyAddedStyleSheets.iterator();
			while (iterator.hasNext()) {
				var url = iterator.next();
				iterator.remove();
				m.invoke(styleManager, url);
				logger.debug("Stylesheet removed {}", url);
			}
		} catch (Exception e) {
			logger.error("Unable to call removeUserAgentStylesheet", e);
		}
	}
	
	private static void addStyleSheets(String... urls) {
		try {
			Class<?> cStyleManager = Class.forName("com.sun.javafx.css.StyleManager");
			Object styleManager = cStyleManager.getMethod("getInstance").invoke(null);
			Method m = styleManager.getClass().getMethod("addUserAgentStylesheet", String.class);
			for (String url : urls) {
				if (previouslyAddedStyleSheets.contains(url))
					continue;
				m.invoke(styleManager, url);
				previouslyAddedStyleSheets.add(url);
				logger.debug("Stylesheet added {}", url);
			}
		} catch (Exception e) {
			logger.error("Unable to call addUserAgentStylesheet", e);
		}
	}
	
	
	/**
	 * Class to run a background thread that picks up changes to a directory containing CSS files, 
	 * and updates the current or available styles as required.
	 */
	private static class CssStylesWatcher implements Runnable {
		
		private static final ThreadFactory THREAD_FACTORY = ThreadTools.createThreadFactory("css-watcher", true);
		
		private Thread thread;
		
		private Path cssPath;
		private WatchService watcher;
		
		private ObservableList<StyleOption> styles = FXCollections.observableArrayList();
		
		private CssStylesWatcher(Path cssPath) {
			thread = THREAD_FACTORY.newThread(this);
			try {
				watcher = FileSystems.getDefault().newWatchService();
				logger.debug("Watching for changes in {}", cssPath);
			} catch (IOException e) {
				logger.error("Exception setting up CSS watcher: {}", e.getMessage(), e);
			}
			setCssPath(cssPath);
			thread.start();
		}
		
		private void setCssPath(Path cssPath) {
			if (Objects.equals(this.cssPath, cssPath))
				return;
			this.cssPath = cssPath;
			if (Files.isDirectory(cssPath)) {
				try {
					cssPath.register(watcher,
							StandardWatchEventKinds.ENTRY_MODIFY,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE);
					logger.debug("Watching for changes in {}", cssPath);
				} catch (IOException e) {
					logger.error("Exception setting up CSS watcher: {}", e.getMessage(), e);
				}
			}
			refreshStylesheets();
		}
		
		
		@Override
		public void run() {
			while (watcher != null) {
				
				WatchKey key = null;
				synchronized(this) {
					try {
						key = watcher.take();
						if (key == null)
							continue;
					} catch (InterruptedException e) {
						return;
					}
				}
				
				for (WatchEvent<?> ev: key.pollEvents()) {
					if (ev.kind() == StandardWatchEventKinds.OVERFLOW)
						continue;

					// Get the path to whatever changed
					var event = (WatchEvent<Path>)ev;
					var basePath = (Path)key.watchable();
					if (!Objects.equals(cssPath, basePath))
						continue;
					
					var path = basePath.resolve(event.context());
					
					// An existing stylesheet has changed
					if (ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY && Files.isRegularFile(path)) {
						try {
							var currentStyle = selectedStyle.get();
							if (currentStyle instanceof CustomStylesheet currentCustomStyle) {
                                var url = path.toUri().toString();
								if (currentCustomStyle.containsUrl(url)) {
									logger.info("Refreshing style {}", currentStyle.getName());
									refresh();
								}
								break;
							}
						} catch (Exception e) {
                            logger.warn("Exception processing CSS refresh: {}", e.getMessage(), e);
						}
					} else {
						// For everything else, refresh the available stylesheets
						refreshStylesheets();
					}
					
				}

				boolean valid = key.reset();
				if (!valid) {
					break;
				}
			}
		}
		
		
		private void refreshStylesheets() {
			try {
				if (Files.isDirectory(cssPath)) {
					var newStyles = Files.list(cssPath)
						.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".css"))
						.map(CustomStylesheet::new)
						.sorted(Comparator.comparing(StyleOption::getName))
						.toList();
					FXUtils.runOnApplicationThread(() -> styles.setAll(newStyles));
					return;
				}
			} catch (IOException e) {
				logger.warn(e.getLocalizedMessage(), e);
			}
			FXUtils.runOnApplicationThread(() -> styles.clear());
		}
		
	}

}
