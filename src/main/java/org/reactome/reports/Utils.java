package org.reactome.reports;

import org.gk.model.ReactomeJavaConstants;
import org.reactome.curation.model.SimpleInstance;

public class Utils {

    public static String getCreatorName(SimpleInstance inst) {
        SimpleInstance createdInstanceEdit = (SimpleInstance) inst.getAttribute(ReactomeJavaConstants.created);
        if (createdInstanceEdit == null) {
            return "UNKNOWN AUTHOR";
        }
        String createdInstanceEditDisplayName = createdInstanceEdit.getDisplayName();
        int nameAndDateSeparatorIndex = createdInstanceEditDisplayName.lastIndexOf(',');
        if (nameAndDateSeparatorIndex == -1) {
            return createdInstanceEditDisplayName;
        }

        String creatorName = createdInstanceEditDisplayName.substring(0, nameAndDateSeparatorIndex);
        return creatorName;
    }
}
