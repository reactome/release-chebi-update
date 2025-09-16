package org.reactome.reports;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

public class Utils {
    private static Logger logger = LogManager.getLogger();

    public static GKInstance getCreator(GKInstance inst) throws InvalidAttributeException, Exception {
        GKInstance createdInstanceEdit = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
        if (createdInstanceEdit == null) {
            // User should probably be warned that the object has no "creator" attribute value
            // so they can explain to the curators why there is no author name in the report.
            logger.warn("Instance {} does not have a value for \"created\" attribute!", inst.toString());
            return null;
        }
        GKInstance creator = (GKInstance) createdInstanceEdit.getAttributeValue(ReactomeJavaConstants.author);
        return creator;
    }

    public static String getCreatorName(GKInstance creator) {
        return creator != null ? creator.toString() : "AUTHOR UNKNOWN";
    }
}
