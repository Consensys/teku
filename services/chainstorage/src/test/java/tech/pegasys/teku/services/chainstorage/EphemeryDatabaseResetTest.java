package tech.pegasys.teku.services.chainstorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.server.Database;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StorageServiceTest {


    private ServiceConfig serviceConfig;

    @Mock
    private VersionedDatabaseFactory dbFactory;

    @Mock
    private Path beaconDataDir;

    @Mock
    private Path slashProtectionDir;

    @Mock
    private FileUtils fileUtils;

    @Mock
    private EphemeryDatabaseReset ephemeryDatabaseReset;

    @Mock
    private Database database;

//    private StorageService storageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serviceConfig = mock(ServiceConfig.class);
        DataDirLayout dataDirLayout = mock(DataDirLayout.class);
        when(serviceConfig.getDataDirLayout()).thenReturn(dataDirLayout);
        when(serviceConfig.getDataDirLayout().getBeaconDataDirectory()).thenReturn(beaconDataDir);
        when(serviceConfig.getDataDirLayout().getValidatorDataDirectory()).thenReturn(slashProtectionDir);
    }

    @Test
    void shouldResetDirectoriesAndCreateDatabase() throws IOException {
        // Mock database creation
        when(dbFactory.createDatabase()).thenReturn(database);

        // Call the method
        Database result = ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);

        // Verify that directories were deleted
        verify(fileUtils).deleteDirectoryRecursively(beaconDataDir);
        verify(fileUtils).deleteDirectoryRecursively(slashProtectionDir.resolve("slashprotection"));

        // Verify that the database was created
        verify(dbFactory).createDatabase();

        // Assert that the result is the mock database
        assertEquals(database, result);
    }

    @Test
    void shouldThrowInvalidConfigurationExceptionWhenDirectoryDeletionFails() throws IOException {
        // Simulate an exception during directory deletion
        doThrow(new IOException("Failed to delete directory")).when(fileUtils).deleteDirectoryRecursively(beaconDataDir);

        // Expect InvalidConfigurationException to be thrown
        assertThrows(InvalidConfigurationException.class, () -> {
            ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
        });

        // Verify that database creation was not attempted
        verify(dbFactory, never()).createDatabase();
    }

    @Test
    void shouldThrowInvalidConfigurationExceptionWhenDatabaseCreationFails() throws IOException {
        // Simulate successful directory deletion but failure in database creation
        when(dbFactory.createDatabase()).thenThrow(new RuntimeException("Database creation failed"));

        // Expect InvalidConfigurationException to be thrown
        assertThrows(InvalidConfigurationException.class, () -> {
            ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
        });

        // Verify that directories were deleted
        verify(fileUtils).deleteDirectoryRecursively(beaconDataDir);
        verify(fileUtils).deleteDirectoryRecursively(slashProtectionDir.resolve("slashprotection"));

        // Verify that database creation was attempted
        verify(dbFactory).createDatabase();
    }
}

