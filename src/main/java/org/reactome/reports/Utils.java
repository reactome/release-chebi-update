package org.reactome.reports;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

public class Utils {

    public static GKInstance getCreator(GKInstance inst) throws Exception {
        GKInstance createdInstanceEdit = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
        if (createdInstanceEdit == null) {
            return null;
        }
        GKInstance creator = (GKInstance) createdInstanceEdit.getAttributeValue(ReactomeJavaConstants.author);
        return creator;
    }

    public static String getCreatorName(GKInstance creator) {
        return creator != null ? creator.toString() : "AUTHOR UNKNOWN";
    }
}
