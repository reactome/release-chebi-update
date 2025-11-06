package org.reactome.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;
import org.gk.schema.Schema;
import org.reactome.reports.ReferenceMoleculeFormulaChangeReporter;
import org.reactome.reports.ReferenceMoleculeNameChangeReporter;
import org.reactome.reports.SimpleEntityNameChangeReporter;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.reactome.reports.Utils.getCreator;
import static org.reactome.reports.Utils.getCreatorName;

public class DBInteractor implements DBReader, DBWriter {
    private static Logger logger = LogManager.getLogger(DBInteractor.class);

    private final MySQLAdaptor dbAdaptor;
    private final long personId;

    private GKInstance instanceEdit;

    private final ReferenceMoleculeNameChangeReporter referenceMoleculeNameChangeReporter;
    private final ReferenceMoleculeFormulaChangeReporter referenceMoleculeFormulaChangeReporter;
    private final SimpleEntityNameChangeReporter simpleEntityNameChangeReporter;

    public DBInteractor(MySQLAdaptor dbAdaptor, long personId) {
        this.dbAdaptor = dbAdaptor;
        this.personId = personId;

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
            List<String> updatedSimpleEntityNames = getUpdatedSimpleEntityNames(referenceMolecule, simpleEntity, newName);

            if (simpleEntityNames.equals(updatedSimpleEntityNames)) {
                continue;
            }

            simpleEntity.setAttributeValue(ReactomeJavaConstants.name, updatedSimpleEntityNames);
            getDbAdaptor().updateInstanceAttribute(simpleEntity, ReactomeJavaConstants.name);
            updateModifiedInstanceEdits(simpleEntity);

            this.simpleEntityNameChangeReporter.report(
                simpleEntity.getDBID().toString(),
                getCreatorName(getCreator(simpleEntity)),
                simpleEntity.getDisplayName(),
                newName,
                updatedSimpleEntityNames.toString()
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

    public boolean updateModifiedInstanceEdits(GKInstance instance) throws Exception {
        instance.getAttributeValuesList(ReactomeJavaConstants.modified);
        instance.addAttributeValue(ReactomeJavaConstants.modified, getInstanceEdit());
        getDbAdaptor().updateInstanceAttribute(instance, ReactomeJavaConstants.modified);
        return true;
    }

    GKInstance getInstanceEdit() throws Exception {
        if (instanceEdit == null) {
            instanceEdit = new GKInstance(getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit));
            instanceEdit.setDbAdaptor(getDbAdaptor());
            instanceEdit.setAttributeValue(ReactomeJavaConstants.note, "ChEBI Update");
            instanceEdit.setAttributeValue(ReactomeJavaConstants.author, getPersonInstance());
            instanceEdit.setAttributeValue(ReactomeJavaConstants.dateTime, getCurrentDateTime());
            InstanceDisplayNameGenerator.setDisplayName(instanceEdit);
        }

        return instanceEdit;
    }

    private List<String> getUpdatedSimpleEntityNames(
        GKInstance referenceMolecule, GKInstance simpleEntity, String newChEBIName) throws Exception {

        List<String> simpleEntityNames = getSimpleEntityInstanceNames(simpleEntity);

        List<String> referenceMoleculeNames = getReferenceMoleculeNames(referenceMolecule);

        String firstReferenceMoleculeName = referenceMoleculeNames.get(0);
        String firstSimpleEntityName = simpleEntityNames.get(0);
        if (firstReferenceMoleculeName.equalsIgnoreCase(firstSimpleEntityName)) {
            simpleEntityNames.remove(newChEBIName);
            simpleEntityNames.add(0, newChEBIName);
        } else {
            // When the reference molecule and simple entity don't share the same first value in their
            // name lists, the first name of the simple entity is assumed to be specially picked by the
            // curator.  The second and third names are checked to see if they are the new ChEBI name and
            // reference molecule name, respectively.  This is because the two slots after the
            // curator's chosen name should be reserved for them.  If not, they are put into the
            // second and third slots by this branch.
            if (secondSimpleEntityNameIsChEBIName(simpleEntityNames, newChEBIName) &&
                thirdSimpleEntityNameIsReferenceMoleculeName(simpleEntityNames, firstReferenceMoleculeName)) {
                return simpleEntityNames;
            }

            simpleEntityNames.remove(newChEBIName);
            simpleEntityNames.add(0, newChEBIName);

            simpleEntityNames.remove(firstReferenceMoleculeName);
            simpleEntityNames.add(1, firstReferenceMoleculeName);

        }
        return simpleEntityNames;
    }

    private boolean secondSimpleEntityNameIsChEBIName(List<String> simpleEntityNames, String chEBIName) {
        if (simpleEntityNames.size() < 2) {
            return false;
        }

        final String secondSimpleEntityName = simpleEntityNames.get(1);

        return secondSimpleEntityName != null && secondSimpleEntityName.equalsIgnoreCase(chEBIName);
    }

    private boolean thirdSimpleEntityNameIsReferenceMoleculeName(List<String> simpleEntityNames, String referenceMoleculeName) {
        if (simpleEntityNames.size() < 3) {
            return false;
        }

        final String thirdSimpleEntityName = simpleEntityNames.get(2);

        return thirdSimpleEntityName != null && thirdSimpleEntityName.equalsIgnoreCase(referenceMoleculeName);
    }

    private List<String> getReferenceMoleculeNames(GKInstance referenceMolecule) throws Exception {
        return referenceMolecule.getAttributeValuesList(ReactomeJavaConstants.name);
    }

    private Schema getSchema() throws Exception {
        if (getDbAdaptor().getSchema() == null) {
            getDbAdaptor().fetchSchema();
        }

        return getDbAdaptor().getSchema();
    }

    private GKInstance getPersonInstance() throws Exception {
        return getDbAdaptor().fetchInstance(getPersonId());
    }

    private String getCurrentDateTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"));
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


    private <E> List<E> safeList(List<E> list) {
        return list != null ? list : new ArrayList<>();
    }

    private MySQLAdaptor getDbAdaptor() {
        return this.dbAdaptor;
    }

    private long getPersonId() {
        return this.personId;
    }
}
