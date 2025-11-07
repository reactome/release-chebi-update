package org.reactome.webservice.helpers;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.reactome.model.ChEBIEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChEBIEntityParserTest {

    private ChEBIEntityParser chEBIEntityParser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chEBIEntityParser = new ChEBIEntityParser();
    }

    @Test
    void parseChEBIEntity_ValidInput_ReturnsCorrectMapping() {
        JSONObject chemicalData = new JSONObject();
        chemicalData.put("formula", "C6H12O6");

        JSONObject chEBIJSON = new JSONObject();
        chEBIJSON.put("chebi_accession", "CHEBI:15377");
        chEBIJSON.put("ascii_name", "water");
        chEBIJSON.put("chemical_data", chemicalData);

        ChEBIEntity entity = chEBIEntityParser.parse(chEBIJSON);

        assertEquals("C6H12O6", entity.getFormula());
        assertEquals("15377", entity.getChEBIId());
        assertEquals("water", entity.getName());
    }

    @Test
    void parseChEBIEntity_MissingFormula_ReturnsEmptyFormulaString() {
        JSONObject chemicalData = new JSONObject();
        chemicalData.put("formula", JSONObject.NULL);

        JSONObject chEBIJSON = new JSONObject();
        chEBIJSON.put("chebi_accession", "CHEBI:15377");
        chEBIJSON.put("ascii_name", "water");
        chEBIJSON.put("chemical_data", chemicalData);

        ChEBIEntity entity = chEBIEntityParser.parse(chEBIJSON);

        assertEquals("", entity.getFormula());
        assertEquals("15377", entity.getChEBIId());
        assertEquals("water", entity.getName());
    }

    @Test
    void parseChEBIEntity_MissingChemicalData_ReturnsEmptyFormulaString() {
        JSONObject chEBIJSON = new JSONObject();
        chEBIJSON.put("chebi_accession", "CHEBI:15377");
        chEBIJSON.put("ascii_name", "water");

        ChEBIEntity entity = chEBIEntityParser.parse(chEBIJSON);

        assertEquals("", entity.getFormula());
        assertEquals("15377", entity.getChEBIId());
        assertEquals("water", entity.getName());
    }
}
