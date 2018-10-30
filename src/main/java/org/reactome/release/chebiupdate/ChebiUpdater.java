package org.reactome.release.chebiupdate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.reactome.release.common.database.InstanceEditUtils;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

/**
 * Updates the ReferenceMolecules with new information from ChEBI.
 * @author sshorser
 *
 */
public class ChebiUpdater
{
	private static final Logger logger = LogManager.getLogger();
	private static final Logger refMolNameChangeLog = LogManager.getLogger("molNameChangeLog");
	private static final Logger refMolIdentChangeLog = LogManager.getLogger("molIdentChangeLog");
	private static final Logger refEntChangeLog = LogManager.getLogger("refEntChangeLog");
	private static final Logger duplicatesLog = LogManager.getLogger("duplicatesLog");
	private static final Logger failedChebiLookupsLog = LogManager.getLogger("failedChebiLookupsLog");
	private boolean testMode = true;
	private MySQLAdaptor adaptor;
	private ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
	private StringBuilder formulaUpdateSB = new StringBuilder();
	private StringBuilder formulaFillSB = new StringBuilder();
	private StringBuilder duplicatesSB = new StringBuilder();
	private Map<GKInstance, List<String>> referenceEntityChanges = new HashMap<GKInstance, List<String>>();
	private long personID;
	private boolean useCache;
	private Comparator<GKInstance> personComparator;
	
	/**
	 * Create a ChebiUpdater
	 * @param adaptor - The database adaptor
	 * @param testMode - Set testMode to TRUE if you want to perform a dry-run. Set to FALSE if you actually want to commit to the database.
	 * @param personID - The DB_ID of the Person whom the InstanceEdits will be associated with.
	 * @param useCache - Set to TRUE to use the cache: If there's a file, load it. If there's no file, write one. If FALSE, the cache file will not be read and it will not be written.
	 */
	public ChebiUpdater(MySQLAdaptor adaptor, boolean testMode, long personID, boolean useCache)
	{
		this.adaptor = adaptor;
		this.testMode = testMode;
		this.personID = personID;
		this.useCache = useCache;

		// A Comparator object that will compare GKInstances, assuming that they are of the "Person" type, with a surname and firstname.		
		this.personComparator = new Comparator<GKInstance>()
		{
			@Override
			public int compare(GKInstance o1, GKInstance o2)
			{
				try
				{
					String surname1 = (String)o1.getAttributeValue(ReactomeJavaConstants.surname);
					String surname2 = (String)o2.getAttributeValue(ReactomeJavaConstants.surname);
					int surnameCompare = surname1.compareTo(surname2);
					// If surnames are the same, compare firstnames.
					if (surnameCompare == 0)
					{
						String firstname1 = (String)o1.getAttributeValue(ReactomeJavaConstants.firstname);
						String firstname2 = (String)o2.getAttributeValue(ReactomeJavaConstants.firstname);
						// MUST return result of firstname-comparison, we're not going any deeper than firstname for Person comparisons.
						return firstname1.compareTo(firstname2);
					}
					return surnameCompare;
				}
				catch (Exception e)
				{
					logger.error("Error while trying to compare objects: o1: "+o1.toString()+ " ; o2: "+o2.toString() + " ; they will be treated as equivalent.");
					e.printStackTrace();
				}
				return 0;
			}
		};
	}

