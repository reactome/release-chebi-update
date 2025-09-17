package org.reactome.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;
import org.reactome.reports.ReferenceMoleculeFormulaChangeReporter;
import org.reactome.reports.ReferenceMoleculeNameChangeReporter;
import org.reactome.reports.SimpleEntityNameChangeReporter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.reactome.reports.Utils.getCreator;
import static org.reactome.reports.Utils.getCreatorName;

public class DBInteractor implements DBReader, DBWriter {
    private static Logger logger = LogManager.getLogger(DBInteractor.class);

    private final MySQLAdaptor dbAdaptor;

    private final ReferenceMoleculeNameChangeReporter referenceMoleculeNameChangeReporter;
    private final ReferenceMoleculeFormulaChangeReporter referenceMoleculeFormulaChangeReporter;
    private final SimpleEntityNameChangeReporter simpleEntityNameChangeReporter;

    public DBInteractor(MySQLAdaptor dbAdaptor) {
        this.dbAdaptor = dbAdaptor;

        this.referenceMoleculeNameChangeReporter = new ReferenceMoleculeNameChangeReporter();
        this.referenceMoleculeFormulaChangeReporter = new ReferenceMoleculeFormulaChangeReporter();
        this.simpleEntityNameChangeReporter = new SimpleEntityNameChangeReporter();
    }

    public void startTransaction() throws TransactionsNotSupportedException, SQLException {
        getDbAdaptor().startTransaction();
    }

    public void commit() throws SQLException {
        getDbAdaptor().commit();
    }

    @Override
    public List<GKInstance> getAllChEBIReferenceMoleculeInstances() throws Exception {
        return new ArrayList<>(
            (Collection<GKInstance>) getDbAdaptor().fetchInstanceByAttribute(
                ReactomeJavaConstants.ReferenceMolecule,
                ReactomeJavaConstants.referenceDatabase,
                "=",
                getChEBIReferenceDatabaseOrThrow().getDBID()
            )
        );
    }

