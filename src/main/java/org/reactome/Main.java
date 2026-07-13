package org.reactome;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.curation.model.SimpleInstance;
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

import static org.reactome.reports.Utils.getCreatorName;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class);

    private static DBInteractor dbInteractor;
    private static FailedChEBILookupReporter failedChEBILookupReporter;
    private static ReferenceMoleculeChEBIIdentifierChangeReporter referenceMoleculeChEBIIdentifierChangeReporter;

    public static void main(String[] args) throws Exception {
        String configFilePath = args.length > 0 ? args[0] : "src/main/resources/config.properties";
        Properties configProperties = getConfigProperties(configFilePath);
        dbInteractor = new DBInteractor(getPersonId(configProperties));

        List<SimpleInstance> referenceMolecules = dbInteractor.getAllChEBIReferenceMoleculeInstances();
        logger.info("Updating reference molecules...");
        updateReferenceMolecules(referenceMolecules);
        logger.info("Done updating reference molecules");

        logger.info("Checking for duplicate reference molecules...");
        checkForDuplicates(referenceMolecules);
        logger.info("Done checking for duplicate reference molecules");

        logger.info("Finished ChEBI update - please check report files for details");
        dbInteractor.close();
    }

    private static void updateReferenceMolecules(List<SimpleInstance> referenceMolecules) throws Exception {
        failedChEBILookupReporter = new FailedChEBILookupReporter();
        referenceMoleculeChEBIIdentifierChangeReporter = new ReferenceMoleculeChEBIIdentifierChangeReporter();

        logger.info("Found " + referenceMolecules.size() + " reference molecules to process");

        final int batchSize = 500;
        int processedCount = 0;
        for (List<SimpleInstance> referenceMoleculeBatch : getReferenceMoleculeBatches(referenceMolecules, batchSize)) {
            updateReferenceMoleculeBatch(referenceMoleculeBatch);

            processedCount += referenceMoleculeBatch.size();
            logger.info("Finished processing " + processedCount + " reference molecules");
        }

        failedChEBILookupReporter.writeFooterIfInitialized();
        referenceMoleculeChEBIIdentifierChangeReporter.writeFooterIfInitialized();
        dbInteractor.closeReports();
    }

    private static void updateReferenceMoleculeBatch(List<SimpleInstance> referenceMoleculeBatch) throws Exception {
        ChEBIEntityRetriever chEBIEntityRetriever = new ChEBIEntityRetriever();

        Map<SimpleInstance, Optional<ChEBIEntity>> referenceMoleculeToPotentialChEBIEntity =
            chEBIEntityRetriever.getDbInstanceToChEBIEntityMap(referenceMoleculeBatch);

        for (SimpleInstance referenceMolecule : referenceMoleculeToPotentialChEBIEntity.keySet() ) {
            Optional<ChEBIEntity> potentialChEBIEntity = referenceMoleculeToPotentialChEBIEntity.get(referenceMolecule);

            // Batch each molecule's commits (its SimpleEntity referrer updates + the ReferenceMolecule
            // itself) into one Neo4j transaction to cut per-commit transaction overhead. Per-molecule
            // granularity keeps transactions bounded and makes each molecule's update atomic.
            potentialChEBIEntity.ifPresentOrElse(chEBIEntity -> {
                dbInteractor.runInTransaction(
                    () -> updateReferenceMoleculeWithChEBIEntity(referenceMolecule, chEBIEntity));
            }, () -> logFailedChEBIEntityLookUp(referenceMolecule));
        }
    }

    private static List<List<SimpleInstance>> getReferenceMoleculeBatches(
        List<SimpleInstance> referenceMolecules, int batchSize) {

        return Lists.partition(referenceMolecules, batchSize);
    }

    private static void checkForDuplicates(List<SimpleInstance> referenceMolecules) throws Exception {
        DuplicateChecker duplicateChecker = new DuplicateChecker(referenceMolecules);
        duplicateChecker.findAndLogDuplicates();
    }

    // TODO Move (second try-block?) to DBInteractor class?
    private static void updateReferenceMoleculeWithChEBIEntity(SimpleInstance referenceMolecule, ChEBIEntity chEBIEntity) {

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

            boolean referenceMoleculeNameToBeUpdated = dbInteractor.stageUpdateForReferenceMoleculeName(referenceMolecule, newChEBIName);
            boolean formulaToBeUpdated = dbInteractor.stageUpdateForReferenceMoleculeFormula(referenceMolecule, newFormula);
            if (referenceMoleculeNameToBeUpdated || formulaToBeUpdated) {
                dbInteractor.stageUpdateForReferenceMoleculeDisplayName(referenceMolecule);

                dbInteractor.updateInDb(referenceMolecule);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update reference molecule: " + referenceMolecule, e);
        }
    }

    private static void logFailedChEBIEntityLookUp(SimpleInstance referenceMolecule) {
        try {
            failedChEBILookupReporter.report(
                referenceMolecule.getDbId().toString(),
                getCreatorName(referenceMolecule),
                referenceMolecule.getDisplayName()
            );
        } catch (Exception e) {
            throw new RuntimeException("Unable to write to failedChEBILookup reporter", e);
        }
    }

    private static void logIfReferenceMoleculeIdentifierChanged(SimpleInstance referenceMolecule, String newChEBIId)
        throws Exception {

        String existingChEBIId = (String) referenceMolecule.getAttribute(ReactomeJavaConstants.identifier);
        if (newChEBIId.equals(existingChEBIId)) {
            return;
        }

        List<SimpleInstance> refMolsWithNewIdentifier = dbInteractor.getReferenceMoleculesWithChEBIIdentifier(newChEBIId);
        if (refMolsWithNewIdentifier.isEmpty()) {
            logReferenceMoleculeIdentifierChange(referenceMolecule, newChEBIId, null);
        }

        for (SimpleInstance referenceMoleculeWithNewIdentifier : refMolsWithNewIdentifier) {
            logReferenceMoleculeIdentifierChange(referenceMolecule, newChEBIId, referenceMoleculeWithNewIdentifier);
        }
    }

    private static void logReferenceMoleculeIdentifierChange(
        SimpleInstance referenceMolecule, String newChEBIId, SimpleInstance newReferenceMolecule) throws Exception {

        String existingChEBIId = (String) referenceMolecule.getAttribute(ReactomeJavaConstants.identifier);

        referenceMoleculeChEBIIdentifierChangeReporter.report(
            referenceMolecule.getDbId().toString(),
            getCreatorName(referenceMolecule),
            referenceMolecule.getDisplayName(),
            existingChEBIId,
            newChEBIId,
            newReferenceMolecule != null ?
                newReferenceMolecule.getDbId().toString() : "No new Reference Molecule DB_ID",
            getReferenceMoleculeReferrerDbIds(referenceMolecule),
            newReferenceMolecule != null ? getReferenceMoleculeReferrerDbIds(newReferenceMolecule) :
                "No simple entities DB_IDs for non-existent new Reference Molecule"
        );
    }

    private static String getReferenceMoleculeReferrerDbIds(SimpleInstance referenceMolecule) throws Exception {
        return dbInteractor.getReferrerInstances(referenceMolecule, ReactomeJavaConstants.referenceEntity)
            .stream()
            .map(referrer -> referrer.getDbId().toString())
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