	/**
	 * Update ChEBI ReferenceMolecules. This method will query ChEBI for up-to-date
	 * information, and using that information it will: <br/>
	 * <ul>
	 * <li>Update the names of ReferenceEntitites that are refer to the
	 * ReferenceMolecule</li>
	 * <li>Update the names of ReferenceMolecules</li>
	 * <li>Update the identifiers of ReferenceMolecules</li>
	 * <li>Update the formulae of ReferenceMolecules</li>
	 * </ul>
	 * 
	 * @throws SQLException
	 * @throws Exception
	 */
	public void updateChebiReferenceMolecules() throws SQLException, Exception
	{
		@SuppressWarnings("unchecked")
		String chebiRefDBID = (new ArrayList<GKInstance>( adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI"))).get(0).getDBID().toString();

		@SuppressWarnings("unchecked")
		Collection<GKInstance> refMolecules = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);

		// A map of the ReferenceMolecules where we could not get info from ChEBI.
		// GKInstances (molecules) map to the message about the failure.
		Map<GKInstance, String> failedEntitiesMap = Collections.synchronizedMap(new HashMap<GKInstance, String>());

		logger.info("{} ChEBI ReferenceMolecules to check...", refMolecules.size());

		// A map: key is the DB_ID of a ReferneceMolecule, value is the
		// uk.ac.ebi.chebi.webapps.chebiWS.model.Entity from ChEBI.
		Map<Long, Entity> entityMap = retrieveUpdatesFromChebi(refMolecules, failedEntitiesMap);

		logger.info("Number of entities we were able to retrieve information about: {}", entityMap.size());
		logger.info("Number of entities we were NOT able to retrieve information about: {}", failedEntitiesMap.size());

		failedChebiLookupsLog.info("# DB_ID\tReferenceMolecule\tReason");
		for (GKInstance molecule : failedEntitiesMap.keySet())
		{
			failedChebiLookupsLog.info("{}\t{}\t{}", molecule.getDBID(), molecule.toString(), failedEntitiesMap.get(molecule));
		}
		
		// print headers for log files
		refMolIdentChangeLog.info("# DB_ID\tReference Molecule\tDeprecated Identifier\tReplacement Identifier\tAffected referenceEntity DB_IDs\tDB_ID of Molecule with Replacement Identifier\tDB_IDs of referenceEntities of Molecule with Replacement Identifier");
		refMolNameChangeLog.info("# DB_ID\tReference Molecule\tOld Name\tNew Name");
		// Begin the transaction (all database-write activities in this process should take place within a single transaction).
		adaptor.startTransaction();
		GKInstance instanceEdit = InstanceEditUtils.createInstanceEdit(this.adaptor, this.personID, this.getClass().getCanonicalName());
		// Process the results that we got back from querying the ChEBI web service. Each key in the map is the DB ID of a molecule 
		// whose ChEBI Identifier was sent as a query to the ChEBI web service.
		for (Long moleculeDBID : entityMap.keySet())
		{
			GKInstance molecule = adaptor.fetchInstance(moleculeDBID);
			Entity entity = entityMap.get(moleculeDBID);

			// Not sure why the old Perl code stripped
			// out "CHEBI:", but I'll do it here for consistency.
			String chebiID = entity.getChebiId().replaceAll("CHEBI:", ""); 

			String chebiName = entity.getChebiAsciiName();
			List<DataItem> chebiFormulae = entity.getFormulae();
			
			reportIfMoleculeIdentifierChanged(molecule, chebiID);
			boolean nameUpdated = updateMoleculeName(molecule, chebiName);
			boolean formulaUpdated = updateMoleculeFormula(molecule, chebiFormulae);
			// If the ChEBI name was updated, update the referenceEntities that refer to the molecule.
			if (nameUpdated)
			{
				updateReferenceEntities(molecule, chebiName, instanceEdit);
			}
			// Now, check to see if we need to update the ReferenceMolecule itself.
			if (nameUpdated || formulaUpdated)
			{
				addInstanceEditToExistingModifieds(instanceEdit, molecule);
				// Update the display name.
				InstanceDisplayNameGenerator.setDisplayName(molecule);
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants._displayName);
			}
		}
		if (!testMode)
		{
			adaptor.commit();
		}
		else
		{
			adaptor.rollback();
		}
		
		logger.info("*** Formula-fill changes ***");
		logger.info(this.formulaFillSB.toString());
		logger.info("*** Formula update changes ***");
		logger.info(this.formulaUpdateSB.toString());
		
		reportReferenceEntityChanges();
	}

