package life.qbic.data_download.rest.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.ByteRangeProvider;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.FileInfo;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class MeasurementFileControllerTest {

  private static final FileInfo CHECKSUM_FILE = new FileInfo("/data/read1.fastq.gz",
      "read1.fastq.gz", 1024, new FileInfo.Checksum("crc32", "123456789"), 1700000000000L,
      1700000001000L);
  private static final FileInfo NO_CHECKSUM_FILE = new FileInfo("/data/read2.fastq.gz",
      "read2.fastq.gz", 2048, null, -1, -1);

  private FakeProviderRegistry providerRegistry;
  private StorageFileIndex storageFileIndex;
  private MeasurementFileController controller;

  @BeforeEach
  void setUp() {
    providerRegistry = new FakeProviderRegistry();
    storageFileIndex = new StorageFileIndex(providerRegistry, Duration.ofMinutes(1));
    controller = new MeasurementFileController(
        providerRegistry, storageFileIndex,
        1024, 4, 30000L, 3);
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("test-user", null));
  }

  @Nested
  @DisplayName("manifest endpoint")
  class ManifestEndpoint {

    @Test
    @DisplayName("returns the manifest with file entries for a known dataset")
    void returnsManifest() {
      providerRegistry.provider = new FakeStorageProvider(List.of(CHECKSUM_FILE, NO_CHECKSUM_FILE));

      ResponseEntity<MeasurementManifest> response = controller.manifest("M-1");

      assertEquals(HttpStatus.OK, response.getStatusCode());
      MeasurementManifest manifest = response.getBody();
      assertNotNull(manifest);
      assertEquals("M-1", manifest.measurementId());
      assertEquals(2, manifest.files().size());

      MeasurementManifest.FileEntry first = manifest.files().get(0);
      assertEquals(0, first.index());
      assertEquals("/data/read1.fastq.gz", first.path());
      assertEquals("read1.fastq.gz", first.fileName());
      assertEquals(1024, first.length());
      assertEquals(123456789L, first.crc32());
      assertNotNull(first.registrationTime());
      assertEquals("/measurements/M-1/files/0", first.links().download().href());

      MeasurementManifest.FileEntry second = manifest.files().get(1);
      assertEquals(0, second.crc32());
      assertNull(second.registrationTime());
    }

    @Test
    @DisplayName("throws MEASUREMENT_NOT_FOUND for an unknown dataset")
    void throwsForUnknownDataset() {
      providerRegistry.provider = new FakeStorageProvider(List.of());

      GlobalException ex = assertThrows(GlobalException.class,
          () -> controller.manifest("M-unknown"));
      assertEquals(GlobalException.ErrorCode.MEASUREMENT_NOT_FOUND, ex.errorCode());
    }

    @Test
    @DisplayName("throws ILLEGAL_MEASUREMENT_ID for identifiers with invalid characters")
    void throwsForIllegalId() {
      GlobalException ex = assertThrows(GlobalException.class,
          () -> controller.manifest("M 1!invalid"));
      assertEquals(GlobalException.ErrorCode.ILLEGAL_MEASUREMENT_ID, ex.errorCode());
    }
  }

  @Nested
  @DisplayName("download endpoint")
  class DownloadEndpoint {

    @Test
    @DisplayName("returns the file content for a valid request")
    void returnsFileContent() {
      FakeStorageProvider provider = new FakeStorageProvider(List.of(CHECKSUM_FILE));
      provider.fileContent = "hello world".getBytes();
      providerRegistry.provider = provider;

      ResponseEntity<StreamingResponseBody> response = controller.downloadFile("M-1", 0, null);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals(1024, response.getHeaders().getContentLength());
      assertEquals("read1.fastq.gz",
          response.getHeaders().getContentDisposition().getFilename());
    }

    @Test
    @DisplayName("throws FILE_NOT_FOUND for an out-of-bounds index")
    void throwsForOutOfBoundsIndex() {
      providerRegistry.provider = new FakeStorageProvider(List.of(CHECKSUM_FILE));

      GlobalException ex = assertThrows(GlobalException.class,
          () -> controller.downloadFile("M-1", 5, null));
      assertEquals(GlobalException.ErrorCode.FILE_NOT_FOUND, ex.errorCode());
    }

    @Test
    @DisplayName("sets Accept-Ranges header when provider supports byte ranges")
    void setsAcceptRangesForRangeCapableProvider() {
      providerRegistry.provider = new FakeRangeProvider(List.of(CHECKSUM_FILE));

      ResponseEntity<StreamingResponseBody> response = controller.downloadFile("M-1", 0, null);

      assertEquals("bytes", response.getHeaders().getFirst("Accept-Ranges"));
    }

    @Test
    @DisplayName("does not set Accept-Ranges header when provider does not support byte ranges")
    void noAcceptRangesForNonRangeProvider() {
      providerRegistry.provider = new FakeStorageProvider(List.of(CHECKSUM_FILE));

      ResponseEntity<StreamingResponseBody> response = controller.downloadFile("M-1", 0, null);

      assertNull(response.getHeaders().getFirst("Accept-Ranges"));
    }

    @Test
    @DisplayName("returns 206 partial content for a valid range request on a range-capable provider")
    void returnsPartialContentForValidRange() {
      providerRegistry.provider = new FakeRangeProvider(List.of(CHECKSUM_FILE));

      ResponseEntity<StreamingResponseBody> response = controller.downloadFile("M-1", 0,
          "bytes=0-99");

      assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
      assertEquals(100, response.getHeaders().getContentLength());
      assertEquals("bytes 0-99/1024", response.getHeaders().getFirst("Content-Range"));
    }

    @Test
    @DisplayName("throws RANGE_NOT_SATISFIABLE for an invalid range header")
    void throwsForInvalidRange() {
      providerRegistry.provider = new FakeRangeProvider(List.of(CHECKSUM_FILE));

      GlobalException ex = assertThrows(GlobalException.class,
          () -> controller.downloadFile("M-1", 0, "bytes=abc"));
      assertEquals(GlobalException.ErrorCode.RANGE_NOT_SATISFIABLE, ex.errorCode());
    }

    @Test
    @DisplayName("throws RANGE_NOT_SATISFIABLE for an out-of-bounds range")
    void throwsForOutOfBoundsRange() {
      providerRegistry.provider = new FakeRangeProvider(List.of(CHECKSUM_FILE));

      GlobalException ex = assertThrows(GlobalException.class,
          () -> controller.downloadFile("M-1", 0, "bytes=2000-3000"));
      assertEquals(GlobalException.ErrorCode.RANGE_NOT_SATISFIABLE, ex.errorCode());
    }
  }

  /** A fake ProviderRegistry that returns a configurable provider. */
  private static final class FakeProviderRegistry implements ProviderRegistry {
    StorageProvider provider;

    @Override
    public StorageProvider getProvider(String datasetId) {
      return provider;
    }
  }

  /** A fake StorageProvider that returns a fixed file list and optional content. */
  private static class FakeStorageProvider implements StorageProvider {
    final List<FileInfo> files;
    byte[] fileContent = new byte[0];

    FakeStorageProvider(List<FileInfo> files) {
      this.files = files;
    }

    @Override
    public List<FileInfo> listFiles(String datasetId) {
      if (files.isEmpty()) {
        throw new DatasetNotFoundException(datasetId);
      }
      return files;
    }

    @Override
    public DataFile getFile(String datasetId, int index) {
      if (index < 0 || index >= files.size()) {
        throw new StorageFileNotFoundException(datasetId, index);
      }
      return new SimpleDataFile(files.get(index), fileContent);
    }

    @Override
    public FileInfo getFileMetadata(String datasetId, int index) {
      if (index < 0 || index >= files.size()) {
        throw new StorageFileNotFoundException(datasetId, index);
      }
      return files.get(index);
    }
  }

  /** A fake StorageProvider that also implements ByteRangeProvider. */
  private static final class FakeRangeProvider extends FakeStorageProvider
      implements ByteRangeProvider {

    FakeRangeProvider(List<FileInfo> files) {
      super(files);
    }

    @Override
    public DataFile getFile(String datasetId, int index, ByteRange range) {
      if (index < 0 || index >= files.size()) {
        throw new StorageFileNotFoundException(datasetId, index);
      }
      FileInfo fi = files.get(index);
      ByteRange.ResolvedRange resolved = range.resolve(fi.size());
      byte[] slice = new byte[(int) resolved.length()];
      return new SimpleDataFile(fi, slice);
    }
  }

  private static final class SimpleDataFile implements DataFile {
    private final FileInfo fileInfo;
    private final byte[] content;

    SimpleDataFile(FileInfo fileInfo, byte[] content) {
      this.fileInfo = fileInfo;
      this.content = content;
    }

    @Override
    public InputStream inputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public FileInfo fileInfo() {
      return fileInfo;
    }
  }
}
