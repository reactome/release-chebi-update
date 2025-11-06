package org.reactome.webservice.helpers;

import org.json.JSONObject;
import org.reactome.model.ChEBIEntity;

public class ChEBIEntityParser {

    public ChEBIEntity parse(JSONObject chEBIJSON) {
        String chebiID = chEBIJSON.getString("chebi_accession").replace("CHEBI:", "");
        String name = chEBIJSON.getString("ascii_name");
        String formula = getFormula(chEBIJSON);

        return new ChEBIEntity(chebiID, name, formula);
    }

    private String getFormula(JSONObject chEBIJSON) {
        if (chEBIJSON.isNull("chemical_data") ||
                chEBIJSON.getJSONObject("chemical_data").isNull("formula")) {
            return "";
        }

        return chEBIJSON.getJSONObject("chemical_data").getString("formula");
    }
}