	/**
	 * Generates the report for ReferenceEntity changes.
	 */
	private void reportReferenceEntityChanges()
	{
		refEntChangeLog.info("# DB_ID\tCreator\tAffected ReferenceEntity\tNew ChEBI Name\tUpdated list of all names");
		// Print the referenceEntities that have changes, sorted by who created them.
		for (GKInstance creator : this.referenceEntityChanges.keySet().stream().sorted(this.personComparator).collect(Collectors.toList()))
		{
			for (String message : this.referenceEntityChanges.get(creator))
			{
				refEntChangeLog.info("{}\t{}",creator.toString(), message);
			}
		}
	}

	/**
	 * Update a molecule's formula. Should always update (set to the ChEBI formula) if the current formula and the formula from ChEBI differ.
	 * 
	 * @param molecule
	 * @param chebiFormulae
	 * @return true if the formula was updated, or if the field was populated (i.e. was NULL before). False, otherwise.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private boolean updateMoleculeFormula(GKInstance molecule, List<DataItem> chebiFormulae) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String moleculeFormulae = (String) molecule.getAttributeValue(ReactomeJavaConstants.formula);
		boolean updated = false;
		String moleculeIdentifier = (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
		String reportLinePrefix = "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: ";
		if (!chebiFormulae.isEmpty())
		{
			String firstFormula = chebiFormulae.get(0).getData();
			if (firstFormula != null && !firstFormula.trim().equals(""))
			{
				if (moleculeFormulae == null)
				{
					this.formulaFillSB.append(reportLinePrefix).append("New Formula: ").append(firstFormula).append("\n");
					updated = true;
				}
				else if (!firstFormula.equals(moleculeFormulae))
				{
					this.formulaUpdateSB.append(reportLinePrefix).append(" Old Formula: ").append(moleculeFormulae).append(" ; ")
																.append("New Formula: ").append(firstFormula).append("\n");
					updated = true;
				}
				//else
				//{
					// Not updated.
				//}
			}
			else
			{
				// Only print a warning if we're going from non-NULL formula to NULL formula.
				if (moleculeFormulae != null && !moleculeFormulae.trim().equals(""))
				{
					logger.warn("Got empty/NULL formula for {}, old formula was: {}", molecule.toString(), moleculeFormulae);
				}
				updated = true;
			}
			// If "updated" was set to true, then the molecule actually needs an update (just set the formula to whatever came from ChEBI).
			if (updated)
			{
				molecule.setAttributeValue(ReactomeJavaConstants.formula, firstFormula);
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.formula);
			}
		}
		return updated;
	}

	/**
	 * Updates a molecule's name.
	 * 
	 * @param molecule
	 * @param chebiName
	 * @return True if the name was updated. False if not.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private boolean updateMoleculeName(GKInstance molecule, String chebiName) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String moleculeName = (String) molecule.getAttributeValuesList(ReactomeJavaConstants.name).get(0);
		if (!chebiName.equals(moleculeName))
		{
			molecule.setAttributeValue(ReactomeJavaConstants.name, chebiName);
			refMolNameChangeLog.info("{}\t{}\t{}\t{}", molecule.getDBID(), molecule.toString() , moleculeName, chebiName);
			adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.name);
			return true;
		}
		return false;
	}

	/**
	 * Writes report lines if a molecule's Identifier has changed, according to ChEBI.
	 * 
	 * @param molecule
	 * @param newChebiID
	 * @return True if the identifier was updated, false otherwise.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private void reportIfMoleculeIdentifierChanged(GKInstance molecule, String newChebiID) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String oldMoleculeIdentifier = (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
		if (!newChebiID.equals(oldMoleculeIdentifier))
		{
			 //Need to get list of DB_IDs of referrers for *old* Identifier and also for *new* Identifier.
			String oldIdentifierReferrersString = referrerIDJoiner(molecule);

			// It's possible that the "new" identifier is already in our system. And duplicate ReferenceMolecules are also *possible*, so this will
			// get a little bit messy...
			@SuppressWarnings("unchecked")
			Collection<GKInstance> refMolsWithNewIdentifier = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.identifier, "=", newChebiID);
			// IF there is an existing ReferenceMolecule(s) already using the "new" identifier,
			// the report needs to contain the DB_ID of anything already using that reference molecule AND the DB_IDs of the referrers to that molecule(s).
			if (refMolsWithNewIdentifier != null && !refMolsWithNewIdentifier.isEmpty())
			{
				// for each ReferenceMolecule with the "new" identifier...
				for (GKInstance refMol : refMolsWithNewIdentifier)
				{
					String newIdentifierReferrersString = referrerIDJoiner(refMol);
					refMolIdentChangeLog.info("{}\t{}\t{}\t{}\t{}\t{}\t{}", molecule.getDBID(), molecule.toString(), oldMoleculeIdentifier, newChebiID, refMol.getDBID(), oldIdentifierReferrersString, newIdentifierReferrersString);
				}
			}
			else // the report line will have and Empty String for the DB_ID of the existing molecule and referrers to that molecule.
			{
				refMolIdentChangeLog.info("{}\t{}\t{}\t{}\t{}\t{}\t{}", molecule.getDBID(), molecule.toString(), oldMoleculeIdentifier, newChebiID, "", oldIdentifierReferrersString, "");
			}
		}
	}

	
	/**
	 * Returns the DB_IDs of the referrers of a ReferenceMolecule, all joined by "|".
	 * @param molecule
	 * @return Returns the DB_IDs of the referrers of a ReferenceMolecule, all joined by "|".
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private String referrerIDJoiner(GKInstance molecule) throws Exception
	{
		return ((Collection<GKInstance>) molecule.getReferers(ReactomeJavaConstants.referenceEntity)).stream()
												.map(referrer -> referrer.getDBID().toString())
												.collect(Collectors.joining("|"));
	}

	/**
	 * Updates Objects that refer to a ReferenceMolecule via the "referenceEntity" attribute. The ChEBI name will be appended to the Entities' list of names, at the end. Unless
	 * the ChEBI name is *already* in the list, in which case nothing will happen.
	 * @param molecule The ReferenceMolecule whose referrers need to be updated.
	 * @param chebiName The ChEBI name.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 */
	private void updateReferenceEntities(GKInstance molecule, String chebiName, GKInstance instanceEdit) throws Exception, InvalidAttributeException, InvalidAttributeValueException
	{
		// now, update the any Entities that refer to the ReferenceMolecule by appending chebiName to the list of names. TODO: Refactor to a function.
		@SuppressWarnings("unchecked")
		Collection<GKInstance> referrers = molecule.getReferers(ReactomeJavaConstants.referenceEntity);
		if (referrers != null && referrers.size() > 0)
		{
			for (GKInstance referrer : referrers)
			{
				@SuppressWarnings("unchecked")
				List<String> names = (List<String>) referrer.getAttributeValuesList(ReactomeJavaConstants.name);
				if (names == null || names.isEmpty())
				{
					logger.error("Referrer to \"{}\" has a NULL/Empty list of names. This doesn't seem right. Entity in question is: {}", molecule.toString(), referrer.toString());
				}
				else
				{
					// If the first name IS the ChEBI name, then nothing to do. But if not, then need to append.
					if (!names.get(0).equals(chebiName))
					{
						if (!names.contains(chebiName))
						{
							names.add(chebiName);
							referrer.setAttributeValue(ReactomeJavaConstants.name, names);
							adaptor.updateInstanceAttribute(referrer, ReactomeJavaConstants.name);
							addInstanceEditToExistingModifieds(instanceEdit, referrer);
							GKInstance createdInstanceEdit = (GKInstance) referrer.getAttributeValue(ReactomeJavaConstants.created);
							GKInstance creator = (GKInstance) createdInstanceEdit.getAttributeValue(ReactomeJavaConstants.author);

							@SuppressWarnings("unchecked")
							String message = referrer.getDBID()+"\t"+referrer.toString()+"\t"+chebiName+"\t"+((List<String>)referrer.getAttributeValuesList(ReactomeJavaConstants.name)).toString();
							// Add the message to the map of messages, keyed by the creator.
							if (this.referenceEntityChanges.containsKey(creator))
							{
								this.referenceEntityChanges.get(creator).add(message);
							}
							else
							{
								this.referenceEntityChanges.put(creator, new ArrayList<String>(Arrays.asList(message)));
							}
						}
						else
						{
							logger.info("\"{}\" *already* has \"{}\" as in its list of names; it will not be added again. Names: {}", referrer.toString(), chebiName, names.toString());
						}
					}
					else
					{
						logger.info("\"{}\" has \"{}\" as its first name: {}", referrer.toString(), chebiName, names.toString());
					}
				}
			}
		}
	}

