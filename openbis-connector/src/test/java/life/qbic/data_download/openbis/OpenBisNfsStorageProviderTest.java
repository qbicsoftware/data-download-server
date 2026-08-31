package life.qbic.data_download.openbis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.FromToRange;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenBisNfsStorageProviderTest {

  @TempDir
  Path tempDir;

  private Path mountPath;
  private FakeMeasurementDataProvider fakeProvider;
  private OpenBisNfsStorageProvider nfsProvider;

  @BeforeEach
  void setUp() throws IOException {
    mountPath = tempDir.resolve("mount");
    Files.createDirectories(mountPath);
    
    // Create test files
    createTestFile("Fastq1/read1.fastq.gz", "read1 content here");
    createTestFile("Fastq1/read2.fastq.gz", "read2 content here");
    createTestFile("Fastq2/read3.fastq.gz", "read3 content here");
    
    fakeProvider = new FakeMeasurementDataProvider(List.of(
        new FileInfo("Fastq1/read1.fastq.gz", "read1.fastq.gz", 18, 123456789L, 1000L, 2000L),
        new FileInfo("Fastq1/read2.fastq.gz", "read2.fastq.gz", 18, 987654321L, 1000L, 2000L),
        new FileInfo("Fastq2/read3.fastq.gz", "read3.fastq.gz", 18, 111111111L, 1000L, 2000L)
    ));
    
    nfsProvider = new OpenBisNfsStorageProvider(fakeProvider, mountPath, Duration.ofMinutes(1));
  }

  private void createTestFile(String relativePath, String content) throws IOException {
    Path file = mountPath.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  @Test
  @DisplayName("listFiles returns files in sorted order with correct metadata")
  void listFilesReturnsSortedFiles() {
    var files = nfsProvider.listFiles("dataset-1");
    
    assertEquals(3, files.size());
    assertEquals("Fastq1/read1.fastq.gz", files.get(0).path());
    assertEquals("Fastq1/read2.fastq.gz", files.get(1).path());
    assertEquals("Fastq2/read3.fastq.gz", files.get(2).path());
    
    // Check metadata
    assertEquals(18, files.get(0).size());
    assertEquals("crc32", files.get(0).checksum().algorithm());
    assertEquals("123456789", files.get(0).checksum().value());
  }

  @Test
  @DisplayName("getFile streams the whole file content")
  void getFileStreamsWholeFile() throws IOException {
    var dataFile = nfsProvider.getFile("dataset-1", 0);
    
    assertNotNull(dataFile);
    assertEquals("Fastq1/read1.fastq.gz", dataFile.fileInfo().path());
    
    try (InputStream is = dataFile.inputStream()) {
      String content = new String(is.readAllBytes());
      assertEquals("read1 content here", content);
    }
  }

  @Test
  @DisplayName("getFile with byte range streams partial content")
  void getFileWithByteRange() throws IOException {
    ByteRange range = new FromToRange(0, 4); // "read1"
    var dataFile = nfsProvider.getFile("dataset-1", 0, range);
    
    assertNotNull(dataFile);
    
    try (InputStream is = dataFile.inputStream()) {
      String content = new String(is.readAllBytes());
      assertEquals("read1", content);
    }
  }

  @Test
  @DisplayName("getFile with null range streams whole file")
  void getFileWithNullRange() throws IOException {
    var dataFile = nfsProvider.getFile("dataset-1", 0, null);
    
    try (InputStream is = dataFile.inputStream()) {
      String content = new String(is.readAllBytes());
      assertEquals("read1 content here", content);
    }
  }

  @Test
  @DisplayName("getFilePath returns the absolute path")
  void getFilePathReturnsAbsolutePath() {
    var path = nfsProvider.getFilePath("dataset-1", 0);
    
    assertTrue(path.isPresent());
    assertEquals(mountPath.resolve("Fastq1/read1.fastq.gz"), path.get());
  }

  @Test
  @DisplayName("getFileMetadata returns file metadata without streaming")
  void getFileMetadataReturnsMetadata() {
    var metadata = nfsProvider.getFileMetadata("dataset-1", 1);
    
    assertEquals("Fastq1/read2.fastq.gz", metadata.path());
    assertEquals(18, metadata.size());
    assertEquals("crc32", metadata.checksum().algorithm());
    assertEquals("987654321", metadata.checksum().value());
  }

  @Test
  @DisplayName("getFile throws StorageFileNotFoundException for invalid index")
  void getFileThrowsForInvalidIndex() {
    assertThrows(StorageFileNotFoundException.class, () -> nfsProvider.getFile("dataset-1", 99));
  }

  @Test
  @DisplayName("listFiles throws DatasetNotFoundException for unknown dataset")
  void listFilesThrowsForUnknownDataset() {
    fakeProvider.setFiles(List.of());
    assertThrows(DatasetNotFoundException.class, () -> nfsProvider.listFiles("unknown"));
  }

  @Test
  @DisplayName("getFile throws when file doesn't exist on filesystem")
  void getFileThrowsWhenFileNotOnFilesystem() throws IOException {
    // Delete the file from filesystem
    Files.deleteIfExists(mountPath.resolve("Fastq1/read1.fastq.gz"));
    
    assertThrows(StorageProviderException.class, () -> nfsProvider.getFile("dataset-1", 0));
  }

  @Test
  @DisplayName("files are cached within TTL")
  void filesAreCachedWithinTtl() {
    nfsProvider.listFiles("dataset-1");
    nfsProvider.listFiles("dataset-1");
    nfsProvider.listFiles("dataset-1");
    
    assertEquals(1, fakeProvider.listFilesCalls);
  }

  @Test
  @DisplayName("constructor validates mount path is a directory")
  void constructorValidatesMountPath() {
    Path notADir = tempDir.resolve("not-a-dir");
    assertThrows(IllegalArgumentException.class, 
        () -> new OpenBisNfsStorageProvider(fakeProvider, notADir));
  }

  @Test
  @DisplayName("constructor validates cache TTL is positive")
  void constructorValidatesCacheTtl() {
    assertThrows(IllegalArgumentException.class,
        () -> new OpenBisNfsStorageProvider(fakeProvider, mountPath, Duration.ZERO));
    assertThrows(IllegalArgumentException.class,
        () -> new OpenBisNfsStorageProvider(fakeProvider, mountPath, Duration.ofSeconds(-1)));
  }

  /**
   * A fake MeasurementDataProvider for testing.
   */
  private static final class FakeMeasurementDataProvider implements MeasurementDataProvider {
    private List<FileInfo> files;
    int listFilesCalls = 0;

    FakeMeasurementDataProvider(List<FileInfo> files) {
      this.files = files;
    }

    void setFiles(List<FileInfo> files) {
      this.files = files;
    }

    @Override
    public MeasurementData loadData(MeasurementId measurementId) {
      return null;
    }

    @Override
    public List<FileInfo> listFiles(MeasurementId measurementId) {
      listFilesCalls++;
      return files;
    }

    @Override
    public DataFile loadFile(MeasurementId measurementId, FileInfo fileInfo) {
      return null;
    }
  }
}
