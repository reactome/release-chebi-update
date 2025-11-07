package org.reactome.webservice.helpers;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ChEBIAPIClientIntegrationTest {

    private ChEBIAPIClient client;

    @BeforeEach
    void setUp() {
        client = new ChEBIAPIClient();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void fetchCompounds_SingleValidCompound_ReturnsCorrectData() throws Exception {
        // Arrange
        Set<String> ids = Set.of("15377"); // Water

        // Act
        JSONObject result = client.fetchCompounds(ids);

        // Assert
        assertNotNull(result);
        assertTrue(result.has("15377"));
        JSONObject compound = result.getJSONObject("15377");
        assertTrue(compound.getBoolean("exists"));
        JSONObject data = compound.getJSONObject("data");
        assertEquals("CHEBI:15377", data.getString("chebi_accession"));
        assertEquals("water", data.getString("name"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void fetchCompounds_MultipleValidCompounds_ReturnsAllData() throws Exception {
        // Arrange
        Set<String> ids = Set.of("15377", "15379"); // Water and Sodium

        // Act
        JSONObject result = client.fetchCompounds(ids);

        // Assert
        assertNotNull(result);
        assertTrue(result.has("15377"));
        assertTrue(result.has("15379"));
        assertTrue(result.getJSONObject("15377").getBoolean("exists"));
        assertTrue(result.getJSONObject("15379").getBoolean("exists"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void fetchCompounds_NonExistentCompound_ReturnsNonExistentFlag() throws Exception {
        // Arrange
        Set<String> ids = Set.of("99999999");

        // Act
        JSONObject result = client.fetchCompounds(ids);

        // Assert
        assertNotNull(result);
        assertTrue(result.has("99999999"));
        assertFalse(result.getJSONObject("99999999").getBoolean("exists"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchCompounds_LargeNumberOfCompounds_HandlesSuccessfully() throws Exception {
        // Arrange
        Set<String> ids = generateLargeIdSet(50); // Adjust number based on API limits

        // Act
        JSONObject result = client.fetchCompounds(ids);
        System.out.println(result.toString(2));
        // Assert
        assertNotNull(result);
        assertEquals(ids.size(), result.length());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchCompounds_RateLimitingScenario_HandlesRetryCorrectly() throws Exception {
        // Arrange
        Set<String> ids = Set.of("15377");

        // Act & Assert
        // Make multiple rapid requests to trigger rate limiting
        for (int i = 0; i < 5; i++) {
            JSONObject result = client.fetchCompounds(ids);
            assertNotNull(result);
            assertTrue(result.has("15377"));
            Thread.sleep(100); // Small delay between requests
        }
    }

    private Set<String> generateLargeIdSet(int count) {
        return IntStream.rangeClosed(0, count).boxed().map(Object::toString).collect(Collectors.toSet());
    }
}