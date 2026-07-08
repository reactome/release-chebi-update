package org.reactome.utils;

import org.reactome.curation.CuratorToolWsApplication;
import org.reactome.curation.controller.CurationController;
import org.reactome.curation.model.InstanceList;
import org.reactome.curation.model.NamedReferrerList;
import org.reactome.curation.model.SimpleInstance;

import org.reactome.server.graph.domain.model.DatabaseObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 7/5/2026
 */
public class CuratorToolAPI {

    private static final Logger logger = LoggerFactory.getLogger(CuratorToolAPI.class);
    private static CurationController controller;

    private ConfigurableApplicationContext applicationContext;

    public CuratorToolAPI() {
        if (controller == null) {
            controller = this.initController();
            if (controller == null) {
                throw new IllegalStateException("Failed to initialize CuratorToolAPI: controller is null");
            }
        }
    }

    // The following code is copied directly from the slicing tool project.
    private CurationController initController() {
        try {
            // curator-tool-ws's bundled application.properties forces DEBUG for these loggers.
            // System properties outrank a classpath application.properties in Spring Boot's
            // precedence order, so this quiets them for the batch run without editing
            // curator-tool-ws. (SpringApplicationBuilder.properties(...) are default/lowest
            // precedence and would NOT override application.properties.)
            System.setProperty("logging.level.org.springframework.data.neo4j", "WARN");
            System.setProperty("logging.level.org.springframework.security", "WARN");

            applicationContext = new SpringApplicationBuilder(CuratorToolWsApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=-1")  // disable HTTP server; keep full servlet context for correct AspectJ wiring
                .run();
            return applicationContext.getBean(CurationController.class);
        }
        catch (Exception e) {
            logger.error("GraphDBInstanceManager.initController(): " + e.getMessage(), e);
        }
        return null;
    }

    public SimpleInstance commit(SimpleInstance simpleInstance) {
        return controller.commit(simpleInstance);
    }

    public List<SimpleInstance> fetchChEBIReferenceMoleculeInstances() {
        List<SimpleInstance> allChEBIReferenceMoleculesInstances = new ArrayList<>();

        int pageSize = 500;
        int skip = 0;
        Integer total = null;

        do {
            InstanceList page = controller.searchInstances(
                "ReferenceMolecule",
                skip,
                pageSize,
                Optional.of("referenceDatabase"),
                Optional.of("equal"),
                Optional.of("ChEBI")
            );

            if (total == null) {
                total = page.getTotalCount();   // set once from the first page
            }
            allChEBIReferenceMoleculesInstances.addAll(page.getInstances());
            skip += pageSize;
        } while (skip < total);

        return allChEBIReferenceMoleculesInstances.parallelStream().map(this::inflate).collect(Collectors.toList());
    }

    public SimpleInstance findDatabaseObjectByDbId(long dbId) {
        DatabaseObject databaseObject = controller.findByDdId(dbId);
        if (databaseObject == null) {
            return null;
        }

        try {
            return controller.getConverter().convert(databaseObject);
        } catch (Exception e) {
            throw new RuntimeException("Unable to convert DatabaseObject " + databaseObject + " to SimpleInstance", e);
        }
    }

    public void close() {
        applicationContext.close();
    }

    public SimpleInstance inflate(SimpleInstance shellInstance) {
        return controller.findByDdIdInInstance(shellInstance.getDbId());
    }

    public List<SimpleInstance> getReferrers(SimpleInstance instance, String referrerAttributeName) throws Exception {
        return controller.getReferrers(instance.getDbId())
            .stream()
            .filter(g -> referrerAttributeName.equals(g.getAttributeName()))
            .findFirst()
            .map(NamedReferrerList::getReferrers)
            .orElse(Collections.emptyList());
    }
}
