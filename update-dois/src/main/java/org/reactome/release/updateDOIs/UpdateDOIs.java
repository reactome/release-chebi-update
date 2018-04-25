package org.reactome.release.updateDOIs;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.model.PersistenceAdaptor;

public class UpdateDOIs {

  public static void main( String[] args ) {

    String pathToResources = "src/main/resources/config.properties";

    if (args.length > 0 && !args[0].equals("")) {
      pathToResources = args[0];
    }

    UpdateDOIs.executeUpdateDOIs(pathToResources);
  }

  public static void executeUpdateDOIs(String pathToResources) {

    MySQLAdaptor testReactomeDBA = null;
    MySQLAdaptor gkCentralDBA = null;
    long authorId = 0;

    try {
      Properties props = new Properties();
      props.load(new FileInputStream(pathToResources));

      String user = props.getProperty("user");
      String password = props.getProperty("password");
      String host = props.getProperty("host");
      String databaseTR = props.getProperty("databaseTR");
      String databaseGk = props.getProperty("databaseGK");
      int port = Integer.valueOf(props.getProperty("port"));
      authorId = Integer.valueOf(props.getProperty("authorId"));

      // Set up db connections.
      testReactomeDBA = new MySQLAdaptor(host, databaseTR, user, password, port);
      gkCentralDBA = new MySQLAdaptor(host, databaseGk, user, password, port);
    } catch (Exception e) {
      e.printStackTrace();
    }

      NewDOIChecker newDOIChecker = new NewDOIChecker();
      newDOIChecker.setTestReactomeAdaptor(testReactomeDBA);
      newDOIChecker.setGkCentralAdaptor(gkCentralDBA);

      String creatorFile = "org.reactome.release.updateDOIs.UpdateDOIs";
      GKInstance instanceEditTestReactome = newDOIChecker.createInstanceEdit(authorId, creatorFile);
      GKInstance instanceEditGkCentral = newDOIChecker.createInstanceEdit(authorId, creatorFile);
      newDOIChecker.findNewDOIs(instanceEditTestReactome, instanceEditGkCentral);
    // Useful to report information back, such as number of changes?
      System.out.println( "UpdateDOIs Complete" );
    }

}
