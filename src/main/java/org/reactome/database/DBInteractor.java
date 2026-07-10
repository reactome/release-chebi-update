package org.reactome.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.reports.ReferenceMoleculeFormulaChangeReporter;
import org.reactome.reports.ReferenceMoleculeNameChangeReporter;
import org.reactome.reports.SimpleEntityNameChangeReporter;
import org.reactome.utils.CuratorToolAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.reactome.reports.Utils.getCreatorName;

public class DBInteractor implements DBReader, DBWriter {
    private static Logger logger = LogManager.getLogger(DBInteractor.class);

    private final long personId;

    private CuratorToolAPI curatorToolAPI;

    private final ReferenceMoleculeNameChangeReporter referenceMoleculeNameChangeReporter;
    private final ReferenceMoleculeFormulaChangeReporter referenceMoleculeFormulaChangeReporter;
    private final SimpleEntityNameChangeReporter simpleEntityNameChangeReporter;

    // The full ChEBI ReferenceMolecule set is expensive to load (a Neo4j round-trip per instance), so load
    // it once and reuse. identifierToReferenceMolecules indexes that set by ChEBI identifier so duplicate
    // look-ups are in-memory instead of re-fetching+re-inflating every molecule on each call (former O(N^2)).
    private List<SimpleInstance> allChEBIReferenceMoleculesCache;
    private Map<String, List<SimpleInstance>> identifierToReferenceMolecules;

    public DBInteractor(long personId) {
        this.personId = personId;

        this.curatorToolAPI = new CuratorToolAPI();

        this.referenceMoleculeNameChangeReporter = new ReferenceMoleculeNameChangeReporter();
        this.referenceMoleculeFormulaChangeReporter = new ReferenceMoleculeFormulaChangeReporter();
        this.simpleEntityNameChangeReporter = new SimpleEntityNameChangeReporter();
    }

    @Override
    public List<SimpleInstance> getAllChEBIReferenceMoleculeInstances() {
        // Load once and memoize; every subsequent caller (including the identifier index) reuses it
        // instead of triggering another full fetch + per-instance inflate.
        if (allChEBIReferenceMoleculesCache == null) {
            allChEBIReferenceMoleculesCache = curatorToolAPI.fetchChEBIReferenceMoleculeInstances();
        }
        return allChEBIReferenceMoleculesCache;
    }

    @Override
    public List<SimpleInstance> getReferenceMoleculesWithChEBIIdentifier(String chEBIId) {
        // In-memory lookup against the identifier index rather than re-fetching all molecules per call.
        return getIdentifierToReferenceMolecules().getOrDefault(chEBIId, Collections.emptyList());
    }

    /**
     * Index of ChEBI identifier -> ReferenceMolecule instances carrying that identifier, built once from the
     * memoized full set. The chebi-update run never mutates ReferenceMolecule identifiers (only name/formula/
     * displayName), so this index stays valid for the whole run.
     */
    private Map<String, List<SimpleInstance>> getIdentifierToReferenceMolecules() {
        if (identifierToReferenceMolecules == null) {
            Map<String, List<SimpleInstance>> index = new HashMap<>();
            for (SimpleInstance referenceMolecule : getAllChEBIReferenceMoleculeInstances()) {
                Object identifier = referenceMolecule.getAttribute(ReactomeJavaConstants.identifier);
                if (identifier == null) {
                    continue;
                }
                index.computeIfAbsent(identifier.toString(), key -> new ArrayList<>()).add(referenceMolecule);
            }
            identifierToReferenceMolecules = index;
        }
        return identifierToReferenceMolecules;
    }

