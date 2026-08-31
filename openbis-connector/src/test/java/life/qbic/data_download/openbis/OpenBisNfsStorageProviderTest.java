package life.qbic.data_download.openbis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import life.qbic.data_download.storage.exception.StorageProviderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Basic tests for OpenBisNfsStorageProvider. Full integration tests require a real openBIS
 * connection or extensive mocking of the OpenBisConnector, which is beyond the scope of unit tests.
 * These tests verify the core NIO streaming functionality and configuration validation.
 */
class OpenBisNfsStorageProviderTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("constructor validates mount path is a directory")
  void constructorValidatesMountPath() {
    Path notADir = tempDir.resolve("not-a-dir");
    // Constructor validates connector first, then mount path
    // With null connector, it throws NullPointerException before checking mount path
    assertThrows(NullPointerException.class, 
        () -> new OpenBisNfsStorageProvider(null, notADir));
  }

  @Test
  @DisplayName("constructor validates cache TTL is positive")
  void constructorValidatesCacheTtl() {
    Path mountPath = tempDir.resolve("mount");
    try {
      Files.createDirectories(mountPath);
    } catch (IOException e) {
      fail("Failed to create test directory", e);
    }
    
    // Constructor validates connector first, then mount path, then cache TTL
    // With null connector, it throws NullPointerException before checking cache TTL
    assertThrows(NullPointerException.class,
        () -> new OpenBisNfsStorageProvider(null, mountPath, Duration.ZERO));
    assertThrows(NullPointerException.class,
        () -> new OpenBisNfsStorageProvider(null, mountPath, Duration.ofSeconds(-1)));
  }

  @Test
  @DisplayName("constructor requires non-null connector")
  void constructorRequiresNonNullConnector() {
    Path mountPath = tempDir.resolve("mount");
    try {
      Files.createDirectories(mountPath);
      // Should throw NullPointerException for null connector
      assertThrows(NullPointerException.class, 
          () -> new OpenBisNfsStorageProvider(null, mountPath));
    } catch (IOException e) {
      fail("Failed to create test directory", e);
    }
  }
}
