package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

/**
 * Get the displayed name of the first TMACoreObject that is an ancestor of the supplied object.
 */
class TMACoreNameMeasurementBuilder extends AbstractStringMeasurementBuilder {

    @Override
    public String getHelpText() {
        return "The name of the selected tissue microarray (TMA) core";
    }

    @Override
    public String getName() {
        return "TMA Core";
    }

    private TMACoreObject getAncestorTMACore(PathObject pathObject) {
        if (pathObject == null)
            return null;
        if (pathObject instanceof TMACoreObject)
            return (TMACoreObject) pathObject;
        return getAncestorTMACore(pathObject.getParent());
    }

    @Override
    public String getMeasurementValue(PathObject pathObject) {
        TMACoreObject core = getAncestorTMACore(pathObject);
        if (core == null)
            return null;
        return core.getDisplayedName();
    }

}
