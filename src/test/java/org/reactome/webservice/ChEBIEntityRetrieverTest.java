package org.reactome.webservice;

import org.gk.model.GKInstance;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactome.webservice.helpers.ChEBIAPIClient;
import org.reactome.webservice.helpers.ChEBIEntityParser;
import org.reactome.model.ChEBIEntity;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChEBIEntityRetrieverTest {

    @Mock
    private ChEBIAPIClient mockApiClient;

    @Mock
    private ChEBIEntityParser mockParser;

    @Mock
    private GKInstance mockGKInstance1;

    @Mock
    private GKInstance mockGKInstance2;

    private ChEBIEntityRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new ChEBIEntityRetriever(mockApiClient, mockParser);
    }

    @Test
    void getChEBIEntities_NullReferenceMolecules_ThrowsIllegalStateException() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> retriever.getChEBIEntities(null));
    }

    @Test
    void getChEBIEntities_EmptyReferenceMolecules_ThrowsIllegalStateException() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> retriever.getChEBIEntities(Collections.emptyList()));
    }

    @Test
    void getChEBIEntities_SingleValidEntry_ReturnsCorrectMapping() throws Exception {
        // Arrange
        String chEBIId = "15377";
        String chEBIName = "water";
        String chEBIFormula = "H2O";

        List<GKInstance> instances = List.of(mockGKInstance1);
        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(chEBIId);

        JSONObject responseJson = new JSONObject()
            .put(chEBIId, new JSONObject()
            .put("exists", true)
            .put("data", new JSONObject().put("chebiId", chEBIId)));

        ChEBIEntity expectedEntity = new ChEBIEntity(chEBIId, chEBIName, chEBIFormula);

        when(mockApiClient.fetchCompounds(any())).thenReturn(responseJson);
        when(mockParser.parse(any())).thenReturn(expectedEntity);

        // Act
        Map<GKInstance, Optional<ChEBIEntity>> result = retriever.getChEBIEntities(instances);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(mockGKInstance1).isPresent());
        assertEquals(expectedEntity, result.get(mockGKInstance1).get());
    }

    @Test
    void getChEBIEntities_MultipleEntries_ReturnsCorrectMapping() throws Exception {
        // Arrange
        String chEBIId1 = "15377";
        String chEBIName1 = "water";
        String chEBIFormula1 = "H2O";

        String chEBIId2 = "15378";
        String chEBIName2 = "hydron";
        String chEBIFormula2 = "H";
        List<GKInstance> instances = List.of(mockGKInstance1, mockGKInstance2);

        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(chEBIId1);
        when(mockGKInstance2.getAttributeValue("identifier")).thenReturn(chEBIId2);

        JSONObject responseJson = new JSONObject()
            .put(chEBIId1, new JSONObject()
            .put("exists", true)
            .put("data", new JSONObject().put("chebiId", chEBIId1)))
            .put(chEBIId2, new JSONObject()
            .put("exists", true)
            .put("data", new JSONObject().put("chebiId", chEBIId2)));

        ChEBIEntity entity1 = new ChEBIEntity(chEBIId1, chEBIName1, chEBIFormula1);
        ChEBIEntity entity2 = new ChEBIEntity(chEBIId2, chEBIName2, chEBIFormula2);

        when(mockApiClient.fetchCompounds(any())).thenReturn(responseJson);
        when(mockParser.parse(any()))
            .thenReturn(entity1)
            .thenReturn(entity2);

        // Act
        Map<GKInstance, Optional<ChEBIEntity>> result = retriever.getChEBIEntities(instances);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.get(mockGKInstance1).isPresent());
        assertTrue(result.get(mockGKInstance2).isPresent());
    }

    @Test
    void getChEBIEntities_NonExistentEntry_ReturnsEmptyOptional() throws Exception {
        // Arrange
        String chEBIId = "99999";
        List<GKInstance> instances = List.of(mockGKInstance1);
        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(chEBIId);

        JSONObject responseJson = new JSONObject()
            .put(chEBIId, new JSONObject()
            .put("exists", false));

        when(mockApiClient.fetchCompounds(any())).thenReturn(responseJson);

        // Act
        Map<GKInstance, Optional<ChEBIEntity>> result = retriever.getChEBIEntities(instances);

        // Assert
        assertFalse(result.get(mockGKInstance1).isPresent());
    }

    @Test
    void getChEBIEntities_APIClientThrowsIOException_PropagatesException() throws Exception {
        // Arrange
        List<GKInstance> instances = List.of(mockGKInstance1);
        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn("15377");
        when(mockApiClient.fetchCompounds(any())).thenThrow(new IOException("Network error"));

        // Act & Assert
        assertThrows(IOException.class, () -> retriever.getChEBIEntities(instances));
    }

    @Test
    void getChEBIEntities_DuplicateIdentifiers_ThrowsIllegalStateException() throws Exception {
        // Arrange
        String chEBIId = "15377";

        List<GKInstance> instances = List.of(mockGKInstance1, mockGKInstance2);
        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(chEBIId);
        when(mockGKInstance2.getAttributeValue("identifier")).thenReturn(chEBIId);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> retriever.getChEBIEntities(instances));
    }

    @Test
    void getChEBIEntities_ParserThrowsException_PropagatesException() throws Exception {
        // Arrange
        String chEBIId = "15377";
        List<GKInstance> instances = List.of(mockGKInstance1);
        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(chEBIId);

        JSONObject responseJson = new JSONObject()
            .put(chEBIId, new JSONObject()
            .put("exists", true)
            .put("data", new JSONObject()));

        when(mockApiClient.fetchCompounds(any())).thenReturn(responseJson);
        when(mockParser.parse(any())).thenThrow(new IllegalArgumentException("Invalid data"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> retriever.getChEBIEntities(instances));
    }

    @Test
    void getChEBIEntities_MixedExistenceResults_HandlesCorrectly() throws Exception {
        // Arrange
        String existingId = "15377";
        String existingName = "water";
        String existingFormula = "H2O";

        String nonExistingId = "99999";
        List<GKInstance> instances = List.of(mockGKInstance1, mockGKInstance2);

        when(mockGKInstance1.getAttributeValue("identifier")).thenReturn(existingId);
        when(mockGKInstance2.getAttributeValue("identifier")).thenReturn(nonExistingId);

        JSONObject responseJson = new JSONObject()
            .put(existingId, new JSONObject()
            .put("exists", true)
            .put("data", new JSONObject().put("chebiId", existingId)))
            .put(nonExistingId, new JSONObject()
            .put("exists", false));

        ChEBIEntity expectedEntity = new ChEBIEntity(existingId, existingName, existingFormula);
        when(mockApiClient.fetchCompounds(any())).thenReturn(responseJson);
        when(mockParser.parse(any())).thenReturn(expectedEntity);

        // Act
        Map<GKInstance, Optional<ChEBIEntity>> result = retriever.getChEBIEntities(instances);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.get(mockGKInstance1).isPresent());
        assertFalse(result.get(mockGKInstance2).isPresent());
    }
}