    @Override
    public List<GKInstance> getReferenceMoleculesWithChEBIIdentifier(String chEBIId) throws Exception {
        Collection<GKInstance> refMolsWithChEBIIdentifier = (Collection<GKInstance>)
            getDbAdaptor().fetchInstanceByAttribute(
                ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.identifier, "=", chEBIId
            );

        if (refMolsWithChEBIIdentifier == null || refMolsWithChEBIIdentifier.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(refMolsWithChEBIIdentifier);
    }

    @Override
    public boolean updateSimpleEntityReferrersNames(GKInstance referenceMolecule, String newName) throws Exception {
        boolean anySimpleEntityNameUpdated = false;
        for (GKInstance simpleEntity : getReferenceMoleculeReferrers(referenceMolecule)) {
            List<String> simpleEntityNames = getSimpleEntityInstanceNames(simpleEntity);

            if (!nameShouldBeAdded(newName, simpleEntity)) {
                continue;
            }

            simpleEntityNames.add(newName);

            simpleEntity.setAttributeValue(ReactomeJavaConstants.name, simpleEntityNames);
            getDbAdaptor().updateInstanceAttribute(simpleEntity, ReactomeJavaConstants.name);

            this.simpleEntityNameChangeReporter.report(
                simpleEntity.getDBID().toString(),
                getCreatorName(getCreator(simpleEntity)),
                simpleEntity.getDisplayName(),
                newName,
                simpleEntityNames.toString()
            );

            anySimpleEntityNameUpdated = true;
        }

        return anySimpleEntityNameUpdated;
    }

    @Override
    public boolean updateReferenceMoleculeName(GKInstance referenceMolecule, String newName) throws Exception {
        // TODO Check with Lisa and Peter if this implementation is correct - do we want to maintain old names
        //  and/or move the new name to be first for the reference molecule?

        List<String> referenceMoleculeNames =
            safeList(referenceMolecule.getAttributeValuesList(ReactomeJavaConstants.name));

        if (referenceMoleculeNames.contains(newName)) {
            return false;
        }

        this.referenceMoleculeNameChangeReporter.report(
            referenceMolecule.getDBID().toString(),
            getCreatorName(getCreator(referenceMolecule)),
            referenceMolecule.getDisplayName(),
            referenceMoleculeNames.get(0),
            newName
        );

        referenceMoleculeNames.add(0, newName);
        referenceMolecule.setAttributeValue(ReactomeJavaConstants.name, referenceMoleculeNames);
        getDbAdaptor().updateInstanceAttribute(referenceMolecule, ReactomeJavaConstants.name);

        return true;
    }

    @Override
    public boolean updateReferenceMoleculeFormula(GKInstance referenceMolecule, String newFormula) throws Exception {
        if (newFormula == null || newFormula.isEmpty()) {
            return false;
        }

        String existingFormula = (String) referenceMolecule.getAttributeValue(ReactomeJavaConstants.formula);
        if (newFormula.equals(existingFormula)) {
            return false;
        }

        referenceMolecule.setAttributeValue(ReactomeJavaConstants.formula, newFormula);
        getDbAdaptor().updateInstanceAttribute(referenceMolecule, ReactomeJavaConstants.formula);

        this.referenceMoleculeFormulaChangeReporter.report(
            referenceMolecule.getDBID().toString(),
            getCreatorName(getCreator(referenceMolecule)),
            referenceMolecule.getDisplayName(),
            existingFormula,
            newFormula
        );

        return true;
    }

    @Override
    public boolean updateReferenceMoleculeDisplayName(GKInstance referenceMolecule) throws Exception {
        InstanceDisplayNameGenerator.setDisplayName(referenceMolecule);
        getDbAdaptor().updateInstanceAttribute(referenceMolecule, ReactomeJavaConstants._displayName);
        return true;
    }

    private GKInstance getChEBIReferenceDatabaseOrThrow() throws Exception {
        Collection<GKInstance> chEBIReferenceDatabaseInstances = getDbAdaptor().fetchInstanceByAttribute(
            ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "ChEBI"
        );

        if (chEBIReferenceDatabaseInstances == null || chEBIReferenceDatabaseInstances.size() != 1) {
            throw new RuntimeException("No unique ChEBI ReferenceDatabase instance could be found");
        }

        return chEBIReferenceDatabaseInstances.iterator().next();
    }

    private List<GKInstance> getReferenceMoleculeReferrers(GKInstance referenceMolecule) throws Exception {
        @SuppressWarnings("unchecked")
        Collection<GKInstance> referrers = referenceMolecule.getReferers(ReactomeJavaConstants.referenceEntity);
        if (referrers == null || referrers.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(referrers);
    }

    private List<String> getSimpleEntityInstanceNames(GKInstance simpleEntityInstance) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) simpleEntityInstance.getAttributeValuesList(ReactomeJavaConstants.name);
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(names);
    }

    private boolean nameShouldBeAdded(String chEBIName, GKInstance simpleEntityInstance) throws Exception {
        List<String> names = getSimpleEntityInstanceNames(simpleEntityInstance);
        if (names.isEmpty()) {
            logger.error("\"{}\" has an empty list of names. This doesn't seem right.",
                    simpleEntityInstance.toString());
            return false;
        }

        // If the first name IS the ChEBI name, then nothing to do. But if not, then need to append.
        if (names.get(0).equals(chEBIName)) {
            logger.info("\"{}\" has \"{}\" as its first name: {}",
                simpleEntityInstance.toString(), chEBIName, names.toString());
            return false;
        }

        if (names.contains(chEBIName)) {
            logger.info("\"{}\" *already* has \"{}\" as in its list of names; it will not be added again. Names: {}",
                    simpleEntityInstance.toString(), chEBIName, names.toString());
            return false;
        }
        return true;
    }

    private <E> List<E> safeList(List<E> list) {
        return list != null ? list : new ArrayList<>();
    }

    private MySQLAdaptor getDbAdaptor() {
        return this.dbAdaptor;
    }
}
