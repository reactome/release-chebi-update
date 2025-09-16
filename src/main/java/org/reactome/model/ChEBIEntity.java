package org.reactome.model;

public class ChEBIEntity {
    private String chEBIId;
    private String name;
    private String formula;

    public ChEBIEntity(String chEBIId, String name, String formula) {
        this.chEBIId = chEBIId;
        this.name = name;
        this.formula = formula;
    }

    public String getChEBIId() {
        return this.chEBIId;
    }
    public String getName() {
        return this.name;
    }
    public String getFormula() {
        return this.formula;
    }

    @Override
    public String toString() {
        return String.format("ChEBI: %s, Name: %s, Formula: %s", this.chEBIId, this.name, this.formula);
    }
}
