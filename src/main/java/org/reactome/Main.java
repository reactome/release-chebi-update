package org.reactome;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.database.DBInteractor;
import org.reactome.model.ChEBIEntity;
import org.reactome.reports.FailedChEBILookupReporter;
import org.reactome.reports.ReferenceMoleculeChEBIIdentifierChangeReporter;
import org.reactome.webservice.ChEBIEntityRetriever;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.reactome.reports.Utils.getCreator;
import static org.reactome.reports.Utils.getCreatorName;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class);

    private static DBInteractor dbInteractor;
    private static FailedChEBILookupReporter failedChEBILookupReporter;
    private static ReferenceMoleculeChEBIIdentifierChangeReporter referenceMoleculeChEBIIdentifierChangeReporter;

    public static void main(String[] args) throws Exception {
        String configFilePath = args.length > 0 ? args[0] : "src/main/resources/config.properties";
        Properties configProperties = getConfigProperties(configFilePath);
        dbInteractor = new DBInteractor(getCuratorDbAdaptor(configProperties), getPersonId(configProperties));

        dbInteractor.startTransaction();

        List<GKInstance> referenceMolecules = dbInteractor.getAllChEBIReferenceMoleculeInstances();
        logger.info("Updating reference molecules...");
        updateReferenceMolecules(referenceMolecules);
        logger.info("Done updating reference molecules");

        logger.info("Checking for duplicate reference molecules...");
        checkForDuplicates(referenceMolecules);
        logger.info("Done checking for duplicate reference molecules");

        dbInteractor.commit();

        logger.info("Finished ChEBI update - please check report files for details");
    }

    private static void updateReferenceMolecules(List<GKInstance> referenceMolecules) throws Exception {
        failedChEBILookupReporter = new FailedChEBILookupReporter();
        referenceMoleculeChEBIIdentifierChangeReporter = new ReferenceMoleculeChEBIIdentifierChangeReporter();

        logger.info("Found " + referenceMolecules.size() + " reference molecules to process");

        final int batchSize = 500;
        int processedCount = 0;
        for (List<GKInstance> referenceMoleculeBatch : getReferenceMoleculeBatches(referenceMolecules, batchSize)) {
            updateReferenceMoleculeBatch(referenceMoleculeBatch);

            processedCount += referenceMoleculeBatch.size();
            logger.info("Finished processing " + processedCount + " reference molecules");
        }
    }

    private static void updateReferenceMoleculeBatch(List<GKInstance> referenceMoleculeBatch) throws Exception {
        ChEBIEntityRetriever chEBIEntityRetriever = new ChEBIEntityRetriever();

        Map<GKInstance, Optional<ChEBIEntity>> referenceMoleculeToPotentialChEBIEntity =
            chEBIEntityRetriever.getChEBIEntities(referenceMoleculeBatch);

        for (GKInstance referenceMolecule : referenceMoleculeToPotentialChEBIEntity.keySet() ) {
            Optional<ChEBIEntity> potentialChEBIEntity = referenceMoleculeToPotentialChEBIEntity.get(referenceMolecule);

            potentialChEBIEntity.ifPresentOrElse(chEBIEntity -> {
                updateReferenceMoleculeWithChEBIEntity(referenceMolecule, chEBIEntity);
            }, () -> logFailedChEBIEntityLookUp(referenceMolecule));
        }
    }

    private static List<List<GKInstance>> getReferenceMoleculeBatches(
        List<GKInstance> referenceMolecules, int batchSize) {

        return Lists.partition(referenceMolecules, batchSize);
    }

    private static void checkForDuplicates(List<GKInstance> referenceMolecules) throws Exception {
        DuplicateChecker duplicateChecker = new DuplicateChecker(referenceMolecules);
        duplicateChecker.findAndLogDuplicates();
    }

    // TODO Move (second try-block?) to DBInteractor class?
    private static void updateReferenceMoleculeWithChEBIEntity(GKInstance referenceMolecule, ChEBIEntity chEBIEntity) {

        String newChEBIId = chEBIEntity.getChEBIId();
        String newChEBIName = chEBIEntity.getName();
        String newFormula = chEBIEntity.getFormula();

        try {
            logIfReferenceMoleculeIdentifierChanged(referenceMolecule, newChEBIId);
        } catch (Exception e) {
            throw new RuntimeException(
                "Unable to log reference molecule identifier change for " + referenceMolecule, e);
        }

        try {
            dbInteractor.updateSimpleEntityReferrersNames(referenceMolecule, newChEBIName);

            boolean referenceMoleculeNameUpdated = dbInteractor.updateReferenceMoleculeName(referenceMolecule, newChEBIName);
            boolean formulaUpdated = dbInteractor.updateReferenceMoleculeFormula(referenceMolecule, newFormula);
            if (referenceMoleculeNameUpdated || formulaUpdated) {
                dbInteractor.updateReferenceMoleculeDisplayName(referenceMolecule);
                dbInteractor.updateModifiedInstanceEdits(referenceMolecule);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update reference molecule: " + referenceMolecule, e);
        }
    }

    private static void logFailedChEBIEntityLookUp(GKInstance referenceMolecule) {
        try {
            failedChEBILookupReporter.report(
                referenceMolecule.getDBID().toString(),
                getCreatorName(getCreator(referenceMolecule)),
                referenceMolecule.getDisplayName()
            );
        } catch (Exception e) {
            throw new RuntimeException("Unable to write to failedChEBILookup reporter", e);
        }
    }

    private static void logIfReferenceMoleculeIdentifierChanged(GKInstance referenceMolecule, String newChEBIId)
        throws Exception {

        String existingChEBIId = (String) referenceMolecule.getAttributeValue(ReactomeJavaConstants.identifier);
        if (newChEBIId.equals(existingChEBIId)) {
            return;
        }

        List<GKInstance> refMolsWithNewIdentifier = dbInteractor.getReferenceMoleculesWithChEBIIdentifier(newChEBIId);
        if (refMolsWithNewIdentifier.isEmpty()) {
            logReferenceMoleculeIdentifierChange(referenceMolecule, newChEBIId, null);
        }

        for (GKInstance referenceMoleculeWithNewIdentifier : refMolsWithNewIdentifier) {
            logReferenceMoleculeIdentifierChange(referenceMolecule, newChEBIId, referenceMoleculeWithNewIdentifier);
        }
    }

    private static void logReferenceMoleculeIdentifierChange(
        GKInstance referenceMolecule, String newChEBIId, GKInstance newReferenceMolecule) throws Exception {

        String existingChEBIId = (String) referenceMolecule.getAttributeValue(ReactomeJavaConstants.identifier);

        referenceMoleculeChEBIIdentifierChangeReporter.report(
            referenceMolecule.getDBID().toString(),
            getCreatorName(getCreator(referenceMolecule)),
            referenceMolecule.getDisplayName(),
            existingChEBIId,
            newChEBIId,
            newReferenceMolecule != null ?
                newReferenceMolecule.getDBID().toString() : "No new Reference Molecule DB_ID",
            getReferenceMoleculeReferrerDbIds(referenceMolecule),
            newReferenceMolecule != null ? getReferenceMoleculeReferrerDbIds(newReferenceMolecule) :
                "No simple entities DB_IDs for non-existent new Reference Molecule"
        );
    }

    private static String getReferenceMoleculeReferrerDbIds(GKInstance referenceMolecule) throws Exception {
        Collection<GKInstance> referrers =
            ((Collection<GKInstance>) referenceMolecule.getReferers(ReactomeJavaConstants.referenceEntity));

        if (referrers == null) {
            return "";
        }

        return referrers.stream()
            .map(referrer -> referrer.getDBID().toString())
            .collect(Collectors.joining("|"));
    }

    private static Properties getConfigProperties(String configFilePath) throws IOException {
        Properties configProperties = new Properties();
        configProperties.load(Files.newInputStream(Path.of(configFilePath)));

        return configProperties;
    }

    private static MySQLAdaptor getCuratorDbAdaptor(Properties configProperties) throws SQLException {
        final String prefix = "curator.database";

        String host = configProperties.getProperty(prefix + ".host", "localhost");
        String dbName = configProperties.getProperty(prefix + ".name");
        String user = configProperties.getProperty(prefix + ".user", "root");
        String password = configProperties.getProperty(prefix + ".password", "root");
        int port = Integer.parseInt(configProperties.getProperty(prefix + ".port", "3306"));

        return new MySQLAdaptor(host, dbName, user, password, port);
    }

    private static long getPersonId(Properties configProperties) {
        return Long.parseLong(configProperties.getProperty("personId"));
    }
}
