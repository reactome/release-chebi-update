# ChEBI Update

This tool will perform the ChEBI update.

It will:

 - Query ChEBI for up-to-date information on ChEBI identifiers in Reactome.
 - Update the names of SimpleEntities that refer to ChEBI ReferenceMolecules in Reactome.
 - Update the names, identifiers, and formulae for ReferenceMolecules that need to be updated (based on ChEBI query results).
 - Check for and report on duplicate ReferenceMolecules (two or more different ReferenceMolecule objects that have the same ChEBI Identifier).
 - Report on any ChEBI ReferenceMolecules for which the ChEBI web service did not provide a response.
 
## Configuration

This application requires a properties file at `config.properties` that looks like this:

```
curator.database.host=database_server
curator.database.user=someuser
curator.database.password=someuserspassword
curator.database.name=reactome_database
curator.database.port=3306
person.id=somepersonIDNumber
```

## Logging
 
Reports will be written under `./reports`. The reports are:
 - duplicates.tsv - This report will list ChEBI Identifiers that are duplicated in the database.
 - failed_chebi_lookups.tsv - This report will list ReferenceMolecules which failed when ChEBI was queried, and the reason for the failure.
 - reference-molecule-chebi-identifier-changes.tsv - This report will list ReferenceMolecules whose ChEBI identifiers have changed, including the old and new identifiers.
 - reference-molecule-name-changes.tsv - This report will list ReferenceMolecules whose names have changed, including the old and new names.
 - simple-entity-name-changes.tsv - This report will list any SimpleEntity (which refers to a ReferenceMolecule) whose name has changed. This report contains the Creator of the Entity, information about the affected Entity, the new name from ChEBI, the full list of names both before and after the update, and whether the update was applied automatically or is a suggestion for curators. 

## Compiling & Running

This is a Java application which requries a Java 8+ environment. You will need maven and a full JDK to compile.

To compile the application, run this command:

```
$ mvn clean package
```

If this is successful, you should see a JAR file in the `target` directory, with a name like `chebi-update-jar-with-dependencies.jar`. This is the file you will run.

To run the program, execute this command:
```
$ java -jar target/chebi-update-jar-with-dependencies.jar [optional_configuration_file_path]
```
