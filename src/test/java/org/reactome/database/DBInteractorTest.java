package org.reactome.database;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactome.reports.ReferenceMoleculeFormulaChangeReporter;
import org.reactome.reports.ReferenceMoleculeNameChangeReporter;
import org.reactome.reports.SimpleEntityNameChangeReporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DBInteractorTest {

    @Mock
    private MySQLAdaptor mockDbAdaptor;
    
    @Mock
    private Schema mockSchema;
    
    @Mock
    private GKInstance mockPersonInstance;
    
    @Mock
    private GKInstance mockInstanceEdit;

    @Mock
    private GKInstance mockRefMol;

    @Mock
    private GKInstance mockSimpleEntity;

    @Mock
    private GKInstance mockChEBIReferenceDatabase;
    
    @Mock
    private ReferenceMoleculeNameChangeReporter mockNameChangeReporter;
    
    @Mock
    private ReferenceMoleculeFormulaChangeReporter mockFormulaChangeReporter;
    
    @Mock
    private SimpleEntityNameChangeReporter mockSimpleEntityNameChangeReporter;

    private DBInteractor dbInteractor;
    private final long PERSON_ID = 12345L;

    @BeforeEach
    void setUp() throws Exception {
        dbInteractor = new DBInteractor(mockDbAdaptor, PERSON_ID);
    }

    @Test
    void testStartTransaction() throws Exception {
        dbInteractor.startTransaction();
        verify(mockDbAdaptor).startTransaction();
    }

    @Test
    void testCommit() throws Exception {
        dbInteractor.commit();
        verify(mockDbAdaptor).commit();
    }

    @Test
    void testGetAllChEBIReferenceMoleculeInstances() throws Exception {
        // Setup
        GKInstance mockRefMol1 = mock(GKInstance.class);
        GKInstance mockRefMol2 = mock(GKInstance.class);
        List<GKInstance> expectedInstances = Arrays.asList(mockRefMol1, mockRefMol2);
        
        when(mockDbAdaptor.fetchInstanceByAttribute(
            eq(ReactomeJavaConstants.ReferenceMolecule),
            eq(ReactomeJavaConstants.referenceDatabase),
            eq("="),
            any()
        )).thenReturn(expectedInstances);

        when(mockDbAdaptor.fetchInstanceByAttribute(
            eq(ReactomeJavaConstants.ReferenceDatabase),
            eq(ReactomeJavaConstants.name),
            eq("="),
            eq("ChEBI")
        )).thenReturn(Collections.singletonList(mockChEBIReferenceDatabase));

        // Execute
        List<GKInstance> result = dbInteractor.getAllChEBIReferenceMoleculeInstances();

        // Verify
        assertEquals(expectedInstances, result);
        verify(mockDbAdaptor).fetchInstanceByAttribute(
            eq("ReferenceMolecule"),
            eq("referenceDatabase"),
            eq("="),
            any()
        );
    }

    @Test
    void testUpdateReferenceMoleculeName() throws Exception {
        // Setup
        GKInstance mockRefMol = mock(GKInstance.class);
        String newName = "New Chemical Name";
        List<String> oldNames = new ArrayList<>(Arrays.asList("Old Chemical Name", "Old Name"));
        List<String> expectedNames = new ArrayList<>();
        expectedNames.add(newName);
        expectedNames.addAll(oldNames);

        when(mockRefMol.getAttributeValuesList("name")).thenReturn(oldNames);

        // Execute
        boolean result = dbInteractor.updateReferenceMoleculeName(mockRefMol, newName);

        // Verify
        assertTrue(result);
        verify(mockRefMol).setAttributeValue(eq("name"), eq(expectedNames));
        verify(mockDbAdaptor).updateInstanceAttribute(eq(mockRefMol), eq("name"));
    }

    @Test
    void testUpdateReferenceMoleculeFormula() throws Exception {
        // Setup
        GKInstance mockRefMol = mock(GKInstance.class);
        String newFormula = "H2O";
        
        // Execute
        boolean result = dbInteractor.updateReferenceMoleculeFormula(mockRefMol, newFormula);

        // Verify
        assertTrue(result);
        verify(mockRefMol).setAttributeValue(eq("formula"), eq(newFormula));
        verify(mockDbAdaptor).updateInstanceAttribute(eq(mockRefMol), eq("formula"));
    }

    @Test
    void testGetReferenceMoleculesWithChEBIIdentifier() throws Exception {
        // Setup
        String chEBIId = "CHEBI:12345";
        GKInstance mockRefMol = mock(GKInstance.class);
        List<GKInstance> expectedInstances = Collections.singletonList(mockRefMol);
        
        when(mockDbAdaptor.fetchInstanceByAttribute(
            anyString(),
            anyString(),
            anyString(),
            any()
        )).thenReturn(expectedInstances);

        // Execute
        List<GKInstance> result = dbInteractor.getReferenceMoleculesWithChEBIIdentifier(chEBIId);

        // Verify
        assertEquals(expectedInstances, result);
        verify(mockDbAdaptor).fetchInstanceByAttribute(
            eq("ReferenceMolecule"),
            eq("identifier"),
            eq("="),
            eq(chEBIId)
        );
    }
//
//    @Test
//    void testUpdateSimpleEntityReferrersNames() throws Exception {
//        // Setup
//        GKInstance mockRefMol = mock(GKInstance.class);
//        GKInstance mockSimpleEntity = mock(GKInstance.class);
//        String newName = "New Name";
//
//        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
//            .thenReturn(Collections.singletonList(mockSimpleEntity));
//
//        // Execute
//        boolean result = dbInteractor.updateSimpleEntityReferrersNames(mockRefMol, newName);
//
//        // Verify
//        assertTrue(result);
//        verify(mockSimpleEntity).setAttributeValue(eq("name"), any());
//        verify(mockDbAdaptor).updateInstanceAttribute(eq(mockSimpleEntity), eq("name"));
//    }

    @Test
    void testUpdateSimpleEntityReferrersNames_NoReferrers() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
            .thenReturn(null);

        // Execute
        boolean result = dbInteractor.updateSimpleEntityReferrersNames(mockRefMol, "New Name");

        // Verify
        assertFalse(result);
        verify(mockDbAdaptor, never()).updateInstanceAttribute(any(GKInstance.class), any(String.class));
    }

    @Test
    void testUpdateSimpleEntityReferrersNames_EmptySimpleEntityNames() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
                .thenReturn(Collections.singletonList(mockSimpleEntity));

        // Return empty list for simple entity names
        when(mockSimpleEntity.getAttributeValuesList(ReactomeJavaConstants.name))
                .thenReturn(new ArrayList<>());

        // Execute & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            dbInteractor.updateSimpleEntityReferrersNames(mockRefMol, "New Name");
        });

        assertEquals("Simple entity has no names", exception.getMessage());

        // Verify no updates were made
        verify(mockDbAdaptor, never()).updateInstanceAttribute(any(GKInstance.class), any(String.class));
    }

    @Test
    void testUpdateSimpleEntityReferrersNames_EmptyReferenceMoleculeNames() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
            .thenReturn(Collections.singletonList(mockSimpleEntity));

        // Return non-empty list for simple entity names
        when(mockSimpleEntity.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(Arrays.asList("Simple Entity Name"));

        // Return empty list for reference molecule names
        when(mockRefMol.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(new ArrayList<>());

        // Execute & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            dbInteractor.updateSimpleEntityReferrersNames(mockRefMol, "New Name");
        });

        assertEquals("Reference molecule has no names", exception.getMessage());

        // Verify no updates were made
        verify(mockDbAdaptor, never()).updateInstanceAttribute(any(GKInstance.class), any(String.class));
    }


    @Test
    void testUpdateSimpleEntityReferrersNames_MatchingFirstNames() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
            .thenReturn(Collections.singletonList(mockSimpleEntity));

        List<String> existingNames = new ArrayList<>(Arrays.asList("Original Name", "Secondary Name"));
        when(mockSimpleEntity.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(existingNames);

        when(mockRefMol.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(Arrays.asList("Original Name"));
        GKInstance mockInstanceEdit = mock(GKInstance.class);
        DBInteractor spyDbInteractor = spy(dbInteractor);

        // Mock getInstanceEdit() to return our mockInstanceEdit
        doReturn(mockInstanceEdit).when(spyDbInteractor).getInstanceEdit();

        String newName = "New Name";

        // Execute
        boolean result = spyDbInteractor.updateSimpleEntityReferrersNames(mockRefMol, newName);

        // Verify
        assertTrue(result);
        verify(mockSimpleEntity).setAttributeValue(eq(ReactomeJavaConstants.name), any(List.class));
        verify(mockDbAdaptor).updateInstanceAttribute(mockSimpleEntity, ReactomeJavaConstants.name);
    }

    @Test
    void testUpdateSimpleEntityReferrersNames_DifferentFirstNames() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
                .thenReturn(Collections.singletonList(mockSimpleEntity));

        List<String> simpleEntityNames = new ArrayList<>(Arrays.asList("Curator Name", "Secondary Name"));
        when(mockSimpleEntity.getAttributeValuesList(ReactomeJavaConstants.name))
                .thenReturn(simpleEntityNames);

        when(mockRefMol.getAttributeValuesList(ReactomeJavaConstants.name))
                .thenReturn(Arrays.asList("Different Name"));

        GKInstance mockInstanceEdit = mock(GKInstance.class);
        DBInteractor spyDbInteractor = spy(dbInteractor);

        // Mock getInstanceEdit() to return our mockInstanceEdit
        doReturn(mockInstanceEdit).when(spyDbInteractor).getInstanceEdit();


        String newName = "New ChEBI Name";

        // Execute
        boolean result = spyDbInteractor.updateSimpleEntityReferrersNames(mockRefMol, newName);

        // Verify
        assertTrue(result);
        verify(mockSimpleEntity).setAttributeValue(eq(ReactomeJavaConstants.name), any(List.class));
        verify(mockDbAdaptor).updateInstanceAttribute(mockSimpleEntity, ReactomeJavaConstants.name);
    }

    @Test
    void testUpdateSimpleEntityReferrersNames_NoChangesNeeded() throws Exception {
        // Setup
        when(mockRefMol.getReferers(ReactomeJavaConstants.referenceEntity))
                .thenReturn(Collections.singletonList(mockSimpleEntity));

        String newName = "New Name";
        List<String> names = Arrays.asList(newName, "Other Name");

        when(mockSimpleEntity.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(names);
        when(mockRefMol.getAttributeValuesList(ReactomeJavaConstants.name))
            .thenReturn(Arrays.asList(newName));

        // Execute
        boolean result = dbInteractor.updateSimpleEntityReferrersNames(mockRefMol, newName);

        // Verify
        assertFalse(result);
        verify(mockDbAdaptor, never()).updateInstanceAttribute(any(GKInstance.class), any(String.class));
    }


    @Test
    void testUpdateModifiedInstanceEdits() throws Exception {
        // Setup
        GKInstance mockInstance = mock(GKInstance.class);
        GKInstance mockInstanceEdit = mock(GKInstance.class);
        DBInteractor spyDbInteractor = spy(dbInteractor);

        // Mock getInstanceEdit() to return our mockInstanceEdit
        doReturn(mockInstanceEdit).when(spyDbInteractor).getInstanceEdit();

        // Mock the existing modified list (if any)
        when(mockInstance.getAttributeValuesList(ReactomeJavaConstants.modified))
                .thenReturn(new ArrayList<>());

        // Execute
        boolean result = spyDbInteractor.updateModifiedInstanceEdits(mockInstance);

        // Verify
        assertTrue(result);
        verify(mockInstance).addAttributeValue(
                eq(ReactomeJavaConstants.modified),
                same(mockInstanceEdit)
        );
        verify(mockDbAdaptor).updateInstanceAttribute(
                mockInstance,
                ReactomeJavaConstants.modified
        );
    }

}