    @Override
    public boolean updateSimpleEntityReferrersNames(SimpleInstance referenceMolecule, String newName) throws Exception {
        boolean anySimpleEntityNameUpdated = false;
        for (SimpleInstance simpleEntity : getReferenceMoleculeReferrers(referenceMolecule)) {

            List<String> simpleEntityNames = getSimpleEntityInstanceNames(simpleEntity);
            List<String> updatedSimpleEntityNames = getUpdatedSimpleEntityNames(referenceMolecule, simpleEntity, newName);

            if (simpleEntityNames.equals(updatedSimpleEntityNames)) {
                continue;
            }

            boolean shouldAutoUpdateSimpleEntityNames = !differentFirstNames(simpleEntityNames, updatedSimpleEntityNames);

            if (shouldAutoUpdateSimpleEntityNames) {
                simpleEntity.setAttribute(ReactomeJavaConstants.name, updatedSimpleEntityNames);
                // curator-tool-ws's curation schema exposes SimpleEntity's referenceEntity slot as
                // "referenceEntityList", which has no graph-core accessor, so inflate() never carries the
                // referenceEntity link. Without re-attaching it, the commit's resetNode deletes the outgoing
                // (SimpleEntity)-[:referenceEntity]->(ReferenceMolecule) edge and store() cannot recreate it.
                // This SimpleEntity is a referenceEntity referrer of referenceMolecule, so re-link it here.
                SimpleInstance referenceMoleculeShell = new SimpleInstance();
                referenceMoleculeShell.setDbId(referenceMolecule.getDbId());
                referenceMoleculeShell.setSchemaClassName(referenceMolecule.getSchemaClassName());
                simpleEntity.setAttribute(ReactomeJavaConstants.referenceEntity, referenceMoleculeShell);
                updateInDb(simpleEntity);
            }

            this.simpleEntityNameChangeReporter.report(
                simpleEntity.getDbId().toString(),
                getCreatorName(simpleEntity),
                simpleEntity.getDisplayName(),
                newName,
                simpleEntityNames.toString(),
                updatedSimpleEntityNames.toString(),
                String.valueOf(shouldAutoUpdateSimpleEntityNames)
            );

            anySimpleEntityNameUpdated = true;
        }

        return anySimpleEntityNameUpdated;
    }

    @Override
    public boolean stageUpdateForReferenceMoleculeName(SimpleInstance referenceMolecule, String newName) throws Exception {
        // TODO Check with Lisa and Peter if this implementation is correct - do we want to maintain old names
        //  and/or move the new name to be first for the reference molecule?

        List<String> referenceMoleculeNames =
            safeList((List<String>) referenceMolecule.getAttribute(ReactomeJavaConstants.name));

        if (referenceMoleculeNames.contains(newName)) {
            return false;
        }

        this.referenceMoleculeNameChangeReporter.report(
            referenceMolecule.getDbId().toString(),
            getCreatorName(referenceMolecule),
            referenceMolecule.getDisplayName(),
            referenceMoleculeNames.get(0),
            newName
        );

        referenceMoleculeNames.add(0, newName);
        referenceMolecule.setAttribute(ReactomeJavaConstants.name, referenceMoleculeNames);

        return true;
    }

    @Override
    public boolean stageUpdateForReferenceMoleculeFormula(SimpleInstance referenceMolecule, String newFormula) throws Exception {
        if (newFormula == null || newFormula.isEmpty()) {
            return false;
        }

        String existingFormula = (String) referenceMolecule.getAttribute(ReactomeJavaConstants.formula);
        if (newFormula.equals(existingFormula)) {
            return false;
        }

        referenceMolecule.setAttribute(ReactomeJavaConstants.formula, newFormula);

        this.referenceMoleculeFormulaChangeReporter.report(
            referenceMolecule.getDbId().toString(),
            getCreatorName(referenceMolecule),
            referenceMolecule.getDisplayName(),
            existingFormula,
            newFormula
        );

        return true;
    }

    @Override
    public void stageUpdateForReferenceMoleculeDisplayName(SimpleInstance referenceMolecule) {
        StringBuilder referenceMoleculeDisplayNameBuilder = new StringBuilder();

        Object nameVal = referenceMolecule.getAttribute("name");
        String name = (nameVal instanceof List)
            ? (((List<?>) nameVal).isEmpty() ? null : String.valueOf(((List<?>) nameVal).get(0)))
            : (String) nameVal;
        if (name != null) {
            referenceMoleculeDisplayNameBuilder.append(name);
        }

        Object refDb = referenceMolecule.getAttribute("referenceDatabase");
        referenceMoleculeDisplayNameBuilder.append(" [").append(refDb instanceof SimpleInstance
            ? ((SimpleInstance) refDb).getDisplayName() : "unknown");

        Object id = referenceMolecule.getAttribute("identifier");
        referenceMoleculeDisplayNameBuilder.append(":").append(id != null ? id : "unknown").append("]");

        referenceMolecule.setDisplayName(referenceMoleculeDisplayNameBuilder.toString());
    }