	/**
	 * Adds an instanceEdit to an existing list of "modified" objects.
	 * @param instanceEdit The InstanceEdit to add.
	 * @param instance The instance to add the InstanceEdit to.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @throws InvalidAttributeValueException
	 */
	private void addInstanceEditToExistingModifieds(GKInstance instanceEdit, GKInstance instance) throws InvalidAttributeException, Exception, InvalidAttributeValueException
	{
		// make sure the "modified" list is loaded.
		instance.getAttributeValuesList(ReactomeJavaConstants.modified);
		// add this instanceEdit to the "modified" list, and update.
		instance.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
		adaptor.updateInstanceAttribute(instance, ReactomeJavaConstants.modified);
	}

	/**
	 * Queries the database for duplicate ChEBI ReferenceMolecules, and prints the
	 * results. A duplicate ChEBI ReferenceMolecule is defined as a
	 * ReferenceMolecule with the same ChEBI Identifier as a different
	 * ReferenceMolecule. No two ReferenceMolecules should share a ChEBI Identifier.
	 * 
	 * @throws SQLException
	 * @throws Exception
	 */
	public void checkForDuplicates() throws SQLException, Exception
	{
		this.duplicatesSB = new StringBuilder();
		String findDuplicateReferenceMolecules = "select ReferenceEntity.identifier, count(ReferenceMolecule.DB_ID)\n"
				+ "from ReferenceMolecule\n"
				+ "inner join ReferenceEntity on ReferenceEntity.DB_ID = ReferenceMolecule.DB_ID\n"
				+ "inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n"
				+ "inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n"
				+ "where ReferenceDatabase_2_name.name = 'ChEBI' and identifier is not null\n" + "group by ReferenceEntity.identifier\n"
				+ "having count(ReferenceMolecule.DB_ID) > 1;\n";

		ResultSet duplicates = adaptor.executeQuery(findDuplicateReferenceMolecules, null);
		duplicatesLog.info("# DB_ID\tDuplicated Identifier\tReferenceMolecule");

		// Should only be one, but API returns collection.
		@SuppressWarnings("unchecked")
		Collection<GKInstance> chebiDBInsts = (Collection<GKInstance>)adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "ChEBI");
		GKInstance chebiDBInst = chebiDBInsts.stream().findFirst().orElse(null);
		