    public void closeReports() throws IOException {
        this.referenceMoleculeNameChangeReporter.writeFooterIfInitialized();
        this.referenceMoleculeFormulaChangeReporter.writeFooterIfInitialized();
        this.simpleEntityNameChangeReporter.writeFooterIfInitialized();
    }

    @Override
    public void updateInDb(SimpleInstance instance) {
        instance.setDefaultPersonId(getPersonId());
        curatorToolAPI.commit(instance);
    }

    /**
     * Run the given work inside a single Neo4j transaction (when available) so the multiple commits it makes
     * are flushed together rather than each in its own transaction. Falls back to running as-is otherwise.
     */
    public void runInTransaction(Runnable work) {
        curatorToolAPI.runInTransaction(work);
    }

    public SimpleInstance inflate(SimpleInstance shellInstance) {
        return curatorToolAPI.inflate(shellInstance);
    }

    private List<String> getUpdatedSimpleEntityNames(
        SimpleInstance referenceMolecule, SimpleInstance simpleEntity, String newChEBIName) throws Exception {

        List<String> simpleEntityNames = getSimpleEntityInstanceNames(simpleEntity);
        if (simpleEntityNames.isEmpty()) {
            throw new IllegalStateException("Simple entity has no names");
        }

        List<String> referenceMoleculeNames = getReferenceMoleculeNames(referenceMolecule);
        if (referenceMoleculeNames.isEmpty()) {
            throw new IllegalStateException("Reference molecule has no names");
        }

        String firstReferenceMoleculeName = referenceMoleculeNames.get(0);
        String firstSimpleEntityName = simpleEntityNames.get(0);
        if (firstReferenceMoleculeName.equalsIgnoreCase(firstSimpleEntityName)) {
            if (!newChEBIName.equalsIgnoreCase(firstSimpleEntityName) ||
                newChEBIName.equals(firstSimpleEntityName)) {
                simpleEntityNames.remove(newChEBIName);
                simpleEntityNames.add(0, newChEBIName);
            }
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

            // Remove existing occurrences
            simpleEntityNames.remove(newChEBIName);
            simpleEntityNames.remove(firstReferenceMoleculeName);

            // Insert after the first element
            int index = Math.min(1, simpleEntityNames.size());

            simpleEntityNames.add(index, newChEBIName);

            // Only add the second if it is different
            if (!newChEBIName.equals(firstReferenceMoleculeName)) {
                simpleEntityNames.add(index + 1, firstReferenceMoleculeName);
            }
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

    private boolean differentFirstNames(List<String> simpleEntityNames, List<String> updatedSimpleEntityNames) {
        if ((simpleEntityNames == null || simpleEntityNames.isEmpty()) &&
            (updatedSimpleEntityNames == null || updatedSimpleEntityNames.isEmpty())) {
            return false;
        }

        if ((simpleEntityNames == null || simpleEntityNames.isEmpty()) ||
            (updatedSimpleEntityNames == null || updatedSimpleEntityNames.isEmpty())) {
            return true;
        }

        String simpleEntityFirstName = simpleEntityNames.get(0);
        String updatedSimpleEntityFirstName = updatedSimpleEntityNames.get(0);

        return !simpleEntityFirstName.equals(updatedSimpleEntityFirstName);
    }

    private List<String> getReferenceMoleculeNames(SimpleInstance referenceMolecule) throws Exception {
        return (List<String>) referenceMolecule.getAttribute(ReactomeJavaConstants.name);
    }

    private List<SimpleInstance> getReferenceMoleculeReferrers(SimpleInstance referenceMolecule) throws Exception {
        return curatorToolAPI.getReferrers(referenceMolecule, ReactomeJavaConstants.referenceEntity)
            .stream()
            .map(this::inflate)
            .collect(Collectors.toList());
    }

    private List<String> getSimpleEntityInstanceNames(SimpleInstance simpleEntityInstance) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) simpleEntityInstance.getAttribute(ReactomeJavaConstants.name);
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(names);
    }

    private <E> List<E> safeList(List<E> list) {
        return list != null ? list : new ArrayList<>();
    }

    private long getPersonId() {
        return this.personId;
    }

    public void close() {
        this.curatorToolAPI.close();
    }
}