		AttributeQueryRequest chebiAQR = adaptor.new AttributeQueryRequest(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.referenceDatabase, "=", chebiDBInst);
		
		while (duplicates.next())
		{
			String identifier = duplicates.getString(1);
			int numberOfDuplicates = duplicates.getInt(2);
			this.duplicatesSB.append("\n** ReferenceMolecule with identifier " + identifier + " occurs " + numberOfDuplicates + " times:\n\n");
			String operator = identifier == null ? "IS NULL" : "=";
			
			AttributeQueryRequest identifierAQR = adaptor.new AttributeQueryRequest(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.identifier, operator, identifier);
			
			@SuppressWarnings("unchecked")
			Collection<GKInstance> dupesOfIdentifier = (Collection<GKInstance>) adaptor._fetchInstance(Arrays.asList(chebiAQR, identifierAQR));
			for (GKInstance duplicate : dupesOfIdentifier)
			{
				duplicatesLog.info("{}\t{}\t{}", duplicate.getDBID(), identifier, duplicate.toString());
			}
		}

		duplicates.close();
		if (this.duplicatesSB.length() > 0)
		{
			logger.info(this.duplicatesSB.toString());
		}
		else
		{
			logger.info("No duplicate ChEBI ReferenceMolecules detected.");
		}
	}

	/**
	 * This looks weird, I know. I needed to be able to set the Formulae on an Entity 
	 * and the Entity class provided by ChEBI does not have a setter for that. It has setters for
	 * other members, just not all of them. So I added a setter, hence the "accessible" name.
	 * @author sshorser
	 *
	 */
	private class AccessibleEntity extends Entity
	{
		public void setFormulae(List<DataItem> formulae)
		{
			this.formulae = formulae;
		}
	}
	
	/**
	 * Makes calls to the ChEBI web service to get info for speciefied ChEBI
	 * identifiers.
	 * 
	 * @param updator
	 * @param refMolecules - a list of ReferenceMolecules. The Identifier of each of these molecules will be sent to ChEBI to get up-to-date information for that Identifier.
	 * @param failedEntitiesList - A list of ReferenceMolecules for which no information was returned by ChEBI. Will be updated by this method.
	 * @return A ReferenceMolecule DB_ID-to-ChEBI Entity map.
	 * @throws IOException 
	 */
	private Map<Long, Entity> retrieveUpdatesFromChebi(Collection<GKInstance> refMolecules, Map<GKInstance, String> failedEntitiesList) throws IOException
	{
		Map<Long, Entity> entityMap = Collections.synchronizedMap(new HashMap<Long, Entity>());
		final Map<String,List<String>> chebiCache = loadCacheFromFile();
		FileWriter fileWriter = new FileWriter("chebi-cache", true);
		// BufferedWriter is supposed to be thread-safe.
		BufferedWriter bw = new BufferedWriter(fileWriter);
		AtomicInteger counter = new AtomicInteger(0);
		// The web service calls are a bit slow to respond, so do them in parallel.
		refMolecules.parallelStream().forEach(molecule ->
		{
			String identifier = null;
			try
			{
				identifier = (String) molecule.getAttributeValue("identifier");
				if (identifier != null && !identifier.trim().equals(""))
				{
					Entity entity;
					// only query web service if the data is not in the chebi-cache - NOTE: chebiCache will always be empty if this.useCache == false
					if (!chebiCache.containsKey("CHEBI:"+identifier))
					{
						if (this.useCache && chebiCache.size() > 0)
						{
							logger.trace("Cache miss for CHEBI:{}", identifier);
						}
						entity = this.chebiClient.getCompleteEntity(identifier);
						if (entity != null && this.useCache)
						{
							bw.write("CHEBI:"+identifier+"\t"+entity.getChebiId()+"\t"+entity.getChebiAsciiName()+"\t"+ (entity.getFormulae().size() > 0 ? entity.getFormulae().get(0).getData() : "") + "\t" + LocalDateTime.now().toString() + "\n");
							bw.flush();
						}
						else
						{
							if (entity == null)
							{
								failedEntitiesList.put(molecule, "ChEBI WebService response was NULL.");
							}
						}
					}
					else // ...Load data from the cache.
					{
						entity = extractChEBIEntityFromCache(chebiCache, identifier);
					}
					// Add entity to map (if it's non-null)
					if (entity != null)
					{
						entityMap.put(molecule.getDBID(), entity);
					}
				}
				else
				{
					logger.error("ERROR: Instance \"{}\" has an empty/null identifier. This should not be allowed.", molecule.toString());
					failedEntitiesList.put(molecule, molecule.toString() + " has an empty/NULL identifier.");
				}
				int i = counter.getAndIncrement();
				if (i % 250 == 0)
				{
					logger.debug("{} ChEBI identifiers checked", i);
				}
			}
			catch (ChebiWebServiceFault_Exception e)
			{
				// "invalid ChEBI identifier" probably shouldn't break execution but should be logged for further investigation.
				if (e.getMessage().contains("invalid ChEBI identifier"))
				{
					logger.error("ERROR: ChEBI Identifier \"{}\" is not formatted correctly.", identifier);
					failedEntitiesList.put(molecule, "ChEBI Identifier \""+identifier+"\" is not formatted correctly.");
				}
				// Log this identifier, but don't fail.
				else if (e.getMessage().contains("the entity in question is deleted, obsolete, or not yet released"))
				{
					logger.error("ERROR: ChEBI Identifier \"{}\" is deleted, obsolete, or not yet released.", identifier);
					failedEntitiesList.put(molecule, "ChEBI Identifier \""+identifier+"\" is deleted, obsolete, or not yet released.");
				}
				else
				{
					// Other Webservice errors should probably break execution - if one fails, they will all probably fail.
					// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically -
					// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
					logger.error("WebService error occurred! Message is: {}", e.getMessage());
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
			catch (InvalidAttributeException e)
			{
				logger.error("InvalidAttributeException caught while trying to get the \"identifier\" attribute on " + molecule.toString());
				// stack trace should be printed, but I don't think this should break execution, though the only way I can think
				// of this happening is if the data model changes - otherwise, this exception would probably never be caught.
				e.printStackTrace();
			}
			catch (Exception e)
			{
				// general exceptions - print stack trace but keep going.
				e.printStackTrace();
			}
		});
		bw.close();
		return entityMap;
	}

	/**
	 * Returns the data from the cache file (if it exists). If it doesn't exit, then an empty map will be returned.
	 * @return A mapping of ChEBI ID to a list with items in this order: ChEBI (might be different if ChEBI has changed identifiers), ChEBI Name, Formula.
	 * @throws IOException
	 */
	private Map<String,List<String>> loadCacheFromFile() throws IOException
	{
		Map<String,List<String>> chebiCache = Collections.synchronizedMap(new HashMap<String, List<String>>());
		if (this.useCache)
		{
			logger.info("useCache is TRUE - chebi-cache file will be read, and populated. Identifiers not in the cache will be queried from ChEBI.");
			// if the cache exists, load it.
			if (Files.exists(Paths.get("chebi-cache")))
			{
				Files.readAllLines(Paths.get("chebi-cache")).parallelStream().forEach( line -> {
					String[] parts = line.split("\t");
					String oldChebiID = parts[0];
					String newChebiID = parts[1];
					String name = parts[2];
					String formula = parts.length > 2 ? parts[3] : "";
					chebiCache.put(oldChebiID, Arrays.asList(newChebiID, name, formula));
				});
			}
			logger.debug("{} entries in the chebi-cache", chebiCache.size());
		}
		else
		{
			logger.info("useCache is FALSE - chebi-cache will NOT be read. ChEBI will be queried for ALL identifiers.");
		}
		return chebiCache;
	}

	/**
	 * Extract a ChEBI entity from the cache, based on the identifier.
	 * @param chebiCache - The cache from a file.
	 * @param identifier - The chebi Identifier.
	 * @return A ChEBI Entity that will be built from the data in the cache. It will only have: an Identifier, ChEBI Name, Formula.
	 */
	private Entity extractChEBIEntityFromCache(Map<String, List<String>> chebiCache, String identifier)
	{
		Entity entity;
		// Use the AccessibleEntity to set the formula.
		entity = new AccessibleEntity();
		entity.setChebiId(chebiCache.get("CHEBI:"+identifier).get(0));
		entity.setChebiAsciiName(chebiCache.get("CHEBI:"+identifier).get(1) );
		DataItem formula = new DataItem();
		formula.setData(chebiCache.get("CHEBI:"+identifier).get(2));
		((AccessibleEntity)entity).setFormulae(Arrays.asList(formula));
		return entity;
	}
}
